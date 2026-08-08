package com.unique.examine.plat.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AuditEvent;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobService;
import com.unique.examine.core.job.JdbcJobStore;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SystemIdentitySyncJourneyIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_identity_sync_test")
            .withUsername("examine_test")
            .withPassword("test-only-password");

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final ClientRequest REQUEST = new ClientRequest("request-sync-1", "trace-sync-1",
            "127.0.0.1", "integration-test");
    private static final List<AuditEvent> AUDIT = new ArrayList<>();
    private static JdbcTemplate jdbc;
    private static JdbcIdentityRepository identityRepository;
    private static SystemIdentitySyncService service;
    private static SequenceIds ids;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationRoot().toString().replace('\\', '/'))
                .load().migrate();
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(source);
        var mapper = new ObjectMapper();
        ids = new SequenceIds(900_000);
        identityRepository = new JdbcIdentityRepository(jdbc, mapper);
        var jobService = new DurableJobService(new JdbcJobStore(jdbc, mapper), ids, mapper, List.of());
        service = new SystemIdentitySyncService(jdbc, mapper, ids, jobService, AUDIT::add,
                Clock.fixed(NOW, ZoneOffset.UTC), new DataSourceTransactionManager(source));
        seedScope();
        identityRepository.insertProvider(901, providerCommand(), 801, NOW);
        identityRepository.recordPreflight(901, 0, true, null, NOW);
        identityRepository.transitionProvider(901, 0, IdentityApi.Status.PUBLISHED, 801, NOW).orElseThrow();
    }

    @Test
    void preflightsConfirmsAndRunsManualAndScheduledTenantSafeSynchronizationWithoutDuplicatingSecrets() {
        var sessionA = session(802, 803);
        var policyA = service.savePolicy(sessionA, 802, new SystemIdentitySyncApi.PolicyCommand(
                "803", "901", List.of("a.example.test"), true, "REQUIRE_REVIEW",
                true, 15, null), REQUEST);
        assertThat(policyA.status()).isEqualTo("DRAFT");
        assertThat(policyA.providerCode()).isEqualTo("group-oidc");

        var snapshotA = service.preflight(sessionA, 802, Long.parseLong(policyA.id()),
                new SystemIdentitySyncApi.PreflightCommand("cursor-a-1", policyA.version(),
                        List.of(
                                new SystemIdentitySyncApi.ExternalDepartment("HQ", "Headquarters", null,
                                        null, Map.of("source", "directory")),
                                new SystemIdentitySyncApi.ExternalDepartment("LOST", "Deleted before apply", null,
                                        "888", Map.of("source", "directory"))),
                        List.of(
                                new SystemIdentitySyncApi.ExternalEmployee("shared-user", "alice@a.example.test",
                                        "Alice A", "A001", "HQ", null, Map.of("title", "Manager")),
                                new SystemIdentitySyncApi.ExternalEmployee("rollback-user", "rollback@a.example.test",
                                        "Rollback User", "A003", "LOST", null, Map.of()),
                                new SystemIdentitySyncApi.ExternalEmployee("blocked-user", "blocked@outside.test",
                                        "Blocked", "A002", "HQ", null, Map.of()))),
                REQUEST);
        assertThat(snapshotA.createCount()).isEqualTo(3);
        assertThat(snapshotA.unmatchedCount()).isEqualTo(1);
        assertThat(snapshotA.items()).extracting(SystemIdentitySyncApi.SyncItemView::issueCode)
                .contains("EMPLOYEE_DOMAIN_DENIED");

        assertThatThrownBy(() -> service.confirm(sessionA, 802, Long.parseLong(policyA.id()),
                Long.parseLong(snapshotA.id()), new SystemIdentitySyncApi.ConfirmCommand(
                        policyA.version(), snapshotA.version(), false), REQUEST))
                .hasMessageContaining("必须人工确认");

        var confirmedA = service.confirm(sessionA, 802, Long.parseLong(policyA.id()),
                Long.parseLong(snapshotA.id()), new SystemIdentitySyncApi.ConfirmCommand(
                        policyA.version(), snapshotA.version(), true), REQUEST);
        assertThat(confirmedA.status()).isEqualTo("CONFIRMED");
        jdbc.update("UPDATE un_plat_department SET status='DISABLED',updated_at=?,version=version+1 WHERE id=888",
                Timestamp.from(NOW.plusSeconds(1)));
        var taskA = service.start(sessionA, 802, Long.parseLong(policyA.id()), Long.parseLong(snapshotA.id()),
                new SystemIdentitySyncApi.StartCommand(confirmedA.version()), REQUEST);
        assertThat(taskA.status()).isEqualTo("QUEUED");
        assertThat(service.executeNext()).isEqualTo(1);

        var tasksA = service.tasks(802, Long.parseLong(policyA.id()));
        assertThat(tasksA).hasSize(1);
        assertThat(tasksA.getFirst().status()).isEqualTo("PARTIAL_FAILED");
        assertThat(tasksA.getFirst().failures()).extracting(SystemIdentitySyncApi.FailureView::failureCode)
                .containsExactlyInAnyOrder("TARGET_DEPARTMENT_GONE", "EMPLOYEE_DEPARTMENT_NOT_APPLIED",
                        "EMPLOYEE_DOMAIN_DENIED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_department WHERE system_id=802 "
                + "AND tenant_id=803 AND status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_identity_binding WHERE provider_id=901 "
                + "AND system_id=802 AND tenant_id=803 AND external_user_id='shared-user'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_account WHERE email_normalized="
                + "'rollback@a.example.test'", Integer.class)).as("failed item account rolls back").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_identity_binding WHERE provider_id=901 "
                + "AND external_user_id='rollback-user'", Integer.class)).as("failed item binding rolls back").isZero();

        var inheritedA = identityRepository.findPublishedProvider("group-oidc", 802L, 803L).orElseThrow();
        assertThat(inheritedA.systemId()).isEqualTo(802L);
        assertThat(inheritedA.tenantId()).isEqualTo(803L);
        assertThat(inheritedA.allowedDomains()).containsExactly("a.example.test");
        assertThat(identityRepository.findPublishedProvider("group-oidc", 802L, 999L)).isEmpty();

        var sessionB = session(802, 804);
        var policyB = service.savePolicy(sessionB, 802, new SystemIdentitySyncApi.PolicyCommand(
                "804", "901", List.of("b.example.test"), true, "REQUIRE_REVIEW",
                false, null, null), REQUEST);
        var snapshotB = service.preflight(sessionB, 802, Long.parseLong(policyB.id()),
                new SystemIdentitySyncApi.PreflightCommand("cursor-b-1", policyB.version(),
                        List.of(new SystemIdentitySyncApi.ExternalDepartment("HQ", "Tenant B HQ", null,
                                null, Map.of())),
                        List.of(new SystemIdentitySyncApi.ExternalEmployee("shared-user", "bob@b.example.test",
                                "Bob B", "B001", "HQ", null, Map.of()))), REQUEST);
        var confirmedB = service.confirm(sessionB, 802, Long.parseLong(policyB.id()),
                Long.parseLong(snapshotB.id()), new SystemIdentitySyncApi.ConfirmCommand(
                        policyB.version(), snapshotB.version(), false), REQUEST);
        service.start(sessionB, 802, Long.parseLong(policyB.id()), Long.parseLong(snapshotB.id()),
                new SystemIdentitySyncApi.StartCommand(confirmedB.version()), REQUEST);
        assertThat(service.executeNext()).isEqualTo(1);
        assertThat(service.tasks(802, Long.parseLong(policyB.id())).getFirst().status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_identity_binding WHERE provider_id=901 "
                + "AND external_user_id='shared-user'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT tenant_id) FROM un_plat_identity_binding "
                + "WHERE provider_id=901 AND external_user_id='shared-user'", Integer.class)).isEqualTo(2);

        jdbc.update("UPDATE un_plat_system_identity_policy SET next_sync_at=? WHERE id=?",
                Timestamp.from(NOW.minusSeconds(1)), Long.parseLong(policyA.id()));
        assertThat(service.enqueueDueSchedules()).isEqualTo(1);
        assertThat(service.tasks(802, Long.parseLong(policyA.id())))
                .extracting(SystemIdentitySyncApi.TaskView::trigger).contains("MANUAL", "SCHEDULED");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_system_identity_policy p "
                + "JOIN un_plat_identity_provider i ON i.id=p.provider_id WHERE i.secret_ref IS NOT NULL "
                + "AND JSON_SEARCH(p.allowed_domains_json,'one','env://%') IS NULL", Integer.class)).isEqualTo(2);
        assertThat(AUDIT).extracting(AuditEvent::eventType).contains(
                "SYSTEM_IDENTITY_POLICY_SAVED", "SYSTEM_IDENTITY_SYNC_PREFLIGHT",
                "SYSTEM_IDENTITY_SYNC_CONFIRMED", "SYSTEM_IDENTITY_SYNC_QUEUED",
                "SYSTEM_IDENTITY_SYNC_COMPLETED");
        assertThat(AUDIT).allSatisfy(event -> {
            assertThat(event.systemId()).isEqualTo(802L);
            assertThat(event.requestId()).isNotBlank();
            assertThat(event.traceId()).isNotBlank();
        });
    }

    private static IdentityApi.ProviderCommand providerCommand() {
        return new IdentityApi.ProviderCommand("group-oidc", "Group OIDC", IdentityApi.Protocol.OIDC,
                "https://id.example.test", "https://id.example.test/authorize",
                "https://id.example.test/token", "https://id.example.test/jwks", null,
                "client-id", "env://IDENTITY_SYNC_SECRET", "v7",
                "https://app.example.test/api/v1/auth/sso/callback", "openid profile email",
                List.of("a.example.test", "b.example.test"),
                Map.of("externalUserId", "sub", "departmentId", "department"),
                true, false, null, null, IdentityApi.MfaPolicy.REQUIRED, null);
    }

    private static AuthenticatedSession session(long systemId, long tenantId) {
        return new AuthenticatedSession(700 + tenantId, 801, com.unique.examine.core.context.ContextType.SYSTEM,
                systemId, tenantId, 805L, 1, Set.of("system.organization.manage"));
    }

    private static void seedScope() {
        var time = Timestamp.from(NOW);
        jdbc.update("""
                INSERT INTO un_plat_account(id,account_code,username,username_normalized,email,email_normalized,
                  display_name,locale,time_zone,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(801,'ACC_801','owner801','owner801','owner801@example.test','owner801@example.test',
                  'Owner 801','zh-CN','Asia/Shanghai','ACTIVE',?,801,?,801,0)
                """, time, time);
        jdbc.update("""
                INSERT INTO un_plat_system(id,system_code,name,status,tenant_mode,owner_account_id,
                  permission_version,initialized_at,created_at,created_by,updated_at,updated_by,version)
                VALUES(802,'identity_sync_system','Identity Sync System','ACTIVE','MULTI',801,1,?,?,801,?,801,0)
                """, time, time, time);
        jdbc.update("""
                INSERT INTO un_plat_tenant(id,system_id,tenant_code,name,is_default,status,created_at,created_by,
                  updated_at,updated_by,version)
                VALUES(803,802,'tenant_a','Tenant A',TRUE,'ACTIVE',?,801,?,801,0),
                      (804,802,'tenant_b','Tenant B',FALSE,'ACTIVE',?,801,?,801,0)
                """, time, time, time, time);
        jdbc.update("""
                INSERT INTO un_plat_tenant_domain(id,system_id,tenant_id,domain_name,status,is_primary,
                  verified_at,created_at,created_by,updated_at,updated_by,version)
                VALUES(806,802,803,'a.example.test','VERIFIED',TRUE,?,?,801,?,801,0),
                      (807,802,804,'b.example.test','VERIFIED',TRUE,?,?,801,?,801,0)
                """, time, time, time, time, time, time);
        jdbc.update("""
                INSERT INTO un_plat_department(id,scope_type,scope_key,system_id,tenant_id,parent_id,
                  department_code,name,sort_order,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(888,'SYSTEM',802,802,803,NULL,'TEMP_LOST','Temporary department',0,'ACTIVE',
                  ?,801,?,801,0)
                """, time, time);
        jdbc.update("""
                INSERT INTO un_plat_department_closure(id,scope_type,scope_key,tenant_id,ancestor_id,
                  descendant_id,depth,created_at,created_by)
                VALUES(889,'SYSTEM',802,803,888,888,0,?,801)
                """, time);
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

    private static final class SequenceIds extends IdService {
        private final AtomicLong value;
        private SequenceIds(long initial) { this.value = new AtomicLong(initial); }
        @Override public long nextId() { return value.incrementAndGet(); }
    }
}
