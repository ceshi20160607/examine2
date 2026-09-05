package com.unique.unexamine.moduleconfig.manage;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModuleConfigurationHttpTest {
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
    void administratorBuildsAnIsolatedDraftWithDefaultPagesActionsAndOptimisticLocking() {
        Session first = register("config_owner_a", "config-system-a", "config-a@example.com");
        Session second = register("config_owner_b", "config-system-b", "config-b@example.com");

        ResponseEntity<Map> groupResponse = exchange("/api/admin/module-config/groups", HttpMethod.POST, first.token(),
                Map.of("code", "sales", "name", "销售管理", "sortOrder", 10));
        assertThat(groupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        long groupId = number(data(groupResponse).get("id"));

        ResponseEntity<Map> duplicateGroup = exchange("/api/admin/module-config/groups", HttpMethod.POST, first.token(),
                Map.of("code", "sales", "name", "重复分组", "sortOrder", 20));
        assertThat(duplicateGroup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map> moduleResponse = exchange("/api/admin/module-config/modules", HttpMethod.POST, first.token(),
                Map.of("groupId", groupId, "code", "customer", "name", "客户"));
        assertThat(moduleResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> initialDraft = data(moduleResponse);
        Map<?, ?> module = (Map<?, ?>) initialDraft.get("module");
        long moduleId = number(module.get("id"));
        assertThat(module.get("status")).isEqualTo("DRAFT");
        List<String> pageTypes = ((List<?>) initialDraft.get("pages")).stream()
                .map(value -> ((Map<?, ?>) value).get("pageType").toString()).toList();
        assertThat(pageTypes).containsExactly("LIST", "FORM", "DETAIL");
        List<String> actionCodes = ((List<?>) initialDraft.get("actions")).stream()
                .map(value -> ((Map<?, ?>) value).get("code").toString()).toList();
        assertThat(actionCodes).containsExactly("LIST", "DETAIL", "CREATE", "UPDATE", "ARCHIVE", "DELETE", "RESTORE", "TRANSFER", "CONVERT", "SHARE", "IMPORT", "EXPORT");
        assertThat((List<?>) initialDraft.get("menus")).hasSize(1);
        assertThat(initialDraft.get("published")).isEqualTo(false);
        assertThat(count("cfg_module_publication", "module_id = " + moduleId)).isZero();

        ResponseEntity<Map> overview = exchange("/api/admin/module-config", HttpMethod.GET, first.token(), null);
        assertThat((List<?>) data(overview).get("groups")).hasSize(1);
        assertThat((List<?>) data(overview).get("modules")).hasSize(1);
        ResponseEntity<Map> emptyCatalog = exchange("/api/runtime/modules", HttpMethod.GET, first.token(), null);
        assertThat((List<?>) emptyCatalog.getBody().get("data")).isEmpty();

        ResponseEntity<Map> invalidCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(invalidCheck).get("valid")).isEqualTo(false);
        assertThat((List<?>) data(invalidCheck).get("issues")).hasSize(1);
        ResponseEntity<Map> invalidPublish = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, first.token(), Map.of("expectedDraftRevision", 1));
        assertThat(invalidPublish.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isZero();

        ResponseEntity<Map> temporaryPage = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages", HttpMethod.POST, first.token(),
                Map.of("pageType", "CUSTOM", "name", "待设计专题",
                        "layout", Map.of("schemaVersion", 1, "layoutType", "CUSTOM",
                                "fieldCodes", List.of(), "components", List.of())));
        assertThat(temporaryPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        long temporaryPageId = number(data(temporaryPage).get("id"));
        ResponseEntity<Map> deletedTemporaryPage = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/CUSTOM?version=0",
                HttpMethod.DELETE, first.token(), null);
        assertThat(deletedTemporaryPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(data(deletedTemporaryPage).get("id"))).isEqualTo(temporaryPageId);
        assertThat(count("cfg_module_page", "id = " + temporaryPageId)).isZero();
        assertThat(count("audit_event", "event_code = 'MODULE_PAGE_DRAFT_DELETED' and object_id = '"
                + temporaryPageId + "'")).isOne();

        ResponseEntity<Map> fieldResponse = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, first.token(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", true,
                        "sortOrder", 10, "config", Map.of("placeholder", "请输入客户名称")));
        assertThat(fieldResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> field = data(fieldResponse);
        long fieldId = number(field.get("id"));
        int fieldVersion = ((Number) field.get("version")).intValue();

        ResponseEntity<Map> hiddenRequiredField = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, first.token(),
                Map.of("layout", Map.of("fieldCodes", List.of()), "version", 0));
        assertThat(hiddenRequiredField.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> invalidFormCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(invalidFormCheck).get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) data(invalidFormCheck).get("issues")).stream()
                .map(issue -> issue.get("code"))).contains("REQUIRED_FIELD_NOT_IN_FORM");
        ResponseEntity<Map> restoredRequiredField = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, first.token(),
                Map.of("layout", Map.of("fieldCodes", List.of("customer_name")), "version", 1));
        assertThat(restoredRequiredField.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> validCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(validCheck).get("valid")).isEqualTo(true);
        int firstDraftRevision = ((Number) data(validCheck).get("draftRevision")).intValue();
        ResponseEntity<Map> firstPublish = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, first.token(), Map.of("expectedDraftRevision", firstDraftRevision));
        assertThat(firstPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstVersionId = number(data(firstPublish).get("versionId"));
        assertThat(data(firstPublish).get("versionNumber")).isEqualTo(1);

        ResponseEntity<Map> firstRuntime = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, first.token(), null);
        assertThat(firstRuntime.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> firstConfiguration = (Map<?, ?>) data(firstRuntime).get("configuration");
        assertThat(publishedFieldName(firstConfiguration)).isEqualTo("客户名称");
        Long systemId = jdbc.queryForObject("select system_id from cfg_module where id=?", Long.class, moduleId);
        Long tenantId = jdbc.queryForObject("select owner_tenant_id from cfg_module where id=?", Long.class, moduleId);
        Long memberId = jdbc.queryForObject("select min(id) from sys_member where system_id=?", Long.class, systemId);
        jdbc.update("insert into biz_record(system_id,tenant_id,module_id,created_config_version_id,updated_config_version_id,"
                        + "record_number,title,status,created_by_member_id,updated_by_member_id) values(?,?,?,?,?,?,?,?,?,?)",
                systemId, tenantId, moduleId, firstVersionId, firstVersionId, "C21-001", "周期二十一存量客户", "ACTIVE",
                memberId, memberId);
        Long existingRecordId = jdbc.queryForObject("select max(id) from biz_record where module_id=?", Long.class, moduleId);
        jdbc.update("insert into biz_record_value(record_id,field_id,field_code,value_type,value_text,normalized_text) "
                        + "values(?,?,?,?,?,?)", existingRecordId, fieldId, "customer_name", "TEXT",
                "周期二十一存量客户", "周期二十一存量客户");
        ResponseEntity<Map> catalog = exchange("/api/runtime/modules", HttpMethod.GET, first.token(), null);
        List<?> catalogItems = (List<?>) catalog.getBody().get("data");
        assertThat(catalogItems).hasSize(1);
        assertThat(((Map<?, ?>) catalogItems.getFirst()).get("moduleCode")).isEqualTo("customer");

        Map<String, Object> update = Map.of(
                "name", "客户全称", "required", true, "sortOrder", 20, "status", "ACTIVE",
                "config", Map.of("placeholder", "请输入客户全称"), "version", fieldVersion);
        ResponseEntity<Map> updated = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields/" + fieldId,
                HttpMethod.PUT, first.token(), update);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) data(updated).get("version")).intValue()).isEqualTo(fieldVersion + 1);

        ResponseEntity<Map> staleUpdate = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields/" + fieldId,
                HttpMethod.PUT, first.token(), update);
        assertThat(staleUpdate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleUpdate.getBody().get("code")).isEqualTo("DRAFT_VERSION_CONFLICT");

        ResponseEntity<Map> runtimeBeforeSecondPublish = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, first.token(), null);
        assertThat(publishedFieldName((Map<?, ?>) data(runtimeBeforeSecondPublish).get("configuration")))
                .isEqualTo("客户名称");

        ResponseEntity<Map> changedDraft = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, first.token(), null);
        int secondDraftRevision = ((Number) ((Map<?, ?>) data(changedDraft).get("module")).get("draftRevision")).intValue();
        ResponseEntity<Map> secondPublish = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, first.token(), Map.of("expectedDraftRevision", secondDraftRevision));
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(secondPublish).get("versionNumber")).isEqualTo(2);
        ResponseEntity<Map> secondRuntime = exchange(
                "/api/runtime/modules/customer/configuration", HttpMethod.GET, first.token(), null);
        assertThat(publishedFieldName((Map<?, ?>) data(secondRuntime).get("configuration"))).isEqualTo("客户全称");

        ResponseEntity<Map> disabledWithData = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields/" + fieldId,
                HttpMethod.PUT, first.token(), Map.of(
                        "name", "客户全称", "required", true, "sortOrder", 20, "status", "DISABLED",
                        "config", Map.of("placeholder", "请输入客户全称"), "version", fieldVersion + 1));
        assertThat(disabledWithData.getStatusCode()).isEqualTo(HttpStatus.OK);
        int disabledVersion = ((Number) data(disabledWithData).get("version")).intValue();
        ResponseEntity<Map> destructiveCheck = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, first.token(), null);
        assertThat(data(destructiveCheck).get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) data(destructiveCheck).get("issues")).stream()
                .map(issue -> issue.get("code"))).contains("FIELD_REMOVAL_REQUIRES_MIGRATION");
        ResponseEntity<Map> restoredField = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields/" + fieldId,
                HttpMethod.PUT, first.token(), Map.of(
                        "name", "客户全称", "required", true, "sortOrder", 20, "status", "ACTIVE",
                        "config", Map.of("placeholder", "请输入客户全称"), "version", disabledVersion));
        assertThat(restoredField.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> versions = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/versions", HttpMethod.GET, first.token(), null);
        List<?> versionList = (List<?>) versions.getBody().get("data");
        assertThat(versionList).hasSize(2);
        int publicationVersion = ((Number) ((Map<?, ?>) versionList.getFirst()).get("publicationVersion")).intValue();
        ResponseEntity<Map> rollback = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rollback", HttpMethod.POST, first.token(),
                Map.of("targetVersionId", firstVersionId, "expectedPublicationVersion", publicationVersion));
        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(rollback).get("versionNumber")).isEqualTo(3);
        assertThat(publishedFieldName((Map<?, ?>) data(rollback).get("configuration"))).isEqualTo("客户名称");
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isEqualTo(3);

        ResponseEntity<Map> draft = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, first.token(), null);
        assertThat(draft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(draft).get("fields")).hasSize(1);
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select v.version_number from cfg_module_publication p join cfg_module_version v "
                        + "on v.id=p.current_version_id where p.module_id = ?", Integer.class, moduleId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "select json_extract(v.snapshot_json, '$.rollbackSourceVersionId') from cfg_module_publication p "
                        + "join cfg_module_version v on v.id=p.current_version_id where p.module_id = ?",
                Long.class, moduleId)).isEqualTo(firstVersionId);

        ResponseEntity<Map> crossSystemRead = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/draft", HttpMethod.GET, second.token(), null);
        assertThat(crossSystemRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Map> crossSystemCreate = exchange("/api/admin/module-config/modules", HttpMethod.POST, second.token(),
                Map.of("groupId", groupId, "code", "illegal", "name", "非法跨系统模块"));
        assertThat(crossSystemCreate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(count("audit_event", "event_code = 'MODULE_DRAFT_CREATED' and result_code = 'SUCCESS' and object_id = '" + moduleId + "'"))
                .isOne();
        assertThat(count("audit_event", "event_code = 'MODULE_FIELD_DRAFT_UPDATED' and result_code = 'SUCCESS' and object_id = '" + fieldId + "'"))
                .isEqualTo(3);

        ResponseEntity<Map> auditEvents = exchange(
                "/api/admin/audit-events?eventCode=MODULE_PUBLISHED&page=1&pageSize=20",
                HttpMethod.GET, first.token(), null);
        assertThat(auditEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> auditData = data(auditEvents);
        List<Map<String, Object>> publishedEvents = (List<Map<String, Object>>) auditData.get("events");
        assertThat(publishedEvents).hasSizeGreaterThanOrEqualTo(2);
        assertThat(count("audit_event", "event_code = 'MODULE_PUBLISHED' and json_extract(detail_json, '$.moduleId') = "
                + moduleId)).isEqualTo(2);
        assertThat(((Number) auditData.get("total")).intValue()).isGreaterThanOrEqualTo(2);

        ResponseEntity<Map> adminLogin = http.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "123123aa"), Map.class);
        String platformToken = (String) data(adminLogin).get("accessToken");
        ResponseEntity<Map> platformOnlyDenied = exchange("/api/admin/module-config/groups", HttpMethod.POST, platformToken,
                Map.of("code", "not_allowed", "name", "无系统身份", "sortOrder", 0));
        assertThat(platformOnlyDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(platformOnlyDenied.getBody().get("code")).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void visualPageAndMenuDraftsOnlyReachPermissionFilteredRuntimeAfterPublishAndRollback() {
        Session owner = register("page_menu_owner", "page-menu-system", "page-menu@example.com");

        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "business", "name", "业务中心", "sortOrder", 20))).get("id"));
        Map<String, Object> created = data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "customer_hub", "name", "客户中心")));
        long moduleId = number(((Map<?, ?>) created.get("module")).get("id"));
        Map<String, Object> menu = (Map<String, Object>) ((List<?>) created.get("menus")).getFirst();
        long menuId = number(menu.get("id"));

        exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "customer_name", "name", "客户名称", "fieldType", "TEXT", "required", true,
                        "sortOrder", 10, "config", Map.of("placeholder", "请输入客户名称")));
        Map<String, Object> formLayout = Map.of(
                "schemaVersion", 1,
                "layoutType", "FORM",
                "fieldCodes", List.of("customer_name"),
                "components", List.of(Map.of(
                        "id", "section-basic", "type", "SECTION", "title", "基础信息", "columns", 2,
                        "fieldCodes", List.of("customer_name"))));
        ResponseEntity<Map> updatedForm = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, owner.token(),
                Map.of("layout", formLayout, "version", 0));
        assertThat(updatedForm.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> customPage = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages", HttpMethod.POST, owner.token(),
                Map.of("pageType", "CUSTOM", "name", "客户工作台", "layout", Map.of(
                        "schemaVersion", 1, "layoutType", "CUSTOM", "fieldCodes", List.of("customer_name"),
                        "components", List.of(Map.of("id", "generic", "type", "SECTION", "fieldCodes", List.of("customer_name"))))));
        assertThat(customPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> genericBlocked = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        assertThat(genericBlocked.get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) genericBlocked.get("issues")).stream().map(issue -> issue.get("code")))
                .contains("SPECIAL_PAGE_DESIGN_REQUIRED");
        ResponseEntity<Map> dedicatedCustomPage = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/CUSTOM", HttpMethod.PUT, owner.token(),
                Map.of("layout", Map.of(
                        "schemaVersion", 1, "layoutType", "CUSTOM", "fieldCodes", List.of("customer_name"),
                        "components", List.of(Map.of("id", "summary", "type", "SUMMARY", "fieldCodes", List.of("customer_name")))),
                        "version", 0));
        assertThat(dedicatedCustomPage.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> invalidRoute = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/menus/" + menuId,
                HttpMethod.PUT, owner.token(), Map.of(
                        "name", "客户中心", "icon", "team", "routePath", "/admin/customer_hub",
                        "sortOrder", 5, "visible", true, "status", "ACTIVE", "version", 0));
        assertThat(invalidRoute.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidRoute.getBody().get("code")).isEqualTo("MENU_ROUTE_INVALID");

        ResponseEntity<Map> cycle = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/menus/" + menuId,
                HttpMethod.PUT, owner.token(), Map.of(
                        "parentId", menuId, "name", "客户中心", "icon", "team", "routePath", "/runtime/customer_hub",
                        "sortOrder", 5, "visible", true, "status", "ACTIVE", "version", 0));
        assertThat(cycle.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(cycle.getBody().get("code")).isEqualTo("MENU_HIERARCHY_CYCLE");

        ResponseEntity<Map> updatedMenu = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/menus/" + menuId,
                HttpMethod.PUT, owner.token(), Map.of(
                        "name", "客户工作台", "icon", "team", "routePath", "/runtime/customer_hub",
                        "sortOrder", 5, "visible", true, "status", "ACTIVE", "version", 0));
        assertThat(updatedMenu.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(updatedMenu).get("version")).isEqualTo(1);

        Map<String, Object> check = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        assertThat(check.get("valid")).isEqualTo(true);
        ResponseEntity<Map> publishedV1 = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
        assertThat(publishedV1.getStatusCode()).isEqualTo(HttpStatus.OK);
        long version1Id = number(data(publishedV1).get("versionId"));

        List<Map<String, Object>> runtimeV1 = (List<Map<String, Object>>) exchange(
                "/api/runtime/modules", HttpMethod.GET, owner.token(), null).getBody().get("data");
        assertThat(runtimeV1).singleElement().satisfies(item -> {
            assertThat(item.get("groupName")).isEqualTo("业务中心");
            assertThat(item.get("menuName")).isEqualTo("客户工作台");
            assertThat(item.get("menuRoutePath")).isEqualTo("/runtime/customer_hub");
            assertThat(item.get("menuSortOrder")).isEqualTo(5);
        });
        Map<String, Object> runtimeConfiguration = data(exchange(
                "/api/runtime/modules/customer_hub/configuration", HttpMethod.GET, owner.token(), null));
        Map<String, Object> publishedSchema = (Map<String, Object>) runtimeConfiguration.get("configuration");
        assertThat(((List<Map<String, Object>>) publishedSchema.get("pages")).stream()
                .map(page -> page.get("pageType"))).contains("FORM", "CUSTOM");
        assertThat(((List<?>) publishedSchema.get("menus"))).hasSize(1);
        assertThat(jdbc.queryForObject("select snapshot_json from cfg_module_version where id=?", String.class, version1Id))
                .contains("客户工作台", "components", "customer_name", "business");

        ResponseEntity<Map> invalidDraftPage = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, owner.token(),
                Map.of("layout", Map.of("schemaVersion", 1, "fieldCodes", List.of("removed_field")), "version", 1));
        assertThat(invalidDraftPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> blocked = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        assertThat(blocked.get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) blocked.get("issues")).stream().map(issue -> issue.get("code")))
                .contains("PAGE_FIELD_NOT_ACTIVE", "REQUIRED_FIELD_NOT_IN_FORM");
        assertThat(((List<Map<String, Object>>) exchange("/api/runtime/modules", HttpMethod.GET, owner.token(), null)
                .getBody().get("data"))).hasSize(1);

        exchange("/api/admin/module-config/modules/" + moduleId + "/pages/FORM", HttpMethod.PUT, owner.token(),
                Map.of("layout", formLayout, "version", 2));
        ResponseEntity<Map> hiddenMenu = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/menus/" + menuId,
                HttpMethod.PUT, owner.token(), Map.of(
                        "name", "客户工作台", "icon", "team", "routePath", "/runtime/customer_hub",
                        "sortOrder", 5, "visible", false, "status", "ACTIVE", "version", 1));
        assertThat(hiddenMenu.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<?>) exchange("/api/runtime/modules", HttpMethod.GET, owner.token(), null)
                .getBody().get("data"))).hasSize(1);

        Map<String, Object> checkV2 = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        assertThat(checkV2.get("valid")).isEqualTo(true);
        exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", checkV2.get("draftRevision")));
        assertThat(((List<?>) exchange("/api/runtime/modules", HttpMethod.GET, owner.token(), null)
                .getBody().get("data"))).isEmpty();

        List<Map<String, Object>> versions = (List<Map<String, Object>>) exchange(
                "/api/admin/module-config/modules/" + moduleId + "/versions", HttpMethod.GET, owner.token(), null)
                .getBody().get("data");
        int publicationVersion = ((Number) versions.getFirst().get("publicationVersion")).intValue();
        ResponseEntity<Map> rollback = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rollback", HttpMethod.POST, owner.token(),
                Map.of("targetVersionId", version1Id, "expectedPublicationVersion", publicationVersion));
        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<?>) exchange("/api/runtime/modules", HttpMethod.GET, owner.token(), null)
                .getBody().get("data"))).hasSize(1);
        assertThat(count("cfg_module_version", "module_id = " + moduleId)).isEqualTo(3);
        assertThat(count("audit_event", "event_code = 'MODULE_MENU_DRAFT_UPDATED' and result_code = 'SUCCESS'")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ruleAndIndexCodesAreGeneratedWhenBusinessUsersOnlyProvideNames() {
        Session owner = register("generated_config_owner", "generated-config-system", "generated-config@example.com");
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("name", "服务管理", "sortOrder", 40))).get("id"));
        Map<String, Object> created = data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "name", "售后工单")));
        long moduleId = number(((Map<?, ?>) created.get("module")).get("id"));
        Map<String, Object> subject = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("name", "工单主题", "fieldType", "TEXT", "required", true,
                        "sortOrder", 10, "config", Map.of("placeholder", "请输入工单主题"))));

        Map<String, Object> ruleDefinition = Map.of(
                "mode", "ALL",
                "conditions", List.of(Map.of("field", subject.get("code"), "operator", "NOT_EMPTY")),
                "effect", Map.of("type", "BLOCK"));
        Map<String, Object> rule = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/rules",
                HttpMethod.POST, owner.token(), Map.of(
                        "name", "主题填写检查", "ruleType", "VALIDATION", "triggerEvent", "CREATE",
                        "definition", ruleDefinition, "message", "主题已填写", "sortOrder", 10)));
        assertThat(rule.get("code").toString()).startsWith("rule_");

        Map<String, Object> index = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/indexes",
                HttpMethod.POST, owner.token(), Map.of(
                        "name", "工单主题查询", "uniqueIndex", false,
                        "fields", List.of(Map.of("fieldId", subject.get("id"), "sortOrder", 0, "sortDirection", "ASC")))));
        assertThat(((Map<String, Object>) index.get("index")).get("code").toString()).startsWith("index_");
    }

    @Test
    @SuppressWarnings("unchecked")
    void structuredRulesAndUniqueIndexesArePreviewedPublishedAndEnforcedInOneTransaction() {
        Session owner = register("rule_index_owner", "rule-index-system", "rule-index@example.com");
        Session outsider = register("rule_index_outsider", "rule-index-other", "rule-index-other@example.com");

        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales_rule", "name", "销售规则", "sortOrder", 30))).get("id"));
        Map<String, Object> created = data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "rule_customer", "name", "规则客户")));
        long moduleId = number(((Map<?, ?>) created.get("module")).get("id"));
        Map<String, Object> phone = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "phone", "name", "手机号", "fieldType", "TEXT", "required", true,
                        "sortOrder", 10, "config", Map.of("placeholder", "请输入手机号"))));
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "amount", "name", "金额", "fieldType", "NUMBER", "required", false,
                        "sortOrder", 20, "config", Map.of("precision", 2)));

        ResponseEntity<Map> arbitraryScript = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/rules",
                HttpMethod.POST, owner.token(), Map.of(
                        "code", "unsafe_script", "name", "不安全脚本", "ruleType", "VALIDATION",
                        "triggerEvent", "CREATE", "definition", Map.of("script", "return true"), "sortOrder", 0));
        assertThat(arbitraryScript.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(arbitraryScript.getBody().get("code")).isEqualTo("RULE_DEFINITION_INVALID");

        Map<String, Object> ruleDefinition = Map.of(
                "mode", "ALL",
                "conditions", List.of(Map.of("field", "amount", "operator", "GT", "value", 1000)),
                "effect", Map.of("type", "BLOCK"));
        ResponseEntity<Map> ruleCreated = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/rules",
                HttpMethod.POST, owner.token(), Map.of(
                        "code", "amount_limit", "name", "金额上限", "ruleType", "VALIDATION",
                        "triggerEvent", "CREATE", "definition", ruleDefinition,
                        "message", "金额不能超过 1000", "sortOrder", 10));
        assertThat(ruleCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        long ruleId = number(data(ruleCreated).get("id"));
        Map<String, Object> untestedCheck = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        assertThat(untestedCheck.get("valid")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) untestedCheck.get("issues")).stream().map(issue -> issue.get("code")))
                .contains("RULE_TEST_REQUIRED");

        ResponseEntity<Map> matchedPreview = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/rules/" + ruleId + "/test",
                HttpMethod.POST, owner.token(), Map.of("sampleFields", Map.of("amount", 1500)));
        assertThat(matchedPreview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(matchedPreview)).containsEntry("matched", true).containsEntry("effectType", "BLOCK");
        assertThat((List<?>) data(matchedPreview).get("conditions")).singleElement();
        assertThat(jdbc.queryForObject("select test_status from cfg_module_rule where id=?", String.class, ruleId))
                .isEqualTo("EXECUTED");
        assertThat(jdbc.queryForObject("select last_test_result_json from cfg_module_rule where id=?", String.class, ruleId))
                .contains("\"matched\": true", "\"effectType\": \"BLOCK\"");
        ResponseEntity<Map> unmatchedPreview = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/rules/" + ruleId + "/test",
                HttpMethod.POST, owner.token(), Map.of("sampleFields", Map.of("amount", 100)));
        assertThat(data(unmatchedPreview)).containsEntry("matched", false);

        ResponseEntity<Map> indexCreated = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes/indexes",
                HttpMethod.POST, owner.token(), Map.of(
                        "code", "phone_unique", "name", "手机号唯一", "uniqueIndex", true,
                        "fields", List.of(Map.of("fieldId", phone.get("id"), "sortOrder", 0, "sortDirection", "ASC"))));
        assertThat(indexCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(indexCreated).get("scope").toString()).contains("SYSTEM", "TENANT", "MODULE", "NON_DELETED");
        assertThat(data(indexCreated).get("projectionPlan").toString()).contains("phone", "SHA-256", "唯一");

        Map<String, Object> ruleIndexDraft = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes", HttpMethod.GET, owner.token(), null));
        assertThat((List<?>) ruleIndexDraft.get("rules")).hasSize(1);
        assertThat((List<?>) ruleIndexDraft.get("indexes")).hasSize(1);
        ResponseEntity<Map> crossSystemDraft = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/rules-indexes", HttpMethod.GET, outsider.token(), null);
        assertThat(crossSystemDraft.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> check = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        assertThat(check.get("valid")).isEqualTo(true);
        assertThat((List<String>) check.get("indexProjectionPlans")).singleElement()
                .satisfies(plan -> assertThat(plan).contains("phone_unique", "phone", "SHA-256"));
        ResponseEntity<Map> published = exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", check.get("draftRevision")));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> runtime = data(exchange(
                "/api/runtime/modules/rule_customer/configuration", HttpMethod.GET, owner.token(), null));
        Map<String, Object> snapshot = (Map<String, Object>) runtime.get("configuration");
        assertThat((List<?>) snapshot.get("rules")).hasSize(1);
        assertThat((List<?>) snapshot.get("queryIndexes")).hasSize(1);
        assertThat((List<?>) snapshot.get("queryIndexFields")).hasSize(1);

        Map<String, Object> validRecord = Map.of(
                "title", "首个客户", "participantMemberIds", List.of(),
                "fields", Map.of("phone", "13800138000", "amount", 800));
        ResponseEntity<Map> firstRecord = exchange(
                "/api/runtime/modules/rule_customer/records", HttpMethod.POST, owner.token(), validRecord);
        assertThat(firstRecord.getStatusCode()).withFailMessage("创建首条业务数据失败：%s", firstRecord.getBody())
                .isEqualTo(HttpStatus.OK);
        long recordId = number(data(firstRecord).get("id"));
        assertThat(count("biz_record_index", "record_id = " + recordId + " and unique_key_hash is not null")).isOne();

        int recordsBeforeReject = count("biz_record", "module_id = " + moduleId);
        ResponseEntity<Map> duplicatePhone = exchange(
                "/api/runtime/modules/rule_customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "重复客户", "participantMemberIds", List.of(),
                        "fields", Map.of("phone", "13800138000", "amount", 500)));
        assertThat(duplicatePhone.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicatePhone.getBody().get("code")).isEqualTo("UNIQUE_RULE_VIOLATION");
        assertThat(count("biz_record", "module_id = " + moduleId)).isEqualTo(recordsBeforeReject);

        ResponseEntity<Map> blockedAmount = exchange(
                "/api/runtime/modules/rule_customer/records", HttpMethod.POST, owner.token(), Map.of(
                        "title", "超额客户", "participantMemberIds", List.of(),
                        "fields", Map.of("phone", "13900139000", "amount", 1500)));
        assertThat(blockedAmount.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(blockedAmount.getBody().get("code")).isEqualTo("BUSINESS_RULE_REJECTED");
        assertThat(count("biz_record", "module_id = " + moduleId)).isEqualTo(recordsBeforeReject);
    }

    @Test
    @SuppressWarnings("unchecked")
    void secondaryTenantPublishesOwnExtensionBindsOnlyExistingApplicationGrantAndFallsBackToMain() {
        Session owner = register("tenant_extension_owner", "tenant-extension-system", "tenant-extension@example.com");
        long groupId = number(data(exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "crm", "name", "客户管理", "sortOrder", 10))).get("id"));
        Map<String, Object> created = data(exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "tenant_customer", "name", "租户客户")));
        long moduleId = number(((Map<?, ?>) created.get("module")).get("id"));
        exchange("/api/admin/module-config/modules/" + moduleId + "/fields", HttpMethod.POST, owner.token(),
                Map.of("code", "phone", "name", "手机号", "fieldType", "TEXT", "required", true,
                        "sortOrder", 10, "config", Map.of("placeholder", "请输入手机号")));
        Map<String, Object> mainCheck = data(exchange(
                "/api/admin/module-config/modules/" + moduleId + "/publication-check", HttpMethod.GET, owner.token(), null));
        exchange("/api/admin/module-config/modules/" + moduleId + "/publish", HttpMethod.POST, owner.token(),
                Map.of("expectedDraftRevision", mainCheck.get("draftRevision")));

        migrateToMulti(owner);
        Map<String, Object> branch = data(exchange("/api/admin/system/tenants", HttpMethod.POST, owner.token(),
                Map.of("code", "east", "name", "华东租户")));
        long branchId = number(branch.get("id"));
        long accountId = jdbc.queryForObject("select id from plat_account where username=?", Long.class,
                "tenant_extension_owner");
        jdbc.update("insert into app_definition(context_type,owner_system_id,owner_tenant_id,code,name,description,"
                        + "application_type,draft_revision,status,created_by_account_id) values('SYSTEM',?,?,?,?,?,'INTERNAL',1,'ACTIVE',?)",
                owner.systemId(), branchId, "tenant-crm", "租户 CRM 应用", "只桥接已授权动作", accountId);
        long applicationId = jdbc.queryForObject("select max(id) from app_definition where owner_system_id=?", Long.class,
                owner.systemId());
        jdbc.update("insert into app_grant(application_id,target_type,target_system_id,target_tenant_id,resource_type,"
                        + "resource_id,action_code,data_scope_json,status) values(?,'TENANT',?,?, 'MODULE',?,'CREATE','{}','ACTIVE')",
                applicationId, owner.systemId(), branchId, "tenant_customer");
        long grantId = jdbc.queryForObject("select max(id) from app_grant where application_id=?", Long.class, applicationId);
        jdbc.update("insert into app_grant_field(grant_id,field_code,readable,writable) values(?,?,1,1),(?,?,1,1)",
                grantId, "phone", grantId, "vip_level");
        jdbc.update("insert into app_grant(application_id,target_type,target_system_id,target_tenant_id,resource_type,"
                        + "resource_id,action_code,data_scope_json,status) values(?,'TENANT',?,?, 'MODULE','other_module','CREATE','{}','ACTIVE')",
                applicationId, owner.systemId(), branchId);
        long otherModuleGrantId = jdbc.queryForObject(
                "select id from app_grant where application_id=? and resource_id='other_module'", Long.class, applicationId);

        Map<String, Object> entered = data(exchange(
                "/api/systems/" + owner.systemId() + "/tenants/" + branchId + "/enter", HttpMethod.POST,
                owner.platformToken(), Map.of("previousSystemId", owner.systemId(), "previousTenantId", owner.tenantId())));
        String branchToken = (String) ((Map<?, ?>) entered.get("tokens")).get("accessToken");

        List<Map<String, Object>> inherited = (List<Map<String, Object>>) exchange(
                "/api/admin/tenant-extensions", HttpMethod.GET, branchToken, null).getBody().get("data");
        assertThat(inherited).singleElement();
        Map<String, Object> inheritedPreview = (Map<String, Object>) inherited.getFirst().get("mergedPreview");
        assertThat((List<Map<String, Object>>) inheritedPreview.get("fields")).singleElement().satisfies(field -> {
            assertThat(field).containsEntry("code", "phone").containsEntry("source", "MAIN")
                    .containsEntry("mandatory", true);
        });
        List<Map<String, Object>> catalogBefore = (List<Map<String, Object>>) exchange(
                "/api/runtime/modules", HttpMethod.GET, branchToken, null).getBody().get("data");
        assertThat(catalogBefore).singleElement().satisfies(item -> {
            assertThat(item).containsEntry("groupName", "客户管理").containsEntry("moduleCode", "tenant_customer");
        });
        Map<String, Object> baseRuntime = (Map<String, Object>) data(exchange(
                "/api/runtime/modules/tenant_customer/configuration", HttpMethod.GET, branchToken, null)).get("configuration");
        assertThat(((List<Map<String, Object>>) baseRuntime.get("fields")).stream().map(field -> field.get("code")))
                .containsExactly("phone");

        Map<String, Object> validField = Map.of(
                "code", "vip_level", "name", "会员等级", "fieldType", "TEXT", "required", false,
                "sortOrder", 20, "config", Map.of("placeholder", "请输入等级"));
        ResponseEntity<Map> baseCodeConflict = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId, HttpMethod.PUT, branchToken,
                Map.of("fields", List.of(Map.of(
                                "code", "phone", "name", "重复手机号", "fieldType", "TEXT", "required", false,
                                "sortOrder", 20, "config", Map.of())),
                        "pages", List.of(), "applicationBindings", List.of(), "expectedVersion", 0));
        assertThat(baseCodeConflict.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(baseCodeConflict.getBody().get("code")).isEqualTo("TENANT_EXTENSION_INVALID");

        ResponseEntity<Map> hiddenMandatory = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId, HttpMethod.PUT, branchToken,
                Map.of("fields", List.of(validField),
                        "pages", List.of(Map.of("pageType", "FORM", "fieldCodes", List.of("vip_level"))),
                        "applicationBindings", List.of(), "expectedVersion", 0));
        assertThat(hiddenMandatory.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> permissionExpansion = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId, HttpMethod.PUT, branchToken,
                Map.of("fields", List.of(validField),
                        "pages", List.of(Map.of("pageType", "FORM", "fieldCodes", List.of("phone", "vip_level"))),
                        "applicationBindings", List.of(Map.of(
                                "applicationId", applicationId, "grantIds", List.of(otherModuleGrantId))),
                        "expectedVersion", 0));
        assertThat(permissionExpansion.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(permissionExpansion.getBody().get("code")).isEqualTo("APPLICATION_BINDING_SCOPE_INVALID");

        ResponseEntity<Map> saved = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId, HttpMethod.PUT, branchToken,
                Map.of("fields", List.of(validField),
                        "pages", List.of(Map.of("pageType", "FORM", "fieldCodes", List.of("phone", "vip_level"))),
                        "applicationBindings", List.of(Map.of(
                                "applicationId", applicationId, "grantIds", List.of(grantId))),
                        "expectedVersion", 0));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> extension = (Map<String, Object>) data(saved).get("extension");
        assertThat(extension).containsEntry("status", "DRAFT").containsEntry("draftRevision", 1);
        long tenantFieldId = jdbc.queryForObject(
                "select id from cfg_module_field where module_id=? and owner_tenant_id=?", Long.class, moduleId, branchId);
        assertThat(jdbc.queryForObject("select code from cfg_module_field where id=?", String.class, tenantFieldId))
                .startsWith("__t" + branchId + "_");

        Map<String, Object> checked = data(exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, branchToken, null));
        assertThat(checked).containsEntry("valid", true).containsEntry("draftRevision", 1);
        Map<String, Object> checkedPreview = (Map<String, Object>) checked.get("mergedPreview");
        assertThat(((List<Map<String, Object>>) checkedPreview.get("fields")).stream().map(field -> field.get("source")))
                .containsExactly("MAIN", "TENANT");
        assertThat(((List<Map<String, Object>>) baseRuntime.get("fields")).stream().map(field -> field.get("code")))
                .doesNotContain("vip_level");

        ResponseEntity<Map> published = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId + "/publish", HttpMethod.POST, branchToken,
                Map.of("expectedDraftRevision", 1));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> publishedExtension = (Map<String, Object>) data(published).get("extension");
        long extensionVersionId = number(publishedExtension.get("currentVersionId"));
        int extensionRowVersion = ((Number) publishedExtension.get("version")).intValue();
        Map<String, Object> extendedRuntime = (Map<String, Object>) data(exchange(
                "/api/runtime/modules/tenant_customer/configuration", HttpMethod.GET, branchToken, null)).get("configuration");
        assertThat(extendedRuntime).containsEntry("tenantExtensionApplied", true)
                .containsEntry("tenantExtensionVersionId", (int) extensionVersionId)
                .containsEntry("tenantExtensionVersionNumber", 1);
        List<Map<String, Object>> effectiveFields = (List<Map<String, Object>>) extendedRuntime.get("fields");
        assertThat(effectiveFields.stream().map(field -> field.get("code"))).containsExactly("phone", "vip_level");
        assertThat(effectiveFields.get(1)).containsEntry("id", (int) tenantFieldId).containsEntry("source", "TENANT");
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) extendedRuntime.get("applicationBindings");
        assertThat(bindings).singleElement().satisfies(binding -> {
            assertThat(binding.get("applicationCode")).isEqualTo("tenant-crm");
            assertThat(((List<?>) binding.get("actionCodes")).stream().map(String::valueOf)).containsExactly("CREATE");
        });
        assertThat((List<Map<String, Object>>) exchange("/api/runtime/modules", HttpMethod.GET, branchToken, null)
                .getBody().get("data")).usingRecursiveComparison().isEqualTo(catalogBefore);

        ResponseEntity<Map> record = exchange(
                "/api/runtime/modules/tenant_customer/records", HttpMethod.POST, branchToken,
                Map.of("title", "华东首个客户", "participantMemberIds", List.of(),
                        "fields", Map.of("phone", "13800138000", "vip_level", "金卡")));
        assertThat(record.getStatusCode()).withFailMessage("租户扩展字段业务录入失败：%s", record.getBody())
                .isEqualTo(HttpStatus.OK);
        long recordId = number(data(record).get("id"));
        assertThat(count("biz_record_value", "record_id = " + recordId + " and field_id = " + tenantFieldId)).isOne();
        assertThat(count("biz_record", "id = " + recordId + " and tenant_id = " + branchId)).isOne();

        ResponseEntity<Map> removed = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId + "/remove", HttpMethod.POST, branchToken,
                Map.of("expectedVersion", extensionRowVersion));
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> removedExtension = (Map<String, Object>) data(removed).get("extension");
        assertThat(removedExtension).containsEntry("status", "DELETED");
        assertThat(jdbc.queryForObject("select current_version_id is null from cfg_tenant_extension where id=?",
                Boolean.class, removedExtension.get("id"))).isTrue();
        Map<String, Object> fallbackRuntime = (Map<String, Object>) data(exchange(
                "/api/runtime/modules/tenant_customer/configuration", HttpMethod.GET, branchToken, null)).get("configuration");
        assertThat(((List<Map<String, Object>>) fallbackRuntime.get("fields")).stream().map(field -> field.get("code")))
                .containsExactly("phone");
        assertThat((List<Map<String, Object>>) exchange("/api/runtime/modules", HttpMethod.GET, branchToken, null)
                .getBody().get("data")).usingRecursiveComparison().isEqualTo(catalogBefore);

        ResponseEntity<Map> rollback = exchange(
                "/api/admin/tenant-extensions/modules/" + moduleId + "/rollback", HttpMethod.POST, branchToken,
                Map.of("targetVersionId", extensionVersionId, "expectedVersion", removedExtension.get("version")));
        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) data(rollback).get("extension")).get("status")).isEqualTo("PUBLISHED");
        assertThat(count("cfg_tenant_extension_version", "extension_id = "
                + ((Map<?, ?>) data(rollback).get("extension")).get("id"))).isOne();
        assertThat(count("audit_event", "event_code in ('TENANT_EXTENSION_PUBLISHED','TENANT_EXTENSION_REMOVED',"
                + "'TENANT_EXTENSION_ROLLED_BACK') and tenant_id = " + branchId)).isEqualTo(3);
    }

    private void migrateToMulti(Session session) {
        ResponseEntity<Map> requested = exchange("/api/admin/system/tenant-mode-migrations", HttpMethod.POST,
                session.token(), Map.of("toMode", "MULTI"));
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.OK);
        long migrationId = number(data(requested).get("id"));
        ResponseEntity<Map> approved = exchange(
                "/api/admin/system/tenant-mode-migrations/" + migrationId + "/decision", HttpMethod.POST,
                session.token(), Map.of("approved", true, "comment", "C25 租户扩展验收", "expectedVersion", 0));
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(approved).get("status")).isEqualTo("COMPLETED");
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username,
                "password", "correct-password",
                "displayName", username,
                "email", email,
                "systemName", systemCode,
                "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        ResponseEntity<Map> login = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "correct-password"), Map.class);
        return new Session(
                (String) ((Map<?, ?>) result.get("tokens")).get("accessToken"),
                (String) data(login).get("accessToken"),
                number(result.get("systemId")),
                number(result.get("tenantId")));
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

    private String publishedFieldName(Map<?, ?> configuration) {
        List<?> fields = (List<?>) configuration.get("fields");
        return ((Map<?, ?>) fields.getFirst()).get("name").toString();
    }

    private record Session(String token, String platformToken, long systemId, long tenantId) {
    }
}
