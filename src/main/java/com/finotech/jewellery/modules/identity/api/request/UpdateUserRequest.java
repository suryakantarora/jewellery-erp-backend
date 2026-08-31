package com.finotech.jewellery.modules.identity.api.request;

import com.finotech.jewellery.modules.identity.domain.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName,
                                @Email @Size(max = 150) String email,
                                @Size(max = 30) String phone,
                                @Size(max = 50) String employeeCode,
                                UserStatus status,
                                UUID primaryBranchId,
                                Set<UUID> roleIds,
                                Set<UUID> branchIds) {
}
