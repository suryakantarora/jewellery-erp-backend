package com.finotech.jewellery.modules.storefront.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore;
import com.finotech.jewellery.modules.storefront.infrastructure.CustomerAppStore.AccountRow;
import com.finotech.jewellery.shared.exception.BusinessException;
import com.finotech.jewellery.shared.exception.ForbiddenException;
import com.finotech.jewellery.shared.exception.UnauthorizedException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedCustomer;
import com.finotech.jewellery.shared.security.JwtService;
import com.finotech.jewellery.shared.security.SecurityProperties;
import com.finotech.jewellery.shared.utils.CodeGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Customer identity for the app: a phone number proved with a one-time code.
 *
 * <p>The customer is the shop's own customer record. Someone the shop already
 * knows signs in to their existing history; a new number creates a customer
 * the staff can see from then on.
 */
@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private static final int MAX_CHALLENGES_PER_HOUR = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerAppStore store;
    private final StorefrontProperties properties;
    private final SecurityProperties securityProperties;
    private final JwtService jwtService;
    private final OtpSender otpSender;
    private final ObjectMapper objectMapper;

    public record Challenge(String phone, int expiresInSeconds, int resendAfterSeconds) {
    }

    public record Session(ObjectNode account, String token) {
    }

    @Transactional
    public Challenge requestOtp(Tenant tenant, String rawPhone) {
        String phone = normalizePhone(rawPhone);
        StorefrontProperties.Otp otp = properties.otp();

        store.latestOpenChallenge(tenant.companyId(), phone).ifPresent(last -> {
            if (last.createdAt().plusSeconds(otp.resendSeconds()).isAfter(Instant.now())) {
                throw new BusinessException("Please wait before requesting another code");
            }
        });
        if (store.challengesSince(tenant.companyId(), phone, Instant.now().minus(Duration.ofHours(1)))
                >= MAX_CHALLENGES_PER_HOUR) {
            throw new BusinessException("Too many codes requested. Try again later.");
        }

        boolean fixed = StringUtils.hasText(otp.fixedCode());
        String code = fixed ? otp.fixedCode().trim() : randomCode(otp.length());
        store.createChallenge(tenant.companyId(), phone, hash(phone, code),
                Instant.now().plusSeconds(otp.ttlSeconds()));
        if (!fixed) {
            otpSender.send(phone, code, tenant.text("brandName", "your jeweller"));
        }
        return new Challenge(phone, otp.ttlSeconds(), otp.resendSeconds());
    }

    /**
     * Not {@code @Transactional} as a whole on purpose: a wrong code must
     * record the failed attempt and still answer 401, and an exception would
     * roll the counter back.
     */
    public Session verifyOtp(Tenant tenant, String rawPhone, String code) {
        String phone = normalizePhone(rawPhone);
        CustomerAppStore.OtpRow challenge = store.latestOpenChallenge(tenant.companyId(), phone)
                .filter(c -> c.expiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new UnauthorizedException("The code has expired. Request a new one."));
        if (challenge.attempts() >= properties.otp().maxAttempts()) {
            store.consumeChallenge(challenge.id());
            throw new UnauthorizedException("Too many wrong attempts. Request a new code.");
        }
        if (code == null || !MessageDigest.isEqual(
                challenge.codeHash().getBytes(StandardCharsets.UTF_8),
                hash(phone, code.trim()).getBytes(StandardCharsets.UTF_8))) {
            store.recordFailedAttempt(challenge.id());
            throw new UnauthorizedException("That code is not right");
        }
        store.consumeChallenge(challenge.id());

        AccountRow account = store.accountByPhone(tenant.companyId(), phone)
                .orElseGet(() -> register(tenant.companyId(), phone));
        requireActive(account);
        store.touchLogin(account.id());
        // Re-read: touchLogin may have created the profile row.
        account = store.account(tenant.companyId(), account.id()).orElseThrow();
        return new Session(toJson(account), issueToken(tenant.companyId(), account));
    }

    /** The signed-in customer, checked against the database on every request. */
    public AccountRow requireCustomer() {
        AuthenticatedCustomer principal = principal();
        AccountRow account = store.account(principal.companyId(), principal.customerId())
                .orElseThrow(() -> new UnauthorizedException("Sign in again"));
        if (account.tokenVersion() != principal.tokenVersion()) {
            throw new UnauthorizedException("Signed out. Sign in again.");
        }
        requireActive(account);
        return account;
    }

    public AuthenticatedCustomer principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedCustomer customer)) {
            throw new UnauthorizedException("Customer sign-in required");
        }
        return customer;
    }

    @Transactional
    public ObjectNode updateProfile(String name, String email, String avatar) {
        AccountRow account = requireCustomer();
        if (!StringUtils.hasText(name) || name.trim().length() > 200) {
            throw new ValidationException("Name is required (up to 200 characters)");
        }
        if (StringUtils.hasText(email) && !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new ValidationException("Email address is not valid");
        }
        UUID companyId = principal().companyId();
        store.updateProfile(companyId, account.id(), name.trim(),
                StringUtils.hasText(email) ? email.trim() : null,
                StringUtils.hasText(avatar) ? avatar.trim() : null);
        return toJson(store.account(companyId, account.id()).orElseThrow());
    }

    /** Signs out every device: tokens issued so far stop working. */
    @Transactional
    public void signOutEverywhere() {
        store.bumpTokenVersion(requireCustomer().id());
    }

    public ObjectNode toJson(AccountRow account) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", account.id().toString());
        node.put("name", account.profileComplete() ? account.fullName() : "");
        node.put("phone", account.phone());
        if (account.email() != null) {
            node.put("email", account.email());
        }
        if (account.avatar() != null) {
            node.put("avatar", account.avatar());
        }
        node.put("memberSince", account.memberSince().toString());
        if (account.tier() != null) {
            node.put("tier", account.tier());
        }
        return node;
    }

    private AccountRow register(UUID companyId, String phone) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = "APP-" + CodeGenerator.random(8);
            if (store.customerCodeExists(companyId, code)) {
                continue;
            }
            try {
                UUID id = store.createCustomer(companyId, code, phone);
                return store.account(companyId, id).orElseThrow();
            } catch (DuplicateKeyException ex) {
                // Two devices verifying the same new number at once.
                return store.accountByPhone(companyId, phone).orElseThrow(() -> ex);
            }
        }
        throw new IllegalStateException("Could not allocate a customer code");
    }

    private String issueToken(UUID companyId, AccountRow account) {
        return jwtService.issueCustomerToken(
                new AuthenticatedCustomer(account.id(), companyId, account.phone(),
                        account.tokenVersion()),
                Duration.ofDays(properties.customerTokenDays()));
    }

    private static void requireActive(AccountRow account) {
        if (!"ACTIVE".equals(account.status())) {
            throw new ForbiddenException("This account cannot be used. Please contact the shop.");
        }
    }

    /** {@code +} and digits only, so the same number always compares equal. */
    static String normalizePhone(String raw) {
        if (raw == null) {
            throw new ValidationException("Phone number is required");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15) {
            throw new ValidationException("Phone number is not valid");
        }
        return "+" + digits;
    }

    private static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** Keyed with the platform secret so a leaked table cannot be brute-forced offline. */
    private String hash(String phone, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((phone + ":" + code + ":" + securityProperties.jwt().secret())
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
