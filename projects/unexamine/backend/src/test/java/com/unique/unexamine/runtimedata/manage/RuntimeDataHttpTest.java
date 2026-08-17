package com.unique.unexamine.runtimedata.manage;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RuntimeDataHttpTest {
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
    void publishedRuntimeStoresTypedValuesVersionsAndEnforcesActionScopeAndTenantBoundary() {
        Session owner = register("runtime_owner", "runtime-system", "runtime-owner@example.com");
        Session colleagueAccount = register("runtime_colleague", "colleague-system", "runtime-colleague@example.com");
        long ownerMemberId = memberId(owner.systemId(), owner.accountId());
        long colleagueAccountId = colleagueAccount.accountId();

        jdbc.update("insert into sys_member(system_id, account_id, display_name, status) values (?, ?, '同部门成员', 'ACTIVE')",
                owner.systemId(), colleagueAccountId);
        long colleagueMemberId = memberId(owner.systemId(), colleagueAccountId);
        jdbc.update("insert into sys_department(system_id, tenant_id, code, name, status) values (?, ?, 'sales', '销售部', 'ACTIVE')",
                owner.systemId(), owner.tenantId());
        long departmentId = jdbc.queryForObject(
                "select id from sys_department where tenant_id = ? and code = 'sales'", Long.class, owner.tenantId());
        jdbc.update("update sys_tenant_member set department_id = ? where tenant_id = ? and system_member_id = ?",
                departmentId, owner.tenantId(), ownerMemberId);
        jdbc.update("insert into sys_tenant_member(system_id, tenant_id, system_member_id, department_id, tenant_admin, status) values (?, ?, ?, ?, 0, 'ACTIVE')",
                owner.systemId(), owner.tenantId(), colleagueMemberId, departmentId);
        long colleagueTenantMemberId = jdbc.queryForObject(
                "select id from sys_tenant_member where tenant_id = ? and system_member_id = ?",
                Long.class, owner.tenantId(), colleagueMemberId);
        installColleaguePermissions(owner, colleagueTenantMemberId);

        long moduleId = createAndPublishCustomerModule(owner);
        long firstConfigVersionId = jdbc.queryForObject(
                "select current_version_id from cfg_module_publication where module_id = ?", Long.class, moduleId);

        ResponseEntity<Map> created = exchange("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", "C-001",
                "title", "北京客户",
                "status", "ACTIVE",
                "ownerMemberId", ownerMemberId,
                "departmentId", departmentId,
                "participantMemberIds", List.of(colleagueMemberId),
                "fields", Map.of(
                        "customer_name", "北京客户",
                        "level", "A",
                        "amount", new BigDecimal("1200.50"),
                        "enabled", true)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createdData = data(created);
        long recordId = number(createdData.get("id"));
        int initialRecordVersion = ((Number) createdData.get("version")).intValue();
        assertThat(number(createdData.get("createdConfigVersionId"))).isEqualTo(firstConfigVersionId);
        assertThat(number(createdData.get("updatedConfigVersionId"))).isEqualTo(firstConfigVersionId);
        assertThat(((List<?>) createdData.get("participantMemberIds")).stream()
                .map(value -> ((Number) value).longValue()).toList()).containsExactly(colleagueMemberId);
        assertThat(jdbc.queryForObject(
                "select value_number from biz_record_value where record_id = ? and field_code = 'amount'",
                BigDecimal.class, recordId)).isEqualByComparingTo("1200.50000000");
        assertThat(jdbc.queryForObject(
                "select value_boolean from biz_record_value where record_id = ? and field_code = 'enabled'",
                Boolean.class, recordId)).isTrue();
        assertThat(count("biz_record_participant", "record_id = " + recordId + " and system_member_id = " + colleagueMemberId)).isOne();

        int recordsBeforeInvalid = count("biz_record", "module_id = " + moduleId);
        ResponseEntity<Map> invalidMissingRequired = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "缺少必填字段", "participantMemberIds", List.of(),
                        "fields", Map.of("level", "A")));
        assertThat(invalidMissingRequired.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidMissingRequired.getBody().get("code")).isEqualTo("FIELD_VALIDATION_FAILED");
        ResponseEntity<Map> invalidDisabledOption = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "停用选项", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "停用选项", "level", "OLD")));
        assertThat(invalidDisabledOption.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(count("biz_record", "module_id = " + moduleId)).isEqualTo(recordsBeforeInvalid);
        assertThat(count("audit_event", "event_code = 'BUSINESS_RECORD_CREATE' and result_code = 'FIELD_VALIDATION_FAILED'"))
                .isEqualTo(2);

        publishSecondVersion(owner, moduleId);
        long secondConfigVersionId = jdbc.queryForObject(
                "select current_version_id from cfg_module_publication where module_id = ?", Long.class, moduleId);
        assertThat(secondConfigVersionId).isNotEqualTo(firstConfigVersionId);
        ResponseEntity<Map> updated = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, owner.token(), Map.of(
                        "recordNumber", "C-001", "title", "北京重要客户", "status", "ACTIVE",
                        "ownerMemberId", ownerMemberId, "departmentId", departmentId,
                        "participantMemberIds", List.of(colleagueMemberId), "version", initialRecordVersion,
                        "fields", Map.of("customer_name", "北京重要客户", "level", "A",
                                "amount", new BigDecimal("1500.75"), "enabled", false)));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(data(updated).get("createdConfigVersionId"))).isEqualTo(firstConfigVersionId);
        assertThat(number(data(updated).get("updatedConfigVersionId"))).isEqualTo(secondConfigVersionId);
        assertThat(((Number) data(updated).get("version")).intValue()).isEqualTo(initialRecordVersion + 1);

        ResponseEntity<Map> colleaguePlatformLogin = http.postForEntity("/api/auth/login", Map.of(
                "username", "runtime_colleague", "password", "correct-password"), Map.class);
        String colleaguePlatformToken = data(colleaguePlatformLogin).get("accessToken").toString();
        ResponseEntity<Map> colleagueEntered = exchange(
                "/api/systems/" + owner.systemId() + "/enter", HttpMethod.POST, colleaguePlatformToken, null);
        String colleagueToken = ((Map<?, ?>) data(colleagueEntered).get("tokens")).get("accessToken").toString();

        ResponseEntity<Map> colleagueConfiguration = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, colleagueToken, null);
        List<String> visibleActions = ((List<?>) ((Map<?, ?>) data(colleagueConfiguration).get("configuration")).get("actions"))
                .stream().map(action -> ((Map<?, ?>) action).get("code").toString()).toList();
        assertThat(visibleActions).containsExactly("LIST", "DETAIL", "UPDATE");
        assertThat(visibleActions).doesNotContain("CREATE");

        ResponseEntity<Map> colleagueList = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.GET, colleagueToken, null);
        assertThat(colleagueList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(colleagueList).get("records")).hasSize(1);
        ResponseEntity<Map> colleagueDetail = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.GET, colleagueToken, null);
        assertThat(colleagueDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> colleagueUpdate = exchange(
                "/api/runtime/modules/customer/records/" + recordId, HttpMethod.PUT, colleagueToken, Map.of(
                        "title", "越权修改", "participantMemberIds", List.of(),
                        "version", ((Number) data(updated).get("version")).intValue(),
                        "fields", Map.of("customer_name", "越权修改", "level", "A")));
        assertThat(colleagueUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> colleagueCreate = exchange(
                "/api/runtime/modules/customer/records", HttpMethod.POST, colleagueToken, Map.of(
                        "title", "越权新建", "participantMemberIds", List.of(),
                        "fields", Map.of("customer_name", "越权新建", "level", "A")));
        assertThat(colleagueCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> colleagueAudit = exchange(
                "/api/admin/audit-events", HttpMethod.GET, colleagueToken, null);
        assertThat(colleagueAudit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select title from biz_record where id = ?", String.class, recordId))
                .isEqualTo("北京重要客户");

        long otherTenantId = createOtherTenant(owner);
        jdbc.update("insert into biz_record(system_id, tenant_id, module_id, created_config_version_id, updated_config_version_id, title, status, owner_member_id, created_by_member_id, updated_by_member_id) values (?, ?, ?, ?, ?, '其他租户数据', 'ACTIVE', ?, ?, ?)",
                owner.systemId(), otherTenantId, moduleId, firstConfigVersionId, firstConfigVersionId,
                ownerMemberId, ownerMemberId, ownerMemberId);
        long otherTenantRecordId = jdbc.queryForObject(
                "select id from biz_record where tenant_id = ? and title = '其他租户数据'", Long.class, otherTenantId);
        ResponseEntity<Map> ownerList = exchange(
                "/api/runtime/modules/customer/records?tenantId=" + otherTenantId, HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) data(ownerList).get("records")).hasSize(1);
        ResponseEntity<Map> crossTenantDetail = exchange(
                "/api/runtime/modules/customer/records/" + otherTenantRecordId, HttpMethod.GET, owner.token(), null);
        assertThat(crossTenantDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(count("audit_event", "event_code = 'BUSINESS_RECORD_CREATED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'BUSINESS_RECORD_UPDATED' and result_code = 'SUCCESS'")).isOne();
        assertThat(count("audit_event", "event_code = 'PERMISSION_CHECK' and result_code = 'PERMISSION_DENIED'"))
                .isGreaterThanOrEqualTo(2);
    }

    private long createAndPublishCustomerModule(Session owner) {
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售管理", "sortOrder", 10))).get("id"));
        Map<String, Object> moduleDraft = data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户")));
        long moduleId = number(((Map<?, ?>) moduleDraft.get("module")).get("id"));
        createField(owner, moduleId, "customer_name", "客户名称", "TEXT", true, 10, Map.of());
        createField(owner, moduleId, "level", "客户级别", "SINGLE_SELECT", true, 20, Map.of("options", List.of(
                Map.of("value", "A", "label", "A级", "status", "ACTIVE"),
                Map.of("value", "OLD", "label", "旧级别", "status", "DISABLED"))));
        createField(owner, moduleId, "amount", "金额", "MONEY", false, 30, Map.of());
        createField(owner, moduleId, "enabled", "是否有效", "BOOLEAN", false, 40, Map.of());
        int revision = jdbc.queryForObject("select draft_revision from cfg_module where id = ?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        return moduleId;
    }

    private void createField(
            Session owner, long moduleId, String code, String name, String type, boolean required, int order, Object config) {
        ResponseEntity<Map> response = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of("code", code, "name", name, "fieldType", type,
                        "required", required, "sortOrder", order, "config", config));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void publishSecondVersion(Session owner, long moduleId) {
        Map<String, Object> draft = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, owner.token(), null));
        Map<?, ?> field = ((List<Map<?, ?>>) draft.get("fields")).stream()
                .filter(value -> "customer_name".equals(value.get("code"))).findFirst().orElseThrow();
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields/" + field.get("id"), HttpMethod.PUT,
                owner.token(), Map.of("name", "客户全称", "required", true, "sortOrder", 10,
                        "status", "ACTIVE", "config", Map.of(), "version", field.get("version")));
        int revision = jdbc.queryForObject("select draft_revision from cfg_module where id = ?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void installColleaguePermissions(Session owner, long colleagueTenantMemberId) {
        jdbc.update("insert into sys_role(system_id, tenant_id, code, name, status) values (?, ?, 'COLLEAGUE', '普通成员', 'ACTIVE')",
                owner.systemId(), owner.tenantId());
        long roleId = jdbc.queryForObject(
                "select id from sys_role where tenant_id = ? and code = 'COLLEAGUE'", Long.class, owner.tenantId());
        jdbc.update("insert into sys_member_role(tenant_id, tenant_member_id, role_id) values (?, ?, ?)",
                owner.tenantId(), colleagueTenantMemberId, roleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type) values (?, 'MODULE', 'customer', 'LIST', 'DEPARTMENT')",
                roleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type) values (?, 'MODULE', 'customer', 'DETAIL', 'PARTICIPATED')",
                roleId);
        jdbc.update("insert into sys_role_permission(role_id, resource_type, resource_code, action_code, data_scope_type) values (?, 'MODULE', 'customer', 'UPDATE', 'SELF')",
                roleId);
    }

    private long createOtherTenant(Session owner) {
        jdbc.update("insert into sys_tenant(system_id, code, name, is_main, main_marker, creator_account_id, status) values (?, 'other', '其他租户', 0, null, ?, 'ACTIVE')",
                owner.systemId(), owner.accountId());
        return jdbc.queryForObject("select id from sys_tenant where system_id = ? and code = 'other'",
                Long.class, owner.systemId());
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", username, "email", email,
                "systemName", systemCode, "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session(number(result.get("accountId")), number(result.get("systemId")), number(result.get("tenantId")),
                ((Map<?, ?>) result.get("tokens")).get("accessToken").toString());
    }

    private long memberId(long systemId, long accountId) {
        return jdbc.queryForObject(
                "select id from sys_member where system_id = ? and account_id = ?", Long.class, systemId, accountId);
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private record Session(long accountId, long systemId, long tenantId, String token) {
    }
}
