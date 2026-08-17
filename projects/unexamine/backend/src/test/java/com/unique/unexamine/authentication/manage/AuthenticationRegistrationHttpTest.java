package com.unique.unexamine.authentication.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authorization.manage.RequirePermission;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AuthenticationRegistrationHttpTest.TestPermissionController.class)
class AuthenticationRegistrationHttpTest {
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
    void defaultAdministratorCanLoginRefreshReadCurrentUserAndLogout() {
        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> tokens = data(login);
        String accessToken = (String) tokens.get("accessToken");
        String refreshToken = (String) tokens.get("refreshToken");
        assertThat(accessToken).isNotBlank();
        assertThat(jdbc.queryForObject("select access_token_hash from auth_session limit 1", String.class))
                .isNotEqualTo(accessToken);

        ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(me)).containsEntry("username", "admin");

        ResponseEntity<Map> refreshed = http.postForEntity("/api/auth/refresh", Map.of("refreshToken", refreshToken), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedAccessToken = (String) data(refreshed).get("accessToken");
        assertThat(rotatedAccessToken).isNotEqualTo(accessToken);
        assertThat(exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(exchangeWithBearer("/api/auth/logout", HttpMethod.POST, rotatedAccessToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchangeWithBearer("/api/auth/me", HttpMethod.GET, rotatedAccessToken, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registrationCreatesACompleteSingleTenantSystemAndConflictRollsBack() {
        Map<String, Object> request = Map.of(
                "username", "owner_one",
                "password", "correct-password",
                "displayName", "系统创建人",
                "email", "owner@example.com",
                "systemName", "客户管理系统",
                "systemCode", "customer_ops");

        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        long systemId = ((Number) result.get("systemId")).longValue();
        long tenantId = ((Number) result.get("tenantId")).longValue();
        assertThat(result).containsEntry("systemCode", "customer_ops")
                .containsEntry("tenantMode", "SINGLE")
                .containsEntry("tenantName", "默认主租户");
        assertThat(((Map<?, ?>) result.get("tokens")).containsKey("accessToken")).isTrue();

        assertThat(count("plat_account", "username = 'owner_one'")).isOne();
        assertThat(count("sys_system", "code = 'customer_ops'")).isOne();
        assertThat(count("sys_tenant", "system_id = " + systemId + " and is_main = 1 and main_marker = 'MAIN'")).isOne();
        assertThat(count("sys_member", "system_id = " + systemId + " and status = 'ACTIVE'")).isOne();
        assertThat(count("sys_tenant_member", "tenant_id = " + tenantId + " and tenant_admin = 1")).isOne();
        assertThat(count("sys_member_role", "tenant_id = " + tenantId)).isOne();
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_role_permission p join sys_role r on r.id = p.role_id where r.tenant_id = ? and p.data_scope_type = 'ALL'",
                Integer.class, tenantId)).isOne();

        ResponseEntity<Map> duplicate = http.postForEntity("/api/auth/register", Map.of(
                "username", "owner_two",
                "password", "correct-password",
                "displayName", "另一个创建人",
                "email", "other@example.com",
                "systemName", "重复编码系统",
                "systemCode", "customer_ops"), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(count("plat_account", "username = 'owner_two'")).isZero();
        assertThat(count("audit_event", "event_code = 'ACCOUNT_REGISTER_SYSTEM_CREATED' and result_code = 'RESOURCE_CONFLICT'")).isOne();
    }

    @Test
    void wrongPasswordDoesNotRevealAccountExistenceAndDisabledAccountCannotUseExistingSession() {
        ResponseEntity<Map> known = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "wrong-value"), Map.class);
        ResponseEntity<Map> unknown = http.postForEntity("/api/auth/login",
                Map.of("username", "missing-user", "password", "wrong-value"), Map.class);
        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(known.getBody().get("code")).isEqualTo(unknown.getBody().get("code"));
        assertThat(known.getBody().get("message")).isEqualTo(unknown.getBody().get("message"));
        assertThat(count("audit_event", "event_code = 'AUTH_LOGIN' and result_code = 'AUTHENTICATION_FAILED'"))
                .isGreaterThanOrEqualTo(2);

        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String accessToken = (String) data(login).get("accessToken");
        try {
            jdbc.update("update plat_account set status = 'DISABLED' where username = 'admin'");
            ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, accessToken, null);
            assertThat(me.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(me.getBody().get("code")).isEqualTo("ACCOUNT_DISABLED");
            assertThat(count("auth_session", "revoked = 1")).isGreaterThanOrEqualTo(1);
            assertThat(count("audit_event", "event_code = 'AUTH_ACCESS' and result_code = 'ACCOUNT_DISABLED'")).isOne();
        } finally {
            jdbc.update("update plat_account set status = 'ACTIVE' where username = 'admin'");
        }
    }

    @Test
    void enteringSystemMergesRoleActionsAndEachActionDataScopeWithoutRoleSwitching() {
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register", Map.of(
                "username", "permission_owner",
                "password", "correct-password",
                "displayName", "权限测试人",
                "email", "permission@example.com",
                "systemName", "权限测试系统",
                "systemCode", "permission_test"), Map.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> registrationData = data(registration);
        long systemId = ((Number) registrationData.get("systemId")).longValue();
        long tenantId = ((Number) registrationData.get("tenantId")).longValue();
        String registrationToken = (String) ((Map<?, ?>) registrationData.get("tokens")).get("accessToken");
        Long tenantMemberId = jdbc.queryForObject(
                "select tm.id from sys_tenant_member tm join sys_member m on m.id = tm.system_member_id where m.account_id = (select id from plat_account where username = 'permission_owner')",
                Long.class);

        jdbc.update("delete from sys_member_role where tenant_member_id = ?", tenantMemberId);
        jdbc.update("delete from sys_role_permission where role_id in (select id from sys_role where tenant_id = ?)", tenantId);
        jdbc.update("delete from sys_role where tenant_id = ?", tenantId);
        jdbc.update("insert into sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'OWNER_ROLE', '本人数据角色', 'ACTIVE')", systemId, tenantId);
        long ownerRoleId = jdbc.queryForObject("select id from sys_role where tenant_id = ? and code = 'OWNER_ROLE'", Long.class, tenantId);
        jdbc.update("insert into sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'DEPARTMENT_ROLE', '部门数据角色', 'ACTIVE')", systemId, tenantId);
        long departmentRoleId = jdbc.queryForObject("select id from sys_role where tenant_id = ? and code = 'DEPARTMENT_ROLE'", Long.class, tenantId);
        jdbc.update("insert into sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?), (?, ?, ?)",
                tenantId, tenantMemberId, ownerRoleId, tenantId, tenantMemberId, departmentRoleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type) values (?, 'MODULE', 'customer', 'LIST', 'SELF')", ownerRoleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type) values (?, 'MODULE', 'customer', 'UPDATE', 'SELF')", ownerRoleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type) values (?, 'MODULE', 'customer', 'LIST', 'DEPARTMENT')", departmentRoleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type, data_scope_json) values (?, 'MODULE', 'customer', 'EXPORT', 'FIELD_RULE', '{\"field\":\"creator\"}')", ownerRoleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type, data_scope_json) values (?, 'MODULE', 'customer', 'EXPORT', 'FIELD_RULE', '{\"field\":\"participant\"}')", departmentRoleId);

