package com.finotech.jewellery.shared.config;

import com.finotech.jewellery.shared.security.SecurityUtils;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.time.Instant;

/**
 * Fills createdAt/createdBy/updatedAt/updatedBy on every entity. Timestamps are
 * always UTC.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "utcDateTimeProvider")
public class JpaAuditingConfig {

    public static final String SYSTEM_USER = "system";

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(SecurityUtils.currentUsername().orElse(SYSTEM_USER));
    }

    @Bean
    public DateTimeProvider utcDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
