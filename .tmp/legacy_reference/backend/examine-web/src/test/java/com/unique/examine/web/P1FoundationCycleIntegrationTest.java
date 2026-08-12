package com.unique.examine.web;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.vnext.manage.bootstrap.DefaultAdminBootstrap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ActiveProfiles("vnext")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class P1FoundationCycleIntegrationTest {
    private static final String BOOTSTRAP_PASSWORD = "P1-Cycle-Integration-Secret-84!";
    private static final long SENTINEL_ID = 9_901_001_001_001_001L;
    private static final Set<String> P1_TABLES = Set.of(
            "un_sys_idempotency",
            "un_audit_security",
            "un_plat_account",
            "un_plat_credential",
            "un_plat_system",
            "un_plat_tenant",
            "un_plat_member",
            "un_plat_member_tenant",
            "un_plat_data_scope",
            "un_plat_role",
            "un_plat_permission",
            "un_plat_role_permission",
            "un_plat_member_role",
            "un_plat_account_role",
            "un_plat_authz_epoch",
            "un_plat_context_session",
            "un_plat_refresh_token"
    );
    private static final Set<String> ADMIN_PERMISSION_CODES = Set.of(
            "platform.runtime.access",
            "platform.admin.access",
            "platform.system.manage"
    );

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.10"))
            .withDatabaseName("examine2_p1_foundation_cycle")
            .withUsername("examine_p1_foundation_cycle")
            .withPassword("container-database-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void foundationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration-vnext");
        registry.add("spring.flyway.clean-disabled", () -> true);
        registry.add("examine.foundation.vnext.enabled", () -> true);
        registry.add("examine.foundation.vnext.bootstrap.username", () -> "admin");
        registry.add("examine.foundation.vnext.bootstrap.password", () -> BOOTSTRAP_PASSWORD);
        registry.add("examine.foundation.vnext.bootstrap.display-name", () -> "P1 Cycle Administrator");
        registry.add("examine.scheduling.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordService passwordService;

    @LocalServerPort
    private int firstPort;

    @Test
    void cleanMigrationAndBootstrapRemainIdempotentAcrossTwoRealSpringStarts() {
        assertThat(firstPort).isPositive();
        assertCleanFoundationMigration(jdbc);
        assertDeferredTablesAreAbsent(jdbc);
        assertBootstrapFieldsDoNotDependOnMappersOrJdbc();

        var migrationBeforeRestart = migrationHistory(jdbc);
        var bootstrapBeforeRestart = assertSingleAdministratorGraph(jdbc);
        assertSecurePassword(bootstrapBeforeRestart);
        insertSentinel(jdbc);

        try (var secondContext = startSecondApplication()) {
            var secondPort = ((ServletWebServerApplicationContext) secondContext).getWebServer().getPort();
            assertThat(secondPort).isPositive().isNotEqualTo(firstPort);
            var secondJdbc = secondContext.getBean(JdbcTemplate.class);

            assertThat(migrationHistory(secondJdbc)).isEqualTo(migrationBeforeRestart);
            assertSentinelSurvived(secondJdbc);
            assertDeferredTablesAreAbsent(secondJdbc);
            var bootstrapAfterRestart = assertSingleAdministratorGraph(secondJdbc);
            assertThat(bootstrapAfterRestart).isEqualTo(bootstrapBeforeRestart);
            assertSecurePassword(bootstrapAfterRestart);
        }
    }

    private static void assertCleanFoundationMigration(JdbcTemplate template) {
        var tables = template.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema=DATABASE() AND table_name REGEXP '^un_' ORDER BY table_name",
                String.class
        );
        assertThat(tables).containsExactlyInAnyOrderElementsOf(P1_TABLES);

        var history = migrationHistory(template);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().version()).isEqualTo("1.0.0");
        assertThat(history.getFirst().script())
                .isEqualTo("V1_0_0__foundation_identity_system_context.sql");
        assertThat(history.getFirst().success()).isTrue();
    }

    private static void assertDeferredTablesAreAbsent(JdbcTemplate template) {
        assertThat(count(template, "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() "
                + "AND table_name IN ('un_plat_role_draft','un_plat_authz_version')")).isZero();
    }

    private static void assertBootstrapFieldsDoNotDependOnMappersOrJdbc() {
        var fieldTypes = Arrays.stream(DefaultAdminBootstrap.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .toList();

        assertThat(fieldTypes).noneMatch(type -> BaseMapper.class.isAssignableFrom(type));
        assertThat(fieldTypes).noneMatch(type -> JdbcOperations.class.isAssignableFrom(type));
        assertThat(fieldTypes).noneMatch(type -> DataSource.class.isAssignableFrom(type));
        assertThat(fieldTypes).noneMatch(type -> type.getName().startsWith("java.sql."));
        assertThat(fieldTypes).noneMatch(type -> type.getName().contains(".base.mapper."));
    }

    private BootstrapSnapshot assertSingleAdministratorGraph(JdbcTemplate template) {
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_account")).isEqualTo(1);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_account "
                + "WHERE username_normalized='admin' AND status='ACTIVE'")).isEqualTo(1);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_credential")).isEqualTo(1);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_role")).isEqualTo(1);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_role "
                + "WHERE scope_type='PLATFORM' AND scope_key=0 "
                + "AND role_code='platform_root' AND role_type='ROOT' AND status='ACTIVE'")).isEqualTo(1);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_permission")).isEqualTo(3);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_role_permission")).isEqualTo(3);
        assertThat(count(template, "SELECT COUNT(DISTINCT permission_id) FROM un_plat_role_permission"))
                .isEqualTo(3);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_account_role")).isEqualTo(1);

        var permissionCodes = template.queryForList(
                "SELECT permission_code FROM un_plat_permission "
                        + "WHERE scope_type='PLATFORM' AND scope_key=0 ORDER BY permission_code",
                String.class
        );
        assertThat(permissionCodes).containsExactlyInAnyOrderElementsOf(ADMIN_PERMISSION_CODES);

        var assignedPermissionCodes = template.queryForList(
                "SELECT p.permission_code FROM un_plat_role_permission rp "
                        + "JOIN un_plat_role r ON r.id=rp.role_id "
                        + "JOIN un_plat_permission p ON p.id=rp.permission_id "
                        + "WHERE r.scope_type='PLATFORM' AND r.scope_key=0 "
                        + "AND r.role_code='platform_root' AND rp.effect='ALLOW' "
                        + "ORDER BY p.permission_code",
                String.class
        );
        assertThat(assignedPermissionCodes).containsExactlyInAnyOrderElementsOf(ADMIN_PERMISSION_CODES);
        assertThat(count(template, "SELECT COUNT(*) FROM un_plat_account_role ar "
                + "JOIN un_plat_account a ON a.id=ar.account_id "
                + "JOIN un_plat_role r ON r.id=ar.role_id "
                + "WHERE a.username_normalized='admin' AND r.role_code='platform_root' "
                + "AND ar.scope_type='PLATFORM' AND ar.scope_key=0")).isEqualTo(1);

        var permissionIds = List.copyOf(template.queryForList(
                "SELECT id FROM un_plat_permission ORDER BY id",
                Long.class
        ));
        var credentials = template.query(
                "SELECT a.id AS account_id,c.id AS credential_id,c.password_hash,"
                        + "c.password_algorithm,c.password_parameters,c.password_changed_at,r.id AS role_id "
                        + "FROM un_plat_account a "
                        + "JOIN un_plat_credential c ON c.account_id=a.id AND c.credential_type='PASSWORD' "
                        + "JOIN un_plat_account_role ar ON ar.account_id=a.id "
                        + "JOIN un_plat_role r ON r.id=ar.role_id AND r.role_code='platform_root' "
                        + "WHERE a.username_normalized='admin'",
                (resultSet, rowNumber) -> new BootstrapSnapshot(
                        resultSet.getLong("account_id"),
                        resultSet.getLong("credential_id"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("password_algorithm"),
                        resultSet.getString("password_parameters"),
                        timestamp(resultSet.getTimestamp("password_changed_at")),
                        resultSet.getLong("role_id"),
                        permissionIds
                )
        );
        assertThat(credentials).hasSize(1);
        return credentials.getFirst();
    }

    private void assertSecurePassword(BootstrapSnapshot snapshot) {
        assertThat(snapshot.passwordHash()).startsWith("$argon2id$v=19$");
        assertThat(snapshot.passwordAlgorithm()).isEqualTo("ARGON2ID");
        assertThat(snapshot.passwordParameters()).isEqualTo("m=65536,t=3,p=1");
        assertTrue(passwordService.matches(BOOTSTRAP_PASSWORD, snapshot.passwordHash()));
        assertTrue(!snapshot.passwordHash().equals(BOOTSTRAP_PASSWORD));
    }

    private static void insertSentinel(JdbcTemplate template) {
        assertThat(template.update(
                "INSERT INTO un_sys_idempotency "
                        + "(id,scope_type,scope_key,idempotency_key,request_hash,status,"
                        + "response_http_status,response_code,response_body,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,DATE_ADD(NOW(3),INTERVAL 1 DAY),NOW(3),NOW(3))",
                SENTINEL_ID,
                "P1_FOUNDATION_CYCLE",
                "SECOND_START",
                "sentinel-survives-restart",
                "a".repeat(64),
                "COMPLETED",
                200,
                "SENTINEL_OK",
                "preserved"
        )).isEqualTo(1);
    }

    private static void assertSentinelSurvived(JdbcTemplate template) {
        assertThat(count(template, "SELECT COUNT(*) FROM un_sys_idempotency "
                + "WHERE id=? AND scope_type='P1_FOUNDATION_CYCLE' "
                + "AND idempotency_key='sentinel-survives-restart' "
                + "AND response_code='SENTINEL_OK' AND response_body='preserved'", SENTINEL_ID))
                .isEqualTo(1);
    }

    private static List<MigrationRecord> migrationHistory(JdbcTemplate template) {
        return template.query(
                "SELECT installed_rank,version,description,script,checksum,success "
                        + "FROM flyway_schema_history ORDER BY installed_rank",
                (resultSet, rowNumber) -> new MigrationRecord(
                        resultSet.getInt("installed_rank"),
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getString("script"),
                        resultSet.getObject("checksum", Integer.class),
                        resultSet.getBoolean("success")
                )
        );
    }

    private static long count(JdbcTemplate template, String sql, Object... arguments) {
        return Objects.requireNonNull(template.queryForObject(sql, Long.class, arguments));
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return Objects.requireNonNull(value).toLocalDateTime();
    }

    private static ConfigurableApplicationContext startSecondApplication() {
        var application = new SpringApplication(ExamineApplication.class);
        application.setAdditionalProfiles("vnext");
        var properties = secondStartProperties();
        application.addInitializers(context -> {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("p1FoundationSecondStart", properties)
            );
            FoundationStartupDiagnostics.validateConfiguration(context.getEnvironment());
        });
        return application.run();
    }

    private static Map<String, Object> secondStartProperties() {
        var properties = new LinkedHashMap<String, Object>();
        properties.put("server.port", 0);
        properties.put("spring.datasource.url", MYSQL.getJdbcUrl());
        properties.put("spring.datasource.username", MYSQL.getUsername());
        properties.put("spring.datasource.password", MYSQL.getPassword());
        properties.put("spring.data.redis.host", REDIS.getHost());
        properties.put("spring.data.redis.port", REDIS.getMappedPort(6379));
        properties.put("spring.data.redis.ssl.enabled", false);
        properties.put("spring.flyway.enabled", true);
        properties.put("spring.flyway.locations", "classpath:db/migration-vnext");
        properties.put("spring.flyway.clean-disabled", true);
        properties.put("examine.foundation.vnext.enabled", true);
        properties.put("examine.foundation.vnext.bootstrap.username", "admin");
        properties.put("examine.foundation.vnext.bootstrap.password", BOOTSTRAP_PASSWORD);
        properties.put("examine.foundation.vnext.bootstrap.display-name", "P1 Cycle Administrator");
        properties.put("examine.scheduling.enabled", false);
        return Map.copyOf(properties);
    }

    private record MigrationRecord(
            int installedRank,
            String version,
            String description,
            String script,
            Integer checksum,
            boolean success
    ) {
    }

    private record BootstrapSnapshot(
            long accountId,
            long credentialId,
            String passwordHash,
            String passwordAlgorithm,
            String passwordParameters,
            LocalDateTime passwordChangedAt,
            long roleId,
            List<Long> permissionIds
    ) {
    }
}
