package com.unique.examine.plat.audit;

import com.unique.examine.plat.manage.audit.JdbcUnifiedAuditQuery;
import com.unique.examine.plat.manage.audit.UnifiedAuditModels;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.PlatformMutationSupport;
import com.unique.examine.plat.manage.settings.PlatformGlobalSettingsModels;
import com.unique.examine.plat.manage.settings.PlatformGlobalSettingsService;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class UnifiedAuditQueryMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_unified_audit")
            .withUsername("examine_test")
            .withPassword("test-only-password");

    private static JdbcTemplate jdbc;
    private static JdbcUnifiedAuditQuery query;

    @BeforeAll
    static void migrateAndSeed() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("filesystem:" + migrationRoot().toString().replace('\\', '/'))
                .load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        query = new JdbcUnifiedAuditQuery(jdbc);
        jdbc.update("""
                INSERT INTO un_plat_account(id,account_code,username,username_normalized,display_name,
                  locale,time_zone,status,created_at,created_by,updated_at,updated_by,version)
                VALUES(98001,'ACC_98001','settings-owner','settings-owner','Settings Owner',
                  'zh-CN','Asia/Shanghai','ACTIVE',UTC_TIMESTAMP(3),98001,UTC_TIMESTAMP(3),98001,0)
                """);
        insertOperation(97001, "PLATFORM_POLICY_UPDATE", "CONFIGURATION", "platform-config",
                null, null, "platform-request", "platform-trace", "SUCCESS",
                "{\"secretRef\":\"env://MUST_NOT_LEAK\"}");
        insertOperation(97002, "MODULE_CONFIG_UPDATE", "MODULE", "module-a",
                7001L, 8001L, "tenant-a-request", "tenant-a-trace", "SUCCESS",
                "{\"password\":\"MUST_NOT_LEAK\"}");
        insertOperation(97003, "MODULE_CONFIG_UPDATE", "MODULE", "module-b",
                7001L, 8002L, "tenant-b-request", "tenant-b-trace", "FAILED",
                "{\"token\":\"MUST_NOT_LEAK\"}");
        jdbc.update("""
                INSERT INTO un_audit_security(
                  id,event_type,account_id,account_hint,system_id,tenant_id,source_type,
                  request_id,trace_id,result,failure_code,detail_json,created_at)
                VALUES(97004,'LOGIN_SUCCESS',NULL,'masked',7001,8001,'PASSWORD',
                  'tenant-auth-request','tenant-auth-trace','SUCCESS',NULL,
                  JSON_OBJECT('credential','MUST_NOT_LEAK'),?)
                """, Timestamp.from(Instant.parse("2026-08-07T01:03:00Z")));
    }

    @Test
    void platformScopeNeverIncludesTenantAuditRows() {
        var result = query.search(UnifiedAuditModels.Scope.platform(), filters(
                null, null, null, null, "ALL", "ALL"));

        assertThat(result.items()).extracting(UnifiedAuditModels.Item::id)
                .contains("OPERATION:97001")
                .doesNotContain("OPERATION:97002", "OPERATION:97003", "SECURITY:97004");
        assertThat(result.items()).allMatch(item -> item.systemId() == null && item.tenantId() == null);
    }

    @Test
    void systemScopeAndAllFiltersAreAppliedWithoutReturningSensitiveJson() {
        var result = query.search(UnifiedAuditModels.Scope.system(7001, 8001),
                new UnifiedAuditModels.Query(
                        "tenant-a-request", "tenant-a-trace", null, "module-a",
                        "SUCCESS", "CONFIG", Instant.parse("2026-08-07T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z"), 1, 20));

        assertThat(result.total()).isEqualTo(1);
        var item = result.items().getFirst();
        assertThat(item.id()).isEqualTo("OPERATION:97002");
        assertThat(item.systemId()).isEqualTo("7001");
        assertThat(item.tenantId()).isEqualTo("8001");
        assertThat(item.details().toString()).doesNotContain("MUST_NOT_LEAK", "password", "secretRef");
    }

    @Test
    void exactTenantAndCategoryIsolationAlsoAppliesToSecuritySource() {
        var tenantA = query.search(UnifiedAuditModels.Scope.system(7001, 8001), filters(
                null, "tenant-auth-trace", null, null, "SUCCESS", "AUTH"));
        var tenantB = query.search(UnifiedAuditModels.Scope.system(7001, 8002), filters(
                null, "tenant-auth-trace", null, null, "SUCCESS", "AUTH"));

        assertThat(tenantA.items()).extracting(UnifiedAuditModels.Item::id)
                .containsExactly("SECURITY:97004");
        assertThat(tenantB.items()).isEmpty();
    }

    @Test
    void persistsVersionedGlobalPoliciesAndNeverStoresInfrastructureSecrets() {
        var service = new PlatformGlobalSettingsService(jdbc, new ObjectMapper().findAndRegisterModules(), mutations());
        assertThat(service.get().version()).isEqualTo("0");
        var update = new PlatformGlobalSettingsModels.Update(
                new PlatformGlobalSettingsModels.Profile("Operations", "Production governance"),
                new PlatformGlobalSettingsModels.StoragePolicy("S3", 50_000_000, 90),
                new PlatformGlobalSettingsModels.SecurityPolicy(45, 14, true),
                new PlatformGlobalSettingsModels.QuotaPolicy(500, 100, 50_000_000_000L),
                new PlatformGlobalSettingsModels.BackupPolicy(true, 60, 12),
                new PlatformGlobalSettingsModels.ReleasePolicy(false, "CANARY", true), "0");

        var saved = service.update(new AuthenticatedSession(1, 98001, ContextType.PLATFORM,
                        null, null, null, 1, Set.of("platform.settings.manage")), update,
                new ClientRequest("request-settings", "trace-settings", "masked", "test"));

        assertThat(saved.version()).isEqualTo("1");
        assertThat(saved.storage().defaultMode()).isEqualTo("S3");
        var persisted = jdbc.queryForMap("SELECT * FROM un_plat_global_setting WHERE id=1").toString();
        assertThat(persisted).doesNotContain("secret", "credential", "jdbc:", "SecretRef");
    }

    @Test
    void initializationFailureStateRequiresCodeAndTimestampAtTheDatabaseBoundary() {
        jdbc.update("""
                INSERT INTO un_plat_system(id,system_code,name,status,tenant_mode,owner_account_id,
                  permission_version,init_failure_code,init_failed_at,created_at,created_by,
                  updated_at,updated_by,version)
                VALUES(98002,'failed_init','Failed init','INIT_FAILED','SINGLE',98001,1,
                  'AUTHZ_PROVISION_BUSY',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),98001,UTC_TIMESTAMP(3),98001,0)
                """);
        assertThat(jdbc.queryForObject("SELECT init_failure_code FROM un_plat_system WHERE id=98002", String.class))
                .isEqualTo("AUTHZ_PROVISION_BUSY");
        assertThatThrownBy(() -> jdbc.update("UPDATE un_plat_system SET init_failure_code=NULL WHERE id=98002"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("ck_plat_system_initialization_failure");
    }

    private static UnifiedAuditModels.Query filters(
            String requestId, String traceId, String actor, String object,
            String result, String category
    ) {
        return new UnifiedAuditModels.Query(
                requestId, traceId, actor, object, result, category, null, null, 1, 100);
    }

    private static void insertOperation(
            long id, String operation, String aggregate, String aggregateId,
            Long systemId, Long tenantId, String requestId, String traceId,
            String result, String beforeJson
    ) {
        jdbc.update("""
                INSERT INTO un_audit_operation(
                  id,operation_type,aggregate_type,aggregate_id,actor_account_id,context_type,
                  system_id,tenant_id,source_type,request_id,trace_id,result,before_json,after_json,
                  failure_code,created_at)
                VALUES(?,?,?,?,NULL,?,?,?,?,?,?,?,CAST(? AS JSON),NULL,NULL,?)
                """, id, operation, aggregate, aggregateId,
                systemId == null ? "PLATFORM" : "SYSTEM", systemId, tenantId, "ADMIN",
                requestId, traceId, result, beforeJson,
                Timestamp.from(Instant.parse("2026-08-07T01:00:00Z").plusSeconds(id - 97001)));
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

    private static PlatformMutationSupport mutations() {
        return new PlatformMutationSupport(new IdempotencyFacade() {
            public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) { return Optional.empty(); }
            public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) { return 1; }
            public void complete(long id, int httpStatus, String responseCode, String responseBody) { }
        }, new OperationAuditFacade() {
            public void recordSuccess(OperationAudit audit) { }
            public void recordDenied(OperationAudit audit) { }
            public void recordFailed(OperationAudit audit) { }
        }, new OutboxFacade() {
            public long enqueue(OutboxEvent event) { return 1; }
        }, new ObjectMapper().findAndRegisterModules());
    }
}
