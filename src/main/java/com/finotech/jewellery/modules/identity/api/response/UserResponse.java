package com.finotech.jewellery.modules.identity.api.response;

import com.finotech.jewellery.modules.identity.domain.entity.User;
import com.finotech.jewellery.modules.identity.domain.enums.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id,
                           String username,
                           String fullName,
                           String email,
                           String phone,
                           String employeeCode,
                           UserStatus status,
                           UUID primaryBranchId,
                           Set<UUID> branchIds,
                           List<String> roles,
                           Set<String> permissions,
                           Instant lastLoginAt,
                           UUID companyId,
                           String companyName) {

    public static UserResponse from(User user) {
        return from(user, null);
    }

    /** With the company's display name resolved by the caller. */
    public static UserResponse from(User user, String companyName) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getEmployeeCode(),
                user.getStatus(),
                user.getPrimaryBranchId(),
                Set.copyOf(user.getBranchIds()),
                user.getRoles().stream().map(r -> r.getCode()).toList(),
                user.permissionCodes(),
                user.getLastLoginAt(),
                user.getCompanyId(),
                companyName);
    }
}
