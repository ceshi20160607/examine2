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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OperationsDeploymentHttpTest {
    private static final String SHA256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final AtomicInteger SMOKE_STATUS = new AtomicInteger(200);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
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
        registry.add("app.file.storage-root", () -> "./target/c54-files");
        registry.add("app.security.application-secret-master-key",
                () -> "cycle-54-integration-secret-key-is-long-enough");
        registry.add("app.preflight.allow-development-defaults", () -> false);
        registry.add("app.preflight.startup-enabled", () -> true);
        registry.add("app.release-version", () -> "0.1.0-C54");
        registry.add("app.deployment.environment-code", () -> "integration");
        registry.add("app.deployment.frontend-version", () -> "0.1.0-C54");
        registry.add("app.deployment.config-version", () -> "integration-v54");
        registry.add("app.deployment.artifact-sha256", () -> SHA256);
        registry.add("app.deployment.frontend-smoke-url", () -> "http://frontend.test/entry");
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private OperationsFrontendSmokeProbe smokeProbe;

    @Test
    void releaseDeploymentFailuresRollbackAndPermissionArePersistedAndObservable() {
        when(smokeProbe.inspect(anyString())).thenAnswer(ignored -> new OperationsFrontendSmokeProbe.Result(
                SMOKE_STATUS.get() == 200, SMOKE_STATUS.get(), 7, null));
        Fixture owner = register("c54_deployment_owner", "c54_deployment_system");
        String base = "/api/admin/system/operations/deployments";

        Map<String, Object> overview = data(exchange(base, HttpMethod.GET, owner.token(), null));
        assertThat(map(overview.get("runtime"))).containsEntry("environmentCode", "integration")
                .containsEntry("backendVersion", "0.1.0-C54")
                .containsEntry("frontendVersion", "0.1.0-C54")
                .containsEntry("databaseVersion", "34")
                .containsEntry("configVersion", "integration-v54")
                .containsEntry("artifactSha256", SHA256)
                .containsEntry("artifactDigestConfigured", true)
                .containsEntry("ready", true);

        Map<String, Object> release = data(exchange(base + "/releases", HttpMethod.POST, owner.token(), Map.of(
                "versionName", "0.1.0-C54", "artifactSha256", SHA256, "databaseVersion", "34",
                "configVersion", "integration-v54", "frontendVersion", "0.1.0-C54",
                "databaseRollbackStrategy", "FORWARD_FIX", "releaseNotes", "C54 可恢复发布包")));
        long releaseId = number(release.get("id"));
        assertThat(release).containsEntry("status", "REGISTERED").containsEntry("artifactSha256", SHA256);

        Map<String, Object> deployed = data(exchange(base + "/execute", HttpMethod.POST, owner.token(), Map.of(
                "releaseId", releaseId, "environmentCode", "integration",
                "expectedBackendVersion", "0.1.0-C54", "expectedFrontendVersion", "0.1.0-C54",
                "databaseStrategy", "FORWARD_FIX", "approvalReference", "CAB-C54-001",
                "confirmation", "DEPLOY 0.1.0-C54 TO integration")));
        long deploymentId = number(deployed.get("id"));
        assertThat(deployed).containsEntry("status", "SUCCESS").containsEntry("failureCode", "");
        assertThat(list(deployed.get("steps"))).hasSize(5).allSatisfy(value ->
                assertThat(map(value)).containsEntry("status", "PASSED"));
        assertThat(list(deployed.get("steps"))).anySatisfy(value ->
                assertThat(map(value)).containsEntry("code", "FRONTEND_SMOKE")
                        .containsEntry("realEntrySmoke", "PASSED"));
        assertThat(map(deployed.get("approval"))).containsEntry("reference", "CAB-C54-001");
        assertThat(number(map(deployed.get("approval")).get("approvedByAccountId"))).isEqualTo(owner.accountId());
        assertThat(map(deployed.get("rollbackPoint"))).containsEntry("preBackupRequired", true)
                .containsEntry("databaseStrategy", "FORWARD_FIX");
        assertThat(count("ops_deployment", "id=" + deploymentId + " and status='SUCCESS'" )).isOne();
        assertThat(jdbc.queryForObject("select concat(step_state_json) from ops_deployment where id=?", String.class, deploymentId))
                .contains("CAB-C54-001").contains("FRONTEND_SMOKE").contains("PASSED");

        Map<String, Object> mismatch = deploy(base, owner, releaseId, "WRONG-FRONTEND", "FORWARD_FIX", "CAB-C54-002");
        assertThat(mismatch).containsEntry("status", "FAILED").containsEntry("failureCode", "FRONTEND_VERSION_MISMATCH");

        Map<String, Object> unrecoverable = deploy(base, owner, releaseId, "0.1.0-C54", "UNRECOVERABLE", "CAB-C54-003");
        assertThat(unrecoverable).containsEntry("status", "FAILED")
                .containsEntry("failureCode", "DATABASE_MIGRATION_UNRECOVERABLE");

        SMOKE_STATUS.set(503);
        Map<String, Object> smokeFailed = deploy(base, owner, releaseId, "0.1.0-C54", "FORWARD_FIX", "CAB-C54-004");
        SMOKE_STATUS.set(200);
        assertThat(smokeFailed).containsEntry("status", "FAILED").containsEntry("failureCode", "FRONTEND_SMOKE_FAILED");
        assertThat(list(smokeFailed.get("steps"))).noneSatisfy(value ->
                assertThat(map(value)).containsEntry("code", "COMPLETE"));

        Map<String, Object> rollback = data(exchange(base + "/" + deploymentId + "/rollback", HttpMethod.POST,
                owner.token(), Map.of("targetReleaseId", releaseId, "databaseStrategy", "REVERSIBLE",
                        "approvalReference", "CAB-C54-R01", "confirmation", "ROLLBACK " + deploymentId)));
        assertThat(rollback).containsEntry("status", "ROLLBACK_READY").containsEntry("deploymentType", "ROLLBACK");
        assertThat(list(rollback.get("steps"))).hasSize(6).allSatisfy(value ->
                assertThat(map(value)).containsEntry("status", "PASSED"));
        assertThat(map(rollback.get("rollbackPoint"))).containsEntry("executionMode", "STABLE_SCRIPT_ATOMIC_SWAP")
                .containsEntry("preBackupRequired", true).containsEntry("databaseStrategy", "REVERSIBLE");
        assertThat(count("audit_event", "event_code in ('OPERATIONS_RELEASE_REGISTERED','OPERATIONS_DEPLOYMENT_EXECUTED','OPERATIONS_ROLLBACK_PREPARED')"))
                .isEqualTo(6);

        revokePermissions(owner);
        ResponseEntity<Map> denied = exchange(base, HttpMethod.GET, owner.token(), null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).containsEntry("code", "PERMISSION_DENIED");
        assertThat(count("audit_event", "event_code='PERMISSION_CHECK' and object_id='OPERATIONS:DEPLOY:MANAGE'" )).isOne();
    }

    private Map<String, Object> deploy(String base, Fixture owner, long releaseId, String expectedFrontend,
                                       String databaseStrategy, String approval) {
        return data(exchange(base + "/execute", HttpMethod.POST, owner.token(), Map.of(
                "releaseId", releaseId, "environmentCode", "integration", "expectedBackendVersion", "0.1.0-C54",
                "expectedFrontendVersion", expectedFrontend, "databaseStrategy", databaseStrategy,
                "approvalReference", approval, "confirmation", "DEPLOY 0.1.0-C54 TO integration")));
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = exchange("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", "C54 发布管理员",
                "email", username + "@example.com", "systemName", "C54 发布系统", "systemCode", systemCode));
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
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        return (List<Object>) value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

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
