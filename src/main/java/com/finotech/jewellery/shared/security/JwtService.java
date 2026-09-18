package com.finotech.jewellery.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Issues and validates stateless access tokens. Refresh tokens are opaque and
 * persisted by the identity module, so they can be revoked.
 */
@Service
public class JwtService {

    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_BRANCHES = "branches";
    private static final String CLAIM_SUPER_ADMIN = "sa";
    private static final String CLAIM_USERNAME = "username";
    /** The tenant. Absent on tokens issued before company scoping existed. */
    private static final String CLAIM_COMPANY = "co";
    /** Principal type. Absent on staff tokens, {@code customer} on customer app tokens. */
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_CUSTOMER = "customer";
    private static final String CLAIM_PHONE = "phone";
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecurityProperties properties;
    private final SecretKey key;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        String secret = properties.jwt().secret();
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jewellery.security.jwt.secret must be set and at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Duration accessTokenTtl() {
        return Duration.ofMinutes(properties.jwt().accessTokenMinutes());
    }

    public Duration refreshTokenTtl() {
        return Duration.ofDays(properties.jwt().refreshTokenDays());
    }

    public String issueAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.userId().toString())
                .claim(CLAIM_USERNAME, user.username())
                .claim(CLAIM_PERMISSIONS, List.copyOf(user.permissions()))
                .claim(CLAIM_BRANCHES, user.branchIds().stream().map(UUID::toString).toList())
                .claim(CLAIM_SUPER_ADMIN, user.superAdmin())
                .claim(CLAIM_COMPANY, user.companyId() == null ? null : user.companyId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    /**
     * A customer app token. Long-lived because the app has no refresh flow: a
     * customer who signed in with an OTP stays signed in, and
     * {@code tokenVersion} is what revokes it.
     */
    public String issueCustomerToken(AuthenticatedCustomer customer, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(customer.customerId().toString())
                .claim(CLAIM_TYPE, TYPE_CUSTOMER)
                .claim(CLAIM_COMPANY, customer.companyId().toString())
                .claim(CLAIM_PHONE, customer.phone())
                .claim(CLAIM_TOKEN_VERSION, customer.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * @return an {@link AuthenticatedUser} or an {@link AuthenticatedCustomer}
     * @throws JwtException when the token is expired, tampered with or malformed
     */
    public Object parsePrincipal(String token) {
        Claims claims = claims(token);
        if (TYPE_CUSTOMER.equals(claims.get(CLAIM_TYPE, String.class))) {
            Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
            return new AuthenticatedCustomer(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_COMPANY, String.class)),
                    claims.get(CLAIM_PHONE, String.class),
                    version == null ? 0 : version);
        }
        return toUser(claims);
    }

    /**
     * @return the principal encoded in the token
     * @throws JwtException when the token is expired, tampered with or malformed
     */
    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = claims(token);
        if (TYPE_CUSTOMER.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("Not a staff access token");
        }
        return toUser(claims);
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.jwt().issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static AuthenticatedUser toUser(Claims claims) {
        List<?> rawPermissions = claims.get(CLAIM_PERMISSIONS, List.class);
        Set<String> permissions = new LinkedHashSet<>();
        if (rawPermissions != null) {
            rawPermissions.forEach(p -> permissions.add(String.valueOf(p)));
        }
        Set<UUID> branches = new HashSet<>();
        List<?> rawBranches = claims.get(CLAIM_BRANCHES, List.class);
        if (rawBranches != null) {
            rawBranches.forEach(b -> branches.add(UUID.fromString(String.valueOf(b))));
        }
        String company = claims.get(CLAIM_COMPANY, String.class);
        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                permissions,
                branches,
                Boolean.TRUE.equals(claims.get(CLAIM_SUPER_ADMIN, Boolean.class)),
                StringUtils.hasText(company) ? UUID.fromString(company) : null);
    }
}
