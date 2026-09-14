package com.finotech.jewellery.modules.identity.application.service;

import com.finotech.jewellery.modules.identity.api.request.ChangePasswordRequest;
import com.finotech.jewellery.modules.identity.api.request.LoginRequest;
import com.finotech.jewellery.modules.identity.api.response.AuthResponse;
import com.finotech.jewellery.modules.identity.api.response.UserResponse;
import com.finotech.jewellery.modules.identity.domain.entity.RefreshToken;
import com.finotech.jewellery.modules.identity.domain.entity.User;
import com.finotech.jewellery.modules.identity.domain.enums.UserStatus;
import com.finotech.jewellery.modules.identity.infrastructure.repository.RefreshTokenRepository;
import com.finotech.jewellery.modules.identity.infrastructure.repository.UserRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.UnauthorizedException;
import com.finotech.jewellery.shared.exception.ValidationException;
import com.finotech.jewellery.shared.security.AuthenticatedUser;
import com.finotech.jewellery.shared.security.JwtService;
import com.finotech.jewellery.shared.security.SecurityUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.finotech.jewellery.modules.organization.application.OrganizationDirectory;

/**
 * Login, token refresh, logout and password change.
 *
 * <p>Refresh tokens are opaque random values; only their hash is persisted and
 * each refresh rotates the token so a replayed token is detectable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final OrganizationDirectory organizationDirectory;

    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String ipAddress) {
        User user = userRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!user.isLoginAllowed()) {
            auditService.record("LOGIN_BLOCKED", "User", user.getId(), null,
                    java.util.Map.of("status", user.getStatus()));
            throw new UnauthorizedException("Account is not permitted to log in");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new UnauthorizedException("Invalid username or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());

        AuthenticatedUser principal = toPrincipal(user);
        String accessToken = jwtService.issueAccessToken(principal);
        String refreshToken = issueRefreshToken(user.getId(), userAgent, ipAddress);

        auditService.record("LOGIN_SUCCESS", "User", user.getId(), null, null);

        return new AuthResponse(accessToken, refreshToken, "Bearer",
                Instant.now().plus(jwtService.accessTokenTtl()),
                user.isMustChangePassword(), profile(user));
    }

    @Transactional
    public AuthResponse refresh(String rawToken, String userAgent, String ipAddress) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!stored.isActive()) {
            // A revoked token being presented again suggests theft: cut every session.
            refreshTokenRepository.revokeAllForUser(stored.getUserId(), Instant.now());
            auditService.record("REFRESH_TOKEN_REUSE", "User", stored.getUserId(), null, null);
            throw new UnauthorizedException("Refresh token is no longer valid");
        }

        User user = userRepository.findWithAuthoritiesById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!user.isLoginAllowed()) {
            throw new UnauthorizedException("Account is not permitted to log in");
        }

        stored.setRevokedAt(Instant.now());
        String rotated = issueRefreshToken(user.getId(), userAgent, ipAddress);
        String accessToken = jwtService.issueAccessToken(toPrincipal(user));

        return new AuthResponse(accessToken, rotated, "Bearer",
                Instant.now().plus(jwtService.accessTokenTtl()),
                user.isMustChangePassword(), profile(user));
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isActive)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void logoutAllSessions(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        auditService.record("LOGOUT_ALL", "User", userId, null, null);
    }

    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        UUID userId = SecurityUtils.requireCurrentUser().userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ValidationException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ValidationException("New password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());

        // Force re-authentication everywhere after a credential change.
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        auditService.record("PASSWORD_CHANGED", "User", userId, null, null);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUserProfile() {
        UUID userId = SecurityUtils.requireCurrentUser().userId();
        return userRepository.findWithAuthoritiesById(userId)
                .map(this::profile)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private UserResponse profile(User user) {
        return UserResponse.from(user,
                organizationDirectory.companyName(user.getCompanyId()).orElse(null));
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plusSeconds(LOCK_MINUTES * 60));
            user.setStatus(UserStatus.LOCKED);
            auditService.record("ACCOUNT_LOCKED", "User", user.getId(), null,
                    java.util.Map.of("failedAttempts", attempts));
        }
    }

    private AuthenticatedUser toPrincipal(User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.permissionCodes(),
                new HashSet<>(user.getBranchIds()), user.isSuperAdmin(), user.getCompanyId());
    }

    private String issueRefreshToken(UUID userId, String userAgent, String ipAddress) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(jwtService.refreshTokenTtl()));
        token.setUserAgent(truncate(userAgent, 255));
        token.setIpAddress(truncate(ipAddress, 64));
        refreshTokenRepository.save(token);
        return raw;
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
