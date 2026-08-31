package com.finotech.jewellery.modules.identity.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(@NotBlank @Size(max = 100) String username,
                                @NotBlank @Size(min = 10, max = 100) String password,
                                @NotBlank @Size(max = 150) String fullName,
                                @Email @Size(max = 150) String email,
                                @Size(max = 30) String phone,
                                @Size(max = 50) String employeeCode,
                                UUID primaryBranchId,
                                @NotNull Set<UUID> roleIds,
                                Set<UUID> branchIds) {
}
