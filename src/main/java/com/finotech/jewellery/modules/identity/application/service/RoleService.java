package com.finotech.jewellery.modules.identity.application.service;

import com.finotech.jewellery.modules.identity.api.request.RoleRequest;
import com.finotech.jewellery.modules.identity.api.response.PermissionResponse;
import com.finotech.jewellery.modules.identity.api.response.RoleResponse;
import com.finotech.jewellery.modules.identity.domain.entity.Permission;
import com.finotech.jewellery.modules.identity.domain.entity.Role;
import com.finotech.jewellery.modules.identity.infrastructure.repository.PermissionRepository;
import com.finotech.jewellery.modules.identity.infrastructure.repository.RoleRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(RoleResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return roleRepository.findWithPermissionsById(id)
                .map(RoleResponse::from)
                .orElseThrow(() -> NotFoundException.of("Role", id));
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        if (roleRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Role code already exists: " + request.code());
        }
        Role role = new Role();
        role.setCode(request.code().trim().toUpperCase());
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setSystemRole(false);
        role.setSuperAdmin(false);
        role.replacePermissions(resolvePermissions(request.permissionIds()));

        Role saved = roleRepository.save(role);
        auditService.record("ROLE_CREATED", "Role", saved.getId(), null, RoleResponse.from(saved));
        return RoleResponse.from(saved);
    }

    @Transactional
    public RoleResponse update(UUID id, RoleRequest request) {
        Role role = roleRepository.findWithPermissionsById(id)
                .orElseThrow(() -> NotFoundException.of("Role", id));
        RoleResponse before = RoleResponse.from(role);

        if (role.isSystemRole() && !role.getCode().equalsIgnoreCase(request.code())) {
            throw new ValidationException("System role codes cannot be changed");
        }
        role.setCode(request.code().trim().toUpperCase());
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.replacePermissions(resolvePermissions(request.permissionIds()));

        RoleResponse after = RoleResponse.from(role);
        auditService.record("ROLE_UPDATED", "Role", id, before, after);
        return after;
    }

    @Transactional
    public void delete(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Role", id));
        if (role.isSystemRole()) {
            throw new ValidationException("System roles cannot be deleted");
        }
        roleRepository.delete(role);
        auditService.record("ROLE_DELETED", "Role", id, RoleResponse.from(role), null);
    }

    private Set<Permission> resolvePermissions(Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Permission> permissions = permissionRepository.findAllByIdIn(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new ValidationException("One or more permissions do not exist");
        }
        return new HashSet<>(permissions);
    }
}
