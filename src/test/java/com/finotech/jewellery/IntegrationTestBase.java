package com.finotech.jewellery;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Boots the whole application against a real PostgreSQL, so Flyway migrations,
 * JPA mappings and genuine row locking are all exercised.
 *
 * <p>The database is chosen in this order:
 * <ol>
 *   <li>{@code TEST_DB_URL} / {@code TEST_DB_USERNAME} / {@code TEST_DB_PASSWORD}
 *       if set — useful in CI, or locally against a container you already run;</li>
 *   <li>otherwise a throwaway Testcontainers PostgreSQL.</li>
 * </ol>
 * If neither is reachable the integration tests are skipped rather than failing,
 * so {@code mvn test} still passes on a machine without Docker.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public abstract class IntegrationTestBase {

    private static final String EXTERNAL_URL = System.getenv("TEST_DB_URL");

    private static PostgreSQLContainer<?> container;
    private static boolean databaseAvailable;

    static {
        if (EXTERNAL_URL != null && !EXTERNAL_URL.isBlank()) {
            databaseAvailable = true;
        } else {
            try {
                container = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("jewellery")
                        .withUsername("jewellery")
                        .withPassword("jewellery");
                container.start();
                databaseAvailable = true;
            } catch (RuntimeException | LinkageError ex) {
                // No Docker environment on this machine.
                databaseAvailable = false;
            }
        }
    }

    @BeforeAll
    static void requireDatabase() {
        Assumptions.assumeTrue(databaseAvailable,
                "No test database available: set TEST_DB_URL or provide a Docker environment");
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (container != null) {
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username",
                    () -> envOrDefault("TEST_DB_USERNAME", "jewellery"));
            registry.add("spring.datasource.password",
                    () -> envOrDefault("TEST_DB_PASSWORD", "jewellery"));
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
