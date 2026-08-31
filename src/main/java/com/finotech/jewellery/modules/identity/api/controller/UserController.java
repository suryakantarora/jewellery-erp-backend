package com.finotech.jewellery.modules.identity.api.controller;

import com.finotech.jewellery.modules.identity.api.request.CreateUserRequest;
import com.finotech.jewellery.modules.identity.api.request.ResetPasswordRequest;
import com.finotech.jewellery.modules.identity.api.request.UpdateUserRequest;
import com.finotech.jewellery.modules.identity.api.response.UserResponse;
import com.finotech.jewellery.modules.identity.application.service.UserService;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "List users")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID branchId,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(userService.search(search, branchId, pageable)));
    }

    @Operation(summary = "Get a user")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.get(id)));
    }

    @Operation(summary = "Create a user")
    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(userService.create(request)));
    }

    @Operation(summary = "Update a user")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, request)));
    }

    @Operation(summary = "Reset a user password")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable UUID id,
                                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password reset"));
    }

    @Operation(summary = "Deactivate a user")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        userService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deactivated"));
    }
}