        ResponseEntity<Map> entered = exchangeWithBearer(
                "/api/systems/" + systemId + "/enter", HttpMethod.POST, registrationToken, null);
        assertThat(entered.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> context = data(entered);
        assertThat((List<?>) context.get("roleIds")).hasSize(2);
        assertThat((List<?>) context.get("permissions")).hasSize(3);
        Map<?, ?> scopes = (Map<?, ?>) context.get("dataScopes");
        Map<?, ?> listScope = (Map<?, ?>) scopes.get("MODULE:customer:LIST");
        assertThat(listScope.get("mode")).isEqualTo("SINGLE");
        assertThat(((Map<?, ?>) ((List<?>) listScope.get("terms")).getFirst()).get("type")).isEqualTo("DEPARTMENT");
        Map<?, ?> updateScope = (Map<?, ?>) scopes.get("MODULE:customer:UPDATE");
        assertThat(((Map<?, ?>) ((List<?>) updateScope.get("terms")).getFirst()).get("type")).isEqualTo("SELF");
        Map<?, ?> exportScope = (Map<?, ?>) scopes.get("MODULE:customer:EXPORT");
        assertThat(exportScope.get("mode")).isEqualTo("UNION");
        assertThat((List<?>) exportScope.get("terms")).hasSize(2);

        String sessionRoles = jdbc.queryForObject(
                "select role_ids_json from auth_session where system_id = ? order by id desc limit 1", String.class, systemId);
        assertThat(sessionRoles).contains(Long.toString(ownerRoleId), Long.toString(departmentRoleId));
        String systemToken = (String) ((Map<?, ?>) context.get("tokens")).get("accessToken");
        ResponseEntity<Map> me = exchangeWithBearer("/api/auth/me", HttpMethod.GET, systemToken, null);
        Map<String, Object> meData = data(me);
        assertThat((List<?>) meData.get("roleIds")).hasSize(2);
        assertThat(exchangeWithBearer("/api/test-support/permission/customer-list", HttpMethod.GET, systemToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> permissionDenied = exchangeWithBearer(
                "/api/test-support/permission/customer-delete", HttpMethod.GET, systemToken, null);
        assertThat(permissionDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(permissionDenied.getBody().get("code")).isEqualTo("PERMISSION_DENIED");
        assertThat(count("audit_event", "event_code = 'PERMISSION_CHECK' and result_code = 'PERMISSION_DENIED'")).isOne();

        ResponseEntity<Map> systems = exchangeWithBearer("/api/systems", HttpMethod.GET, registrationToken, null);
        assertThat(systems.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) systems.getBody().get("data")).hasSize(1);

        ResponseEntity<Map> adminLogin = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String adminToken = (String) data(adminLogin).get("accessToken");
        ResponseEntity<Map> denied = exchangeWithBearer(
                "/api/systems/" + systemId + "/enter", HttpMethod.POST, adminToken, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getBody().get("code")).isEqualTo("SYSTEM_ACCESS_DENIED");
        assertThat(count("audit_event", "event_code = 'SYSTEM_ENTER' and result_code = 'SYSTEM_ACCESS_DENIED'")).isOne();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private ResponseEntity<Map> exchangeWithBearer(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @TestConfiguration
    static class PermissionTestConfiguration {
    }

    @RestController
    @RequestMapping("/api/test-support/permission")
    static class TestPermissionController {
        @GetMapping("/customer-list")
        @RequirePermission(resourceType = "MODULE", resourceCode = "customer", actionCode = "LIST")
        ApiResult<String> customerList() {
            return ApiResult.ok("allowed");
        }

        @GetMapping("/customer-delete")
        @RequirePermission(resourceType = "MODULE", resourceCode = "customer", actionCode = "DELETE")
        ApiResult<String> customerDelete() {
            return ApiResult.ok("allowed");
        }
    }
}
