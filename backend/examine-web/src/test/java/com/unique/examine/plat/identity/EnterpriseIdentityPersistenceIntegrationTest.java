package com.unique.examine.plat.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EnterpriseIdentityPersistenceIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_identity_test")
            .withUsername("examine_test")
            .withPassword("test-only-password");

    private static JdbcTemplate jdbc;
    private static JdbcIdentityRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationRoot().toString().replace('\\', '/'))
                .load().migrate();
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        repository = new JdbcIdentityRepository(jdbc, new ObjectMapper());
    }

    @Test
    void persistsVersionedLifecycleOneTimeStateTenantIsolationAndMfaRecoveryWithoutPlaintextSecrets() {
        var now = Instant.parse("2030-01-01T00:00:00Z");
        var platform = repository.insertProvider(901, command("platform-oidc", null, null), 10, now);
        assertThat(platform.status()).isEqualTo(IdentityApi.Status.DRAFT);
        assertThat(platform.secretRef()).isEqualTo("env://IDENTITY_TEST_SECRET");
        assertThat(repository.recordPreflight(901, 0, true, null, now)).isPresent();
        var published = repository.transitionProvider(901, 0, IdentityApi.Status.PUBLISHED, 10, now)
                .orElseThrow();
        assertThat(published.version()).isEqualTo(1);
        assertThat(repository.findPublishedProvider("platform-oidc", null, null)).isPresent();
        assertThat(repository.transitionProvider(901, 0, IdentityApi.Status.DISABLED, 10, now)).isEmpty();

        seedScope(now);
        repository.insertProvider(902, command("tenant-oidc", "802", "803"), 801, now);
        repository.recordPreflight(902, 0, true, null, now);
        repository.transitionProvider(902, 0, IdentityApi.Status.PUBLISHED, 801, now).orElseThrow();
        assertThat(repository.findPublishedProvider("tenant-oidc", 802L, 803L)).isPresent();
        assertThat(repository.findPublishedProvider("tenant-oidc", 802L, 804L)).isEmpty();

        var scoped = repository.findProvider(902).orElseThrow();
        repository.createAuthState(903, TotpService.hash("raw-state"), scoped, 802L, 803L,
                "/work", "nonce", "verifier", now.plusSeconds(300), now);
        assertThat(repository.consumeAuthState(TotpService.hash("raw-state"), now)).isPresent();
        assertThat(repository.consumeAuthState(TotpService.hash("raw-state"), now)).isEmpty();

        var enrollment = repository.upsertEnrollment(904, 801, 802L, 803L,
                "env://MFA_TEST_SECRET", "v1", now);
        repository.replaceRecoveryCodes(enrollment.id(),
                List.of(new IdentityRepository.RecoveryCodeHash(905, TotpService.hash("RECOVERY01"))), now);
        assertThat(repository.consumeRecoveryCode(enrollment.id(), TotpService.hash("RECOVERY01"), now)).isTrue();
        assertThat(repository.consumeRecoveryCode(enrollment.id(), TotpService.hash("RECOVERY01"), now)).isFalse();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND table_name LIKE 'un_plat_identity%'
                  AND column_name IN ('secret','client_secret','secret_value','secret_ciphertext')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_identity_provider "
                + "WHERE secret_ref='env://IDENTITY_TEST_SECRET' AND secret_version='v1'", Integer.class))
                .isEqualTo(2);
    }

    private static IdentityApi.ProviderCommand command(String code, String systemId, String tenantId) {
        return new IdentityApi.ProviderCommand(code, "Identity " + code, IdentityApi.Protocol.OIDC,
                "https://id.example.test", "https://id.example.test/authorize",
                "https://id.example.test/token", "https://id.example.test/jwks", null,
                "client-id", "env://IDENTITY_TEST_SECRET", "v1",
                "https://app.example.test/api/v1/auth/sso/callback", "openid profile email",
                List.of("example.test"), Map.of("externalUserId", "sub", "departmentId", "department"),
                true, systemId != null, systemId, tenantId, IdentityApi.MfaPolicy.REQUIRED, null);
    }

    private static void seedScope(Instant now) {
        jdbc.update("""
                INSERT INTO un_plat_account(id,account_code,username,username_normalized,email,email_normalized,
                  display_name,locale,time_zone,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(801,'ACC_801','owner801','owner801','owner801@example.test','owner801@example.test',
                  'Owner 801','zh-CN','Asia/Shanghai','ACTIVE',?,801,?,801,0)
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbc.update("""
                INSERT INTO un_plat_system(id,system_code,name,status,tenant_mode,owner_account_id,
                  permission_version,initialized_at,created_at,created_by,updated_at,updated_by,version)
                VALUES(802,'identity_system','Identity System','ACTIVE','MULTI',801,1,?,?,801,?,801,0)
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        jdbc.update("""
                INSERT INTO un_plat_tenant(id,system_id,tenant_code,name,is_default,status,created_at,created_by,
                  updated_at,updated_by,version)
                VALUES(803,802,'tenant_a','Tenant A',TRUE,'ACTIVE',?,801,?,801,0)
                """, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration");
    }
}

