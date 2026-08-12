package com.unique.examine.plat.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.web.ExamineApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = ExamineApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PlatformLifecycleJourneyIntegrationTest.DnsProofConfiguration.class)
class PlatformLifecycleJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "cycle115_root";
    private static final String ROOT_PASSWORD = "Cycle115-Root-Test-Password-91!";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_platform_lifecycle_test")
            .withUsername("examine_platform_lifecycle_test")
            .withPassword("container-test-password");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.flyway.locations", () -> "filesystem:"
                + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name", () -> "Cycle115 Test Root");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://127.0.0.1:" + port;
    }

    @Test
    void completesConfirmedSystemDeletionAndTenantFullDataLifecycle() throws Exception {
        var anonymous = new TestClient();
        assertError(anonymous.get("/api/v1/systems/1/admin/tenants/1/quotas"), 401, "AUTH_REQUIRED");

        var platform = loginPlatform();
        var main = createSystem(platform, "lifecycle_main_" + System.nanoTime(), "Lifecycle Main");
        var foreign = createSystem(platform, "lifecycle_foreign_" + System.nanoTime(), "Lifecycle Foreign");
        var foreignDefaultTenant = id(jdbc.queryForObject(
                "SELECT id FROM un_plat_tenant WHERE system_id=? AND is_default=TRUE", Long.class, mainId(foreign)));

        assertError(platform.get("/api/v1/systems/" + main.id()
                + "/admin/tenants/" + foreignDefaultTenant + "/domains"), 403, "CONTEXT_SYSTEM_MISMATCH");

        var system = loginSystem(main.id());
        assertError(system.postWithCsrf("/api/v1/platform/admin/systems/" + main.id()
                + "/deletion:preview", "{}", Map.of()), 403, "CONTEXT_PLATFORM_REQUIRED");

        var source = createTenant(system, main.id(), "source_" + System.nanoTime(), "Source Tenant");
        system = loginSystem(main.id());
        var target = createTenant(system, main.id(), "target_" + System.nanoTime(), "Target Tenant");
        system = loginSystem(main.id());

        var projectId = Math.abs(System.nanoTime());
        jdbc.update("INSERT INTO un_work_project(id,system_id,tenant_id,creator_member_id,title,description,"
                        + "status,created_at,updated_at,version) VALUES(?,?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6),"
                        + "UTC_TIMESTAMP(6),1)", projectId, mainId(main), tenantId(source), 1L,
                "Lifecycle source project", "Encrypted backup and migration proof");

        var tenantRoot = "/api/v1/systems/" + main.id() + "/admin/tenants/" + source.id();
        var domainCreated = system.postWithCsrf(tenantRoot + "/domains",
                json(Map.of("domainName", "source-" + System.nanoTime() + ".example.test")),
                Map.of("Idempotency-Key", key()));
        assertOk(domainCreated);
        var domainId = text(domainCreated.body(), "/data/id");
        var verificationToken = text(domainCreated.body(), "/data/verificationToken");
        assertThat(verificationToken).isNotBlank();
        assertThat(text(domainCreated.body(), "/data/version")).isEqualTo("0");

        var invalidVerify = system.postWithCsrf(tenantRoot + "/domains/" + domainId + ":verify",
                json(Map.of("verificationToken", "wrong-token", "expectedVersion", "0")), Map.of());
        assertError(invalidVerify, 409, "TENANT_DOMAIN_VERIFICATION_INVALID");
        var verified = system.postWithCsrf(tenantRoot + "/domains/" + domainId + ":verify",
                json(Map.of("verificationToken", verificationToken, "expectedVersion", "0")), Map.of());
        assertOk(verified);
        assertThat(text(verified.body(), "/data/status")).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT verification_token_hash FROM un_plat_tenant_domain WHERE id=?",
                String.class, Long.parseLong(domainId))).isNull();

        var primary = system.putWithCsrf(tenantRoot + "/domains/" + domainId + ":primary",
                json(Map.of("expectedVersion", "1", "reason", "primary route", "impactConfirmed", true)), Map.of());
        assertOk(primary);
        assertThat(primary.body().at("/data/primary").asBoolean()).isTrue();

        for (var quotaKey : new String[]{"MEMBERS", "MODULES", "FIELDS", "STORAGE_BYTES",
                "IMPORT_EXPORT_JOBS", "OPENAPI_CALLS"}) {
            var hardLimit = "STORAGE_BYTES".equals(quotaKey) ? 100 : 1_000_000;
            var quotaCreated = system.putWithCsrf(tenantRoot + "/quotas",
                    json(Map.of("quotaKey", quotaKey, "softLimit", hardLimit / 2,
                            "hardLimit", hardLimit)), Map.of("Idempotency-Key", key()));
            assertOk(quotaCreated);
        }
        var quotaAdjusted = system.postWithCsrf(tenantRoot + "/quotas/STORAGE_BYTES:adjust",
                json(Map.of("delta", 40, "reason", "reserve capacity", "expectedVersion", "0")),
                Map.of("Idempotency-Key", key()));
        assertOk(quotaAdjusted);
        assertThat(quotaAdjusted.body().at("/data/usedValue").asLong()).isEqualTo(40);

        var quotaExceeded = system.postWithCsrf(tenantRoot + "/quotas/STORAGE_BYTES:adjust",
                json(Map.of("delta", 70, "reason", "must be rejected", "expectedVersion", "1")),
                Map.of("Idempotency-Key", key()));
        assertError(quotaExceeded, 429, "TENANT_QUOTA_EXCEEDED");
        assertThat(jdbc.queryForObject("SELECT used_value FROM un_plat_quota WHERE system_id=? AND tenant_id=? "
                        + "AND quota_key='STORAGE_BYTES'", Long.class, mainId(main), tenantId(source)))
                .isEqualTo(40L);

        var backup = system.postWithCsrf(tenantRoot + "/lifecycle:backup",
                json(Map.of("reason", "pre-change control-plane backup", "expectedTenantVersion", "0")),
                Map.of("Idempotency-Key", key()));
        assertOk(backup);
        var backupId = text(backup.body(), "/data/id");
        assertThat(text(backup.body(), "/data/operationType")).isEqualTo("BACKUP");
        assertThat(text(backup.body(), "/data/result/scope")).isEqualTo("TENANT_FULL_DATA_PLANE_ENCRYPTED");
        assertThat(backup.body().at("/data/payloadRowCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(backup.body().at("/data/payloadSizeBytes").asLong()).isPositive();
        assertThat(jdbc.queryForMap("SELECT payload_ciphertext,payload_ciphertext_sha256,"
                        + "payload_plaintext_sha256,encryption_key_ref,encryption_key_version FROM "
                        + "un_plat_tenant_lifecycle_operation WHERE id=?", Long.parseLong(backupId)))
                .containsKeys("payload_ciphertext", "payload_ciphertext_sha256",
                        "payload_plaintext_sha256", "encryption_key_ref", "encryption_key_version");

        jdbc.update("UPDATE un_work_project SET title='Mutated after backup',version=version+1 "
                + "WHERE system_id=? AND tenant_id=? AND id=?", mainId(main), tenantId(source), projectId);

        var disabledDomain = system.postWithCsrf(tenantRoot + "/domains/" + domainId + ":disable",
                json(Map.of("expectedVersion", "2", "reason", "exercise recovery", "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(disabledDomain);
        var changedQuota = system.putWithCsrf(tenantRoot + "/quotas",
                json(Map.of("quotaKey", "STORAGE_BYTES", "softLimit", 100, "hardLimit", 200,
                        "expectedVersion", "1")), Map.of("Idempotency-Key", key()));
        assertOk(changedQuota);

        var disabledTenant = system.postWithCsrf("/api/v1/systems/" + main.id()
                        + "/admin/tenants/" + source.id() + ":disable",
                json(Map.of("reason", "prepare recovery", "version", "0", "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(disabledTenant);
        assertThat(text(disabledTenant.body(), "/data/status")).isEqualTo("DISABLED");

        system = loginSystem(main.id());
        var recoveryPreview = system.postWithCsrf(tenantRoot + "/lifecycle:recovery-preview",
                json(Map.of("backupOperationId", backupId, "expectedTenantVersion", "1")), Map.of());
        assertOk(recoveryPreview);
        assertThat(recoveryPreview.body().at("/data/eligible").asBoolean()).isTrue();
        var recoveryPlanId = text(recoveryPreview.body(), "/data/id");
        var recoveryToken = text(recoveryPreview.body(), "/data/confirmationToken");
        assertThat(recoveryPreview.body().at("/data/rowCount").asLong()).isGreaterThanOrEqualTo(1);

        var invalidRecovery = system.postWithCsrf(tenantRoot + "/lifecycle:recover",
                json(Map.of("planOperationId", recoveryPlanId, "confirmationToken", "wrong-token",
                        "reason", "reject invalid confirmation", "expectedTenantVersion", "1",
                        "impactConfirmed", true)), Map.of("Idempotency-Key", key()));
        assertError(invalidRecovery, 409, "TENANT_DATA_CONFIRMATION_INVALID");
        var recovered = system.postWithCsrf(tenantRoot + "/lifecycle:recover",
                json(Map.of("planOperationId", recoveryPlanId, "confirmationToken", recoveryToken,
                        "reason", "restore verified configuration", "expectedTenantVersion", "1",
                        "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(recovered);
        assertThat(text(recovered.body(), "/data/result/restoredStatus")).isEqualTo("DISABLED");
        assertThat(text(recovered.body(), "/data/result/scope"))
                .isEqualTo("TENANT_FULL_DATA_PLANE_ENCRYPTED");
        assertThat(recovered.body().at("/data/result/restoredDataRows").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM un_work_project WHERE system_id=? AND tenant_id=? "
                        + "AND id=?", String.class, mainId(main), tenantId(source), projectId))
                .isEqualTo("Lifecycle source project");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant_domain WHERE id=? "
                        + "AND tenant_id=? AND status='VERIFIED' AND is_primary=TRUE", Integer.class,
                Long.parseLong(domainId), tenantId(source))).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT soft_limit,hard_limit,used_value FROM un_plat_quota WHERE system_id=? "
                        + "AND tenant_id=? AND quota_key='STORAGE_BYTES'", mainId(main), tenantId(source)))
                .containsEntry("soft_limit", 50L).containsEntry("hard_limit", 100L).containsEntry("used_value", 0L);

        system = loginSystem(main.id());
        var disabledTarget = system.postWithCsrf("/api/v1/systems/" + main.id()
                        + "/admin/tenants/" + target.id() + ":disable",
                json(Map.of("reason", "prepare migration target", "version", "0", "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(disabledTarget);
        system = loginSystem(main.id());
        var isolated = system.postWithCsrf(tenantRoot + "/lifecycle:migration-preview",
                json(Map.of("targetTenantId", foreignDefaultTenant, "expectedSourceVersion", "2",
                        "expectedTargetVersion", "0")), Map.of());
        assertError(isolated, 404, "RESOURCE_NOT_FOUND");

        var migrationPreview = system.postWithCsrf(tenantRoot + "/lifecycle:migration-preview",
                json(Map.of("targetTenantId", target.id(), "expectedSourceVersion", "2",
                        "expectedTargetVersion", "1")), Map.of());
        assertOk(migrationPreview);
        assertThat(migrationPreview.body().at("/data/eligible").asBoolean())
                .withFailMessage("migration preview was blocked: %s", migrationPreview.body())
                .isTrue();
        var migrationPlanId = text(migrationPreview.body(), "/data/id");
        var migrationToken = text(migrationPreview.body(), "/data/confirmationToken");
        var migrated = system.postWithCsrf(tenantRoot + "/lifecycle:migrate",
                json(Map.of("planOperationId", migrationPlanId, "confirmationToken", migrationToken,
                        "targetTenantId", target.id(), "reason", "move tenant configuration",
                        "expectedSourceVersion", "2", "expectedTargetVersion", "1", "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(migrated);
        assertThat(text(migrated.body(), "/data/result/scope"))
                .isEqualTo("TENANT_FULL_DATA_PLANE_ENCRYPTED");
        assertThat(migrated.body().at("/data/result/movedDomains").asInt()).isEqualTo(1);
        assertThat(migrated.body().at("/data/result/movedQuotas").asInt()).isEqualTo(6);
        assertThat(migrated.body().at("/data/result/movedDataRows").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant_domain WHERE tenant_id=?",
                Integer.class, tenantId(source))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant_domain WHERE tenant_id=?",
                Integer.class, tenantId(target))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM un_plat_tenant WHERE id=?", String.class,
                tenantId(source))).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_work_project WHERE system_id=? AND tenant_id=? "
                        + "AND id=? AND title='Lifecycle source project'", Integer.class,
                mainId(main), tenantId(target), projectId)).isEqualTo(1);

        verifyConfirmedSystemDeletion();
        verifyPersistedEvidence(main, source, target);
    }

    private void verifyConfirmedSystemDeletion() throws Exception {
        var platform = loginPlatform();
        var candidate = createSystem(platform, "delete_candidate_" + System.nanoTime(), "Delete Candidate");
        var disabled = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id() + ":disable",
                json(Map.of("reason", "prepare deletion", "version", candidate.version(), "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertOk(disabled);
        var archived = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id() + ":archive",
                json(Map.of("reason", "prepare deletion", "version", text(disabled.body(), "/data/version"),
                        "impactConfirmed", true)), Map.of("Idempotency-Key", key()));
        assertOk(archived);

        var preview = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id()
                + "/deletion:preview", "{}", Map.of());
        assertOk(preview);
        assertThat(preview.body().at("/data/eligible").asBoolean()).isTrue();
        assertThat(preview.body().at("/data/dependencies/un_plat_tenant").asLong()).isGreaterThanOrEqualTo(1);
        var previewId = text(preview.body(), "/data/previewId");
        var token = text(preview.body(), "/data/confirmationToken");
        var version = text(preview.body(), "/data/systemVersion");
        assertThat(token).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT confirmation_token_hash FROM un_plat_system_delete_request WHERE id=?",
                String.class, Long.parseLong(previewId))).matches("[0-9a-f]{64}").isNotEqualTo(token);

        var invalid = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id()
                        + "/deletion:confirm",
                json(Map.of("previewId", previewId, "confirmationToken", "wrong-token", "expectedVersion", version,
                        "reason", "confirmed deletion", "impactConfirmed", true)),
                Map.of("Idempotency-Key", key()));
        assertError(invalid, 409, "SYSTEM_DELETE_CONFIRMATION_INVALID");

        var deletionKey = key();
        var body = json(Map.of("previewId", previewId, "confirmationToken", token, "expectedVersion", version,
                "reason", "confirmed recoverable tombstone deletion", "impactConfirmed", true));
        var deleted = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id()
                + "/deletion:confirm", body, Map.of("Idempotency-Key", deletionKey));
        assertOk(deleted);
        assertThat(text(deleted.body(), "/data/status")).isEqualTo("TOMBSTONED");
        assertThat(deleted.body().at("/data/disabledTenants").asInt()).isGreaterThanOrEqualTo(1);
        var replay = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id()
                + "/deletion:confirm", body, Map.of("Idempotency-Key", deletionKey));
        assertOk(replay);
        assertThat(text(replay.body(), "/data/deletedAt")).isEqualTo(text(deleted.body(), "/data/deletedAt"));

        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM un_plat_system WHERE id=?",
                Integer.class, mainId(candidate))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tombstone_reason FROM un_plat_system WHERE id=?",
                String.class, mainId(candidate))).isEqualTo("confirmed recoverable tombstone deletion");
        assertThat(jdbc.queryForObject("SELECT status FROM un_plat_system_delete_request WHERE id=?",
                String.class, Long.parseLong(previewId))).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant WHERE system_id=? AND status='ACTIVE'",
                Integer.class, mainId(candidate))).isZero();

        var tombstones = platform.get("/api/v1/platform/admin/system-tombstones?page=1&size=100");
        assertOk(tombstones);
        var tombstone = tombstones.body().at("/data/items").findValuesAsText("id").stream()
                .anyMatch(candidate.id()::equals);
        assertThat(tombstone).isTrue();
        var tombstoneVersion = jdbc.queryForObject("SELECT version FROM un_plat_system WHERE id=?",
                Long.class, mainId(candidate));
        var restoreKey = key();
        var restored = platform.postWithCsrf("/api/v1/platform/admin/systems/" + candidate.id()
                        + "/tombstone:restore",
                json(Map.of("reason", "operator recovery verification", "version",
                        Long.toString(tombstoneVersion))), Map.of("Idempotency-Key", restoreKey));
        assertOk(restored);
        assertThat(text(restored.body(), "/data/status")).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL AND deleted_by IS NULL "
                        + "AND tombstone_reason IS NULL FROM un_plat_system WHERE id=?",
                Integer.class, mainId(candidate))).isEqualTo(1);
    }

    private void verifyPersistedEvidence(SystemRef main, TenantRef source, TenantRef target) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant_lifecycle_operation WHERE system_id=? "
                        + "AND operation_type IN ('BACKUP','RECOVERY','MIGRATION') AND status='SUCCEEDED' "
                        + "AND snapshot_checksum REGEXP '^[0-9a-f]{64}$'", Integer.class, mainId(main)))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant_lifecycle_operation WHERE system_id=? "
                        + "AND JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.scope'))="
                        + "'TENANT_FULL_DATA_PLANE_ENCRYPTED' AND operation_type IN "
                        + "('BACKUP','RECOVERY','MIGRATION') AND status='SUCCEEDED'",
                Integer.class, mainId(main))).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_audit_operation WHERE result='SUCCESS' AND "
                        + "operation_type IN ('TENANT_DOMAIN_CREATED','TENANT_DOMAIN_VERIFIED',"
                        + "'TENANT_DOMAIN_PRIMARY_CHANGED','TENANT_DOMAIN_DISABLED','TENANT_QUOTA_CREATED',"
                        + "'TENANT_QUOTA_USAGE_ADJUSTED','TENANT_QUOTA_UPDATED','TENANT_BACKUP_CREATED',"
                        + "'TENANT_RECOVERED','TENANT_CONFIGURATION_MIGRATED','PLATFORM_SYSTEM_DELETE_PREVIEWED',"
                        + "'PLATFORM_SYSTEM_TOMBSTONED','PLATFORM_SYSTEM_TOMBSTONE_RESTORED')", Integer.class))
                .isGreaterThanOrEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_sys_outbox_event WHERE event_type IN "
                        + "('TENANT_BACKUP_CREATED','TENANT_RECOVERED','TENANT_CONFIGURATION_MIGRATED',"
                        + "'PLATFORM_SYSTEM_TOMBSTONED','PLATFORM_SYSTEM_TOMBSTONE_RESTORED')", Integer.class))
                .isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_tenant_domain WHERE system_id=? "
                        + "AND tenant_id=? AND deleted_at IS NULL", Integer.class, mainId(main), tenantId(source)))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM un_plat_quota WHERE system_id=? AND tenant_id=?",
                Integer.class, mainId(main), tenantId(target))).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='8.86.0' "
                + "AND success=TRUE", Integer.class)).isEqualTo(1);
    }

    private SystemRef createSystem(TestClient client, String code, String name) throws Exception {
        var response = client.postWithCsrf("/api/v1/platform/admin/systems",
                json(Map.of("code", code, "name", name, "description", "Cycle115 lifecycle journey",
                        "tenantMode", "MULTI")), Map.of("Idempotency-Key", key()));
        assertOk(response);
        return new SystemRef(text(response.body(), "/data/id"), text(response.body(), "/data/version"));
    }

    private TenantRef createTenant(TestClient client, String systemId, String code, String name) throws Exception {
        var response = client.postWithCsrf("/api/v1/systems/" + systemId + "/admin/tenants",
                json(Map.of("code", code, "name", name)), Map.of("Idempotency-Key", key()));
        assertOk(response);
        return new TenantRef(text(response.body(), "/data/id"), text(response.body(), "/data/version"));
    }

    private TestClient loginPlatform() throws Exception {
        var client = new TestClient();
        var login = client.post("/api/v1/auth/login",
                json(Map.of("account", ROOT_USERNAME, "password", ROOT_PASSWORD)), Map.of());
        assertOk(login);
        var switched = client.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of());
        assertOk(switched);
        return client;
    }

    private TestClient loginSystem(String systemId) throws Exception {
        var client = loginPlatform();
        var switched = client.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(switched);
        return client;
    }

    private static long mainId(SystemRef ref) {
        return Long.parseLong(ref.id());
    }

    private static long tenantId(TenantRef ref) {
        return Long.parseLong(ref.id());
    }

    private static String id(Long value) {
        assertThat(value).isNotNull();
        return Long.toString(value);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP 200 but got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected HTTP %s but got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private record SystemRef(String id, String version) { }
    private record TenantRef(String id, String version) { }
    private record TestResponse(int status, JsonNode body) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class DnsProofConfiguration {
        @Bean
        @Primary
        DomainOwnershipVerifier testDomainOwnershipVerifier() {
            return (domainName, verificationToken) -> domainName.endsWith(".example.test")
                    && verificationToken != null && !verificationToken.isBlank();
        }

        @Bean
        @Primary
        PlatformSecretResolverFacade lifecycleBackupSecrets() {
            return request -> Optional.of(SecretResolverFacade.ResolvedSecret.utf8(
                    "cycle115-real-mysql-lifecycle-backup-key-material"));
        }
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        private TestResponse request(String method, String path, String body, Map<String, String> headers,
                                     boolean csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) builder.header("X-CSRF-Token", csrf());
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(builder.header("X-Request-ID", "cycle115-" + UUID.randomUUID()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }
}
