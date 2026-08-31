package com.finotech.jewellery.modules.identity.application.service;

import com.finotech.jewellery.modules.identity.api.request.CreateUserRequest;
import com.finotech.jewellery.modules.identity.api.request.ResetPasswordRequest;
import com.finotech.jewellery.modules.identity.api.request.UpdateUserRequest;
import com.finotech.jewellery.modules.identity.api.response.UserResponse;
import com.finotech.jewellery.modules.identity.domain.entity.Role;
import com.finotech.jewellery.modules.identity.domain.entity.User;
import com.finotech.jewellery.modules.identity.domain.enums.UserStatus;
import com.finotech.jewellery.modules.identity.infrastructure.repository.RefreshTokenRepository;
import com.finotech.jewellery.modules.identity.infrastructure.repository.RoleRepository;
import com.finotech.jewellery.modules.identity.infrastructure.repository.UserRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.common.PageResponse;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import com.finotech.jewellery.shared.exception.ValidationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration: creation, profile and role/branch assignment.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String search, UUID branchId, Pageable pageable) {
        return PageResponse.of(userRepository.search(search, branchId, pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userRepository.findWithAuthoritiesById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> NotFoundException.of("User", id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("Username already exists: " + request.username());
        }
        if (request.email() != null && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Email already in use: " + request.email());
        }

        User user = new User();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setEmployeeCode(request.employeeCode());
        user.setPrimaryBranchId(request.primaryBranchId());
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(Instant.now());
        user.setRoles(resolveRoles(request.roleIds()));
        user.setBranchIds(resolveBranches(request.branchIds(), request.primaryBranchId()));

        User saved = userRepository.save(user);
        auditService.record("USER_CREATED", "User", saved.getId(), null, UserResponse.from(saved));
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = userRepository.findWithAuthoritiesById(id)
                .orElseThrow(() -> NotFoundException.of("User", id));
        UserResponse before = UserResponse.from(user);

        user.setFullName(request.fullName().trim());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setEmployeeCode(request.employeeCode());
        user.setPrimaryBranchId(request.primaryBranchId());
        if (request.status() != null) {
            if (request.status() != UserStatus.LOCKED) {
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
            }
            user.setStatus(request.status());
        }
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(request.roleIds()));
        }
        if (request.branchIds() != null) {
            user.setBranchIds(resolveBranches(request.branchIds(), request.primaryBranchId()));
        }

        UserResponse after = UserResponse.from(user);
        auditService.record("USER_UPDATED", "User", id, before, after);
        return after;
    }

    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("User", id));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(request.mustChangePassword());
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }
        refreshTokenRepository.revokeAllForUser(id, Instant.now());
        auditService.record("PASSWORD_RESET", "User", id, null, null);
    }

    @Transactional
    public void deactivate(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("User", id));
        user.setStatus(UserStatus.INACTIVE);
        refreshTokenRepository.revokeAllForUser(id, Instant.now());
        auditService.record("USER_DEACTIVATED", "User", id, null, null);
    }

    private Set<Role> resolveRoles(Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new ValidationException("At least one role must be assigned");
        }
        Set<Role> roles = roleRepository.findAllByIdIn(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new ValidationException("One or more roles do not exist");
        }
        return new HashSet<>(roles);
    }

    private Set<UUID> resolveBranches(Set<UUID> branchIds, UUID primaryBranchId) {
        Set<UUID> branches = branchIds == null ? new HashSet<>() : new HashSet<>(branchIds);
        if (primaryBranchId != null) {
            branches.add(primaryBranchId);
        }
        return branches;
    }
}
