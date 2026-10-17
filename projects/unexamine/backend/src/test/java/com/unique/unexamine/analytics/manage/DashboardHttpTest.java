package com.unique.unexamine.analytics.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DashboardHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private MockMvc http;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void legacyModuleDashboardKeepsListViewerOnFrozenPageUntilActionIsExplicitlyGranted() {
        Session owner = register("dashboard_legacy_owner", "dashboard_legacy_system", "dashboard-legacy@example.com");
        long moduleId = publishCustomerModule(owner);
        Map<String, Object> oldInput = dashboard("old_customer_home", "旧客户首页", "升级前页面",
                List.of(component("old_entry", "QUICK_ENTRY", "旧页面入口", null,
                        0, Map.of(), Map.of("width", 1), Map.of("moduleCode", "customer"))), null);
        oldInput.put("dashboardKind", "MODULE_HOME");
        oldInput.put("moduleId", moduleId);
        Map<String, Object> oldDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), oldInput);
        long oldDashboardId = number(oldDashboard.get("id"));
        ok("/api/analytics/admin/dashboards/" + oldDashboardId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", oldDashboard.get("draftRevision")));
        // 模拟 V23 从 V22 发布指针固定的旧首页；新装库不会自动写入清单。
        assertThat(count("un_ana_legacy_module_dashboard", "module_id=" + moduleId)).isZero();
        jdbc.update("""
                insert into un_ana_legacy_module_dashboard
                  (dashboard_id,module_id,system_id,tenant_id,source_module_version_id,source_dashboard_version_id)
                select d.id,d.module_id,d.system_id,d.owner_tenant_id,mp.current_version_id,dp.current_version_id
                from un_ana_dashboard d join un_ana_dashboard_publication dp on dp.dashboard_id=d.id
                join un_cfg_module_publication mp on mp.module_id=d.module_id where d.id=?
                """, oldDashboardId);
        String runtime = "/api/analytics/runtime?moduleCode=customer";
        assertThat(ok(runtime, HttpMethod.GET, owner.token(), null)).containsEntry("dashboardId", (int) oldDashboardId);
        assertThat(list(map(ok("/api/runtime/modules/customer/configuration", HttpMethod.GET,
                owner.token(), null).get("configuration")).get("actions")))
                .anyMatch(item -> "DASHBOARD".equals(item.get("code"))
                        && Boolean.TRUE.equals(item.get("legacyDashboardSynthetic")));

        register("dashboard_legacy_viewer", "dashboard_legacy_viewer_system",
                "dashboard-legacy-viewer@example.com");
        long accountId = jdbc.queryForObject("select id from un_plat_account where username='dashboard_legacy_viewer'",
                Long.class);
        jdbc.update("insert into un_sys_member(system_id,account_id,display_name,status) "
                + "values (?,?,'旧首页成员','ACTIVE')", owner.systemId(), accountId);
        long memberId = jdbc.queryForObject("select id from un_sys_member where system_id=? and account_id=?",
                Long.class, owner.systemId(), accountId);
        jdbc.update("insert into un_sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status) "
                + "values (?,?,?,0,'ACTIVE')", owner.systemId(), owner.tenantId(), memberId);
        long tenantMemberId = jdbc.queryForObject("select id from un_sys_tenant_member where tenant_id=? "
                + "and system_member_id=?", Long.class, owner.tenantId(), memberId);
        Map<String, Object> role = ok("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.token(), Map.of("code", "legacy_customer_list", "name", "旧客户列表",
                        "permissions", List.of(Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST", "dataScopeType", "ALL")), "fieldPolicies", List.of()));
        long roleId = number(role.get("id"));
        ok("/api/admin/system/authorization/roles/" + roleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("reason", "验证旧首页原授权", "expectedVersion", role.get("version")));
        int memberVersion = jdbc.queryForObject("select version from un_sys_tenant_member where id=?",
                Integer.class, tenantMemberId);
        ok("/api/admin/system/authorization/members/" + tenantMemberId + "/assignment", HttpMethod.POST,
                owner.token(), Map.of("roleIds", List.of(roleId), "expectedVersion", memberVersion));
        Map<String, Object> entered = ok("/api/systems/" + owner.systemId() + "/enter", HttpMethod.POST,
                login("dashboard_legacy_viewer", "correct-password"), Map.of());
        String viewerToken = String.valueOf(map(entered.get("tokens")).get("accessToken"));
        assertThat(ok(runtime, HttpMethod.GET, viewerToken, null)).containsEntry("dashboardId", (int) oldDashboardId);

        Map<String, Object> newInput = dashboard("new_customer_home", "新客户首页", "升级后页面",
                List.of(component("new_entry", "QUICK_ENTRY", "新页面入口", null,
                        0, Map.of(), Map.of("width", 1), Map.of("moduleCode", "customer"))), null);
        newInput.put("dashboardKind", "MODULE_HOME");
        newInput.put("moduleId", moduleId);
        Map<String, Object> newDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), newInput);
        long newDashboardId = number(newDashboard.get("id"));
        ok("/api/analytics/admin/dashboards/" + newDashboardId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", newDashboard.get("draftRevision")));
        assertThat(ok(runtime, HttpMethod.GET, owner.token(), null)).containsEntry("dashboardId", (int) oldDashboardId);
        assertThat(ok(runtime, HttpMethod.GET, viewerToken, null)).containsEntry("dashboardId", (int) oldDashboardId);
        Map<String, Object> action = ok("/api/admin/module-config/modules/" + moduleId + "/actions/dashboard",
                HttpMethod.POST, owner.token(), null);
        int publicationVersion = jdbc.queryForObject("select version from un_cfg_module_publication where module_id=?",
                Integer.class, moduleId);
        Map<String, Object> enabled = ok("/api/admin/module-config/modules/" + moduleId
                + "/dashboard-action-publish?expectedPublicationVersion=" + publicationVersion
                + "&expectedActionVersion=" + action.get("version"), HttpMethod.POST, owner.token(), null);
        assertThat(ok(runtime, HttpMethod.GET, owner.token(), null)).containsEntry("dashboardId", (int) newDashboardId);
        assertThat(ok(runtime, HttpMethod.GET, viewerToken, null)).containsEntry("dashboardId", (int) oldDashboardId);
        Map<String, Object> disabled = ok("/api/admin/module-config/modules/" + moduleId + "/actions/DASHBOARD",
                HttpMethod.PUT, owner.token(), Map.of("location", "MODULE_ENTRY", "config", Map.of(),
                        "status", "DISABLED", "version", action.get("version")));
        publicationVersion = jdbc.queryForObject("select version from un_cfg_module_publication where module_id=?",
                Integer.class, moduleId);
        ok("/api/admin/module-config/modules/" + moduleId + "/dashboard-action-publish?expectedPublicationVersion="
                + publicationVersion + "&expectedActionVersion=" + disabled.get("version"),
                HttpMethod.POST, owner.token(), null);
        assertThat(exchange(runtime, HttpMethod.GET, viewerToken, null).status()).isEqualTo(HttpStatus.CONFLICT);
        publicationVersion = jdbc.queryForObject("select version from un_cfg_module_publication where module_id=?",
                Integer.class, moduleId);
        ok("/api/admin/module-config/modules/" + moduleId + "/rollback", HttpMethod.POST, owner.token(),
                Map.of("targetVersionId", enabled.get("versionId"), "expectedPublicationVersion", publicationVersion));
        assertThat(ok(runtime, HttpMethod.GET, viewerToken, null)).containsEntry("dashboardId", (int) oldDashboardId);
    }

    @Test
    void publishedModuleDashboardActionGatesCurrentTenantPageAndKeepsOtherDraftsPrivate() {
        Session owner = register("dashboard_action_owner", "dashboard_action_system", "dashboard-action@example.com");
        long existingId = publishCustomerModule(owner);
        String existingRuntime = "/api/analytics/runtime?moduleCode=customer";
        Map<String, Object> existingDashboardInput = dashboard("customer_home", "客户首页", "客户业务概览",
                List.of(component("customer_entry", "QUICK_ENTRY", "客户记录", null,
                        0, Map.of(), Map.of("width", 1), Map.of("moduleCode", "customer"))), null);
        existingDashboardInput.put("dashboardKind", "MODULE_HOME");
        existingDashboardInput.put("moduleId", existingId);
        Map<String, Object> existingDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), existingDashboardInput);
        ok("/api/analytics/admin/dashboards/" + existingDashboard.get("id") + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", existingDashboard.get("draftRevision")));
        assertThat(exchange(existingRuntime, HttpMethod.GET, owner.token(), null).status()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> action = ok("/api/admin/module-config/modules/" + existingId + "/actions/dashboard",
                HttpMethod.POST, owner.token(), null);
        // 已有模块的无关字段草稿不能随着首页入口发布。
        ok("/api/admin/module-config/modules/" + existingId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "future_note", "name", "待发布备注", "fieldType", "TEXT", "required", false,
                        "searchable", false, "sortOrder", 80, "config", Map.of()));
        assertThat(exchange(existingRuntime, HttpMethod.GET, owner.token(), null).status()).isEqualTo(HttpStatus.CONFLICT);
        String actionPublish = "/api/admin/module-config/modules/" + existingId + "/dashboard-action-publish";
        // 当前发布指针用乐观版本检查，避免覆盖其他管理员刚发布的版本。
        int publicationVersion = jdbc.queryForObject("select version from un_cfg_module_publication where module_id=?",
                Integer.class, existingId);
        Map<String, Object> enabled = ok(actionPublish + "?expectedPublicationVersion=" + publicationVersion
                + "&expectedActionVersion=" + action.get("version"), HttpMethod.POST, owner.token(), null);
        assertThat(ok(existingRuntime, HttpMethod.GET, owner.token(), null))
                .containsEntry("configured", true).containsEntry("name", "客户首页");
        Map<String, Object> publishedModule = ok("/api/admin/module-config/modules/" + existingId + "/published",
                HttpMethod.GET, owner.token(), null);
        assertThat(list(map(publishedModule.get("configuration")).get("fields")))
                .noneMatch(field -> "future_note".equals(field.get("code")));
        assertThat(list(map(publishedModule.get("configuration")).get("actions")))
                .anyMatch(item -> "DASHBOARD".equals(item.get("code")) && "ACTIVE".equals(item.get("status")));

        Map<String, Object> disabled = ok("/api/admin/module-config/modules/" + existingId + "/actions/DASHBOARD",
                HttpMethod.PUT, owner.token(), Map.of("location", "MODULE_ENTRY", "config", Map.of(),
                        "status", "DISABLED", "version", action.get("version")));
        assertThat(ok(existingRuntime, HttpMethod.GET, owner.token(), null)).containsEntry("configured", true);
        publicationVersion = jdbc.queryForObject("select version from un_cfg_module_publication where module_id=?",
                Integer.class, existingId);
        ok(actionPublish + "?expectedPublicationVersion=" + publicationVersion
                + "&expectedActionVersion=" + disabled.get("version"), HttpMethod.POST, owner.token(), null);
        assertThat(exchange(existingRuntime, HttpMethod.GET, owner.token(), null).status()).isEqualTo(HttpStatus.CONFLICT);
        publicationVersion = jdbc.queryForObject("select version from un_cfg_module_publication where module_id=?",
                Integer.class, existingId);
        ok("/api/admin/module-config/modules/" + existingId + "/rollback", HttpMethod.POST, owner.token(),
                Map.of("targetVersionId", enabled.get("versionId"), "expectedPublicationVersion", publicationVersion));
        assertThat(ok(existingRuntime, HttpMethod.GET, owner.token(), null)).containsEntry("configured", true);

        long groupId = number(ok("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "operations", "name", "运营", "sortOrder", 20)).get("id"));
        long newId = number(map(ok("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "campaign", "name", "活动")).get("module")).get("id"));
        ok("/api/admin/module-config/modules/" + newId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "campaign_name", "name", "活动名称", "fieldType", "TEXT", "required", true,
                        "searchable", true, "sortOrder", 10, "config", Map.of()));
        ok("/api/admin/module-config/modules/" + newId + "/actions/dashboard", HttpMethod.POST, owner.token(), null);
        Map<String, Object> check = ok("/api/admin/module-config/modules/" + newId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        ok("/api/admin/module-config/modules/" + newId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
        assertThat(ok("/api/analytics/runtime?moduleCode=campaign", HttpMethod.GET, owner.token(), null))
                .containsEntry("configured", false);
        Map<String, Object> newDashboardInput = dashboard("campaign_home", "活动首页", "活动概览",
                List.of(component("campaign_entry", "QUICK_ENTRY", "活动记录", null,
                        0, Map.of(), Map.of("width", 1), Map.of("moduleCode", "campaign"))), null);
        newDashboardInput.put("dashboardKind", "MODULE_HOME");
        newDashboardInput.put("moduleId", newId);
        Map<String, Object> newDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), newDashboardInput);
        ok("/api/analytics/admin/dashboards/" + newDashboard.get("id") + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", newDashboard.get("draftRevision")));
        assertThat(ok("/api/analytics/runtime?moduleCode=campaign", HttpMethod.GET, owner.token(), null))
                .containsEntry("configured", true).containsEntry("name", "活动首页");
        register("dashboard_action_viewer", "dashboard_action_viewer_system",
                "dashboard-action-viewer@example.com");
        long viewerAccountId = jdbc.queryForObject(
                "select id from un_plat_account where username='dashboard_action_viewer'", Long.class);
        jdbc.update("insert into un_sys_member(system_id,account_id,display_name,status) "
                + "values (?,?,'首页受限成员','ACTIVE')", owner.systemId(), viewerAccountId);
        long viewerMemberId = jdbc.queryForObject(
                "select id from un_sys_member where system_id=? and account_id=?", Long.class,
                owner.systemId(), viewerAccountId);
        jdbc.update("insert into un_sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status) "
                + "values (?,?,?,0,'ACTIVE')", owner.systemId(), owner.tenantId(), viewerMemberId);
        long viewerTenantMemberId = jdbc.queryForObject(
                "select id from un_sys_tenant_member where tenant_id=? and system_member_id=?", Long.class,
                owner.tenantId(), viewerMemberId);
        Map<String, Object> role = ok("/api/admin/system/authorization/roles", HttpMethod.POST,
                owner.token(), Map.of("code", "dashboard_list_only", "name", "仅看模块记录",
                        "permissions", List.of(Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST", "dataScopeType", "ALL")), "fieldPolicies", List.of()));
        long roleId = number(role.get("id"));
        ok("/api/admin/system/authorization/roles/" + roleId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("reason", "验证仪表盘动作需单独授权", "expectedVersion", role.get("version")));
        int memberVersion = jdbc.queryForObject("select version from un_sys_tenant_member where id=?",
                Integer.class, viewerTenantMemberId);
        ok("/api/admin/system/authorization/members/" + viewerTenantMemberId + "/assignment",
                HttpMethod.POST, owner.token(), Map.of("roleIds", List.of(roleId), "expectedVersion", memberVersion));
        Map<String, Object> entered = ok("/api/systems/" + owner.systemId() + "/enter", HttpMethod.POST,
                login("dashboard_action_viewer", "correct-password"), Map.of());
        String viewerToken = String.valueOf(map(entered.get("tokens")).get("accessToken"));
        assertThat(exchange(existingRuntime, HttpMethod.GET, viewerToken, null).status())
                .isEqualTo(HttpStatus.FORBIDDEN);
        ok("/api/admin/system/settings", HttpMethod.PUT, owner.token(),
                Map.of("name", "仪表盘隔离系统", "tenantMode", "MULTI"));
        long branchId = number(ok("/api/admin/system/tenants", HttpMethod.POST, owner.token(),
                Map.of("code", "dashboard_branch", "name", "仪表盘分租户")).get("id"));
        Map<String, Object> branchEntered = ok("/api/systems/" + owner.systemId() + "/tenants/" + branchId
                + "/enter", HttpMethod.POST, owner.token(),
                Map.of("previousSystemId", owner.systemId(), "previousTenantId", owner.tenantId()));
        String branchToken = String.valueOf(map(branchEntered.get("tokens")).get("accessToken"));
        assertThat(ok(existingRuntime, HttpMethod.GET, branchToken, null)).containsEntry("configured", false);
        Session outsider = register("dashboard_action_other", "dashboard_action_other_system",
                "dashboard-action-other@example.com");
        assertThat(exchange(existingRuntime, HttpMethod.GET, outsider.token(), null).status())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mainTenantPublishedSourceCanBeReusedWithoutBorrowingItsDataScope() {
        Session owner = register("dashboard_shared_owner", "dashboard_shared_system", "dashboard-shared-owner@example.com");
        publishCustomerModule(owner);
        Map<String, Object> mainSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("main_customers", "主租户客户", "MODULE_RECORDS",
                        Map.of("moduleCode", "customer", "limit", 5),
                        Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"), null));
        mainSource = ok("/api/analytics/admin/data-sources/" + mainSource.get("id") + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", mainSource.get("draftRevision")));
        long sourceId = number(mainSource.get("id"));
        Map<String, Object> mainDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), dashboard("system_home", "主租户首页", "主租户独立页面",
                        List.of(component("main_total", "METRIC", "主租户客户数", sourceId,
                                0, Map.of(), Map.of("width", 1), Map.of())), null));
        ok("/api/analytics/admin/dashboards/" + mainDashboard.get("id") + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", mainDashboard.get("draftRevision")));

        ok("/api/admin/system/settings", HttpMethod.PUT, owner.token(),
                Map.of("name", "共享数据源系统", "tenantMode", "MULTI"));
        long branchId = number(ok("/api/admin/system/tenants", HttpMethod.POST, owner.token(),
                Map.of("code", "branch", "name", "分支租户")).get("id"));
        Map<String, Object> ownerEntered = ok("/api/systems/" + owner.systemId() + "/tenants/" + branchId
                        + "/enter", HttpMethod.POST, owner.token(),
                Map.of("previousSystemId", owner.systemId(), "previousTenantId", owner.tenantId()));
        String branchOwnerToken = String.valueOf(map(ownerEntered.get("tokens")).get("accessToken"));
        // 分租户尚未发布自己的首页时应显示未配置，不能回退到主租户页面。
        assertThat(ok("/api/analytics/runtime", HttpMethod.GET, branchOwnerToken, null))
                .containsEntry("configured", false);
        Map<String, Object> branchSource = list(ok("/api/analytics/admin", HttpMethod.GET,
                branchOwnerToken, null).get("dataSources")).stream()
                .filter(item -> number(item.get("id")) == sourceId).findFirst().orElseThrow();
        assertThat(branchSource).containsEntry("inherited", true).containsEntry("status", "PUBLISHED");
        assertThat(exchange("/api/analytics/admin/data-sources/" + sourceId, HttpMethod.PUT,
                branchOwnerToken, source("main_customers", "越权改主租户源", "MODULE_RECORDS",
                        Map.of("moduleCode", "customer", "limit", 5), Map.of(),
                        ((Number) mainSource.get("version")).intValue())).status()).isEqualTo(HttpStatus.NOT_FOUND);

        register("dashboard_shared_viewer", "dashboard_viewer_home",
                "dashboard-shared-viewer@example.com");
        long viewerAccountId = jdbc.queryForObject(
                "select id from un_plat_account where username='dashboard_shared_viewer'", Long.class);
        jdbc.update("insert into un_sys_member(system_id,account_id,display_name,status) "
                + "values (?,?,'仪表盘查看人','ACTIVE')", owner.systemId(), viewerAccountId);
        long viewerMemberId = jdbc.queryForObject(
                "select id from un_sys_member where system_id=? and account_id=?", Long.class,
                owner.systemId(), viewerAccountId);
        jdbc.update("insert into un_sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status) "
                + "values (?,?,?,0,'ACTIVE')", owner.systemId(), branchId, viewerMemberId);
        long viewerTenantMemberId = jdbc.queryForObject(
                "select id from un_sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, branchId, viewerMemberId);

        // 分支租户两条记录分别归属查看人与系统所属人，来源定义不能带来主租户权限。
        ok("/api/runtime/modules/customer/records", HttpMethod.POST, branchOwnerToken, Map.of(
                "recordNumber", "DS-BR-001", "title", "查看人负责客户", "status", "ACTIVE",
                "ownerMemberId", viewerMemberId, "participantMemberIds", List.of(),
                "fields", Map.of("customer_name", "查看人负责客户")));
        ok("/api/runtime/modules/customer/records", HttpMethod.POST, branchOwnerToken, Map.of(
                "recordNumber", "DS-BR-002", "title", "所属人负责客户", "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of("customer_name", "所属人负责客户")));
        Map<String, Object> branchDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                branchOwnerToken, dashboard("system_home", "分租户首页", "仅分租户发布",
                        List.of(component("branch_total", "METRIC", "分租户客户数", sourceId,
                                0, Map.of(), Map.of("width", 1), Map.of())), null));
        ok("/api/analytics/admin/dashboards/" + branchDashboard.get("id") + "/publish", HttpMethod.POST,
                branchOwnerToken, Map.of("expectedDraftRevision", branchDashboard.get("draftRevision")));
        Map<String, Object> branchRuntime = ok("/api/analytics/runtime", HttpMethod.GET, branchOwnerToken, null);
        assertThat(branchRuntime).containsEntry("configured", true).containsEntry("name", "分租户首页");
        assertThat(list(branchRuntime.get("components"))).singleElement()
                .satisfies(item -> assertThat(item).containsEntry("componentKey", "branch_total")
                        .containsEntry("value", 2));
        assertThat(ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null))
                .containsEntry("name", "主租户首页");

        String viewerPlatformToken = login("dashboard_shared_viewer", "correct-password");
        Map<String, Object> viewerEntered = ok("/api/systems/" + owner.systemId() + "/tenants/" + branchId
                + "/enter", HttpMethod.POST, viewerPlatformToken, null);
        String viewerToken = String.valueOf(map(viewerEntered.get("tokens")).get("accessToken"));
        assertThat(list(ok("/api/analytics/personal", HttpMethod.GET, viewerToken, null).get("dataSources")))
                .noneMatch(item -> number(item.get("id")) == sourceId);
        Map<String, Object> personalInput = mapWithNullable("name", "我的客户", "description", "只看负责的数据",
                "components", List.of(component("mine", "METRIC", "我的客户数", sourceId,
                        0, Map.of(), Map.of("width", 1), Map.of())), "expectedVersion", null);
        assertThat(exchange("/api/analytics/personal", HttpMethod.PUT, viewerToken, personalInput).status())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // 通过角色发布和成员分配刷新权限版本，避免直写表留下旧权限缓存。
        Map<String, Object> roleDraft = ok("/api/admin/system/authorization/roles", HttpMethod.POST,
                branchOwnerToken, Map.of("code", "dashboard_viewer", "name", "客户查看人",
                        "permissions", List.of(Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST", "dataScopeType", "SELF")),
                        "fieldPolicies", List.of()));
        long roleId = number(roleDraft.get("id"));
        ok("/api/admin/system/authorization/roles/" + roleId + "/publish", HttpMethod.POST,
                branchOwnerToken, Map.of("reason", "验证分支租户仪表盘数据范围",
                        "expectedVersion", roleDraft.get("version")));
        int viewerMemberVersion = jdbc.queryForObject(
                "select version from un_sys_tenant_member where id=?", Integer.class, viewerTenantMemberId);
        ok("/api/admin/system/authorization/members/" + viewerTenantMemberId + "/assignment",
                HttpMethod.POST, branchOwnerToken,
                Map.of("roleIds", List.of(roleId), "expectedVersion", viewerMemberVersion));
        viewerPlatformToken = login("dashboard_shared_viewer", "correct-password");
        viewerEntered = ok("/api/systems/" + owner.systemId() + "/tenants/" + branchId
                + "/enter", HttpMethod.POST, viewerPlatformToken, null);
        viewerToken = String.valueOf(map(viewerEntered.get("tokens")).get("accessToken"));
        assertThat(list(ok("/api/analytics/personal", HttpMethod.GET, viewerToken, null).get("dataSources")))
                .anyMatch(item -> number(item.get("id")) == sourceId);
        ok("/api/analytics/personal", HttpMethod.PUT, viewerToken, personalInput);
        Map<String, Object> runtime = ok("/api/analytics/personal/runtime", HttpMethod.GET, viewerToken, null);
        assertThat(list(runtime.get("components"))).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("outcome", "READY");
            assertThat(number(item.get("value"))).isEqualTo(1L);
            assertThat(list(item.get("items"))).singleElement().satisfies(record ->
                    assertThat(record).containsEntry("title", "查看人负责客户"));
        });
    }

    @Test
    void publishesContextBoundDashboardExecutesRealDataAndKeepsPublishedSnapshotsImmutable() {
        String currentMonth = YearMonth.now().toString();
        Session owner = register("dashboard_owner", "dashboard_system", "dashboard-owner@example.com");
        long customerModuleId = publishCustomerModule(owner);
        Map<String, Object> customerOne = createCustomer(owner, "C48-001", "北辰客户");
        Map<String, Object> customerTwo = createCustomer(owner, "C48-002", "星海客户");
        publishOrderModule(owner, customerModuleId);
        createOrder(owner, "C49-ORDER-001", "北辰首单", number(customerOne.get("id")), 120);
        createOrder(owner, "C49-ORDER-002", "星海续费", number(customerTwo.get("id")), 280);

        Map<String, Object> reportSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("customer_order_report", "客户订单权限报表", "MODULE_REPORT",
                        reportDefinition(100), Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST"), null));
        long reportSourceId = number(reportSource.get("id"));
        Map<String, Object> queryExplanation = map(reportSource.get("definition")).containsKey("queryExplanation")
                ? map(map(reportSource.get("definition")).get("queryExplanation")) : Map.of();
        assertThat(queryExplanation).containsEntry("mode", "MULTI_MODULE")
                .containsEntry("arbitrarySqlAllowed", false);
        assertThat((List<?>) queryExplanation.get("permissionFilters")).hasSize(4);
        assertThat(ok("/api/analytics/admin/report-metadata", HttpMethod.GET, owner.token(), null))
                .containsEntry("arbitrarySqlAllowed", false).containsEntry("maximumModules", 3);

        Map<String, Object> reportPreview = ok("/api/analytics/admin/data-sources/" + reportSourceId
                + "/report-preview", HttpMethod.GET, owner.token(), null);
        assertThat(reportPreview).containsEntry("valid", true);
        Map<String, Object> sample = map(reportPreview.get("sampleResult"));
        assertThat(number(sample.get("value"))).isEqualTo(2);
        assertThat((List<?>) sample.get("items")).hasSize(2);
        assertThat(list(sample.get("items"))).allSatisfy(item -> {
            assertThat(map(item.get("fields")).keySet())
                    .containsExactly("customer.title", "orders.amount");
            assertThat(item.get("route").toString()).contains("module=customer", "recordId=");
        });
        assertThat(list(sample.get("groups"))).singleElement().satisfies(group -> {
            assertThat(group).containsEntry("key", "ACTIVE");
            assertThat(number(group.get("value"))).isEqualTo(2);
            assertThat(number(group.get("rowCount"))).isEqualTo(2);
        });
        assertThat(map(sample.get("queryPlan"))).containsEntry("estimatedRows", 2)
                .containsEntry("arbitrarySqlAllowed", false);
        assertThat(sample.get("metricDefinition").toString()).contains("权限", "2 行授权结果");

        reportSource = ok("/api/analytics/admin/data-sources/" + reportSourceId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", reportSource.get("draftRevision")));
        Map<String, Object> publishedReport = ok("/api/analytics/report/data-sources/" + reportSourceId,
                HttpMethod.GET, owner.token(), null);
        assertThat(number(publishedReport.get("value"))).isEqualTo(2);
        assertThat(publishedReport).containsEntry("dataSourceVersionNumber", 1).containsKey("definitionHash");
        long reportVersionId = jdbc.queryForObject(
                "select id from un_ana_data_source_version where data_source_id=?", Long.class, reportSourceId);
        long ownerAccountId = jdbc.queryForObject(
                "select id from un_plat_account where username='dashboard_owner'", Long.class);
        long ownerTenantMemberId = jdbc.queryForObject(
                "select stm.id from un_sys_tenant_member stm join un_sys_member sm on sm.id=stm.system_member_id "
                        + "where stm.system_id=? and stm.tenant_id=? and sm.account_id=?",
                Long.class, owner.systemId(), owner.tenantId(), ownerAccountId);

        Map<String, Object> kpi = ok("/api/analytics/admin/kpis", HttpMethod.POST, owner.token(),
                kpi("customer_monthly", "月度有效客户", reportVersionId, 3,
                        Map.of(), Map.of("resourceType", "MODULE", "resourceCode", "customer",
                                "actionCode", "LIST"), ownerTenantMemberId, null));
        long kpiId = number(kpi.get("id"));
        assertThat(number(kpi.get("dataSourceVersionId"))).isEqualTo(reportVersionId);
        assertThat(kpi).containsEntry("periodType", "MONTH").containsEntry("version", 0);
        Map<String, Object> kpiPreview = ok("/api/analytics/admin/kpis/" + kpiId
                + "/preview?periodKey=" + currentMonth, HttpMethod.GET, owner.token(), null);
        assertThat(kpiPreview).containsEntry("status", "UNDER_TARGET").containsEntry("kpiVersion", 0);
        assertThat(number(kpiPreview.get("actualValue"))).isEqualTo(2);
        assertThat(number(kpiPreview.get("targetValue"))).isEqualTo(3);
        assertThat(kpiPreview.get("sourceExplanation").toString()).contains("发布版本 #" + reportVersionId);
        Response invalidPeriod = exchange("/api/analytics/admin/kpis/" + kpiId
                + "/preview?periodKey=2026-Q3", HttpMethod.GET, owner.token(), null);
        assertThat(invalidPeriod.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidPeriod.body()).containsEntry("code", "KPI_PERIOD_INVALID");

        Map<String, Object> kpiResult = ok("/api/analytics/admin/kpis/" + kpiId + "/calculate",
                HttpMethod.POST, owner.token(), Map.of("periodKey", currentMonth));
        assertThat(kpiResult).containsEntry("status", "UNDER_TARGET").containsEntry("stale", false);
        // 运行结果按当前访问者重新聚合，不向页面返回管理员计算快照的定义哈希。
        assertThat(map(kpiResult.get("explanation"))).containsKeys("metricDefinition", "sourceRecordIds")
                .doesNotContainKey("definitionHash");
        assertThat(number(map(kpiResult.get("explanation")).get("dataSourceVersionId")))
                .isEqualTo(reportVersionId);
        assertThat(list(kpiResult.get("reminders"))).singleElement().satisfies(reminder -> {
            assertThat(number(reminder.get("recipientTenantMemberId"))).isEqualTo(ownerTenantMemberId);
            assertThat(reminder).containsEntry("status", "SENT").containsKey("messageId");
        });
        long reminderCount = count("un_ana_kpi_reminder", "kpi_result_id=" + kpiResult.get("id"));
        long messageCount = count("un_msg_message", "source_type='KPI_ALERT'");
        assertThat(jdbc.queryForObject("select target_route from un_msg_message where source_type='KPI_ALERT'",
                String.class)).isEqualTo("/systems/" + owner.systemId() + "?workspace=kpi&kpiId=" + kpiId);

        jdbc.update("update un_ana_data_source set source_type='MODULE_RECORDS' where id=?", reportSourceId);
        // 修改数据源草稿不能改变 KPI 绑定的已发布报表版本。
        Map<String, Object> stillPublishedResult = ok("/api/analytics/admin/kpis/" + kpiId + "/calculate",
                HttpMethod.POST, owner.token(), Map.of("periodKey", currentMonth));
        assertThat(stillPublishedResult).containsEntry("status", "UNDER_TARGET").containsEntry("stale", false);
        assertThat(number(stillPublishedResult.get("actualValue"))).isEqualTo(2);
        assertThat(number(stillPublishedResult.get("targetValue"))).isEqualTo(3);
        assertThat(count("un_ana_kpi_reminder", "kpi_result_id=" + kpiResult.get("id"))).isEqualTo(reminderCount);
        assertThat(count("un_msg_message", "source_type='KPI_ALERT'")).isEqualTo(messageCount);
        jdbc.update("update un_ana_data_source set source_type='MODULE_REPORT' where id=?", reportSourceId);
        kpiResult = ok("/api/analytics/admin/kpis/" + kpiId + "/calculate",
                HttpMethod.POST, owner.token(), Map.of("periodKey", currentMonth));
        assertThat(kpiResult).containsEntry("status", "UNDER_TARGET").containsEntry("stale", false);
        assertThat(jdbc.queryForObject("select count(*) from un_ana_kpi_reminder r "
                + "join un_ana_kpi_result x on x.id=r.kpi_result_id where x.kpi_id=?",
                Long.class, kpiId)).isEqualTo(reminderCount);

        Map<String, Object> source = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("customer_records", "客户记录", "MODULE_RECORDS",
                        Map.of("moduleCode", "customer", "limit", 5),
                        Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"), null));
        long sourceId = number(source.get("id"));
        source = ok("/api/analytics/admin/data-sources/" + sourceId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", source.get("draftRevision")));
        assertThat(source).containsEntry("status", "PUBLISHED");
        assertThat((List<?>) source.get("versions")).singleElement();

        List<Map<String, Object>> components = List.of(
                component("customer_count", "METRIC", "客户总数", sourceId, 0, Map.of(),
                        Map.of("width", 1), Map.of("moduleCode", "customer", "permission",
                                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"))),
                component("recent_customers", "LIST", "最近客户", sourceId, 10, Map.of("limit", 1),
                        Map.of("width", 2, "refreshSeconds", 60), Map.of()),
                component("customer_entry", "QUICK_ENTRY", "进入客户模块", null, 20, Map.of(),
                        Map.of("width", 1), Map.of("moduleCode", "customer", "permission",
                                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"))),
                component("customer_order_count", "CHART", "客户订单授权聚合", reportSourceId, 30, Map.of(),
                        Map.of("width", 2), Map.of("moduleCode", "customer", "permission",
                                Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"))),
                component("customer_kpi", "KPI", "月度有效客户 KPI", reportSourceId, 40,
                        Map.of("kpiId", kpiId, "kpiVersion", 0), Map.of("width", 2),
                        Map.of("route", "/systems/" + owner.systemId() + "?workspace=kpi&kpiId=" + kpiId)));
        Map<String, Object> dashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), dashboard("system_home", "客户经营工作台", "当前租户真实客户统计", components, null));
        long dashboardId = number(dashboard.get("id"));
        Map<String, Object> preview = ok("/api/analytics/admin/dashboards/" + dashboardId + "/preview",
                HttpMethod.GET, owner.token(), null);
        assertThat(preview).containsEntry("valid", true);
        List<Map<String, Object>> previewComponents = list(preview.get("components"));
        assertThat(previewComponents).extracting(item -> item.get("outcome"))
                .containsExactly("READY", "READY", "READY", "READY", "READY");
        assertThat(number(previewComponents.get(0).get("value"))).isEqualTo(2);
        assertThat((List<?>) previewComponents.get(1).get("items")).hasSize(1);
        assertThat(number(previewComponents.get(3).get("value"))).isEqualTo(2);
        assertThat(number(previewComponents.get(4).get("value"))).isEqualTo(2);
        assertThat(previewComponents.get(4).get("metricDefinition").toString())
                .contains("目标 GTE 3", "达成率", "数据源版本 #" + reportVersionId);
        assertThat(previewComponents.get(0).get("metricDefinition").toString())
                .contains("当前系统", "当前租户", "LIST 权限", "有效记录");

        Map<String, Object> publication = ok("/api/analytics/admin/dashboards/" + dashboardId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", dashboard.get("draftRevision")));
        assertThat(publication).containsEntry("versionNumber", 1).containsKey("snapshotHash");
        long dashboardVersionId = number(publication.get("versionId"));
        Map<String, Object> currentDashboard = list(ok("/api/analytics/admin", HttpMethod.GET,
                owner.token(), null).get("dashboards")).stream()
                .filter(item -> number(item.get("id")) == dashboardId).findFirst().orElseThrow();
        long sourceVersionId = jdbc.queryForObject(
                "select id from un_ana_data_source_version where data_source_id=?", Long.class, sourceId);

        Map<String, Object> runtime = ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null);
        assertThat(runtime).containsEntry("configured", true).containsEntry("contextType", "SYSTEM")
                .containsEntry("versionNumber", 1).containsEntry("name", "客户经营工作台");
        List<Map<String, Object>> runtimeComponents = list(runtime.get("components"));
        assertThat(number(runtimeComponents.get(0).get("value"))).isEqualTo(2);
        assertThat(((Map<?, ?>) runtimeComponents.get(0).get("drillTarget")).get("route").toString())
                .isEqualTo("/systems/" + owner.systemId() + "?workspace=runtime&module=customer");
        assertThat(((List<Map<String, Object>>) runtimeComponents.get(1).get("items")).get(0).get("route").toString())
                .contains("/systems/" + owner.systemId(), "module=customer", "recordId=");
        assertThat(number(runtimeComponents.get(3).get("value"))).isEqualTo(2);
        assertThat(list(runtimeComponents.get(3).get("items"))).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("title", "ACTIVE");
            assertThat(number(item.get("value"))).isEqualTo(2);
            assertThat(item.get("status").toString()).contains("2 行授权记录");
            assertThat(map(item.get("filter"))).containsEntry("fieldCode", "status")
                    .containsEntry("operator", "EQ").containsEntry("value", "ACTIVE");
        });
        assertThat(runtimeComponents.get(4)).containsEntry("componentType", "KPI")
                .containsEntry("outcome", "READY").containsEntry("drillAvailable", true);
        assertThat(list(runtimeComponents.get(4).get("items"))).hasSize(2);

        Map<String, Object> reportDraft = ok("/api/analytics/admin/data-sources/" + reportSourceId,
                HttpMethod.PUT, owner.token(), source("customer_order_report", "未发布的新报表草稿",
                        "MODULE_REPORT", reportDefinition(50), Map.of("resourceType", "MODULE",
                                "resourceCode", "customer", "actionCode", "LIST"),
                        ((Number) reportSource.get("version")).intValue()));
        assertThat(reportDraft).containsEntry("status", "DRAFT");
        assertThat(number(ok("/api/analytics/report/data-sources/" + reportSourceId,
                HttpMethod.GET, owner.token(), null).get("value"))).isEqualTo(2);
        assertThat(number(list(ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null)
                .get("components")).get(3).get("value"))).isEqualTo(2);

        Response arbitrarySql = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("forbidden_sql", "禁止任意 SQL", "MODULE_REPORT",
                        new java.util.LinkedHashMap<>(Map.of("sql", "select * from un_biz_record",
                                "modules", List.of(Map.of("alias", "customer", "moduleCode", "customer")),
                                "outputFields", List.of(Map.of("alias", "customer", "fieldCode", "title")),
                                "metric", Map.of("operation", "COUNT"))), Map.of(), null));
        assertThat(arbitrarySql.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(arbitrarySql.body()).containsEntry("code", "REPORT_ARBITRARY_SQL_FORBIDDEN");

        Map<String, Object> ambiguousDefinition = new java.util.LinkedHashMap<>(reportDefinition(100));
        ambiguousDefinition.put("relations", List.of());
        Response ambiguous = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("ambiguous_relation", "歧义关系", "MODULE_REPORT", ambiguousDefinition, Map.of(), null));
        assertThat(ambiguous.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ambiguous.body()).containsEntry("code", "REPORT_RELATION_AMBIGUOUS");

        Map<String, Object> nonIndexedDefinition = new java.util.LinkedHashMap<>(reportDefinition(100));
        nonIndexedDefinition.put("filters", List.of(Map.of("alias", "orders", "fieldCode", "notes",
                "operator", "CONTAINS", "value", "内部")));
        Response nonIndexed = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("non_indexed_report", "不可索引报表", "MODULE_REPORT", nonIndexedDefinition, Map.of(), null));
        assertThat(nonIndexed.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(nonIndexed.body()).containsEntry("code", "REPORT_QUERY_NOT_INDEXABLE");

        Map<String, Object> unauthorizedDefinition = new java.util.LinkedHashMap<>(reportDefinition(100));
        unauthorizedDefinition.put("outputFields", List.of(
                Map.of("alias", "customer", "fieldCode", "secret_field")));
        Response unauthorized = exchange("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("unauthorized_field", "越权字段报表", "MODULE_REPORT", unauthorizedDefinition, Map.of(), null));
        assertThat(unauthorized.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorized.body()).containsEntry("code", "REPORT_OUTPUT_FIELD_FORBIDDEN");

        Map<String, Object> costly = ok("/api/analytics/admin/data-sources", HttpMethod.POST, owner.token(),
                source("costly_report", "成本超限报表", "MODULE_REPORT", singleModuleDefinition(1), Map.of(), null));
        Map<String, Object> costlyPreview = ok("/api/analytics/admin/data-sources/" + costly.get("id")
                + "/report-preview", HttpMethod.GET, owner.token(), null);
        assertThat(costlyPreview).containsEntry("valid", false);
        assertThat(list(costlyPreview.get("issues"))).singleElement()
                .satisfies(issue -> assertThat(issue).containsEntry("code", "REPORT_QUERY_COST_EXCEEDED"));
        Response costlyPublish = exchange("/api/analytics/admin/data-sources/" + costly.get("id") + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", costly.get("draftRevision")));
        assertThat(costlyPublish.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(costlyPublish.body()).containsEntry("code", "REPORT_QUERY_COST_EXCEEDED");

        Map<String, Object> draftSource = ok("/api/analytics/admin/data-sources/" + sourceId, HttpMethod.PUT,
                owner.token(), source("customer_records", "客户记录新草稿", "MODULE_RECORDS",
                        Map.of("moduleCode", "customer", "limit", 20),
                        Map.of("resourceType", "MODULE", "resourceCode", "customer", "actionCode", "LIST"),
                        ((Number) source.get("version")).intValue()));
        assertThat(draftSource).containsEntry("status", "DRAFT");
        Map<String, Object> runtimeDuringSourceDraft = ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null);
        assertThat(runtimeDuringSourceDraft).containsEntry("versionNumber", 1);
        assertThat(list(runtimeDuringSourceDraft.get("components")))
                .allSatisfy(item -> assertThat(item.get("outcome")).isEqualTo("READY"));

        Map<String, Object> draftDashboard = ok("/api/analytics/admin/dashboards/" + dashboardId, HttpMethod.PUT,
                owner.token(), dashboard("system_home", "尚未发布的新标题", "草稿不能覆盖运行版",
                        components, ((Number) currentDashboard.get("version")).intValue()));
        Map<String, Object> runtimeDuringDashboardDraft = ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null);
        assertThat(runtimeDuringDashboardDraft).containsEntry("name", "客户经营工作台")
                .containsEntry("versionNumber", 1);
        assertThat(draftDashboard).containsEntry("status", "DRAFT");

        Map<String, Object> invalidSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                owner.token(), source("ghost_records", "失效模块数据源", "MODULE_RECORDS",
                        Map.of("moduleCode", "ghost_module", "limit", 5), Map.of(), null));
        invalidSource = ok("/api/analytics/admin/data-sources/" + invalidSource.get("id") + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", invalidSource.get("draftRevision")));
        Map<String, Object> invalidDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                owner.token(), dashboard("invalid_home", "无效源工作台", "用于验证明确失败反馈",
                        List.of(component("ghost_count", "METRIC", "不存在模块", number(invalidSource.get("id")),
                                0, Map.of(), Map.of("width", 1), Map.of())), null));
        Map<String, Object> invalidPreview = ok("/api/analytics/admin/dashboards/" + invalidDashboard.get("id")
                + "/preview", HttpMethod.GET, owner.token(), null);
        assertThat(invalidPreview).containsEntry("valid", false);
        Map<String, Object> invalidComponent = list(invalidPreview.get("components")).get(0);
        assertThat(invalidComponent).containsEntry("outcome", "ERROR").containsEntry("value", null)
                .containsKey("errorCode").containsKey("updatedAt");
        Response invalidPublish = exchange("/api/analytics/admin/dashboards/" + invalidDashboard.get("id")
                + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", invalidDashboard.get("draftRevision")));
        assertThat(invalidPublish.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidPublish.body()).containsEntry("code", "DASHBOARD_PUBLICATION_INVALID");

        String platformAdmin = login("admin", "123123aa");
        Map<String, Object> platformSource = ok("/api/analytics/admin/data-sources", HttpMethod.POST,
                platformAdmin, source("authorized_systems", "已授权系统", "PLATFORM_SYSTEMS",
                        Map.of("limit", 10), Map.of(), null));
        platformSource = ok("/api/analytics/admin/data-sources/" + platformSource.get("id") + "/publish",
                HttpMethod.POST, platformAdmin,
                Map.of("expectedDraftRevision", platformSource.get("draftRevision")));
        Map<String, Object> platformDashboard = ok("/api/analytics/admin/dashboards", HttpMethod.POST,
                platformAdmin, dashboard("platform_home", "平台授权概览", "仅平台聚合与系统入口",
                        List.of(component("system_count", "METRIC", "可进入系统", number(platformSource.get("id")),
                                0, Map.of(), Map.of("width", 1), Map.of())), null));
        ok("/api/analytics/admin/dashboards/" + platformDashboard.get("id") + "/publish", HttpMethod.POST,
                platformAdmin, Map.of("expectedDraftRevision", platformDashboard.get("draftRevision")));
        Map<String, Object> platformRuntime = ok("/api/analytics/runtime", HttpMethod.GET, platformAdmin, null);
        assertThat(platformRuntime).containsEntry("contextType", "PLATFORM").containsEntry("name", "平台授权概览");
        assertThat(ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null))
                .containsEntry("contextType", "SYSTEM").containsEntry("name", "客户经营工作台");

        // 发布版本由业务接口保持只读；草稿修改后的运行态仍读取固定版本，不依赖数据库触发器。
        assertThat(number(ok("/api/analytics/runtime", HttpMethod.GET, owner.token(), null)
                .get("versionId"))).isEqualTo(dashboardVersionId);
        assertThat(jdbc.queryForObject("SELECT definition_hash FROM un_ana_data_source_version WHERE id=?",
                String.class, sourceVersionId)).hasSize(64);
        assertThat(count("un_audit_event", "event_code='DASHBOARD_DATA_SOURCE_PUBLISHED'")).isGreaterThanOrEqualTo(3);
        assertThat(count("un_audit_event", "event_code='DASHBOARD_PUBLISHED'")).isGreaterThanOrEqualTo(2);
        assertThat(count("un_audit_event", "event_code='ANALYTICS_REPORT_PREVIEWED'")).isGreaterThanOrEqualTo(1);
        assertThat(count("un_audit_event", "event_code='ANALYTICS_REPORT_EXECUTED'")).isGreaterThanOrEqualTo(2);
        assertThat(count("un_audit_event", "event_code='KPI_CALCULATED'")).isGreaterThanOrEqualTo(2);

        Map<String, Object> updatedKpi = ok("/api/analytics/admin/kpis/" + kpiId, HttpMethod.PUT,
                owner.token(), kpi("customer_monthly", "月度有效客户", reportVersionId, 3,
                        Map.of(), Map.of("tenantMemberIds", List.of(999999L)), ownerTenantMemberId, 0));
        assertThat(updatedKpi).containsEntry("version", 1);
        Map<String, Object> visibleWithoutDrill = ok("/api/analytics/kpis/" + kpiId,
                HttpMethod.GET, owner.token(), null);
        assertThat(map(visibleWithoutDrill.get("latestResult")))
                .containsEntry("drillAvailable", false).containsEntry("status", "UNDER_TARGET");
        Map<String, Object> dashboardAfterKpiChange = ok("/api/analytics/runtime", HttpMethod.GET,
                owner.token(), null);
        assertThat(list(dashboardAfterKpiChange.get("components")).get(4))
                .containsEntry("outcome", "ERROR").containsEntry("errorCode", "DASHBOARD_KPI_VERSION_CHANGED")
                .containsEntry("value", null);
    }

    private Map<String, Object> source(String code, String name, String type, Map<String, Object> definition,
                                       Map<String, Object> permission, Integer expectedVersion) {
        return mapWithNullable("code", code, "name", name, "sourceType", type, "definition", definition,
                "permissionPolicy", permission, "expectedVersion", expectedVersion);
    }

    private Map<String, Object> dashboard(String code, String name, String description,
                                          List<Map<String, Object>> components, Integer expectedVersion) {
        return mapWithNullable("code", code, "name", name, "description", description,
                "components", components, "expectedVersion", expectedVersion);
    }

    private Map<String, Object> kpi(String code, String name, long sourceVersionId, int target,
                                    Map<String, Object> visibility, Map<String, Object> drill,
                                    long recipientTenantMemberId, Integer expectedVersion) {
        return mapWithNullable("code", code, "name", name, "dataSourceVersionId", sourceVersionId,
                "targetValue", target, "targetOperator", "GTE", "periodType", "MONTH",
                "responsibleType", "PERSON", "responsibleIds", List.of(recipientTenantMemberId),
                "visibilityPermission", visibility, "drillPermission", drill,
                "reminderEnabled", true, "reminderRecipientTenantMemberIds", List.of(recipientTenantMemberId),
                "reminderBelowPercent", 100, "status", "ACTIVE", "expectedVersion", expectedVersion);
    }

    private Map<String, Object> reportDefinition(int maxScanRows) {
        return Map.of(
                "modules", List.of(
                        Map.of("alias", "customer", "moduleCode", "customer"),
                        Map.of("alias", "orders", "moduleCode", "orders")),
                "relations", List.of(Map.of("relationType", "REFERENCE", "joinType", "INNER",
                        "leftAlias", "customer", "leftField", "id",
                        "rightAlias", "orders", "rightField", "customer_id")),
                "outputFields", List.of(
                        Map.of("alias", "customer", "fieldCode", "title", "label", "客户"),
                        Map.of("alias", "orders", "fieldCode", "amount", "label", "订单金额")),
                "metric", Map.of("operation", "COUNT"),
                "dimension", Map.of("type", "STATUS", "alias", "customer", "fieldCode", "status"),
                "filters", List.of(Map.of("alias", "customer", "fieldCode", "status",
                        "operator", "EQ", "value", "ACTIVE")),
                "sort", Map.of("alias", "orders", "fieldCode", "amount", "direction", "DESC"),
                "timeField", Map.of("alias", "customer", "fieldCode", "updatedAt"),
                "maxScanRows", maxScanRows, "limit", 20);
    }

    private Map<String, Object> singleModuleDefinition(int maxScanRows) {
        return Map.of(
                "modules", List.of(Map.of("alias", "customer", "moduleCode", "customer")),
                "relations", List.of(),
                "outputFields", List.of(Map.of("alias", "customer", "fieldCode", "title")),
                "metric", Map.of("operation", "COUNT"), "dimension", Map.of(), "filters", List.of(),
                "sort", Map.of("alias", "customer", "fieldCode", "updatedAt", "direction", "DESC"),
                "timeField", Map.of(), "maxScanRows", maxScanRows, "limit", 20);
    }

    private Map<String, Object> component(String key, String type, String title, Long sourceId, int order,
                                          Map<String, Object> query, Map<String, Object> layout,
                                          Map<String, Object> drill) {
        return mapWithNullable("componentKey", key, "componentType", type, "title", title,
                "dataSourceId", sourceId, "layout", layout, "queryParameters", query,
                "displayConfig", Map.of("refreshSeconds", 60, "accentColor", "#315efb"),
                "drillTarget", drill, "sortOrder", order);
    }

    private Map<String, Object> mapWithNullable(Object... values) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) result.put((String) values[index], values[index + 1]);
        return result;
    }

    private long publishCustomerModule(Session owner) {
        long groupId = number(ok("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10)).get("id"));
        long moduleId = number(((Map<?, ?>) ok("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "customer", "name", "客户")).get("module")).get("id"));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", true,
                        "searchable", true, "sortOrder", 10, "config", Map.of("placeholder", "客户名称")));
        Map<String, Object> check = ok("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        ok("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
        return moduleId;
    }

    private void publishOrderModule(Session owner, long customerModuleId) {
        long groupId = number(ok("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "commerce", "name", "交易", "sortOrder", 20)).get("id"));
        long moduleId = number(((Map<?, ?>) ok("/api/admin/module-config/modules", HttpMethod.POST,
                owner.token(), Map.of("groupId", groupId, "code", "orders", "name", "订单")).get("module")).get("id"));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_id", "name", "客户", "fieldType", "REFERENCE", "required", true,
                        "referenceModuleId", customerModuleId, "searchable", true, "sortOrder", 10, "config", Map.of()));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "amount", "name", "订单金额", "fieldType", "NUMBER", "required", true,
                        "searchable", true, "sortOrder", 20, "config", Map.of()));
        ok("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "notes", "name", "内部备注", "fieldType", "TEXT", "required", false,
                        "searchable", false, "sortOrder", 30, "config", Map.of()));
        Map<String, Object> check = ok("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        ok("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
    }

    private Map<String, Object> createCustomer(Session owner, String number, String name) {
        return ok("/api/runtime/modules/customer/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", number, "title", name, "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of("customer_name", name)));
    }

    private void createOrder(Session owner, String number, String title, long customerId, int amount) {
        ok("/api/runtime/modules/orders/records", HttpMethod.POST, owner.token(), Map.of(
                "recordNumber", number, "title", title, "status", "ACTIVE",
                "participantMemberIds", List.of(), "fields", Map.of(
                        "customer_id", customerId, "amount", amount, "notes", "内部")));
    }

    private Session register(String username, String systemCode, String email) {
        Map<String, Object> result = ok("/api/auth/register", HttpMethod.POST, null, Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode));
        return new Session((String) ((Map<?, ?>) result.get("tokens")).get("accessToken"),
                number(result.get("systemId")), number(result.get("tenantId")));
    }

    private String login(String username, String password) {
        return (String) ok("/api/auth/login", HttpMethod.POST, null,
                Map.of("username", username, "password", password)).get("accessToken");
    }

    private Map<String, Object> ok(String path, HttpMethod method, String token, Object body) {
        Response response = exchange(path, method, token, body);
        assertThat(response.status()).withFailMessage("%s %s failed: %s", method, path, response.body())
                .isEqualTo(HttpStatus.OK);
        return map(response.body().get("data"));
    }

    private Response exchange(String path, HttpMethod method, String token, Object body) {
        try {
            var request = MockMvcRequestBuilders.request(method, path).contentType(MediaType.APPLICATION_JSON);
            if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            if (body != null) request.content(objectMapper.writeValueAsBytes(body));
            var response = http.perform(request).andReturn().getResponse();
            return new Response(HttpStatus.valueOf(response.getStatus()),
                    objectMapper.readValue(response.getContentAsByteArray(), Map.class));
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP test request failed: " + method + " " + path, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : new java.math.BigDecimal(String.valueOf(value)).longValue();
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private record Session(String token, long systemId, long tenantId) {
    }

    private record Response(HttpStatus status, Map<String, Object> body) {
    }
}
