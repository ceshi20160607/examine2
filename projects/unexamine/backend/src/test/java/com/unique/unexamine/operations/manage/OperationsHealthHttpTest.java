package com.unique.unexamine.operations.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OperationsHealthHttpTest {
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
        registry.add("app.file.storage-root", () -> "./target/c53-files");
        registry.add("app.security.application-secret-master-key",
                () -> "cycle-53-integration-secret-key-is-long-enough");
        registry.add("app.preflight.allow-development-defaults", () -> false);
        registry.add("app.preflight.startup-enabled", () -> true);
        registry.add("app.release-version", () -> "0.1.0-C53");
    }

    @Autowired
    private MockMvc http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void operationsAdminRechecksDependenciesAndTracesThePersistedWebRequest() {
        Fixture owner = register("c53_operations_owner", "c53_operations_system");
        String base = "/api/admin/system/operations";

        ResponseEntity<Map> readiness = exchange("/actuator/health/readiness", HttpMethod.GET, null, null);
        assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness.getBody()).containsEntry("status", "UP");

        ResponseEntity<Map> checked = exchange(base + "/health/recheck", HttpMethod.POST, owner.token(), null);
        assertThat(checked.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> health = data(checked);
        assertThat(health).containsEntry("status", "READY").containsEntry("ready", true)
                .containsEntry("releaseVersion", "0.1.0-C53").containsEntry("migrationVersion", "34");
        assertThat((List<?>) health.get("items")).hasSize(5).allSatisfy(value -> {
            Map<?, ?> item = (Map<?, ?>) value;
            assertThat(item.get("status")).isEqualTo("PASSED");
            assertThat(String.valueOf(item)).doesNotContain("unexamine_test")
                    .doesNotContain("cycle-53-integration-secret-key-is-long-enough");
        });
        String requestId = String.valueOf(health.get("requestId"));
        assertThat(requestId).matches("[A-Za-z0-9_-]{8,64}");

        ResponseEntity<Map> latest = exchange(base + "/health", HttpMethod.GET, owner.token(), null);
        assertThat(data(latest)).containsEntry("id", health.get("id")).containsEntry("requestId", requestId);
        assertThat(count("ops_health_run", "id=" + health.get("id") + " and status='READY'" )).isOne();
        assertThat(count("ops_health_item", "health_run_id=" + health.get("id") + " and status='PASSED'" )).isEqualTo(5);
        assertThat(count("audit_event", "request_id='" + requestId + "' and event_code='OPERATIONS_HEALTH_RECHECKED'" )).isOne();
        String persisted = jdbc.queryForObject("select concat(summary_json) from ops_health_run where id=?",
                String.class, health.get("id"));
        assertThat(persisted).contains("structuredContext").contains("migrationVersion")
                .doesNotContain("password").doesNotContain("cycle-53-integration-secret-key-is-long-enough");

        ResponseEntity<Map> observed = exchange(base + "/observability?requestId=" + requestId,
                HttpMethod.GET, owner.token(), null);
        assertThat(observed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> observation = data(observed);
        assertThat(observation).containsEntry("requestId", requestId).containsEntry("observable", true);
        assertThat(((Number) observation.get("systemId")).longValue()).isEqualTo(owner.systemId());
        assertThat(((Number) observation.get("tenantId")).longValue()).isEqualTo(owner.tenantId());
        assertThat((List<?>) observation.get("logs")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(map(observation.get("metrics")))
                .containsEntry("requestCount", 1).containsEntry("auditCount", 1).containsEntry("healthCheckCount", 5);
        assertThat((List<?>) observation.get("stages")).anySatisfy(value -> {
            Map<?, ?> stage = (Map<?, ?>) value;
            assertThat(stage.get("code")).isEqualTo("FRONTEND");
            assertThat(stage.get("observed")).isEqualTo(true);
        });
        assertThat((List<?>) observation.get("stages")).anySatisfy(value -> {
            Map<?, ?> stage = (Map<?, ?>) value;
            assertThat(stage.get("code")).isEqualTo("DATABASE");
            assertThat(stage.get("observed")).isEqualTo(true);
        });
        assertThat(count("ops_request_log", "request_id='" + requestId + "' and client_source='WEB' and status_code=200" )).isOne();

        revokePermissions(owner);
        ResponseEntity<Map> denied = exchange(base + "/health", HttpMethod.GET, owner.token(), null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).containsEntry("code", "PERMISSION_DENIED");
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = exchange("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", "C53 运维管理员",
                "email", username + "@example.com", "systemName", "C53 体检系统", "systemCode", systemCode));
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(registration);
        long accountId = jdbc.queryForObject("select id from plat_account where username=?", Long.class, username);
        return new Fixture(accountId, ((Number) result.get("systemId")).longValue(),
                ((Number) result.get("tenantId")).longValue(),
                String.valueOf(((Map<?, ?>) result.get("tokens")).get("accessToken")));
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
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
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
