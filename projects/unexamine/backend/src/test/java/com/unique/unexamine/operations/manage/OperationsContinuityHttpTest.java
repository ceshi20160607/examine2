package com.unique.unexamine.operations.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OperationsContinuityHttpTest {
    private static final String HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String HASH_C = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String HASH_D = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine").withUsername("unexamine").withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

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
        registry.add("app.file.storage-root", () -> "./target/c55-files");
        registry.add("app.security.application-secret-master-key", () -> "cycle-55-integration-secret-key-is-long-enough");
        registry.add("app.preflight.allow-development-defaults", () -> false);
        registry.add("app.preflight.startup-enabled", () -> true);
        registry.add("app.release-version", () -> "0.1.0-C55");
        registry.add("app.deployment.environment-code", () -> "integration");
        registry.add("app.deployment.config-version", () -> "integration-v55");
    }

    @Autowired private MockMvc http;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void backupRestoreUpgradeFailuresPermissionsAndIdempotencyArePersisted() {
        Fixture owner = register("c55_continuity_owner", "c55_continuity_system");
        long releaseId = insertSourceRelease(owner.accountId());
        String base = "/api/admin/system/operations/continuity";

        Map<String, Object> overview = data(exchange(base, HttpMethod.GET, owner.token(), null));
        assertThat(overview).containsEntry("runtimeDatabaseVersion", "34")
                .containsEntry("runtimeConfigVersion", "integration-v55");

        Map<String, Object> invalidConsistencyInput = new LinkedHashMap<>(backupRequest(releaseId, 1000, 5000));
        invalidConsistencyInput.put("consistencyPoint", "2099-01-01T00:00:00Z");
        ResponseEntity<Map> invalidConsistency = exchange(base + "/backups", HttpMethod.POST, owner.token(),
                invalidConsistencyInput);
        assertThat(invalidConsistency.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalidConsistency.getBody()).containsEntry("code", "BACKUP_CONSISTENCY_POINT_INVALID");
        assertThat(count("ops_backup", "1=1")).isZero();

        ResponseEntity<Map> insufficient = exchange(base + "/backups", HttpMethod.POST, owner.token(),
                backupRequest(releaseId, 1000, 999));
        assertThat(insufficient.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(insufficient.getBody()).containsEntry("code", "BACKUP_INSUFFICIENT_SPACE");
        assertThat(count("ops_backup", "1=1")).isZero();

        Map<String, Object> backup = data(exchange(base + "/backups", HttpMethod.POST, owner.token(),
                backupRequest(releaseId, 1000, 5000)));
        long backupId = number(backup.get("id"));
        assertThat(backup).containsEntry("status", "VERIFIED")
                .containsEntry("secretMaterialStrategy", "REFERENCE_ONLY_ROTATE_ON_RESTORE")
                .containsEntry("consistencyPoint", "2026-08-31T08:00:00");
        assertThat(number(backup.get("sourceReleaseId"))).isEqualTo(releaseId);
        assertThat(list(backup.get("items"))).hasSize(4).allSatisfy(item ->
                assertThat(map(item)).containsEntry("status", "VERIFIED"));

        Map<String, Object> manifest = data(exchange(base + "/backups/" + backupId + "/manifest",
                HttpMethod.GET, owner.token(), null));
        assertThat(String.valueOf(manifest)).doesNotContain("database-password", "application-secret-value");
        assertThat(String.valueOf(manifest.get("downloadNotice"))).contains("不包含");
        assertThat(count("audit_event", "event_code='OPERATIONS_BACKUP_MANIFEST_READ' and object_id='" + backupId
                + "' and result_code='SUCCESS'" )).isOne();

        ResponseEntity<Map> nonIsolated = exchange(base + "/backups/" + backupId + "/restore-drills",
                HttpMethod.POST, owner.token(), restoreDrillRequest(backupId, "integration"));
        assertThat(nonIsolated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nonIsolated.getBody()).containsEntry("code", "RESTORE_DRILL_ISOLATION_REQUIRED");

        Map<String, Object> drill = data(exchange(base + "/backups/" + backupId + "/restore-drills",
                HttpMethod.POST, owner.token(), restoreDrillRequest(backupId, "restore-c55")));
        assertThat(drill).containsEntry("status", "PASSED");
        assertThat(map(drill.get("verification"))).containsEntry("databaseRestore", "READABLE")
                .containsEntry("fileObjects", "EXTRACTED")
                .containsEntry("secretReferences", "REFERENCE_ONLY_ROTATE_ON_RESTORE")
                .containsEntry("source", "restore-drill.ps1");
        assertThat(list(map(drill.get("verification")).get("items"))).hasSize(4);

        Map<String, Object> conflictInput = upgradeRequest(releaseId, backupId, Map.of("module", "CONFLICT"), "CAB-C55-001");
        Map<String, Object> conflict = data(exchange(base + "/upgrades/preflight", HttpMethod.POST, owner.token(), conflictInput));
        assertThat(conflict).containsEntry("ready", false);
        assertThat(list(conflict.get("checks"))).anySatisfy(check ->
                assertThat(map(check)).containsEntry("code", "MAPPING").containsEntry("status", "FAILED"));
        ResponseEntity<Map> blocked = exchange(base + "/upgrades", HttpMethod.POST, owner.token(), conflictInput);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody()).containsEntry("code", "UPGRADE_PREFLIGHT_FAILED");
        assertThat(count("ops_upgrade", "1=1")).isZero();

        Map<String, Object> failed = data(exchange(base + "/upgrades", HttpMethod.POST, owner.token(),
                upgradeRequest(releaseId, backupId, Map.of("module", "FAIL_DURING_APPLY"), "CAB-C55-002")));
        assertThat(failed).containsEntry("status", "RECOVERABLE_FAILED");
        assertThat(list(failed.get("steps"))).hasSize(4).anySatisfy(step ->
                assertThat(map(step)).containsEntry("stepType", "AUTOMATIC_ROLLBACK")
                        .containsEntry("status", "ROLLED_BACK"));
        assertThat(map(failed.get("rollbackPoint"))).containsKeys("completedSteps", "rolledBackSteps", "pendingManualSteps");

        Map<String, Object> successInput = upgradeRequest(releaseId, backupId,
                Map.of("module", "module-v55", "flow", "flow-v55"), "CAB-C55-003");
        Map<String, Object> success = data(exchange(base + "/upgrades", HttpMethod.POST, owner.token(), successInput));
        long upgradeId = number(success.get("id"));
        assertThat(success).containsEntry("status", "SUCCESS");
        assertThat(list(success.get("steps"))).hasSize(5).allSatisfy(step ->
                assertThat(map(step)).containsEntry("status", "PASSED"));
        assertThat(map(success.get("rollbackPoint"))).containsEntry("oldSnapshotInterpretable", true);
        Map<String, Object> repeated = data(exchange(base + "/upgrades", HttpMethod.POST, owner.token(), successInput));
        assertThat(number(repeated.get("id"))).isEqualTo(upgradeId);
        assertThat(count("ops_upgrade_step", "upgrade_id=" + upgradeId)).isEqualTo(5);

        assertThat(jdbc.queryForObject("select concat(impact_report_json) from ops_upgrade where id=?", String.class, upgradeId))
                .contains("CAB-C55-003").contains("module-v55");
        assertThat(count("audit_event", "event_code in ('OPERATIONS_BACKUP_BLOCKED','OPERATIONS_BACKUP_VERIFIED',"
                + "'OPERATIONS_BACKUP_MANIFEST_READ','OPERATIONS_RESTORE_DRILL_COMPLETED',"
                + "'OPERATIONS_UPGRADE_BLOCKED','OPERATIONS_UPGRADE_EXECUTED')"))
                .isEqualTo(8);

        revokePermissions(owner);
        ResponseEntity<Map> denied = exchange(base, HttpMethod.GET, owner.token(), null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(count("audit_event", "event_code='PERMISSION_CHECK' and object_id='OPERATIONS:BACKUP:READ'")).isOne();
    }

    private Map<String, Object> backupRequest(long releaseId, long required, long available) {
        return Map.of("sourceReleaseId", releaseId, "backupType", "FULL", "consistencyPoint", "2026-08-31T08:00:00Z",
                "encryptionKeyReference", "kms://unexamine/backup/c55", "confirmation", "BACKUP " + releaseId, "retentionDays", 30,
                "requiredBytes", required, "availableBytes", available, "items", List.of(
                        artifact("DATABASE", "backup://c55/database.sql.zst", 500, HASH_A),
                        artifact("FILE_STORAGE", "backup://c55/files.zip", 300, HASH_B),
                        artifact("CONFIGURATION", "backup://c55/config.json", 100, HASH_C),
                        artifact("SECRET_REFERENCES", "backup://c55/secret-refs.json", 100, HASH_D)));
    }

    private Map<String, Object> artifact(String type, String uri, long size, String hash) {
        return Map.of("itemType", type, "storageUri", uri, "sizeBytes", size,
                "sha256", hash, "verificationStatus", "VERIFIED");
    }

    private Map<String, Object> restoreDrillRequest(long backupId, String environment) {
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("backupId", backupId);
        verification.put("environment", environment);
        verification.put("status", "PASSED");
        verification.put("databaseRestore", "READABLE");
        verification.put("fileObjects", "EXTRACTED");
        verification.put("configurationVersion", "integration-v54");
        verification.put("secretReferences", "REFERENCE_ONLY_ROTATE_ON_RESTORE");
        verification.put("verifiedAt", "2026-08-31T08:10:00Z");
        verification.put("items", List.of(
                restoreArtifact("DATABASE", 500, HASH_A),
                restoreArtifact("FILE_STORAGE", 300, HASH_B),
                restoreArtifact("CONFIGURATION", 100, HASH_C),
                restoreArtifact("SECRET_REFERENCES", 100, HASH_D)));
        return Map.of("environmentCode", environment,
                "confirmation", "DRILL " + backupId + " IN " + environment,
                "verification", verification);
    }

    private Map<String, Object> restoreArtifact(String type, long size, String hash) {
        return Map.of("itemType", type, "sizeBytes", size, "sha256", hash, "verificationStatus", "VERIFIED");
    }

    private Map<String, Object> upgradeRequest(long releaseId, long backupId, Map<String, String> mapping, String approval) {
        return Map.of("releaseId", releaseId, "backupId", backupId, "sourceDatabaseVersion", "34",
                "targetDatabaseVersion", "34", "sourceConfigVersion", "integration-v54",
                "targetConfigVersion", "integration-v55", "approvalReference", approval,
                "confirmation", "UPGRADE " + releaseId + " WITH BACKUP " + backupId,
                "configurationMapping", mapping);
    }

    private long insertSourceRelease(long accountId) {
        jdbc.update("insert into ops_release(version_name,artifact_hash,database_version,config_version,compatibility_json,"
                        + "release_notes,status,created_by_account_id) values (?,?,?,?,cast(? as json),?,?,?)",
                "0.1.0-C54", HASH_A, "34", "integration-v54", "{\"frontendVersion\":\"0.1.0-C54\"}",
                "C55 upgrade source", "REGISTERED", accountId);
        return jdbc.queryForObject("select id from ops_release where version_name='0.1.0-C54'", Long.class);
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = exchange("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", "C55 连续性管理员",
                "email", username + "@example.com", "systemName", "C55 连续性系统", "systemCode", systemCode));
        Map<String, Object> result = data(registration);
        long accountId = jdbc.queryForObject("select id from plat_account where username=?", Long.class, username);
        return new Fixture(accountId, number(result.get("systemId")), number(result.get("tenantId")),
                String.valueOf(map(result.get("tokens")).get("accessToken")));
    }

    private void revokePermissions(Fixture fixture) {
        jdbc.update("delete rp from sys_role_permission rp join sys_member_role mr on mr.role_id=rp.role_id "
                        + "and mr.tenant_id=rp.tenant_id join sys_tenant_member tm on tm.id=mr.tenant_member_id "
                        + "and tm.tenant_id=mr.tenant_id join sys_member sm on sm.id=tm.system_member_id "
                        + "and sm.system_id=tm.system_id where sm.account_id=? and sm.system_id=? and tm.tenant_id=?",
                fixture.accountId(), fixture.systemId(), fixture.tenantId());
        jdbc.update("update auth_session set permission_version=-1 where account_id=? and system_id=? and tenant_id=?",
                fixture.accountId(), fixture.systemId(), fixture.tenantId());
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked") private List<Object> list(Object value) { return (List<Object>) value; }
    private long number(Object value) { return ((Number) value).longValue(); }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        try {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(method, URI.create(path)).header("X-Client-Source", "WEB");
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(body));
            var response = http.perform(request).andReturn().getResponse();
            return ResponseEntity.status(response.getStatus())
                    .body(objectMapper.readValue(response.getContentAsByteArray(), Map.class));
        } catch (Exception exception) {
            throw new IllegalStateException("Mock HTTP exchange failed", exception);
        }
    }

    private record Fixture(long accountId, long systemId, long tenantId, String token) { }
}
