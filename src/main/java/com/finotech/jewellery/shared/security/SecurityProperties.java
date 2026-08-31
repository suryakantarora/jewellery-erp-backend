package com.finotech.jewellery.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings bound from {@code jewellery.security.*}. Secrets always come
 * from the environment, never from committed configuration.
 */
@ConfigurationProperties(prefix = "jewellery.security")
public record SecurityProperties(Jwt jwt, List<String> publicPaths) {

    public record Jwt(String issuer, String secret, long accessTokenMinutes, long refreshTokenDays) {
    }
}
