package com.unique.examine.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PublishedPageRuleJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "page_rule_root";
    private static final String ROOT_PASSWORD = "Page-Rule-Root-Test-84!";
    private static final String MEMBER_PASSWORD = "Page-Rule-Member-Test-42!";
    private static volatile RestartEvidence restartEvidence;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_page_rule_test")
            .withUsername("examine_page_rule_test")
            .withPassword("container-test-password")
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
        registry.add("spring.flyway.locations", () -> "filesystem:"
                + migrationRoot().toString().replace('\\', '/'));
        registry.add("examine.security.secure-cookies", () -> false);
        registry.add("examine.bootstrap.root.username", () -> ROOT_USERNAME);
        registry.add("examine.bootstrap.root.password", () -> ROOT_PASSWORD);
        registry.add("examine.bootstrap.root.display-name", () -> "Page Rule Test Root");
        registry.add("examine.runtime.draft-expiry.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.runtime.reference-recalculation.initial-delay-ms", () -> 3_600_000);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void publishedPagesAndRulesDriveOrdinaryMemberListFormDetailAndServerGuards() throws Exception {
        var admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var system = admin.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "page_rule_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Published Page Rule", "description", "published member runtime closure",
                "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        assertOk(admin.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";

        var group = admin.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "operations", "name", "Operations", "description", "", "iconKey", "folder",
                "sortOrder", 0, "status", "ENABLED", "draftRevision", revision(admin, configRoot)
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var module = admin.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"), "code", "runtime_rule", "name", "Runtime Rule",
                "description", "published pages and rules", "iconKey", "clipboard", "sortOrder", 0,
                "status", "ENABLED", "allowComments", false, "allowTeam", false,
                "draftRevision", revision(admin, configRoot)
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");

        var statusField = createField(admin, configRoot, moduleId,
                "status", "Status", 0, true, true, true, 180);
        var reasonField = createField(admin, configRoot, moduleId,
                "reason", "Reason", 1, false, true, true, 260);
        var statusFieldId = text(statusField.body(), "/data/id");
        var reasonFieldId = text(reasonField.body(), "/data/id");

        var pagesResponse = admin.get(configRoot + "/modules/" + moduleId + "/pages");
        assertOk(pagesResponse);
        var listPage = item(pagesResponse.body().at("/data"), "type", "LIST");
        var formPage = item(pagesResponse.body().at("/data"), "type", "FORM");
        var detailPage = item(pagesResponse.body().at("/data"), "type", "DETAIL");
        listPage = updatePage(admin, configRoot, moduleId, listPage,
                Map.of("columns", 24, "gap", 8, "density", "COMPACT", "pageSize", 20,
                        "showSearch", true, "showFilters", true));
        formPage = updatePage(admin, configRoot, moduleId, formPage,
                Map.of("columns", 24, "gap", 12, "labelPosition", "TOP", "stickyActions", true));
        detailPage = updatePage(admin, configRoot, moduleId, detailPage,
                Map.of("columns", 24, "gap", 12, "labelPosition", "LEFT"));

        var listRoot = componentRoot(configRoot, moduleId, listPage);
        createComponent(admin, configRoot, listRoot, null, statusFieldId, "status_column", "FIELD",
                0, 0, 0, 12, Map.of("label", "Status", "variant", "FIXED_LEFT"));
        createComponent(admin, configRoot, listRoot, null, reasonFieldId, "reason_column", "FIELD",
                1, 0, 12, 12, Map.of("label", "Reason"));

        var formRoot = componentRoot(configRoot, moduleId, formPage);
        var formSection = createComponent(admin, configRoot, formRoot, null, null, "business_fields", "SECTION",
                0, 0, 0, 24, Map.of("title", "Business fields", "columns", 24,
                        "collapsible", true, "collapsed", false));
        createComponent(admin, configRoot, formRoot, text(formSection.body(), "/data/id"), statusFieldId,
                "form_status", "FIELD", 1, 0, 0, 12, Map.of("label", "Status"));
        createComponent(admin, configRoot, formRoot, text(formSection.body(), "/data/id"), reasonFieldId,
                "form_reason", "FIELD", 2, 0, 12, 12, Map.of("label", "Reason"));

        var detailRoot = componentRoot(configRoot, moduleId, detailPage);
        var detailSection = createComponent(admin, configRoot, detailRoot, null, null,
                "business_detail", "SECTION", 0, 0, 0, 24,
                Map.of("title", "Business detail", "columns", 24, "collapsible", true, "collapsed", true));
        createComponent(admin, configRoot, detailRoot, text(detailSection.body(), "/data/id"), statusFieldId,
                "detail_status", "FIELD", 1, 0, 0, 12, Map.of("label", "Status"));
        createComponent(admin, configRoot, detailRoot, text(detailSection.body(), "/data/id"), reasonFieldId,
                "detail_reason", "FIELD", 2, 0, 12, 12, Map.of("label", "Reason"));

        var updateAction = admin.postWithCsrf(configRoot + "/modules/" + moduleId + "/actions", json(Map.of(
                "code", "published_update", "name", "Published update", "type", "UPDATE",
                "placement", "DETAIL", "confirmMessage", "", "sortOrder", 0, "status", "ENABLED",
                "properties", Map.of("style", "PRIMARY"), "draftRevision", revision(admin, configRoot)
        )), Map.of("Idempotency-Key", key()));
        assertOk(updateAction);
        var updateActionId = text(updateAction.body(), "/data/id");

        createRule(admin, configRoot, moduleId, "reason_required", "FIELD_REQUIRED", 10,
                statusFieldId, "EQ", "NEEDS_REASON", "REQUIRED", reasonFieldId, true);
        createRule(admin, configRoot, moduleId, "reason_hidden", "FIELD_VISIBILITY", 20,
                statusFieldId, "EQ", "HIDE", "VISIBLE", reasonFieldId, false);
        createRule(admin, configRoot, moduleId, "reason_readonly", "FIELD_READ_ONLY", 30,
                statusFieldId, "EQ", "LOCKED", "READ_ONLY", reasonFieldId, true);
        createRule(admin, configRoot, moduleId, "update_disabled", "ACTION_ENABLED", 40,
                statusFieldId, "EQ", "NO_EDIT", "ACTION_ENABLED", updateActionId, false);
        createRule(admin, configRoot, moduleId, "delete_denied", "DELETE_ALLOWED", 50,
                statusFieldId, "EQ", "LOCKED", "DELETE_ALLOWED", null, false);
        createRule(admin, configRoot, moduleId, "approval_required", "APPROVAL_REQUIRED", 60,
                statusFieldId, "EQ", "NEEDS_APPROVAL", "APPROVAL_REQUIRED", null, true);

        var checked = admin.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", revision(admin, configRoot))), Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/status")).isEqualTo("PASSED");
        var root = admin.get(configRoot);
        assertOk(root);
        var published = admin.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", text(root.body(), "/data/draftRevision"),
                "configRootVersion", text(root.body(), "/data/version"),
                "reason", "page-rule real HTTP closure"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        var publishedVersionId = text(published.body(), "/data/version/id");
        assertOk(admin.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var tenantId = jdbcTemplate.queryForObject(
                "SELECT default_tenant_id FROM un_plat_member WHERE system_id=? AND deleted_at IS NULL LIMIT 1",
                String.class, Long.parseLong(systemId));
        var memberUsername = "page_rule_member_" + Long.toUnsignedString(System.nanoTime(), 36);
        var registration = new TestClient().post("/api/v1/auth/register", json(Map.of(
                "username", memberUsername, "displayName", "Page Rule Ordinary Member",
                "password", MEMBER_PASSWORD, "systemName", "Page Rule Member Home",
                "systemCode", "page_rule_home_" + Long.toUnsignedString(System.nanoTime(), 36)
        )), Map.of("Idempotency-Key", key()));
        assertOk(registration);

        var dataScopes = admin.get("/api/v1/systems/" + systemId + "/admin/data-scopes?size=200");
        assertOk(dataScopes);
        var role = admin.postWithCsrf("/api/v1/systems/" + systemId + "/admin/roles", json(Map.of(
                "code", "page_rule_member", "name", "Page Rule Member", "description", "runtime member"
        )), Map.of("Idempotency-Key", key()));
        assertOk(role);
        var roleId = text(role.body(), "/data/id");
        var deniedPublishedRole = publishRole(admin, systemId, roleId, text(role.body(), "/data/version"),
                itemId(dataScopes.body().at("/data/items"), "kind", "ALL"),
                List.of("system.runtime.access", "system.workbench.view"));

        var ordinary = login(memberUsername, MEMBER_PASSWORD);
        var access = ordinary.postWithCsrf("/api/v1/context/systems/" + systemId + "/access-requests", json(Map.of(
                "targetTenantId", tenantId, "reason", "published page-rule journey"
        )), Map.of("Idempotency-Key", key()));
        assertOk(access);
        admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var approved = admin.postWithCsrf("/api/v1/systems/" + systemId + "/admin/access-requests/"
                + text(access.body(), "/data/id") + ":approve", json(Map.of(
                "reason", "approve page-rule journey", "version", text(access.body(), "/data/version"),
                "tenantIds", List.of(tenantId), "roleIds", List.of(roleId)
        )), Map.of("Idempotency-Key", key()));
        assertOk(approved);
        assertOk(ordinary.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var runtimeRoot = "/api/v1/systems/" + systemId + "/runtime/modules/runtime_rule";
        assertError(ordinary.get(runtimeRoot + "/record-schema"), 403, "PERMISSION_DENIED");
        assertError(ordinary.get(runtimeRoot + "/records"), 403, "PERMISSION_DENIED");

        admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf("/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var allowedPublishedRole = publishRole(admin, systemId, roleId,
                text(deniedPublishedRole.body(), "/data/version"),
                itemId(dataScopes.body().at("/data/items"), "kind", "ALL"),
                List.of("system.runtime.access", "system.workbench.view", "module.runtime_rule.view",
                        "module.runtime_rule.create", "module.runtime_rule.update", "module.runtime_rule.delete"));
        assertOk(allowedPublishedRole);
        assertError(ordinary.get(runtimeRoot + "/record-schema"), 401, "AUTHZ_SNAPSHOT_STALE");
        assertOk(ordinary.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var schema = ordinary.get(runtimeRoot + "/record-schema");
        assertOk(schema);
        assertPublishedContract(schema.body(), publishedVersionId);
        var listPageContract = item(schema.body().at("/data/publishedRuntime/pages"), "type", "LIST");
        assertThat(text(listPageContract, "/density")).isEqualTo("COMPACT");
        assertThat(listPageContract.at("/pageSize").asInt()).isEqualTo(20);
        assertThat(listPageContract.at("/fields/0/fieldCode").asText()).isEqualTo("status");
        assertThat(listPageContract.at("/fields/0/fixed").asText()).isEqualTo("LEFT");
        assertThat(listPageContract.at("/fields/0/width").asInt()).isEqualTo(180);
        var formContract = item(schema.body().at("/data/publishedRuntime/pages"), "type", "FORM");
        assertThat(formContract.at("/sections/0/collapsible").asBoolean()).isTrue();
        assertThat(formContract.at("/fields/0/gridSpan").asInt()).isEqualTo(12);
        assertThat(formContract.at("/fields/1/gridColumn").asInt()).isEqualTo(12);
        var detailContract = item(schema.body().at("/data/publishedRuntime/pages"), "type", "DETAIL");
        assertThat(detailContract.at("/sections/0/collapsed").asBoolean()).isTrue();

        var schemaVersionId = text(schema.body(), "/data/schemaVersionId");
        var requiredDraft = createRecord(ordinary, runtimeRoot, schemaVersionId,
                "required rule", Map.of("status", "NEEDS_REASON"));
        assertCreated(requiredDraft);
        var requiredId = text(requiredDraft.body(), "/data/recordId");
        assertError(ordinary.postWithCsrf(runtimeRoot + "/records/" + requiredId + ":activate",
                json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", key())),
                422, "RECORD_VALIDATION_FAILED");
        var requiredUpdated = updateRecord(ordinary, runtimeRoot, requiredId, schemaVersionId, 0,
                Map.of("reason", "now complete"));
        assertOk(requiredUpdated);
        var requiredActive = ordinary.postWithCsrf(runtimeRoot + "/records/" + requiredId + ":activate",
                json(Map.of("expectedVersion", 1)), Map.of("Idempotency-Key", key()));
        assertOk(requiredActive);

        var hiddenWrite = createRecord(ordinary, runtimeRoot, schemaVersionId,
                "hidden write", Map.of("status", "HIDE", "reason", "must be rejected"));
        assertError(hiddenWrite, 403, "RECORD_FIELD_FORBIDDEN");

        var lockedDraft = createRecord(ordinary, runtimeRoot, schemaVersionId,
                "locked record", Map.of("status", "OTHER", "reason", "immutable reason"));
        assertCreated(lockedDraft);
        var lockedId = text(lockedDraft.body(), "/data/recordId");
        var lockedUpdate = updateRecord(ordinary, runtimeRoot, lockedId, schemaVersionId, 0,
                Map.of("status", "LOCKED"));
        assertOk(lockedUpdate);
        var lockedDetail = ordinary.get(runtimeRoot + "/records/" + lockedId);
        assertOk(lockedDetail);
        assertThat(lockedDetail.body().at("/data/actions").toString()).doesNotContain("TRASH");
        assertError(updateRecord(ordinary, runtimeRoot, lockedId, schemaVersionId, 1,
                Map.of("reason", "forbidden change")), 403, "RECORD_FIELD_FORBIDDEN");
        assertError(ordinary.postWithCsrf(runtimeRoot + "/records/" + lockedId + ":discard",
                json(Map.of("expectedVersion", 1)), Map.of("Idempotency-Key", key())),
                403, "RUNTIME_RULE_DENIED");
        assertError(ordinary.postWithCsrf(runtimeRoot + "/records:batch-trash", json(Map.of(
                "items", List.of(Map.of("recordId", lockedId, "expectedVersion", 1))
        )), Map.of("Idempotency-Key", key())), 403, "RUNTIME_RULE_DENIED");

        var noEditDraft = createRecord(ordinary, runtimeRoot, schemaVersionId,
                "action denied", Map.of("status", "NO_EDIT"));
        assertCreated(noEditDraft);
        var noEditId = text(noEditDraft.body(), "/data/recordId");
        var noEditActive = ordinary.postWithCsrf(runtimeRoot + "/records/" + noEditId + ":activate",
                json(Map.of("expectedVersion", 0)), Map.of("Idempotency-Key", key()));
        assertOk(noEditActive);
        assertError(updateRecord(ordinary, runtimeRoot, noEditId,
                schemaVersionId, 1, Map.of("reason", "blocked by published action rule")),
                403, "RUNTIME_RULE_DENIED");
        assertError(ordinary.postWithCsrf(runtimeRoot + "/records:batch-edit", json(Map.of(
                "items", List.of(Map.of(
                        "recordId", noEditId, "expectedVersion", 1)),
                "changes", List.of(Map.of(
                        "fieldCode", "reason", "operation", "SET", "value", "also blocked"))
        )), Map.of("Idempotency-Key", key())), 403, "RUNTIME_RULE_DENIED");

        var approvalDraft = createRecord(ordinary, runtimeRoot, schemaVersionId,
                "approval", Map.of("status", "NEEDS_APPROVAL"));
        assertCreated(approvalDraft);
        var approvalDetail = ordinary.get(runtimeRoot + "/records/" + text(approvalDraft.body(), "/data/recordId"));
        assertOk(approvalDetail);
        assertThat(approvalDetail.body().at("/data/actions").toString()).contains("START_APPROVAL");

        var hiddenByEdit = updateRecord(ordinary, runtimeRoot, requiredId, schemaVersionId,
                longValue(requiredActive.body(), "/data/version"), Map.of("status", "HIDE"));
        assertOk(hiddenByEdit);
        var hiddenDetail = ordinary.get(runtimeRoot + "/records/" + requiredId);
        assertOk(hiddenDetail);
        assertThat(hiddenDetail.body().at("/data/values").toString()).doesNotContain("reason");

        var list = ordinary.get(runtimeRoot + "/records?page=1&size=20");
        assertOk(list);
        assertThat(list.body().at("/data/rows")).isNotEmpty();
        for (var row : list.body().at("/data/rows")) {
            assertThat(row.at("/values/0/fieldCode").asText()).isEqualTo("status");
            if (row.at("/values").size() > 1) {
                assertThat(row.at("/values/1/fieldCode").asText()).isEqualTo("reason");
            }
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM un_module_config_version WHERE id=? AND system_id=?",
                Integer.class, Long.parseLong(publishedVersionId), Long.parseLong(systemId))).isEqualTo(1);

        restartEvidence = new RestartEvidence(systemId, memberUsername, publishedVersionId, requiredId);
    }

    @Test
    @Order(2)
    void ordinaryMemberReadsTheSamePublishedPageRuleVersionAfterSpringRestart() throws Exception {
        var evidence = restartEvidence;
        assertThat(evidence).as("ordered publication journey must persist restart evidence").isNotNull();
        var ordinary = login(evidence.memberUsername(), MEMBER_PASSWORD);
        assertOk(ordinary.postWithCsrf("/api/v1/context/systems/" + evidence.systemId() + ":switch", "{}", Map.of()));
        var runtimeRoot = "/api/v1/systems/" + evidence.systemId() + "/runtime/modules/runtime_rule";
        var schema = ordinary.get(runtimeRoot + "/record-schema");
        assertOk(schema);
        assertPublishedContract(schema.body(), evidence.publishedVersionId());
        var detail = ordinary.get(runtimeRoot + "/records/" + evidence.recordId());
        assertOk(detail);
        assertThat(text(detail.body(), "/data/schemaVersionId")).isEqualTo(evidence.publishedVersionId());
        assertThat(detail.body().at("/data/values").toString()).contains("status").doesNotContain("reason");
    }

    private TestResponse createField(TestClient client, String configRoot, String moduleId,
                                     String code, String name, int sortOrder, boolean required,
                                     boolean showInList, boolean showInDetail, int width) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("dictionaryId", null);
        body.put("targetModuleId", null);
        body.put("code", code);
        body.put("name", name);
        body.put("type", "TEXT");
        body.put("sortOrder", sortOrder);
        body.put("required", required);
        body.put("hidden", false);
        body.put("readonly", false);
        body.put("searchable", true);
        body.put("filterable", true);
        body.put("showInList", showInList);
        body.put("showInDetail", showInDetail);
        body.put("indexMode", "NONE");
        body.put("status", "ENABLED");
        body.put("properties", Map.of("maxLength", 200, "width", width));
        body.put("draftRevision", revision(client, configRoot));
        var response = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/fields", json(body),
                Map.of("Idempotency-Key", key()));
        assertOk(response);
        return response;
    }

    private JsonNode updatePage(TestClient client, String configRoot, String moduleId,
                                JsonNode page, Map<String, Object> layout) throws Exception {
        var response = client.putWithCsrf(configRoot + "/modules/" + moduleId + "/pages/" + page.path("id").asText(),
                json(Map.of(
                        "code", page.path("code").asText(), "name", page.path("name").asText(),
                        "type", page.path("type").asText(), "isDefault", page.path("isDefault").asBoolean(),
                        "status", page.path("status").asText(), "layout", layout,
                        "version", page.path("version").asText(), "draftRevision", revision(client, configRoot)
                )), Map.of());
        assertOk(response);
        return response.body().at("/data");
    }

    private TestResponse createComponent(TestClient client, String configRoot, String componentRoot,
                                         String parentId, String fieldId, String componentKey, String type,
                                         int sortOrder, int row, int column, int span,
                                         Map<String, Object> properties) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("parentComponentId", parentId);
        body.put("fieldId", fieldId);
        body.put("key", componentKey);
        body.put("type", type);
        body.put("sortOrder", sortOrder);
        body.put("gridRow", row);
        body.put("gridColumn", column);
        body.put("gridSpan", span);
        body.put("properties", properties);
        body.put("draftRevision", revision(client, configRoot));
        var response = client.postWithCsrf(componentRoot, json(body), Map.of("Idempotency-Key", key()));
        assertOk(response);
        return response;
    }

    private void createRule(TestClient client, String configRoot, String moduleId,
                            String code, String type, int priority, String conditionFieldId,
                            String operator, String conditionValue, String effect, String targetId,
                            boolean effectValue) throws Exception {
        var condition = Map.of("fieldId", conditionFieldId, "operator", operator,
                "value", conditionValue, "children", List.of());
        var effectBody = new LinkedHashMap<String, Object>();
        effectBody.put("effect", effect);
        if (targetId != null) effectBody.put("targetId", targetId);
        effectBody.put("value", effectValue);
        var response = client.postWithCsrf(configRoot + "/modules/" + moduleId + "/rules", json(Map.of(
                "code", code, "name", code, "type", type, "priority", priority,
                "condition", condition, "effects", List.of(effectBody), "status", "ENABLED",
                "draftRevision", revision(client, configRoot)
        )), Map.of("Idempotency-Key", key()));
        assertOk(response);
    }

    private TestResponse publishRole(TestClient client, String systemId, String roleId, String version,
                                     String dataScopeId, List<String> permissionCodes) throws Exception {
        var root = "/api/v1/systems/" + systemId + "/admin/roles/" + roleId;
        var draft = client.putWithCsrf(root + "/draft", json(Map.of(
                "name", "Page Rule Member", "description", "runtime published page-rule member",
                "permissionCodes", permissionCodes, "deniedPermissionCodes", List.of(),
                "dataScopeId", dataScopeId, "version", version
        )), Map.of());
        assertOk(draft);
        var checked = client.postWithCsrf(root + "/draft:check",
                json(Map.of("version", text(draft.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(checked);
        var published = client.postWithCsrf(root + "/draft:publish",
                json(Map.of("version", text(checked.body(), "/data/version"))),
                Map.of("Idempotency-Key", key()));
        assertOk(published);
        return published;
    }

    private TestResponse createRecord(TestClient client, String runtimeRoot, String schemaVersionId,
                                      String title, Map<String, String> values) throws Exception {
        return client.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", schemaVersionId, "title", title, "values", values
        )), Map.of("Idempotency-Key", key()));
    }

    private TestResponse updateRecord(TestClient client, String runtimeRoot, String recordId,
                                      String schemaVersionId, long expectedVersion,
                                      Map<String, String> values) throws Exception {
        return client.putWithCsrf(runtimeRoot + "/records/" + recordId, json(Map.of(
                "schemaVersionId", schemaVersionId, "title", "updated", "expectedVersion", expectedVersion,
                "values", values
        )), Map.of("Idempotency-Key", key()));
    }

    private static void assertPublishedContract(JsonNode body, String versionId) {
        assertThat(text(body, "/data/schemaVersionId")).isEqualTo(versionId);
        assertThat(text(body, "/data/publishedRuntime/schemaVersionId")).isEqualTo(versionId);
        assertThat(body.at("/data/publishedRuntime/pages")).hasSize(3);
        assertThat(body.at("/data/publishedRuntime/rules")).hasSize(6);
    }

    private String revision(TestClient client, String configRoot) throws Exception {
        var root = client.get(configRoot);
        assertOk(root);
        return text(root.body(), "/data/draftRevision");
    }

    private static String componentRoot(String configRoot, String moduleId, JsonNode page) {
        return configRoot + "/modules/" + moduleId + "/pages/" + page.path("id").asText() + "/components";
    }

    private TestClient login(String username, String password) throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", username, "password", password)), Map.of()));
        return client;
    }

    private static JsonNode item(JsonNode items, String field, String value) {
        for (var item : items) {
            if (value.equals(item.path(field).asText())) return item;
        }
        throw new AssertionError("No item with " + field + "=" + value + " in " + items);
    }

    private static String itemId(JsonNode items, String field, String value) {
        return item(items, field, value).path("id").asText();
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status()).withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status()).withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status()).withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
    }

    private static long longValue(JsonNode node, String pointer) {
        return node.at(pointer).asLong();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static Path migrationRoot() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate sql/migration from test process");
    }

    private record RestartEvidence(String systemId, String memberUsername,
                                   String publishedVersionId, String recordId) { }

    private record TestResponse(int status, JsonNode body) { }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl(path))).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(String path, String body, Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        private TestResponse request(String method, String path, String body,
                                     Map<String, String> headers, boolean csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl(path)))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) builder.header("X-CSRF-Token", csrf());
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder) throws IOException, InterruptedException {
            var response = client.send(builder.header("X-Request-ID", "page-rule-test-" + key()).build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue).findFirst()
                    .orElseThrow(() -> new IllegalStateException("CSRF cookie is missing"));
        }
    }

    private String baseUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
