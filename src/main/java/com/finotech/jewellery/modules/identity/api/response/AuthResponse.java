package com.finotech.jewellery.modules.identity.api.response;

import java.time.Instant;

public record AuthResponse(String accessToken,
                           String refreshToken,
                           String tokenType,
                           Instant accessTokenExpiresAt,
                           boolean mustChangePassword,
                           UserResponse user) {
}
