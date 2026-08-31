package com.finotech.jewellery.modules.identity.domain.entity;

import com.finotech.jewellery.modules.identity.domain.enums.UserStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A back-office or POS operator. Branch access is stored here because nearly
 * every downstream operation is branch-scoped.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "app_user", schema = "identity")
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "employee_code", length = 50)
    private String employeeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "primary_branch_id")
    private UUID primaryBranchId;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_role", schema = "identity",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_branch", schema = "identity",
            joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "branch_id", nullable = false)
    private Set<UUID> branchIds = new HashSet<>();

    public boolean isSuperAdmin() {
        return roles.stream().anyMatch(Role::isSuperAdmin);
    }

    public Set<String> permissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        roles.forEach(role -> role.getPermissions().forEach(p -> codes.add(p.getCode())));
        return codes;
    }

    public boolean isLoginAllowed() {
        return status == UserStatus.ACTIVE
                && (lockedUntil == null || lockedUntil.isBefore(Instant.now()));
    }
}
