package com.unique.unexamine.application.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 应用 HTTP 验收：发布授权、签名调用、负责人范围及主数据缺省值在同一条真实链路中验证。 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationScopeHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine").withUsername("unexamine")
            .withPassword("unexamine_test").withCommand("--log-bin-trust-function-creators=1");

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
        registry.add("app.security.application-secret-master-key", () -> "application-scope-http-test-master-key-2026");
        registry.add("app.file.storage-root", () -> "target/application-scope-test-files");
    }

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    void publishedApplicationUsesResponsibleScopeAfterRoleChangeAndDefaultsMissingOwnership() throws Exception {
        Session owner = register("app_scope_owner", "app_scope_system");
        Session alice = register("app_scope_alice", "app_scope_alice_system");
        Session bob = register("app_scope_bob", "app_scope_bob_system");
        long ownerMemberId = memberId(owner.systemId(), owner.accountId());

        jdbc.update("insert into un_sys_department(system_id,tenant_id,code,name,path_code,status) "
                + "values (?,?,?,?,?,'ACTIVE')", owner.systemId(), owner.tenantId(), "sales", "销售部", "/sales");
        long salesDepartmentId = jdbc.queryForObject("select id from un_sys_department where system_id=? and code='sales'",
                Long.class, owner.systemId());
        long aliceMemberId = addMember(owner, alice, salesDepartmentId);
        long bobMemberId = addMember(owner, bob, null);
        long moduleId = publishCustomerModule(owner);

        long aliceRecordId = createRecord(owner, aliceMemberId, salesDepartmentId, "Alice 客户");
        long bobRecordId = createRecord(owner, bobMemberId, null, "Bob 客户");
        long aliceTenantMemberId = jdbc.queryForObject("select id from un_sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, owner.tenantId(), aliceMemberId);
        jdbc.update("insert into un_sys_role(system_id,tenant_id,code,name,status) values (?,?,?,?, 'ACTIVE')",
                owner.systemId(), owner.tenantId(), "APP_SCOPE_READ", "应用范围成员角色");
        long roleId = jdbc.queryForObject("select id from un_sys_role where tenant_id=? and code='APP_SCOPE_READ'",
                Long.class, owner.tenantId());
        jdbc.update("insert into un_sys_member_role(tenant_id,tenant_member_id,role_id) values (?,?,?)",
                owner.tenantId(), aliceTenantMemberId, roleId);
        jdbc.update("insert into un_sys_role_permission(system_id,tenant_id,role_id,resource_type,resource_code,action_code,data_scope_type) "
                + "values (?,?,?,'MODULE','customer','LIST','SELF')", owner.systemId(), owner.tenantId(), roleId);

        ResponseEntity<Map> applicationCreated = exchange("/api/applications", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_access", "name", "客户接口", "applicationType", "SERVICE"));
        assertThat(applicationCreated.getStatusCode()).as("创建应用：%s", applicationCreated.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> application = map(data(applicationCreated).get("application"));
        Map<String, Object> credential = map(data(applicationCreated).get("issuedCredential"));
        long applicationId = number(application.get("id"));
        String clientId = String.valueOf(credential.get("clientId"));
        String clientSecret = String.valueOf(credential.get("clientSecret"));
        Map<String, Object> listScope = Map.of("type", "RESPONSIBLE",
                "memberIds", List.of(aliceMemberId, bobMemberId), "departmentIds", List.of(salesDepartmentId));
        Map<String, Object> createScope = Map.of("type", "RESPONSIBLE",
                "memberIds", List.of(ownerMemberId), "departmentIds", List.of());
        ResponseEntity<Map> draft = exchange("/api/applications/" + applicationId + "/draft", HttpMethod.PUT,
                owner.token(), Map.of("expectedVersion", 0, "name", "客户接口", "applicationType", "SERVICE",
                        "callbacks", List.of(), "grants", List.of(
                                grant(owner, "LIST", listScope, false), grant(owner, "CREATE", createScope, true))));
        assertThat(draft.getStatusCode()).as("保存应用授权：%s", draft.getBody()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> published = exchange("/api/applications/" + applicationId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 2, "changeSummary", "按负责人开放客户接口"));
        assertThat(published.getStatusCode()).as("发布应用：%s", published.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select current_version_id from un_app_publication where application_id=?",
                Long.class, applicationId)).isNotNull();

        // 撤销所选人员的角色后，应用仍按发布授权读取其负责的数据。
        jdbc.update("delete from un_sys_member_role where tenant_id=? and tenant_member_id=? and role_id=?",
                owner.tenantId(), aliceTenantMemberId, roleId);
        assertThat(jdbc.queryForObject("select count(*) from un_sys_member_role where tenant_member_id=?",
                Integer.class, aliceTenantMemberId)).isZero();
        ResponseEntity<Map> firstList = signedCall(owner, clientId, clientSecret, "LIST", Map.of("pageSize", 20), 1);
        assertThat(firstList.getStatusCode()).as("角色撤销后的应用查询：%s", firstList.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(recordIds(firstList)).containsExactlyInAnyOrder(aliceRecordId, bobRecordId);
        // 只授权客户名称时，业务字段和展示元数据都不能带出未授权的内部备注。
        List<?> listedRecords = (List<?>) map(map(data(firstList).get("result")).get("businessResult")).get("records");
        for (Object listed : listedRecords) {
            Map<String, Object> record = map(listed);
            assertThat(map(record.get("fields"))).containsOnlyKeys("customer_name");
            assertThat(map(record.get("fieldPresentation"))).containsOnlyKeys("customer_name");
        }

        // 不传负责人和创建人时，主记录及自定义字段一并落库，审计保留应用来源。
        ResponseEntity<Map> created = signedCall(owner, clientId, clientSecret, "CREATE",
                Map.of("title", "应用新客户", "fields", Map.of("customer_name", "应用新客户")), 2);
        assertThat(created.getStatusCode()).as("应用缺省创建：%s", created.getBody()).isEqualTo(HttpStatus.OK);
        long createdRecordId = number(map(map(data(created).get("result")).get("businessResult")).get("id"));
        Map<String, Object> stored = jdbc.queryForMap("select system_id,tenant_id,module_id,owner_member_id,created_by_member_id "
                + "from un_biz_record where id=?", createdRecordId);
        assertThat(number(stored.get("system_id"))).isEqualTo(owner.systemId());
        assertThat(number(stored.get("tenant_id"))).isEqualTo(owner.tenantId());
        assertThat(number(stored.get("module_id"))).isEqualTo(moduleId);
        assertThat(number(stored.get("owner_member_id"))).isEqualTo(ownerMemberId);
        assertThat(number(stored.get("created_by_member_id"))).isEqualTo(ownerMemberId);
        assertThat(jdbc.queryForObject("select value_text from un_biz_record_value where record_id=? and field_code='customer_name'",
                String.class, createdRecordId)).isEqualTo("应用新客户");
        assertThat(jdbc.queryForObject("select count(*) from un_audit_event where event_code='APPLICATION_CALL_SUCCEEDED' "
                + "and object_type='BUSINESS_RECORD' and object_id=? "
                + "and json_unquote(json_extract(detail_json, '$.applicationId'))=?", Integer.class,
                Long.toString(createdRecordId), Long.toString(applicationId))).isOne();

        // 一人异常只收缩其数据，其他有效负责人仍可调用。
        jdbc.update("update un_plat_account set status='DISABLED' where id=?", alice.accountId());
        ResponseEntity<Map> remaining = signedCall(owner, clientId, clientSecret, "LIST", Map.of("pageSize", 20), 3);
        assertThat(remaining.getStatusCode()).as("人员停用后的应用查询：%s", remaining.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(recordIds(remaining)).containsExactly(bobRecordId);

        // 已选择的部门失效时，即使还有有效人员也必须报错要求管理员修正配置。
        jdbc.update("update un_sys_department set status='DISABLED' where id=?", salesDepartmentId);
        ResponseEntity<Map> invalidDepartment = signedCall(owner, clientId, clientSecret, "LIST", Map.of("pageSize", 20), 4);
        assertThat(invalidDepartment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invalidDepartment.getBody()).containsEntry("code", "APPLICATION_SCOPE_DEPARTMENT_INACTIVE");
        jdbc.update("update un_sys_department set status='ACTIVE' where id=?", salesDepartmentId);
        jdbc.update("update un_plat_account set status='DISABLED' where id=?", bob.accountId());
        ResponseEntity<Map> emptyScope = signedCall(owner, clientId, clientSecret, "LIST",
                Map.of("pageSize", 20), 5);
        assertThat(emptyScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(emptyScope.getBody()).containsEntry("code", "APPLICATION_SCOPE_EMPTY");
        assertThat(jdbc.queryForObject("select count(*) from un_app_call where application_id=? and status='SUCCESS'",
                Integer.class, applicationId)).isEqualTo(3);
    }

    @Test
    void platformApplicationSelectsOnlyPlatformOrganizationMembersAndDepartments() throws Exception {
        Session owner = register("app_scope_platform_owner", "app_scope_platform_system");
        long platformId = jdbc.queryForObject("select platform_id from un_plat_member where account_id=?",
                Long.class, owner.accountId());
        long platformMemberId = jdbc.queryForObject("select id from un_plat_member where account_id=?",
                Long.class, owner.accountId());
        jdbc.update("insert into un_plat_department(platform_id,code,name,path_code,status) "
                + "values (?,?,?,?,'ACTIVE')", platformId, "app_scope_platform_sales", "平台销售部", "/app_scope_platform_sales");
        long departmentId = jdbc.queryForObject("select id from un_plat_department where platform_id=? and code=?",
                Long.class, platformId, "app_scope_platform_sales");
        jdbc.update("update un_plat_member set department_id=? where id=?", departmentId, platformMemberId);
        jdbc.update("insert into un_plat_role(platform_id,code,name,built_in,status,version) "
                + "values (?,?,?,0,'ACTIVE',0)", platformId, "APP_SCOPE_PLATFORM_ADMIN", "平台应用管理");
        long roleId = jdbc.queryForObject("select id from un_plat_role where platform_id=? and code=?",
                Long.class, platformId, "APP_SCOPE_PLATFORM_ADMIN");
        jdbc.update("insert into un_plat_member_role(platform_id,member_id,role_id) values (?,?,?)",
                platformId, platformMemberId, roleId);
        for (String action : List.of("VIEW", "MANAGE", "PUBLISH")) {
            jdbc.update("insert into un_plat_role_permission(platform_id,role_id,resource_type,resource_code,"
                            + "action_code,data_scope_type,effect,version) values (?,?,'APPLICATION','PLATFORM',?,"
                            + "'PLATFORM','ALLOW',0)", platformId, roleId, action);
        }
        for (String action : List.of("VIEW", "DESIGN", "PUBLISH")) {
            jdbc.update("insert into un_plat_role_permission(platform_id,role_id,resource_type,resource_code,"
                            + "action_code,data_scope_type,effect,version) values (?,?,'FLOW','PLATFORM',?,"
                            + "'PLATFORM','ALLOW',0)", platformId, roleId, action);
        }
        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", "app_scope_platform_owner", "password", "correct-password"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String platformToken = String.valueOf(data(login).get("accessToken"));
        ResponseEntity<Map> created = exchange("/api/applications", HttpMethod.POST, platformToken,
                Map.of("code", "platform_scope_catalog", "name", "平台范围应用", "applicationType", "SERVICE"));
        assertThat(created.getStatusCode()).as("创建平台应用：%s", created.getBody()).isEqualTo(HttpStatus.OK);
        long applicationId = number(map(data(created).get("application")).get("id"));
        ResponseEntity<Map> catalog = exchange("/api/applications/" + applicationId + "/scope-catalog",
                HttpMethod.GET, platformToken, null);
        assertThat(catalog.getStatusCode()).as("读取平台组织范围：%s", catalog.getBody()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(catalog).get("members")).anySatisfy(row ->
                assertThat(number(map(row).get("id"))).isEqualTo(platformMemberId));
        assertThat((List<?>) data(catalog).get("departments")).anySatisfy(row ->
                assertThat(number(map(row).get("id"))).isEqualTo(departmentId));
        assertThat(map(data(created).get("application"))).containsEntry("contextType", "PLATFORM")
                .containsEntry("ownerSystemId", null);

        // 平台 Flow 与应用均发布版本，应用以平台部门确定有效人员范围。
        ResponseEntity<Map> flowCreated = exchange("/api/flows", HttpMethod.POST, platformToken,
                Map.of("code", "platform_scope_flow", "name", "平台范围流程", "description", "应用范围验收"));
        assertThat(flowCreated.getStatusCode()).as("建立平台 Flow：%s", flowCreated.getBody()).isEqualTo(HttpStatus.OK);
        long flowId = number(data(flowCreated).get("id"));
        ResponseEntity<Map> flowSaved = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, platformToken,
                Map.of("expectedVersion", 0, "nodes", List.of(
                                Map.of("nodeKey", "start", "nodeType", "START", "name", "开始",
                                        "positionX", 0, "positionY", 0, "config", Map.of()),
                                Map.of("nodeKey", "end", "nodeType", "END", "name", "结束",
                                        "positionX", 400, "positionY", 0, "config", Map.of())),
                        "edges", List.of(Map.of("edgeKey", "done", "sourceNodeKey", "start",
                                "targetNodeKey", "end", "priorityOrder", 10, "config", Map.of()))));
        assertThat(flowSaved.getStatusCode()).as("保存平台 Flow：%s", flowSaved.getBody()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> flowPublished = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST,
                platformToken, Map.of("expectedDraftRevision", 2, "changeSummary", "发布平台应用目标流程"));
        assertThat(flowPublished.getStatusCode()).as("发布平台 Flow：%s", flowPublished.getBody())
                .isEqualTo(HttpStatus.OK);
        Map<String, Object> departmentScope = Map.of("type", "RESPONSIBLE", "memberIds", List.of(),
                "departmentIds", List.of(departmentId));
        Map<String, Object> flowGrant = Map.of("targetType", "PLATFORM", "resourceType", "FLOW",
                "resourceId", "platform_scope_flow", "actionCode", "START", "dataScope", departmentScope,
                "rateLimit", Map.of("accessChannels", List.of("INTERNAL", "EXTERNAL"), "maxRequests", 100,
                        "windowSeconds", 600, "allowedIps", List.of("*")), "fields", List.of());
        ResponseEntity<Map> draft = exchange("/api/applications/" + applicationId + "/draft", HttpMethod.PUT,
                platformToken, Map.of("expectedVersion", 0, "name", "平台范围应用", "applicationType", "SERVICE",
                        "callbacks", List.of(), "grants", List.of(flowGrant)));
        assertThat(draft.getStatusCode()).as("保存平台应用授权：%s", draft.getBody()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> published = exchange("/api/applications/" + applicationId + "/publish",
                HttpMethod.POST, platformToken,
                Map.of("expectedDraftRevision", 2, "changeSummary", "按平台部门授权流程"));
        assertThat(published.getStatusCode()).as("发布平台应用：%s", published.getBody()).isEqualTo(HttpStatus.OK);
        String snapshot = jdbc.queryForObject("select snapshot_json from un_app_version where application_id=?",
                String.class, applicationId);
        assertThat(snapshot).contains("platform_scope_flow", "departmentIds", "resourceVersionId");

        // 平台会话没有系统 member_id；内部调用仍须按平台账号的有效成员身份放行，并拒绝未发布动作。
        HttpHeaders internalHeaders = new HttpHeaders();
        internalHeaders.setBearerAuth(platformToken);
        internalHeaders.set("X-Idempotency-Key", "platform-scope-internal-start");
        Map<String, Object> internalBody = Map.of("resourceType", "FLOW", "resourceId", "platform_scope_flow",
                "actionCode", "START", "requestedDataScope", departmentScope,
                "payload", Map.of("title", "平台内部发起验收", "variables", Map.of()));
        ResponseEntity<Map> internalCall = http.exchange(
                "/api/application-internal-access/v1/applications/" + applicationId + "/calls", HttpMethod.POST,
                new HttpEntity<>(internalBody, internalHeaders), Map.class);
        assertThat(internalCall.getStatusCode()).as("平台应用内部调用：%s", internalCall.getBody())
                .isEqualTo(HttpStatus.OK);
        internalHeaders.set("X-Idempotency-Key", "platform-scope-internal-denied");
        ResponseEntity<Map> internalDenied = http.exchange(
                "/api/application-internal-access/v1/applications/" + applicationId + "/calls", HttpMethod.POST,
                new HttpEntity<>(Map.of("resourceType", "FLOW", "resourceId", "platform_scope_flow",
                        "actionCode", "PUBLISH", "requestedDataScope", departmentScope,
                        "payload", Map.of("flowId", flowId)), internalHeaders), Map.class);
        assertThat(internalDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(internalDenied.getBody()).containsEntry("code", "APPLICATION_GRANT_DENIED");

        // 发布后撤销当前人员的角色，应用仍只按自身发布授权和实时有效范围调用。
        jdbc.update("delete from un_plat_member_role where platform_id=? and member_id=? and role_id=?",
                platformId, platformMemberId, roleId);
        Map<String, Object> credential = map(data(created).get("issuedCredential"));
        Map<String, Object> callBody = Map.of("resourceType", "FLOW", "resourceId", "platform_scope_flow",
                "actionCode", "START", "payload", Map.of("title", "平台外部发起验收", "variables", Map.of()));
        ResponseEntity<Map> called = signedRequest(callBody, String.valueOf(credential.get("clientId")),
                String.valueOf(credential.get("clientSecret")), 101);
        assertThat(called.getStatusCode()).as("平台应用签名调用：%s", called.getBody()).isEqualTo(HttpStatus.OK);
        long instanceId = number(map(map(data(called).get("result")).get("instance")).get("id"));
        assertThat(jdbc.queryForObject("select count(*) from un_flow_instance where id=? and platform_id=?",
                Integer.class, instanceId, platformId)).isOne();
        jdbc.update("update un_plat_department set status='DISABLED' where id=?", departmentId);
        ResponseEntity<Map> invalidDepartment = signedRequest(callBody, String.valueOf(credential.get("clientId")),
                String.valueOf(credential.get("clientSecret")), 102);
        assertThat(invalidDepartment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(invalidDepartment.getBody()).containsEntry("code", "APPLICATION_SCOPE_DEPARTMENT_INACTIVE");
    }

    private Session register(String username, String systemCode) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", username + "@example.com", "systemName", systemCode, "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).as("注册测试账号：%s", response.getBody()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session(number(result.get("accountId")), number(result.get("systemId")),
                number(result.get("tenantId")), String.valueOf(map(result.get("tokens")).get("accessToken")));
    }

    private long addMember(Session owner, Session account, Long departmentId) {
        jdbc.update("insert into un_sys_member(system_id,account_id,display_name,status) values (?,?,?,'ACTIVE')",
                owner.systemId(), account.accountId(), "测试成员 " + account.accountId());
        long memberId = memberId(owner.systemId(), account.accountId());
        jdbc.update("insert into un_sys_tenant_member(system_id,tenant_id,system_member_id,department_id,tenant_admin,status) "
                + "values (?,?,?,?,0,'ACTIVE')", owner.systemId(), owner.tenantId(), memberId, departmentId);
        return memberId;
    }

    private long publishCustomerModule(Session owner) {
        ResponseEntity<Map> group = exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售管理", "sortOrder", 10));
        assertThat(group.getStatusCode()).as("创建模块组：%s", group.getBody()).isEqualTo(HttpStatus.OK);
        long groupId = number(data(group).get("id"));
        ResponseEntity<Map> module = exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户"));
        assertThat(module.getStatusCode()).as("创建模块：%s", module.getBody()).isEqualTo(HttpStatus.OK);
        long moduleId = number(map(data(module).get("module")).get("id"));
        ResponseEntity<Map> field = exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST,
                owner.token(), Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT",
                        "required", true, "sortOrder", 10, "config", Map.of()));
        assertThat(field.getStatusCode()).as("创建字段：%s", field.getBody()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> privateField = exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST,
                owner.token(), Map.of("code", "private_note", "name", "内部备注", "fieldType", "TEXT",
                        "required", false, "sortOrder", 20, "config", Map.of()));
        assertThat(privateField.getStatusCode()).as("创建内部字段：%s", privateField.getBody()).isEqualTo(HttpStatus.OK);
        int revision = jdbc.queryForObject("select draft_revision from un_cfg_module where id=?", Integer.class, moduleId);
        ResponseEntity<Map> published = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", revision));
        assertThat(published.getStatusCode()).as("发布模块：%s", published.getBody()).isEqualTo(HttpStatus.OK);
        return moduleId;
    }

    private long createRecord(Session owner, long memberId, Long departmentId, String name) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("title", name);
        body.put("ownerMemberId", memberId);
        if (departmentId != null) body.put("departmentId", departmentId);
        body.put("participantMemberIds", List.of());
        body.put("fields", Map.of("customer_name", name, "private_note", "仅内部可读：" + name));
        ResponseEntity<Map> response = exchange("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), body);
        assertThat(response.getStatusCode()).as("建立负责人测试数据：%s", response.getBody()).isEqualTo(HttpStatus.OK);
        return number(data(response).get("id"));
    }

    private Map<String, Object> grant(Session owner, String action, Map<String, Object> scope, boolean write) {
        return Map.of("targetType", "SYSTEM", "targetSystemId", owner.systemId(), "targetTenantId", owner.tenantId(),
                "resourceType", "MODULE", "resourceId", "customer", "actionCode", action,
                "dataScope", scope, "rateLimit", Map.of("accessChannels", List.of("EXTERNAL"),
                        "maxRequests", 100, "windowSeconds", 600, "allowedIps", List.of("*")),
                "fields", List.of(Map.of("fieldCode", "customer_name", "readable", true, "writable", write,
                        "maskStrategy", "NONE")));
    }

    private ResponseEntity<Map> signedCall(Session owner, String clientId, String secret, String action,
            Map<String, Object> payload, int sequence) throws Exception {
        Map<String, Object> body = Map.of("resourceType", "MODULE", "resourceId", "customer", "actionCode", action,
                "targetSystemId", owner.systemId(), "targetTenantId", owner.tenantId(), "payload", payload);
        return signedRequest(body, clientId, secret, sequence);
    }

    private ResponseEntity<Map> signedRequest(Map<String, Object> body, String clientId, String secret,
            int sequence) throws Exception {
        String raw = json.writeValueAsString(body);
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = "app-scope-nonce-" + sequence;
        String idempotencyKey = "app-scope-call-" + sequence;
        String requestHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
        String canonical = clientId + "\n" + timestamp + "\n" + nonce + "\n" + idempotencyKey + "\n" + requestHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-App-Client-Id", clientId);
        headers.set("X-App-Timestamp", timestamp);
        headers.set("X-App-Nonce", nonce);
        headers.set("X-App-Idempotency-Key", idempotencyKey);
        headers.set("X-App-Signature", signature);
        return http.exchange("/api/application-access/v1/calls", HttpMethod.POST,
                new HttpEntity<>(raw, headers), Map.class);
    }

    private List<Long> recordIds(ResponseEntity<Map> response) {
        Map<String, Object> result = map(data(response).get("result"));
        Map<String, Object> business = map(result.get("businessResult"));
        return ((List<?>) business.get("records")).stream().map(value -> number(map(value).get("id"))).toList();
    }

    private long memberId(long systemId, long accountId) {
        return jdbc.queryForObject("select id from un_sys_member where system_id=? and account_id=?",
                Long.class, systemId, accountId);
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private record Session(long accountId, long systemId, long tenantId, String token) {
    }
}
