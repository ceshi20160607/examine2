package com.unique.unexamine.platform.manage.bootstrap;

import com.unique.unexamine.authentication.manage.Pbkdf2PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DefaultPlatformAdministratorInitializerTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.bootstrap.default-admin.deployment-key", () -> "integration-bootstrap-key-2026");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DefaultPlatformAdministratorInitializer initializer;

    @Autowired
    private Pbkdf2PasswordHasher passwordHasher;

    @Autowired
    private ApplicationArguments applicationArguments;

    @Test
    void createsDefaultAdministratorOnceWithoutResettingCredential() throws Exception {
        String originalHash = jdbc.queryForObject(
                "select c.password_hash from plat_account_credential c join plat_account a on a.id = c.account_id where a.username = 'admin'",
                String.class);
        Integer originalVersion = jdbc.queryForObject(
                "select c.credential_version from plat_account_credential c join plat_account a on a.id = c.account_id where a.username = 'admin'",
                Integer.class);

        initializer.run(applicationArguments);

        assertThat(jdbc.queryForObject("select count(*) from plat_account where username = 'admin'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from plat_account_credential", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from plat_member", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from plat_member_role", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from plat_role_permission", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where event_code = 'PLATFORM_DEFAULT_ADMIN_CREATED'", Integer.class)).isOne();
        assertThat(originalHash).doesNotContain("123123aa");
        assertThat(passwordHasher.matches("123123aa".toCharArray(), originalHash)).isTrue();
        assertThat(jdbc.queryForObject("select password_hash from plat_account_credential", String.class)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("select credential_version from plat_account_credential", Integer.class)).isEqualTo(originalVersion);
    }

    @Test
    void rejectsMissingOrShortDeploymentKeysBeforeInitialization() {
        assertThatThrownBy(() -> DefaultPlatformAdministratorInitializer.validateDeploymentKey(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deployment key");
        assertThatThrownBy(() -> DefaultPlatformAdministratorInitializer.validateDeploymentKey("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deployment key");
    }
}
