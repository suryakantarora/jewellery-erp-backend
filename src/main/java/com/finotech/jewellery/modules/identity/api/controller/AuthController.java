package com.finotech.jewellery.modules.identity.api.controller;

import com.finotech.jewellery.modules.identity.api.request.ChangePasswordRequest;
import com.finotech.jewellery.modules.identity.api.request.LoginRequest;
import com.finotech.jewellery.modules.identity.api.request.RefreshTokenRequest;
import com.finotech.jewellery.modules.identity.api.response.AuthResponse;
import com.finotech.jewellery.modules.identity.api.response.UserResponse;
import com.finotech.jewellery.modules.identity.application.service.AuthenticationService;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @Operation(summary = "Authenticate and obtain access + refresh tokens")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest http) {
        AuthResponse response = authenticationService.login(request,
                http.getHeader("User-Agent"), clientIp(http));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Exchange a refresh token for a new access token")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                             HttpServletRequest http) {
        AuthResponse response = authenticationService.refresh(request.refreshToken(),
                http.getHeader("User-Agent"), clientIp(http));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "Revoke the presented refresh token")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out"));
    }

    @Operation(summary = "Revoke every session of the current user")
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll() {
        authenticationService.logoutAllSessions(SecurityUtils.requireCurrentUser().userId());
        return ResponseEntity.ok(ApiResponse.ok(null, "All sessions revoked"));
    }

    @Operation(summary = "Change the password of the current user")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changeOwnPassword(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed"));
    }

    @Operation(summary = "Profile, roles and permissions of the current user")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authenticationService.currentUserProfile()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
