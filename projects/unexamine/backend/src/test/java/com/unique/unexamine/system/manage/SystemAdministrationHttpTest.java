package com.unique.unexamine.system.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SystemAdministrationHttpTest {
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
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void administratorEnablesMultiTenantCreatesTenantAndSwitchesContext() {
        Fixture fixture = register("tenant_owner", "tenant_system");

        ResponseEntity<Map> settings = exchange("/api/admin/system/settings", HttpMethod.GET, fixture.systemToken(), null);
        assertThat(settings.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(settings)).containsEntry("tenantMode", "SINGLE");

        ResponseEntity<Map> createBeforeEnable = exchange("/api/admin/system/tenants", HttpMethod.POST,
                fixture.systemToken(), Map.of("code", "branch", "name", "分公司"));
        assertThat(createBeforeEnable.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(createBeforeEnable.getBody()).containsEntry("code", "MULTI_TENANT_NOT_ENABLED");

        ResponseEntity<Map> updated = exchange("/api/admin/system/settings", HttpMethod.PUT, fixture.systemToken(),
                Map.of("name", "租户管理系统", "tenantMode", "MULTI"));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(updated)).containsEntry("name", "租户管理系统").containsEntry("tenantMode", "MULTI");

        ResponseEntity<Map> created = exchange("/api/admin/system/tenants", HttpMethod.POST, fixture.systemToken(),
                Map.of("code", "branch", "name", "分公司"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long branchId = ((Number) data(created).get("id")).longValue();
        assertThat(data(created)).containsEntry("main", false).containsEntry("status", "ACTIVE");
        assertThat(count("sys_tenant", "system_id = " + fixture.systemId())).isEqualTo(2);
        assertThat(count("sys_tenant_member", "tenant_id = " + branchId + " and tenant_admin = 1")).isOne();
        assertThat(count("sys_role_permission", "role_id in (select id from sys_role where tenant_id = " + branchId + ") and data_scope_type = 'ALL'")).isOne();

        ResponseEntity<Map> entered = exchange("/api/systems/" + fixture.systemId() + "/tenants/" + branchId + "/enter",
                HttpMethod.POST, fixture.platformToken(), null);
        assertThat(entered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) data(entered).get("tenantId")).longValue()).isEqualTo(branchId);
        assertThat(data(entered)).containsEntry("tenantName", "分公司");
        assertThat((List<?>) data(entered).get("roleIds")).hasSize(1);
        String branchToken = (String) ((Map<?, ?>) data(entered).get("tokens")).get("accessToken");

        ResponseEntity<Map> disable = exchange("/api/admin/system/tenants/" + branchId + "/status",
                HttpMethod.PUT, fixture.systemToken(), Map.of("status", "DISABLED"));
        assertThat(disable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(disable)).containsEntry("status", "DISABLED");
        ResponseEntity<Map> revokedSession = exchange("/api/auth/me", HttpMethod.GET, branchToken, null);
        assertThat(revokedSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Map> enterDisabled = exchange("/api/systems/" + fixture.systemId() + "/tenants/" + branchId + "/enter",
                HttpMethod.POST, fixture.platformToken(), null);
        assertThat(enterDisabled.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(enterDisabled.getBody()).containsEntry("code", "TENANT_ACCESS_DENIED");

        assertThat(count("audit_event", "event_code = 'SYSTEM_SETTINGS_UPDATED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'TENANT_CREATED' and object_id = '" + branchId + "'")).isOne();
        assertThat(count("audit_event", "event_code = 'TENANT_ENTER' and result_code = 'TENANT_ACCESS_DENIED'")).isOne();
    }

    @Test
    void mainTenantIsProtectedAndMultiTenantCannotReturnToSingleWithSecondaryTenant() {
        Fixture fixture = register("tenant_safety", "tenant_safety_system");
        exchange("/api/admin/system/settings", HttpMethod.PUT, fixture.systemToken(),
                Map.of("name", "租户安全系统", "tenantMode", "MULTI"));
        ResponseEntity<Map> created = exchange("/api/admin/system/tenants", HttpMethod.POST, fixture.systemToken(),
                Map.of("code", "branch", "name", "分公司"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> mainDisabled = exchange("/api/admin/system/tenants/" + fixture.mainTenantId() + "/status",
                HttpMethod.PUT, fixture.systemToken(), Map.of("status", "DISABLED"));
        assertThat(mainDisabled.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mainDisabled.getBody()).containsEntry("code", "MAIN_TENANT_PROTECTED");
        assertThat(jdbc.queryForObject("select status from sys_tenant where id = ?", String.class, fixture.mainTenantId()))
                .isEqualTo("ACTIVE");

        ResponseEntity<Map> single = exchange("/api/admin/system/settings", HttpMethod.PUT, fixture.systemToken(),
                Map.of("name", "租户安全系统", "tenantMode", "SINGLE"));
        assertThat(single.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(single.getBody()).containsEntry("code", "TENANT_MODE_CHANGE_BLOCKED");
    }

    @Test
    void memberWithoutSystemManagementPermissionCannotReadOrChangeSettings() {
        Fixture fixture = register("tenant_limited", "tenant_limited_system");
        Long tenantMemberId = jdbc.queryForObject("select id from sys_tenant_member where tenant_id = ?", Long.class,
                fixture.mainTenantId());
        jdbc.update("delete from sys_member_role where tenant_member_id = ?", tenantMemberId);
        ResponseEntity<Map> entered = exchange("/api/systems/" + fixture.systemId() + "/enter", HttpMethod.POST,
                fixture.platformToken(), null);
        String restrictedToken = (String) ((Map<?, ?>) data(entered).get("tokens")).get("accessToken");

        ResponseEntity<Map> denied = exchange("/api/admin/system/settings", HttpMethod.GET, restrictedToken, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody()).containsEntry("code", "PERMISSION_DENIED");
    }

    private Fixture register(String username, String systemCode) {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", "correct-password",
                "displayName", "租户管理员",
                "email", username + "@example.com",
                "systemName", "租户测试系统",
                "systemCode", systemCode), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> registrationData = data(registration);
        long systemId = ((Number) registrationData.get("systemId")).longValue();
        long tenantId = ((Number) registrationData.get("tenantId")).longValue();
        String systemToken = (String) ((Map<?, ?>) registrationData.get("tokens")).get("accessToken");

        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "correct-password"), Map.class);
        String platformToken = (String) data(login).get("accessToken");
        return new Fixture(systemId, tenantId, systemToken, platformToken);
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private record Fixture(long systemId, long mainTenantId, String systemToken, String platformToken) {
    }
}
