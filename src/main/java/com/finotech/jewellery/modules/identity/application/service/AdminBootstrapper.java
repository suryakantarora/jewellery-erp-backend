package com.finotech.jewellery.modules.identity.application.service;

import com.finotech.jewellery.modules.identity.domain.entity.Role;
import com.finotech.jewellery.modules.identity.domain.entity.User;
import com.finotech.jewellery.modules.identity.domain.enums.UserStatus;
import com.finotech.jewellery.modules.identity.infrastructure.repository.RoleRepository;
import com.finotech.jewellery.modules.identity.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first super administrator on an empty database. The password
 * comes from the environment and must be changed at first login — no
 * credentials are ever committed to migrations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapper implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${jewellery.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${jewellery.bootstrap.admin-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIgnoreCase(adminUsername)) {
            return;
        }
        if (!StringUtils.hasText(adminPassword)) {
            log.warn("No admin user exists and jewellery.bootstrap.admin-password is not set. "
                    + "Set it once to create the initial super administrator.");
            return;
        }
        Role superAdmin = roleRepository.findByCodeIgnoreCase("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing; run migrations"));

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName("System Administrator");
        admin.setStatus(UserStatus.ACTIVE);
        admin.setMustChangePassword(true);
        admin.setPasswordChangedAt(Instant.now());
        admin.setRoles(Set.of(superAdmin));
        userRepository.save(admin);
        log.info("Created initial super administrator '{}'", adminUsername);
    }
}
