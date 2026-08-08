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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowExtensionJourneyIntegrationTest {
    private static final String ROOT_USERNAME = "flow_extension_root";
    private static final String ROOT_PASSWORD = "Flow-Extension-Root-Test-84!";
    private static final String MEMBER_PASSWORD = "Flow-Extension-Member-Test-42!";
    private static final String MODULE_CODE = "flow_case";
    private static volatile RestartEvidence restartEvidence;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0.44"))
            .withDatabaseName("examine2_flow_extension_test")
            .withUsername("examine_flow_extension_test")
            .withPassword("container-test-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
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
        registry.add("examine.bootstrap.root.display-name",
                () -> "Flow Extension Test Root");
        registry.add("examine.flow.periodic.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.deadline.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.webhook.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.subflow.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.flow.compensation.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.runtime.draft-expiry.initial-delay-ms", () -> 3_600_000);
        registry.add("examine.runtime.reference-recalculation.initial-delay-ms",
                () -> 3_600_000);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void publishesGraphAndPersistsPolicyBoundFormAndWaitingNodeOverHttp()
            throws Exception {
        var admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf("/api/v1/context/platform:switch", "{}", Map.of()));
        var system = admin.postWithCsrf("/api/v1/platform/admin/systems", json(Map.of(
                "code", "flow_extension_" + Long.toUnsignedString(System.nanoTime(), 36),
                "name", "Flow Extension Journey",
                "description", "published graph, business form and waiting node closure",
                "tenantMode", "SINGLE"
        )), Map.of("Idempotency-Key", key()));
        assertOk(system);
        var systemId = text(system.body(), "/data/id");
        var systemSwitch = admin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(systemSwitch);
        var tenantId = text(systemSwitch.body(), "/data/context/tenantId");
        var configRoot = "/api/v1/systems/" + systemId + "/admin/config";
        var runtimeRoot = "/api/v1/systems/" + systemId
                + "/runtime/modules/" + MODULE_CODE;
        var flowRoot = "/api/v1/systems/" + systemId + "/flow";

        var group = admin.postWithCsrf(configRoot + "/module-groups", json(Map.of(
                "code", "flow_operations", "name", "Flow Operations",
                "description", "", "iconKey", "workflow", "sortOrder", 0,
                "status", "ENABLED", "draftRevision", revision(admin, configRoot)
        )), Map.of("Idempotency-Key", key()));
        assertOk(group);
        var module = admin.postWithCsrf(configRoot + "/modules", json(Map.of(
                "groupId", text(group.body(), "/data/id"), "code", MODULE_CODE,
                "name", "Flow Case", "description", "Flow node business form record",
                "iconKey", "clipboard", "sortOrder", 0, "status", "ENABLED",
                "allowComments", false, "allowTeam", false,
                "draftRevision", revision(admin, configRoot)
        )), Map.of("Idempotency-Key", key()));
        assertOk(module);
        var moduleId = text(module.body(), "/data/id");
        createTextField(admin, configRoot, moduleId, "required_note", "Required note", 0);
        createTextField(admin, configRoot, moduleId, "editable_amount", "Editable amount", 1);
        createTextField(admin, configRoot, moduleId, "locked_note", "Locked note", 2);
        createTextField(admin, configRoot, moduleId, "secret_note", "Secret note", 3);
        var publishedConfigVersion = publishConfig(admin, configRoot);
        assertOk(admin.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var record = admin.postWithCsrf(runtimeRoot + "/records", json(Map.of(
                "schemaVersionId", publishedConfigVersion,
                "title", "Flow extension case",
                "values", Map.of(
                        "required_note", "initial requirement",
                        "editable_amount", "10",
                        "locked_note", "immutable through Flow",
                        "secret_note", "classified"
                )
        )), Map.of("Idempotency-Key", key()));
        assertCreated(record);
        var recordId = text(record.body(), "/data/recordId");
        var activatedRecord = admin.postWithCsrf(
                runtimeRoot + "/records/" + recordId + ":activate",
                json(Map.of("expectedVersion", 0)),
                Map.of("Idempotency-Key", key()));
        assertOk(activatedRecord);
        assertThat(text(activatedRecord.body(), "/data/status")).isEqualTo("ACTIVE");
        assertThat(activatedRecord.body().at("/data/version").asLong()).isOne();

        var memberUsername = "flow_extension_member_"
                + Long.toUnsignedString(System.nanoTime(), 36);
        var registration = new TestClient().post("/api/v1/auth/register", json(Map.of(
                "username", memberUsername, "displayName", "Flow Extension Member",
                "password", MEMBER_PASSWORD, "systemName", "Flow Extension Member Home",
                "systemCode", "flow_extension_home_"
                        + Long.toUnsignedString(System.nanoTime(), 36)
        )), Map.of("Idempotency-Key", key()));
        assertOk(registration);

        var scopes = admin.get("/api/v1/systems/" + systemId
                + "/admin/data-scopes?size=200");
        assertOk(scopes);
        var role = admin.postWithCsrf("/api/v1/systems/" + systemId + "/admin/roles",
                json(Map.of(
                        "code", "flow_extension_member", "name", "Flow Extension Member",
                        "description", "business form member"
                )), Map.of("Idempotency-Key", key()));
        assertOk(role);
        var roleId = text(role.body(), "/data/id");
        var deniedRole = publishRole(admin, systemId, roleId,
                text(role.body(), "/data/version"),
                itemId(scopes.body().at("/data/items"), "kind", "ALL"),
                runtimePermissions());

        var member = login(memberUsername, MEMBER_PASSWORD);
        var access = member.postWithCsrf(
                "/api/v1/context/systems/" + systemId + "/access-requests",
                json(Map.of("targetTenantId", tenantId,
                        "reason", "Flow extension real journey")),
                Map.of("Idempotency-Key", key()));
        assertOk(access);
        admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var approvedAccess = admin.postWithCsrf(
                "/api/v1/systems/" + systemId + "/admin/access-requests/"
                        + text(access.body(), "/data/id") + ":approve",
                json(Map.of(
                        "reason", "approve Flow extension journey",
                        "version", text(access.body(), "/data/version"),
                        "tenantIds", List.of(tenantId), "roleIds", List.of(roleId)
                )), Map.of("Idempotency-Key", key()));
        assertOk(approvedAccess);
        var memberSwitch = member.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of());
        assertOk(memberSwitch);
        var memberId = text(memberSwitch.body(), "/data/context/memberId");
        assertOk(admin.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var catalog = admin.get(flowRoot + "/node-catalog");
        assertOk(catalog);
        assertThat(catalog.body().at("/data")).hasSize(22);
        assertThat(catalog.body().at("/data"))
                .extracting(node -> node.path("type").asText())
                .contains("FORM", "WAIT", "AI_ASSIST", "END");
        assertError(member.get(flowRoot + "/node-catalog"),
                403, "PERMISSION_DENIED");

        var definition = admin.postWithCsrf(flowRoot + "/definitions", json(Map.of(
                "name", "Flow extension form approval", "approverId", memberId
        )), Map.of());
        assertCreated(definition);
        var definitionId = text(definition.body(), "/data/definitionId");
        var extensionPath = flowRoot + "/definitions/" + definitionId
                + "/extension-draft";
        assertError(member.get(extensionPath), 403, "PERMISSION_DENIED");
        assertError(admin.get(flowRoot + "/definitions/9223372036854775806/extension-draft"),
                404, "FLOW_DRAFT_NOT_FOUND");
        var emptyExtension = admin.get(extensionPath);
        assertOk(emptyExtension);
        assertThat(emptyExtension.body().at("/data").isMissingNode()).isTrue();
        var graph = graph();
        var savedGraph = admin.putWithCsrf(
                extensionPath,
                json(Map.of("expectedRevision", 1, "graph", graph)), Map.of());
        assertOk(savedGraph);
        assertThat(savedGraph.body().at("/data/sourceRevision").asInt()).isOne();
        assertThat(savedGraph.body().at("/data/graph/nodes")).hasSize(4);
        assertThat(text(savedGraph.body(), "/data/checksum")).hasSize(64);

        var draftGraph = admin.get(extensionPath);
        assertOk(draftGraph);
        assertThat(text(draftGraph.body(), "/data/checksum"))
                .isEqualTo(text(savedGraph.body(), "/data/checksum"));
        var impact = admin.get(
                flowRoot + "/definitions/" + definitionId + "/publish-impact");
        assertOk(impact);
        assertThat(impact.body().at("/data/ready").asBoolean()).isTrue();
        assertThat(impact.body().at("/data/issues")).isEmpty();
        var checked = admin.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/draft:check",
                "{}", Map.of("Idempotency-Key", key()));
        assertOk(checked);
        assertThat(text(checked.body(), "/data/verdict")).isEqualTo("READY");
        var published = admin.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + ":publish", "{}", Map.of());
        assertOk(published);
        assertThat(published.body().at("/data/version").asInt()).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM un_flow_definition_extension_version
                 WHERE system_id=? AND tenant_id=? AND definition_id=?
                   AND definition_version=1
                """, Integer.class, Long.parseLong(systemId), Long.parseLong(tenantId),
                Long.parseLong(definitionId))).isOne();

        var started = admin.postWithCsrf(
                flowRoot + "/definitions/" + definitionId + "/instances",
                json(Map.of(
                        "definitionVersion", 1,
                        "businessKey", "flow-extension-" + key(),
                        "recordBinding", Map.of(
                                "moduleCode", MODULE_CODE, "recordId", recordId)
                )), Map.of("Idempotency-Key", key()));
        assertCreated(started);
        var instanceId = text(started.body(), "/data/instanceId");
        var formPath = flowRoot + "/instances/" + instanceId
                + "/nodes/member_form/form";
        assertError(member.get(formPath), 403, "PERMISSION_DENIED");

        admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf(
                "/api/v1/context/systems/" + systemId + ":switch", "{}", Map.of()));
        var allowedPermissions = new java.util.ArrayList<>(runtimePermissions());
        allowedPermissions.add("flow.instance.read");
        allowedPermissions.add("flow.instance.decide");
        publishRole(admin, systemId, roleId,
                text(deniedRole.body(), "/data/version"),
                itemId(scopes.body().at("/data/items"), "kind", "ALL"),
                allowedPermissions);
        assertError(member.get(formPath), 401, "AUTHZ_SNAPSHOT_STALE");
        assertOk(member.postWithCsrf("/api/v1/auth/refresh", "{}", Map.of()));

        var form = member.get(formPath);
        assertOk(form);
        assertThat(text(form.body(), "/data/moduleCode")).isEqualTo(MODULE_CODE);
        assertThat(text(form.body(), "/data/recordId")).isEqualTo(recordId);
        assertThat(form.body().at("/data/recordVersion").asLong()).isOne();
        assertThat(form.body().at("/data/snapshotVersion").asLong()).isZero();
        assertThat(form.body().at("/data/fields"))
                .extracting(field -> field.path("fieldCode").asText())
                .containsExactly("required_note", "editable_amount", "locked_note")
                .doesNotContain("secret_note");
        assertThat(item(form.body().at("/data/fields"), "fieldCode", "required_note")
                .path("required").asBoolean()).isTrue();
        assertThat(item(form.body().at("/data/fields"), "fieldCode", "editable_amount")
                .path("editable").asBoolean()).isTrue();
        assertThat(item(form.body().at("/data/fields"), "fieldCode", "locked_note")
                .path("editable").asBoolean()).isFalse();

        assertError(member.putWithCsrf(formPath, json(Map.of(
                "expectedSnapshotVersion", 0, "expectedRecordVersion", 1,
                "values", Map.of()
        )), Map.of("Idempotency-Key", key())),
                422, "FLOW_FORM_CHANGES_REQUIRED");
        assertError(member.putWithCsrf(formPath, json(Map.of(
                "expectedSnapshotVersion", 0, "expectedRecordVersion", 1,
                "values", Map.of("secret_note", "attempted leak")
        )), Map.of("Idempotency-Key", key())),
                403, "FLOW_FORM_FIELD_WRITE_FORBIDDEN");
        var requiredMissing = new LinkedHashMap<String, Object>();
        requiredMissing.put("expectedSnapshotVersion", 0);
        requiredMissing.put("expectedRecordVersion", 1);
        requiredMissing.put("values", Map.of(
                "required_note", objectMapper.getNodeFactory().nullNode()));
        assertError(member.putWithCsrf(formPath, json(requiredMissing),
                Map.of("Idempotency-Key", key())),
                422, "FLOW_FORM_REQUIRED_FIELD_MISSING");

        var written = member.putWithCsrf(formPath, json(Map.of(
                "expectedSnapshotVersion", 0, "expectedRecordVersion", 1,
                "values", Map.of(
                        "required_note", "member confirmed",
                        "editable_amount", "25"
                )
        )), Map.of("Idempotency-Key", "flow-form-write-" + key()));
        assertOk(written);
        assertThat(written.body().at("/data/form/recordVersion").asLong()).isEqualTo(2);
        assertThat(written.body().at("/data/form/snapshotVersion").asLong()).isOne();
        assertThat(written.body().at("/data/history/sequence").asInt()).isOne();
        assertThat(text(written.body(), "/data/history/actorId")).isEqualTo(memberId);

        var formHistory = member.get(formPath + "/history");
        assertOk(formHistory);
        assertThat(formHistory.body().at("/data")).singleElement()
                .satisfies(history -> {
                    assertThat(history.path("sequence").asInt()).isOne();
                    assertThat(history.path("recordVersionBefore").asLong()).isOne();
                    assertThat(history.path("recordVersionAfter").asLong()).isEqualTo(2);
                    assertThat(history.at("/changes/required_note").asText())
                            .isEqualTo("member confirmed");
                });
        var detail = member.get(runtimeRoot + "/records/" + recordId);
        assertOk(detail);
        assertThat(value(detail.body().at("/data/values"), "required_note"))
                .isEqualTo("member confirmed");
        assertThat(value(detail.body().at("/data/values"), "editable_amount"))
                .isEqualTo("25");
        assertThat(value(detail.body().at("/data/values"), "locked_note"))
                .isEqualTo("immutable through Flow");
        assertThat(value(detail.body().at("/data/values"), "secret_note"))
                .isEqualTo("classified");

        var waitPath = flowRoot + "/instances/" + instanceId + "/nodes/wait_event";
        var executed = member.postWithCsrf(waitPath + ":execute", json(Map.of(
                "expectedVersion", 0, "input", Map.of("source", "real-http")
        )), Map.of("Idempotency-Key", "flow-wait-execute-" + key()));
        assertOk(executed);
        assertThat(text(executed.body(), "/data/status")).isEqualTo("WAITING_EVENT");
        assertThat(executed.body().at("/data/version").asLong()).isZero();
        assertThat(text(executed.body(), "/data/result/eventKey"))
                .isEqualTo("flow_case.reviewed");
        var resumed = member.postWithCsrf(waitPath + ":resume", json(Map.of(
                "expectedVersion", 0,
                "input", Map.of("eventKey", "flow_case.reviewed", "payload", "accepted")
        )), Map.of("Idempotency-Key", "flow-wait-resume-" + key()));
        assertOk(resumed);
        assertThat(text(resumed.body(), "/data/status")).isEqualTo("CONTINUED");
        assertThat(resumed.body().at("/data/version").asLong()).isOne();
        var executionHistory = member.get(waitPath + "/execution-history");
        assertOk(executionHistory);
        assertThat(executionHistory.body().at("/data")).hasSize(2);
        assertThat(text(executionHistory.body(), "/data/0/toStatus"))
                .isEqualTo("WAITING_EVENT");
        assertThat(text(executionHistory.body(), "/data/1/fromStatus"))
                .isEqualTo("WAITING_EVENT");
        assertThat(text(executionHistory.body(), "/data/1/toStatus"))
                .isEqualTo("CONTINUED");
        assertThat(executionHistory.body().at("/data/1/sequence").asInt()).isEqualTo(2);

        restartEvidence = new RestartEvidence(
                systemId, tenantId, memberUsername, definitionId, instanceId,
                recordId, text(savedGraph.body(), "/data/checksum"));
    }

    @Test
    @Order(2)
    void readsPublishedGraphAndHistoriesAfterSpringRestart() throws Exception {
        var evidence = restartEvidence;
        assertThat(evidence)
                .as("ordered Flow extension journey must persist restart evidence")
                .isNotNull();

        var admin = login(ROOT_USERNAME, ROOT_PASSWORD);
        assertOk(admin.postWithCsrf(
                "/api/v1/context/systems/" + evidence.systemId() + ":switch",
                "{}", Map.of()));
        var flowRoot = "/api/v1/systems/" + evidence.systemId() + "/flow";
        var draft = admin.get(flowRoot + "/definitions/" + evidence.definitionId()
                + "/extension-draft");
        assertOk(draft);
        assertThat(text(draft.body(), "/data/checksum"))
                .isEqualTo(evidence.graphChecksum());
        assertThat(draft.body().at("/data/graph/nodes"))
                .extracting(node -> node.path("code").asText())
                .containsExactly("start", "member_form", "wait_event", "end");
        var versions = admin.get(flowRoot + "/definitions/" + evidence.definitionId()
                + "/versions?page=1&size=20");
        assertOk(versions);
        assertThat(versions.body().at("/data/items")).singleElement()
                .satisfies(version -> assertThat(version.path("version").asInt()).isOne());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT graph_checksum
                  FROM un_flow_definition_extension_version
                 WHERE system_id=? AND tenant_id=? AND definition_id=?
                   AND definition_version=1
                """, String.class, Long.parseLong(evidence.systemId()),
                Long.parseLong(evidence.tenantId()),
                Long.parseLong(evidence.definitionId())))
                .isEqualTo(evidence.graphChecksum());

        var member = login(evidence.memberUsername(), MEMBER_PASSWORD);
        assertOk(member.postWithCsrf(
                "/api/v1/context/systems/" + evidence.systemId() + ":switch",
                "{}", Map.of()));
        var formPath = flowRoot + "/instances/" + evidence.instanceId()
                + "/nodes/member_form/form";
        var form = member.get(formPath);
        assertOk(form);
        assertThat(form.body().at("/data/recordVersion").asLong()).isEqualTo(2);
        assertThat(form.body().at("/data/snapshotVersion").asLong()).isOne();
        assertThat(item(form.body().at("/data/fields"), "fieldCode", "required_note")
                .path("value").asText()).isEqualTo("member confirmed");
        var formHistory = member.get(formPath + "/history");
        assertOk(formHistory);
        assertThat(formHistory.body().at("/data")).singleElement()
                .satisfies(history -> {
                    assertThat(history.path("sequence").asInt()).isOne();
                    assertThat(history.path("recordVersionAfter").asLong()).isEqualTo(2);
                });
        var executionHistory = member.get(flowRoot + "/instances/"
                + evidence.instanceId() + "/nodes/wait_event/execution-history");
        assertOk(executionHistory);
        assertThat(executionHistory.body().at("/data"))
                .extracting(event -> event.path("toStatus").asText())
                .containsExactly("WAITING_EVENT", "CONTINUED");
        var detail = member.get("/api/v1/systems/" + evidence.systemId()
                + "/runtime/modules/" + MODULE_CODE + "/records/" + evidence.recordId());
        assertOk(detail);
        assertThat(detail.body().at("/data/version").asLong()).isEqualTo(2);
        assertThat(value(detail.body().at("/data/values"), "required_note"))
                .isEqualTo("member confirmed");
    }

    private void createTextField(
            TestClient client,
            String configRoot,
            String moduleId,
            String code,
            String name,
            int sortOrder) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("dictionaryId", null);
        body.put("targetModuleId", null);
        body.put("code", code);
        body.put("name", name);
        body.put("type", "TEXT");
        body.put("sortOrder", sortOrder);
        body.put("required", false);
        body.put("hidden", false);
        body.put("readonly", false);
        body.put("searchable", true);
        body.put("filterable", true);
        body.put("showInList", true);
        body.put("showInDetail", true);
        body.put("indexMode", "NONE");
        body.put("status", "ENABLED");
        body.put("properties", Map.of("maxLength", 200));
        body.put("draftRevision", revision(client, configRoot));
        var response = client.postWithCsrf(configRoot + "/modules/" + moduleId
                        + "/fields", json(body),
                Map.of("Idempotency-Key", key()));
        assertOk(response);
    }

    private String publishConfig(TestClient client, String configRoot) throws Exception {
        var checked = client.postWithCsrf(configRoot + "/checks",
                json(Map.of("draftRevision", revision(client, configRoot))), Map.of());
        assertOk(checked);
        assertThat(text(checked.body(), "/data/status")).isEqualTo("PASSED");
        var root = client.get(configRoot);
        assertOk(root);
        var published = client.postWithCsrf(configRoot + ":publish", json(Map.of(
                "checkId", text(checked.body(), "/data/id"),
                "draftRevision", text(root.body(), "/data/draftRevision"),
                "configRootVersion", text(root.body(), "/data/version"),
                "reason", "Flow extension real HTTP closure"
        )), Map.of("Idempotency-Key", key()));
        assertOk(published);
        return text(published.body(), "/data/version/id");
    }

    private TestResponse publishRole(
            TestClient client,
            String systemId,
            String roleId,
            String version,
            String dataScopeId,
            List<String> permissions) throws Exception {
        var root = "/api/v1/systems/" + systemId + "/admin/roles/" + roleId;
        var draft = client.putWithCsrf(root + "/draft", json(Map.of(
                "name", "Flow Extension Member",
                "description", "published business form member",
                "permissionCodes", permissions,
                "deniedPermissionCodes", List.of(),
                "dataScopeId", dataScopeId,
                "version", version
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

    private Map<String, Object> graph() {
        return Map.of(
                "nodes", List.of(
                        Map.of(
                                "code", "start", "name", "Start", "type", "START",
                                "applicationCode", "core", "config", Map.of(),
                                "fieldPolicies", List.of(), "next", List.of("member_form")
                        ),
                        Map.of(
                                "code", "member_form", "name", "Member form",
                                "type", "FORM", "applicationCode", "core",
                                "moduleCode", MODULE_CODE,
                                "config", Map.of("formAction", "UPDATE"),
                                "fieldPolicies", List.of(
                                        Map.of("fieldCode", "required_note", "mode", "REQUIRED"),
                                        Map.of("fieldCode", "editable_amount", "mode", "EDITABLE"),
                                        Map.of("fieldCode", "locked_note", "mode", "VISIBLE"),
                                        Map.of("fieldCode", "secret_note", "mode", "HIDDEN")
                                ),
                                "next", List.of("wait_event")
                        ),
                        Map.of(
                                "code", "wait_event", "name", "Wait for event",
                                "type", "WAIT", "applicationCode", "core",
                                "config", Map.of("eventKey", "flow_case.reviewed"),
                                "fieldPolicies", List.of(), "next", List.of("end")
                        ),
                        Map.of(
                                "code", "end", "name", "End", "type", "END",
                                "applicationCode", "core", "config", Map.of(),
                                "fieldPolicies", List.of(), "next", List.of()
                        )
                ),
                "dependencies", List.of()
        );
    }

    private static List<String> runtimePermissions() {
        return List.of(
                "system.runtime.access",
                "system.workbench.view",
                "module." + MODULE_CODE + ".view",
                "module." + MODULE_CODE + ".update"
        );
    }

    private String revision(TestClient client, String configRoot) throws Exception {
        var root = client.get(configRoot);
        assertOk(root);
        return text(root.body(), "/data/draftRevision");
    }

    private TestClient login(String username, String password) throws Exception {
        var client = new TestClient();
        assertOk(client.post("/api/v1/auth/login", json(Map.of(
                "account", username, "password", password)), Map.of()));
        return client;
    }

    private static JsonNode item(JsonNode items, String field, String expected) {
        for (var item : items) {
            if (expected.equals(item.path(field).asText())) return item;
        }
        throw new AssertionError("No item with " + field + "=" + expected + " in " + items);
    }

    private static String itemId(JsonNode items, String field, String expected) {
        return item(items, field, expected).path("id").asText();
    }

    private static String value(JsonNode values, String fieldCode) {
        return item(values, "fieldCode", fieldCode).path("value").asText();
    }

    private static void assertOk(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 200, got %s: %s", response.status(), response.body())
                .isEqualTo(200);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertCreated(TestResponse response) {
        assertThat(response.status())
                .withFailMessage("Expected 201, got %s: %s", response.status(), response.body())
                .isEqualTo(201);
        assertThat(text(response.body(), "/code")).isEqualTo("OK");
    }

    private static void assertError(TestResponse response, int status, String code) {
        assertThat(response.status())
                .withFailMessage("Expected %s, got %s: %s", status, response.status(), response.body())
                .isEqualTo(status);
        assertThat(text(response.body(), "/code")).isEqualTo(code);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String text(JsonNode node, String pointer) {
        return node.at(pointer).asText();
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

    private record RestartEvidence(
            String systemId,
            String tenantId,
            String memberUsername,
            String definitionId,
            String instanceId,
            String recordId,
            String graphChecksum) {
    }

    private record TestResponse(int status, JsonNode body) {
    }

    private final class TestClient {
        private final CookieManager cookies = new CookieManager(
                null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        TestResponse get(String path) throws Exception {
            return send(HttpRequest.newBuilder(URI.create(baseUrl(path))).GET());
        }

        TestResponse post(String path, String body, Map<String, String> headers)
                throws Exception {
            return request("POST", path, body, headers, false);
        }

        TestResponse postWithCsrf(
                String path,
                String body,
                Map<String, String> headers) throws Exception {
            return request("POST", path, body, headers, true);
        }

        TestResponse putWithCsrf(
                String path,
                String body,
                Map<String, String> headers) throws Exception {
            return request("PUT", path, body, headers, true);
        }

        private TestResponse request(
                String method,
                String path,
                String body,
                Map<String, String> headers,
                boolean csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(baseUrl(path)))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(builder::header);
            if (csrf) builder.header("X-CSRF-Token", csrf());
            return send(builder);
        }

        private TestResponse send(HttpRequest.Builder builder)
                throws IOException, InterruptedException {
            var response = client.send(builder
                            .header("X-Request-ID", "flow-extension-test-" + key())
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return new TestResponse(
                    response.statusCode(), objectMapper.readTree(response.body()));
        }

        private String csrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EXAMINE_CSRF".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "CSRF cookie is missing"));
        }
    }

    private String baseUrl(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
