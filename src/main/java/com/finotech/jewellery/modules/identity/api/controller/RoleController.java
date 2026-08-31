package com.finotech.jewellery.modules.identity.api.controller;

import com.finotech.jewellery.modules.identity.api.request.RoleRequest;
import com.finotech.jewellery.modules.identity.api.response.PermissionResponse;
import com.finotech.jewellery.modules.identity.api.response.RoleResponse;
import com.finotech.jewellery.modules.identity.application.service.RoleService;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Roles & Permissions")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "List roles")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.listRoles()));
    }

    @Operation(summary = "Get a role")
    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<RoleResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.get(id)));
    }

    @Operation(summary = "Create a role")
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody RoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(roleService.create(request)));
    }

    @Operation(summary = "Update a role")
    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody RoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, request)));
    }

    @Operation(summary = "Delete a non-system role")
    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role deleted"));
    }

    @Operation(summary = "List every permission in the catalogue")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> permissions() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.listPermissions()));
    }
}
