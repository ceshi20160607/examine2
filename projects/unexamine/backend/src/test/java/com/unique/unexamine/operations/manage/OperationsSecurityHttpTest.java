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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OperationsSecurityHttpTest {
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
        registry.add("app.file.storage-root", () -> "./target/c56-files");
        registry.add("app.security.application-secret-master-key",
                () -> "cycle-56-integration-secret-key-is-long-enough");
        registry.add("app.preflight.allow-development-defaults", () -> false);
        registry.add("app.preflight.startup-enabled", () -> true);
        registry.add("app.release-version", () -> "0.1.0-C56");
        registry.add("app.deployment.environment-code", () -> "integration");
        registry.add("app.deployment.config-version", () -> "integration-v56");
    }

    @Autowired private MockMvc http;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void twoPhaseRotationSecurityPerformanceFailuresAndPermissionsArePersisted() {
        Fixture owner = register("c56_security_owner", "c56_security_system");
        String base = "/api/admin/system/operations/security-performance";

        Map<String, Object> security = data(exchange(base + "/security-runs", HttpMethod.POST, owner.token(), Map.of(
                "approvalReference", "CAB-C56-SEC-001", "confirmation", "RUN SECURITY BASELINE")));
        long securityRunId = number(security.get("id"));
        assertThat(security).containsEntry("status", "PASSED");
        assertThat(list(security.get("findings"))).isEmpty();

        Map<String, Object> created = data(exchange(base + "/secret-refs", HttpMethod.POST, owner.token(), Map.of(
                "secretCode", "webhook.primary", "secretType", "WEBHOOK",
                "consumerCodes", List.of("billing-webhook", "crm-webhook"),
                "approvalReference", "CAB-C56-ROT-001", "confirmation", "CREATE SECRET webhook.primary")));
        String initialSecret = String.valueOf(created.get("oneTimeSecret"));
        long refId = number(map(created.get("secretRef")).get("id"));
        assertThat(initialSecret).hasSizeGreaterThanOrEqualTo(40);
        assertThat(created).containsEntry("displayOnce", true);
        assertThat(map(created.get("secretRef"))).containsEntry("currentVersion", "v1")
                .containsEntry("status", "ACTIVE");

        Map<String, Object> failedPrepared = prepare(base, owner, refId, "CAB-C56-ROT-002");
        String failedIssuedSecret = String.valueOf(failedPrepared.get("oneTimeSecret"));
        long failedRotationId = number(map(failedPrepared.get("rotation")).get("id"));
        Map<String, Object> failed = activate(base, owner, failedRotationId, securityRunId, List.of(
                check("billing-webhook", "PASSED", "canary-request-c56-1"),
                check("crm-webhook", "FAILED", "canary-request-c56-2")));
        assertThat(failed).containsEntry("status", "FAILED_CANARY").containsEntry("switched", false)
                .containsEntry("activeVersion", "v1").containsEntry("oldVersionStatus", "ACTIVE")
                .containsEntry("failureCode", "CONSUMER_CANARY_FAILED");

        String originalHash = jdbc.queryForObject("select password_hash from plat_account_credential where account_id=?", String.class,
                owner.accountId());
        jdbc.update("update plat_account_credential set password_hash='plaintext-is-forbidden' where account_id=?", owner.accountId());
        Map<String, Object> blockedSecurity = data(exchange(base + "/security-runs", HttpMethod.POST, owner.token(), Map.of(
                "approvalReference", "CAB-C56-SEC-002", "confirmation", "RUN SECURITY BASELINE")));
        assertThat(blockedSecurity).containsEntry("status", "FAILED");
        long blockedSecurityRunId = number(blockedSecurity.get("id"));
        jdbc.update("update plat_account_credential set password_hash=? where account_id=?", originalHash, owner.accountId());

        Map<String, Object> blockedPrepared = prepare(base, owner, refId, "CAB-C56-ROT-003");
        long blockedRotationId = number(map(blockedPrepared.get("rotation")).get("id"));
        Map<String, Object> blocked = activate(base, owner, blockedRotationId, blockedSecurityRunId, List.of(
                check("billing-webhook", "PASSED", "canary-request-c56-3"),
                check("crm-webhook", "PASSED", "canary-request-c56-4")));
        assertThat(blocked).containsEntry("status", "BLOCKED_SECURITY").containsEntry("switched", false)
                .containsEntry("activeVersion", "v1").containsEntry("oldVersionStatus", "ACTIVE")
                .containsEntry("failureCode", "SECURITY_BASELINE_NOT_PASSED");

        Map<String, Object> passedSecurity = data(exchange(base + "/security-runs", HttpMethod.POST, owner.token(), Map.of(
                "approvalReference", "CAB-C56-SEC-003", "confirmation", "RUN SECURITY BASELINE")));
        long passedSecurityRunId = number(passedSecurity.get("id"));
        assertThat(passedSecurity).containsEntry("status", "PASSED");
        Map<String, Object> successPrepared = prepare(base, owner, refId, "CAB-C56-ROT-004");
        String successIssuedSecret = String.valueOf(successPrepared.get("oneTimeSecret"));
        long successRotationId = number(map(successPrepared.get("rotation")).get("id"));
        Map<String, Object> success = activate(base, owner, successRotationId, passedSecurityRunId, List.of(
                check("billing-webhook", "PASSED", "canary-request-c56-5"),
                check("crm-webhook", "PASSED", "canary-request-c56-6")));
        assertThat(success).containsEntry("status", "ACTIVE").containsEntry("switched", true)
                .containsEntry("activeVersion", "v2").containsEntry("oldVersionStatus", "DISABLED");

        Map<String, Object> performance = data(exchange(base + "/performance-runs", HttpMethod.POST, owner.token(),
                performance(10000)));
        assertThat(performance).containsEntry("status", "PASSED");
        assertThat(list(map(performance.get("result")).get("paths"))).hasSize(4).allSatisfy(value -> {
            Map<String, Object> path = map(value);
            assertThat(path).containsKeys("code", "latencyMillis", "thresholdMillis", "timeoutMillis");
        });
        Map<String, Object> exceeded = data(exchange(base + "/performance-runs", HttpMethod.POST, owner.token(),
                performance(0)));
        assertThat(exceeded).containsEntry("status", "THRESHOLD_EXCEEDED");
        assertThat(list(exceeded.get("findings"))).hasSize(4).allSatisfy(value ->
                assertThat(map(value).get("title").toString()).startsWith("优化任务："));

        Map<String, Object> overview = data(exchange(base, HttpMethod.GET, owner.token(), null));
        String visible = String.valueOf(overview);
        assertThat(visible).doesNotContain(initialSecret, failedIssuedSecret, successIssuedSecret,
                "materialReference", "ops-aesgcm");
        assertThat(list(overview.get("secretRefs"))).singleElement().satisfies(value ->
                assertThat(map(value)).containsEntry("currentVersion", "v2").containsEntry("status", "ACTIVE"));
        assertThat(jdbc.queryForObject("select concat(verification_json) from ops_secret_rotation where id=?",
                String.class, failedRotationId)).doesNotContain(failedIssuedSecret).contains("materialDestroyed");
        assertThat(count("ops_secret_rotation", "secret_ref_id=" + refId)).isEqualTo(4);
        assertThat(count("ops_verification_run", "system_id=" + owner.systemId())).isEqualTo(5);
        assertThat(count("ops_verification_finding", "status='OPEN'" )).isEqualTo(5);
        assertThat(count("audit_event", "event_code like 'OPERATIONS_SECRET_%' or "
                + "event_code in ('OPERATIONS_SECURITY_BASELINE_EXECUTED','OPERATIONS_PERFORMANCE_BASELINE_EXECUTED')"))
                .isGreaterThanOrEqualTo(10);

        revokePermissions(owner);
        ResponseEntity<Map> denied = exchange(base, HttpMethod.GET, owner.token(), null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).containsEntry("code", "PERMISSION_DENIED");
        assertThat(count("audit_event", "event_code='PERMISSION_CHECK' and object_id='OPERATIONS:SECURITY:READ'"))
                .isOne();
    }

    private Map<String, Object> prepare(String base, Fixture owner, long refId, String approval) {
        return data(exchange(base + "/secret-refs/" + refId + "/rotations", HttpMethod.POST, owner.token(), Map.of(
                "consumerCodes", List.of("billing-webhook", "crm-webhook"),
                "approvalReference", approval, "confirmation", "ROTATE webhook.primary")));
    }

    private Map<String, Object> activate(String base, Fixture owner, long rotationId, long securityRunId,
                                         List<Map<String, Object>> checks) {
        return data(exchange(base + "/rotations/" + rotationId + "/activate", HttpMethod.POST, owner.token(), Map.of(
                "securityRunId", securityRunId, "consumerChecks", checks,
                "confirmation", "ACTIVATE ROTATION " + rotationId)));
    }

    private Map<String, Object> check(String consumer, String status, String evidence) {
        return Map.of("consumerCode", consumer, "status", status, "evidenceReference", evidence);
    }

    private Map<String, Object> performance(long threshold) {
        return Map.of("pageSize", 25, "fileProbeBytes", 65536, "timeoutMillis", 3000,
                "listThresholdMillis", threshold, "fileThresholdMillis", threshold,
                "queueThresholdMillis", threshold, "statisticsThresholdMillis", threshold,
                "approvalReference", "CAB-C56-PERF-" + threshold);
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = exchange("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", "C56 安全管理员",
                "email", username + "@example.com", "systemName", "C56 安全系统", "systemCode", systemCode));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) { return (List<Object>) value; }

    private long number(Object value) { return ((Number) value).longValue(); }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        try {
            var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .request(method, URI.create(path)).header("X-Client-Source", "WEB");
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (body != null) request.contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(body));
            var response = http.perform(request).andReturn().getResponse();
            Map result = objectMapper.readValue(response.getContentAsByteArray(), Map.class);
            return ResponseEntity.status(response.getStatus()).body(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Mock HTTP exchange failed", exception);
        }
    }

    private record Fixture(long accountId, long systemId, long tenantId, String token) { }
}
