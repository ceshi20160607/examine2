package com.unique.unexamine.flow.manage;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FlowAndWorkManagementHttpTest {
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
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.file.storage-root", () -> "target/test-file-storage");
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void flowDraftIsValidatedSimulatedAndPublishedAsImmutableScopedVersions() {
        Session owner = register("c32_flow_owner", "c32-flow-system", "c32-flow@example.com");
        Session outsider = register("c32_flow_outsider", "c32-flow-other", "c32-flow-other@example.com");

        ResponseEntity<Map> created = exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "contract_approval", "name", "合同审批", "description", "C32 Flow"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long flowId = number(data(created).get("id"));
        assertThat(data(created)).containsEntry("contextType", "SYSTEM")
                .containsEntry("systemId", (int) owner.systemId())
                .containsEntry("tenantId", (int) owner.tenantId())
                .containsEntry("draftRevision", 1)
                .containsEntry("version", 0);

        List<Map<String, Object>> invalidNodes = List.of(
                node("start", "START", "开始", Map.of()),
                node("approve", "APPROVAL", "审批", Map.of()),
                node("end", "END", "结束", Map.of()),
                node("orphan", "NOTIFICATION", "不可达通知", Map.of()));
        List<Map<String, Object>> invalidEdges = List.of(
                edge("e1", "start", "approve", 10),
                edge("e2", "approve", "end", 20));
        ResponseEntity<Map> invalidDraft = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT,
                owner.token(), Map.of("expectedVersion", 0, "nodes", invalidNodes, "edges", invalidEdges));
        assertThat(invalidDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(invalidDraft)).containsEntry("draftRevision", 2).containsEntry("version", 1);

        ResponseEntity<Map> invalidCheck = exchange("/api/flows/" + flowId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        assertThat(invalidCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(invalidCheck)).containsEntry("valid", false);
        assertThat(issueCodes(invalidCheck)).contains("APPROVER_MISSING", "UNREACHABLE_NODE", "DEAD_END_NODE");
        ResponseEntity<Map> blockedPublish = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 2, "changeSummary", "不应发布"));
        assertThat(blockedPublish.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(blockedPublish.getBody().get("code")).isEqualTo("FLOW_PUBLICATION_INVALID");
        assertThat(count("flow_version", "flow_id=" + flowId)).isZero();

        List<Map<String, Object>> validNodes = List.of(
                node("start", "START", "开始", Map.of()),
                Map.of("nodeKey", "approve", "nodeType", "APPROVAL", "name", "部门负责人审批",
                        "positionX", 200, "positionY", 0,
                        "assigneePolicy", Map.of("type", "ACCOUNT", "accountIds", List.of(owner.accountId())),
                        "formPolicy", Map.of("editable", List.of("amount")), "config", Map.of()),
                node("end", "END", "结束", Map.of()));
        ResponseEntity<Map> validDraft = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT,
                owner.token(), Map.of("expectedVersion", 1, "nodes", validNodes, "edges", invalidEdges));
        assertThat(validDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(validDraft)).containsEntry("draftRevision", 3).containsEntry("version", 2);
        assertThat(((List<?>) data(validDraft).get("nodes")).stream()
                .map(item -> String.valueOf(map(item).get("nodeType"))).toList())
                .containsExactly("START", "APPROVAL", "END");
        assertThat(data(exchange("/api/flows/" + flowId + "/publication-check", HttpMethod.GET,
                owner.token(), null))).containsEntry("valid", true);

        ResponseEntity<Map> simulated = exchange("/api/flows/" + flowId + "/simulate", HttpMethod.POST,
                owner.token(), Map.of("variables", Map.of("amount", 8000)));
        assertThat(simulated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(simulated)).containsEntry("successful", true).containsEntry("draftRevision", 3);
        assertThat(((List<?>) data(simulated).get("steps"))).hasSize(3);

        ResponseEntity<Map> firstPublish = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 3, "changeSummary", "首版合同审批",
                        "simulationVariables", Map.of("amount", 8000)));
        assertThat(firstPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstVersionId = number(data(firstPublish).get("versionId"));
        String firstHash = String.valueOf(data(firstPublish).get("definitionHash"));
        String firstSnapshot = jdbc.queryForObject("select snapshot_json from flow_version where id=?",
                String.class, firstVersionId);
        assertThat(firstHash).hasSize(64);

        List<Map<String, Object>> changedNodes = List.of(
                node("start", "START", "开始", Map.of()),
                Map.of("nodeKey", "approve", "nodeType", "APPROVAL", "name", "财务负责人审批",
                        "positionX", 200, "positionY", 0,
                        "assigneePolicy", Map.of("type", "ACCOUNT", "accountIds", List.of(owner.accountId())),
                        "formPolicy", Map.of("editable", List.of("amount")), "config", Map.of()),
                node("end", "END", "结束", Map.of()));
        ResponseEntity<Map> changedDraft = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT,
                owner.token(), Map.of("expectedVersion", 3, "nodes", changedNodes, "edges", invalidEdges));
        assertThat(changedDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("select snapshot_json from flow_version where id=?", String.class, firstVersionId))
                .isEqualTo(firstSnapshot);
        assertThat(jdbc.queryForObject("select definition_hash from flow_version where id=?", String.class, firstVersionId))
                .isEqualTo(firstHash);

        ResponseEntity<Map> secondPublish = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 4, "changeSummary", "财务审批版"));
        assertThat(secondPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(secondPublish)).containsEntry("versionNumber", 2)
                .containsEntry("previousVersionId", (int) firstVersionId);
        assertThat(count("flow_version", "flow_id=" + flowId)).isEqualTo(2);
        assertThat(count("audit_event", "event_code='FLOW_VERSION_PUBLISHED' and object_id='"
                + number(data(secondPublish).get("versionId")) + "'")).isOne();

        ResponseEntity<Map> crossSystem = exchange("/api/flows/" + flowId, HttpMethod.GET, outsider.token(), null);
        assertThat(crossSystem.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((List<?>) exchange("/api/flows", HttpMethod.GET, outsider.token(), null).getBody().get("data"))
                .isEmpty();

        grantPlatformPermissions(owner.accountId(), "FLOW", List.of("VIEW", "DESIGN"));
        String limitedPlatformToken = platformLogin(owner.username());
        ResponseEntity<Map> platformFlow = exchange("/api/flows", HttpMethod.POST, limitedPlatformToken,
                Map.of("code", "platform_contract", "name", "平台合同流程"));
        assertThat(platformFlow.getStatusCode()).isEqualTo(HttpStatus.OK);
        long platformFlowId = number(data(platformFlow).get("id"));
        assertThat(data(platformFlow)).containsEntry("contextType", "PLATFORM")
                .containsEntry("platformId", 1).containsEntry("systemId", null).containsEntry("tenantId", null);
        assertThat((List<?>) exchange("/api/flows", HttpMethod.GET, limitedPlatformToken, null)
                .getBody().get("data")).hasSize(1);
        assertThat((List<?>) exchange("/api/flows", HttpMethod.GET, owner.token(), null)
                .getBody().get("data")).hasSize(1);
        ResponseEntity<Map> simulateDenied = exchange("/api/flows/" + platformFlowId + "/simulate",
                HttpMethod.POST, limitedPlatformToken, Map.of("variables", Map.of()));
        assertThat(simulateDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(simulateDenied.getBody().get("code")).isEqualTo("PERMISSION_DENIED");

        grantPlatformPermissions(owner.accountId(), "FLOW", List.of("SIMULATE", "PUBLISH"));
        String fullPlatformToken = platformLogin(owner.username());
        ResponseEntity<Map> platformDraft = exchange("/api/flows/" + platformFlowId + "/draft", HttpMethod.PUT,
                fullPlatformToken, Map.of("expectedVersion", 0,
                        "nodes", List.of(node("start", "START", "开始", Map.of()),
                                node("end", "END", "结束", Map.of())),
                        "edges", List.of(edge("e1", "start", "end", 10))));
        assertThat(platformDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(exchange("/api/flows/" + platformFlowId + "/simulate", HttpMethod.POST,
                fullPlatformToken, Map.of("variables", Map.of())))).containsEntry("successful", true);
        assertThat(exchange("/api/flows/" + platformFlowId + "/publish", HttpMethod.POST, fullPlatformToken,
                Map.of("expectedDraftRevision", 2, "changeSummary", "平台首版")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void projectTaskLifecyclePersistsGroupsMembersHistoryAndRejectsInvalidChanges() {
        Session owner = register("c32_work_owner", "c32-work-system", "c32-work@example.com");
        Session outsider = register("c32_work_outsider", "c32-work-other", "c32-work-other@example.com");

        ResponseEntity<Map> created = exchange("/api/work/projects", HttpMethod.POST, owner.token(), Map.of(
                "code", "delivery_2026", "name", "交付项目", "description", "C32 project",
                "startDate", "2026-08-22", "dueDate", "2026-09-30", "members", List.of()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long projectId = number(data(created).get("id"));
        assertThat(data(created)).containsEntry("status", "PLANNED").containsEntry("version", 0);
        assertThat((List<?>) data(created).get("members")).singleElement().satisfies(member ->
                assertThat(map(member)).containsEntry("projectRole", "OWNER")
                        .containsEntry("accountId", (int) owner.accountId()));

        ResponseEntity<Map> group = exchange("/api/work/projects/" + projectId + "/groups", HttpMethod.POST,
                owner.token(), Map.of("name", "第一阶段", "sortOrder", 10));
        assertThat(group.getStatusCode()).isEqualTo(HttpStatus.OK);
        long groupId = number(((Map<?, ?>) ((List<?>) data(group).get("taskGroups")).getFirst()).get("id"));

        ResponseEntity<Map> invalidAssignee = exchange("/api/work/projects/" + projectId + "/tasks",
                HttpMethod.POST, owner.token(), Map.of("taskGroupId", groupId, "title", "无效负责人任务",
                        "priority", "HIGH", "ownerAccountId", outsider.accountId(),
                        "collaboratorAccountIds", List.of(), "customValues", Map.of()));
        assertThat(invalidAssignee.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidAssignee.getBody().get("code")).isEqualTo("TASK_ASSIGNEE_CONTEXT_INVALID");

        ResponseEntity<Map> taskCreated = exchange("/api/work/projects/" + projectId + "/tasks",
                HttpMethod.POST, owner.token(), Map.of("taskGroupId", groupId, "title", "完成 C32 验收",
                        "description", "按真实业务状态推进", "priority", "HIGH",
                        "ownerAccountId", owner.accountId(), "startAt", "2026-08-22T09:00:00",
                        "dueAt", "2026-08-22T18:00:00", "collaboratorAccountIds", List.of(),
                        "customValues", Map.of("acceptance", "flow-and-work")));
        assertThat(taskCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        long taskId = number(data(taskCreated).get("id"));
        assertThat(data(taskCreated)).containsEntry("status", "TODO").containsEntry("version", 0);
        assertThat((List<?>) data(taskCreated).get("history")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("actionCode", "CREATED"));

        Map<String, Object> completeDirectly = taskUpdate(owner, 0, "COMPLETED", 100, "越级完成");
        ResponseEntity<Map> invalidTransition = exchange("/api/work/tasks/" + taskId, HttpMethod.PUT,
                owner.token(), completeDirectly);
        assertThat(invalidTransition.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidTransition.getBody().get("code")).isEqualTo("WORK_TASK_TRANSITION_INVALID");

        Map<String, Object> start = taskUpdate(owner, 0, "IN_PROGRESS", 35, "开始执行");
        ResponseEntity<Map> started = exchange("/api/work/tasks/" + taskId, HttpMethod.PUT, owner.token(), start);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(started)).containsEntry("status", "IN_PROGRESS").containsEntry("version", 1);
        assertThat((List<?>) data(started).get("history")).hasSize(2);

        ResponseEntity<Map> stale = exchange("/api/work/tasks/" + taskId, HttpMethod.PUT, owner.token(), start);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code")).isEqualTo("WORK_TASK_VERSION_CONFLICT");

        ResponseEntity<Map> completed = exchange("/api/work/tasks/" + taskId, HttpMethod.PUT, owner.token(),
                taskUpdate(owner, 1, "COMPLETED", 100, "验收完成"));
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(completed)).containsEntry("status", "COMPLETED").containsEntry("version", 2);
        assertThat(data(completed).get("completedAt")).isNotNull();
        assertThat((List<?>) data(completed).get("history")).hasSize(3);
        assertThat(count("work_task_history", "task_id=" + taskId)).isEqualTo(3);

        ResponseEntity<Map> project = exchange("/api/work/projects/" + projectId, HttpMethod.GET,
                owner.token(), null);
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(project).get("taskGroups")).hasSize(1);
        assertThat((List<?>) data(project).get("tasks")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("id", (int) taskId).containsEntry("status", "COMPLETED"));
        assertThat(count("audit_event", "event_code in ('WORK_PROJECT_CREATED','WORK_TASK_GROUP_CREATED',"
                + "'WORK_PROJECT_TASK_CREATED','WORK_TASK_UPDATED') and system_id=" + owner.systemId()))
                .isEqualTo(5);

        ResponseEntity<Map> crossSystem = exchange("/api/work/projects/" + projectId, HttpMethod.GET,
                outsider.token(), null);
        assertThat(crossSystem.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((List<?>) exchange("/api/work/projects", HttpMethod.GET, outsider.token(), null)
                .getBody().get("data")).isEmpty();

        grantPlatformPermissions(owner.accountId(), "WORK",
                List.of("VIEW", "CREATE_PROJECT", "MANAGE_PROJECT", "CREATE_TASK", "UPDATE_TASK"));
        String platformToken = platformLogin(owner.username());
        ResponseEntity<Map> platformProject = exchange("/api/work/projects", HttpMethod.POST, platformToken,
                Map.of("code", "platform_delivery", "name", "平台交付项目", "members", List.of()));
        assertThat(platformProject.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(platformProject)).containsEntry("contextType", "PLATFORM")
                .containsEntry("systemId", null).containsEntry("tenantId", null);
        assertThat((List<?>) exchange("/api/work/projects", HttpMethod.GET, platformToken, null)
                .getBody().get("data")).hasSize(1);
        assertThat((List<?>) exchange("/api/work/projects", HttpMethod.GET, owner.token(), null)
                .getBody().get("data")).hasSize(1);

        ResponseEntity<Map> ordinaryCreated = exchange("/api/work/tasks", HttpMethod.POST, owner.token(), Map.of(
                "title", "跟进客户回访", "description", "不属于任何项目的独立任务", "priority", "NORMAL",
                "ownerAccountId", owner.accountId(), "dueAt", "2026-08-24T18:00:00",
                "collaboratorAccountIds", List.of(), "customValues", Map.of()));
        assertThat(ordinaryCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        long ordinaryTaskId = number(data(ordinaryCreated).get("id"));
        assertThat(data(ordinaryCreated)).containsEntry("taskType", "ORDINARY")
                .containsEntry("projectId", null).containsEntry("status", "TODO");
        assertThat((List<?>) exchange("/api/work/tasks", HttpMethod.GET, owner.token(), null)
                .getBody().get("data")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("id", (int) ordinaryTaskId).containsEntry("taskType", "ORDINARY"));
        ResponseEntity<Map> ordinaryStarted = exchange("/api/work/tasks/" + ordinaryTaskId, HttpMethod.PUT,
                owner.token(), taskUpdate(owner, 0, "IN_PROGRESS", 10, "开始执行"));
        assertThat(ordinaryStarted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(ordinaryStarted)).containsEntry("status", "IN_PROGRESS").containsEntry("version", 1);
        assertThat((List<?>) exchange("/api/work/tasks", HttpMethod.GET, outsider.token(), null)
                .getBody().get("data")).isEmpty();
        assertThat(count("audit_event", "event_code='WORK_ORDINARY_TASK_CREATED' and object_id='"
                + ordinaryTaskId + "'")).isOne();
    }

    @Test
    void flowBindingPublishesOneActiveScopeAndResolvesTenantOverrideOrMainDefaultSnapshot() {
        Session owner = register("c34_flow_binding", "c34-flow-binding", "c34-flow-binding@example.com");
        long moduleId = seedPublishedModule(owner, "contract");
        PublishedFlow firstDefault = createPublishedFlow(owner, "contract_default_v1");

        ResponseEntity<Map> invalidCondition = exchange("/api/flow-bindings/publish", HttpMethod.POST,
                owner.token(), Map.of("moduleId", moduleId, "triggerEvent", "CREATE",
                        "flowId", firstDefault.flowId(), "executionMode", "AFTER_EXECUTION",
                        "priorityOrder", 0, "conditionExpression", "amount ?? 10",
                        "mutuallyExclusive", true, "replaceExisting", false));
        assertThat(invalidCondition.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidCondition.getBody().get("code")).isEqualTo("FLOW_BINDING_CONDITION_INVALID");

        ResponseEntity<Map> defaultBinding = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(),
                bindingRequest(moduleId, firstDefault.flowId(), 0, false, false));
        assertThat(defaultBinding.getStatusCode()).isEqualTo(HttpStatus.OK);
        long firstBindingId = number(data(defaultBinding).get("id"));
        assertThat(data(defaultBinding)).containsEntry("scopeType", "DEFAULT")
                .containsEntry("flowVersionId", (int) firstDefault.versionId());

        ResponseEntity<Map> firstResolution = exchange("/api/flow-bindings/resolve", HttpMethod.POST, owner.token(),
                Map.of("moduleId", moduleId, "triggerEvent", "CREATE", "variables", Map.of("amount", 8000)));
        assertThat(firstResolution.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(firstResolution)).containsEntry("source", "DEFAULT")
                .containsEntry("flowVersionId", (int) firstDefault.versionId());
        assertThat(data(firstResolution).get("definitionSnapshot")).isInstanceOf(Map.class);
        ResponseEntity<Map> conditionMiss = exchange("/api/flow-bindings/resolve", HttpMethod.POST, owner.token(),
                Map.of("moduleId", moduleId, "triggerEvent", "CREATE", "variables", Map.of("amount", 5)));
        assertThat(conditionMiss.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(conditionMiss.getBody().get("code")).isEqualTo("FLOW_BINDING_CONDITION_NOT_MATCHED");

        Session limitedHome = register("c34_flow_limited", "c34-flow-limited", "c34-flow-limited@example.com");
        addMemberWithPermissions(owner, limitedHome.accountId(), "FLOW", List.of("BIND_PUBLISH"), "FLOW_LIMITED");
        Session limited = enterTenant(limitedHome, owner.systemId(), owner.tenantId(),
                platformLogin(limitedHome.username()));
        assertThat(exchange("/api/flow-bindings/publish", HttpMethod.POST, limited.token(),
                bindingRequest(moduleId, firstDefault.flowId(), 0, false, false)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> triggerDenied = exchange("/api/flow-bindings/resolve", HttpMethod.POST,
                limited.token(), Map.of("moduleId", moduleId, "triggerEvent", "CREATE",
                        "variables", Map.of("amount", 100)));
        assertThat(triggerDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        PublishedFlow secondDefault = createPublishedFlow(owner, "contract_default_v2");
        ResponseEntity<Map> replacementRequired = exchange("/api/flow-bindings/publish", HttpMethod.POST,
                owner.token(), bindingRequest(moduleId, secondDefault.flowId(), 0, false, false));
        assertThat(replacementRequired.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(replacementRequired.getBody().get("code")).isEqualTo("FLOW_BINDING_REPLACEMENT_REQUIRED");
        ResponseEntity<Map> replaced = exchange("/api/flow-bindings/publish", HttpMethod.POST, owner.token(),
                bindingRequest(moduleId, secondDefault.flowId(), 0, true, false));
        assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(data(replaced).get("id"))).isNotEqualTo(firstBindingId);
        assertThat(count("flow_trigger_binding", "owner_tenant_id=" + owner.tenantId() + " and status='ACTIVE'"))
                .isOne();
        assertThat(count("flow_trigger_binding", "id=" + firstBindingId + " and flow_id="
                + firstDefault.flowId() + " and status='DISABLED'")).isOne();

        long overrideTenantId = addTenantWithWildcard(owner, "override");
        Session override = enterTenant(owner, overrideTenantId);
        PublishedFlow overrideFlow = createPublishedFlow(override, "contract_override");
        ResponseEntity<Map> incompatible = exchange("/api/flow-bindings/publish", HttpMethod.POST,
                override.token(), bindingRequest(moduleId, secondDefault.flowId(), 0, false, false));
        assertThat(incompatible.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(incompatible.getBody().get("code")).isEqualTo("FLOW_BINDING_FLOW_SCOPE_INVALID");
        assertThat(exchange("/api/flow-bindings/publish", HttpMethod.POST, override.token(),
                bindingRequest(moduleId, overrideFlow.flowId(), 0, false, false)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> overrideResolved = exchange("/api/flow-bindings/resolve", HttpMethod.POST,
                override.token(), Map.of("moduleId", moduleId, "triggerEvent", "CREATE",
                        "variables", Map.of("amount", 100)));
        assertThat(data(overrideResolved)).containsEntry("source", "TENANT_OVERRIDE")
                .containsEntry("sourceTenantId", (int) overrideTenantId)
                .containsEntry("flowVersionId", (int) overrideFlow.versionId());

        long fallbackTenantId = addTenantWithWildcard(owner, "fallback");
        Session fallback = enterTenant(owner, fallbackTenantId);
        ResponseEntity<Map> fallbackResolved = exchange("/api/flow-bindings/resolve", HttpMethod.POST,
                fallback.token(), Map.of("moduleId", moduleId, "triggerEvent", "CREATE",
                        "variables", Map.of("amount", 100)));
        assertThat(data(fallbackResolved)).containsEntry("source", "DEFAULT")
                .containsEntry("sourceTenantId", (int) owner.tenantId())
                .containsEntry("requestedTenantId", (int) fallbackTenantId)
                .containsEntry("flowVersionId", (int) secondDefault.versionId());
        assertThat(count("flow_binding_resolution", "module_id=" + moduleId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select flow_version_id from flow_binding_resolution where id=?",
                Long.class, number(data(firstResolution).get("resolutionId")))).isEqualTo(firstDefault.versionId());
    }

    @Test
    void manualWorkLogKeepsImmutableRevisionsLinksAndExplicitOtherAuthorPermission() {
        Session owner = register("c34_log_owner", "c34-log-system", "c34-log-owner@example.com");
        Session outsider = register("c34_log_viewer", "c34-log-viewer-system", "c34-log-viewer@example.com");

        ResponseEntity<Map> project = exchange("/api/work/projects", HttpMethod.POST, owner.token(), Map.of(
                "code", "c34_log_project", "name", "C34 日志关联项目", "members", List.of()));
        long projectId = number(data(project).get("id"));
        ResponseEntity<Map> group = exchange("/api/work/projects/" + projectId + "/groups", HttpMethod.POST,
                owner.token(), Map.of("name", "日志验收", "sortOrder", 10));
        long groupId = number(((Map<?, ?>) ((List<?>) data(group).get("taskGroups")).getFirst()).get("id"));
        ResponseEntity<Map> task = exchange("/api/work/projects/" + projectId + "/tasks", HttpMethod.POST,
                owner.token(), Map.of("taskGroupId", groupId, "title", "完成 C34 日志验收", "priority", "HIGH",
                        "ownerAccountId", owner.accountId(), "collaboratorAccountIds", List.of(),
                        "customValues", Map.of()));
        long taskId = number(data(task).get("id"));

        String today = LocalDate.now().toString();
        Map<String, Object> links = Map.of("projectId", projectId, "taskIds", List.of(taskId));
        ResponseEntity<Map> created = exchange("/api/work/logs", HttpMethod.POST, owner.token(), Map.of(
                "workDate", today, "title", "C34 每日交付日志", "content", "完成 Flow 绑定后端",
                "durationMinutes", 120, "customValues", links));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long logId = number(data(created).get("id"));
        assertThat(data(created)).containsEntry("status", "DRAFT").containsEntry("version", 0);
        assertThat((List<?>) data(created).get("revisions")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("revisionNumber", 1));
        assertThat(((List<?>) exchange("/api/work/logs?workDate=" + today, HttpMethod.GET,
                owner.token(), null).getBody().get("data"))).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("id", (int) logId));

        Map<String, Object> submit = logUpdate(0, today, "C34 每日交付日志", "完成 Flow 绑定与日志服务",
                "SUBMITTED", links, "完成当日工作并提交");
        ResponseEntity<Map> submitted = exchange("/api/work/logs/" + logId, HttpMethod.PUT, owner.token(), submit);
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(submitted)).containsEntry("status", "SUBMITTED").containsEntry("version", 1);
        assertThat((List<?>) data(submitted).get("revisions")).hasSize(2);

        ResponseEntity<Map> submittedMutation = exchange("/api/work/logs/" + logId, HttpMethod.PUT, owner.token(),
                logUpdate(1, today, "C34 每日交付日志", "偷偷改写已提交内容", "SUBMITTED", links, "错误改写"));
        assertThat(submittedMutation.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(submittedMutation.getBody().get("code")).isEqualTo("WORK_LOG_SUBMITTED_IMMUTABLE");
        ResponseEntity<Map> future = exchange("/api/work/logs", HttpMethod.POST, owner.token(), Map.of(
                "workDate", LocalDate.now().plusDays(1).toString(), "title", "未来日志", "content", "不允许",
                "durationMinutes", 10, "customValues", Map.of()));
        assertThat(future.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(future.getBody().get("code")).isEqualTo("WORK_LOG_FUTURE_DATE_INVALID");

        ResponseEntity<Map> withdrawn = exchange("/api/work/logs/" + logId, HttpMethod.PUT, owner.token(),
                logUpdate(1, today, "C34 每日交付日志", "完成 Flow 绑定与日志服务", "WITHDRAWN", links, "发现遗漏，主动撤回"));
        assertThat(withdrawn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(withdrawn)).containsEntry("status", "WITHDRAWN").containsEntry("version", 2);
        assertThat((List<?>) data(withdrawn).get("revisions")).hasSize(3);
        ResponseEntity<Map> stale = exchange("/api/work/logs/" + logId, HttpMethod.PUT, owner.token(),
                logUpdate(1, today, "C34 每日交付日志", "过期覆盖", "WITHDRAWN", links, "旧页面保存"));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        int logCountBeforeTaskChange = count("work_log", "author_account_id=" + owner.accountId());
        assertThat(exchange("/api/work/tasks/" + taskId, HttpMethod.PUT, owner.token(),
                taskUpdate(owner, 0, "IN_PROGRESS", 25, "推进任务但不得自动写日志")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(count("work_log", "author_account_id=" + owner.accountId())).isEqualTo(logCountBeforeTaskChange);

        long viewerRoleId = addMemberWithPermissions(
                owner, outsider.accountId(), "WORK", List.of("VIEW", "UPDATE_LOG"), "LOG_VIEWER");
        Session viewer = enterTenant(outsider, owner.systemId(), owner.tenantId(), platformLogin(outsider.username()));
        ResponseEntity<Map> denied = exchange("/api/work/logs?authorAccountId=" + owner.accountId(),
                HttpMethod.GET, viewer.token(), null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> authorDenied = exchange("/api/work/logs/" + logId, HttpMethod.PUT, viewer.token(),
                logUpdate(2, today, "C34 每日交付日志", "越权改写", "DRAFT", links, "越权"));
        assertThat(authorDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authorDenied.getBody().get("code")).isEqualTo("WORK_LOG_AUTHOR_REQUIRED");

        grantSystemRoleAction(owner.systemId(), owner.tenantId(), viewerRoleId, "WORK", "SYSTEM", "VIEW_OTHERS");
        viewer = enterTenant(viewer, owner.systemId(), owner.tenantId(), platformLogin(outsider.username()));
        ResponseEntity<Map> visible = exchange("/api/work/logs?authorAccountId=" + owner.accountId(),
                HttpMethod.GET, viewer.token(), null);
        assertThat(visible.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) visible.getBody().get("data")).hasSize(1);
        assertThat(count("work_log_revision", "work_log_id=" + logId)).isEqualTo(3);
        assertThat(count("audit_event", "object_type='WORK_LOG' and object_id='" + logId + "'")).isEqualTo(3);
    }

    @Test
    void flowRuntimeFreezesVersionHandlesApprovalOnceAndPersistsExplainableFailure() {
        Session owner = register("c36_flow_runtime", "c36-flow-runtime", "c36-flow-runtime@example.com");
        ResponseEntity<Map> created = exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "runtime_approval", "name", "C36 运行审批"));
        long flowId = number(data(created).get("id"));
        List<Map<String, Object>> nodes = List.of(
                node("start", "START", "开始", Map.of()),
                Map.of("nodeKey", "approve", "nodeType", "APPROVAL", "name", "负责人审批",
                        "positionX", 200, "positionY", 0,
                        "assigneePolicy", Map.of("type", "ACCOUNT", "accountIds", List.of(owner.accountId())),
                        "formPolicy", Map.of("editable", List.of("amount")), "config", Map.of()),
                node("end", "END", "结束", Map.of()));
        List<Map<String, Object>> edges = List.of(edge("e1", "start", "approve", 10),
                edge("e2", "approve", "end", 20));
        assertThat(exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0, "nodes", nodes, "edges", edges)).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> published = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 2, "changeSummary", "C36 运行首版"));
        long versionId = number(data(published).get("versionId"));

        ResponseEntity<Map> started = exchange("/api/flow-runtime/instances", HttpMethod.POST, owner.token(), Map.of(
                "flowId", flowId, "title", "合同 #C36 审批", "businessSnapshot", Map.of("amount", 8000),
                "variables", Map.of("amount", 8000), "idempotencyKey", "c36-start-approval"));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> startedInstance = map(data(started).get("instance"));
        long instanceId = number(startedInstance.get("id"));
        assertThat(startedInstance).containsEntry("status", "WAITING")
                .containsEntry("flowVersionId", (int) versionId)
                .containsEntry("currentNodeKey", "approve");
        assertThat(map(startedInstance.get("variables"))).containsEntry("amount", 8000);
        Map<String, Object> pendingTask = map(((List<?>) startedInstance.get("tasks")).getFirst());
        long taskId = number(pendingTask.get("id"));
        assertThat(pendingTask).containsEntry("status", "PENDING")
                .containsEntry("assigneeAccountId", (int) owner.accountId());
        assertThat((List<?>) pendingTask.get("candidates")).singleElement().satisfies(candidate ->
                assertThat(map(candidate)).containsEntry("candidateId", String.valueOf(owner.accountId())));
        assertThat(((List<?>) startedInstance.get("allowedActions")).stream().map(String::valueOf).toList())
                .contains("APPROVE", "REJECT", "RETURN", "TRANSFER", "WITHDRAW", "TERMINATE");

        Session limitedHome = register("c36_flow_observer", "c36-flow-observer", "c36-flow-observer@example.com");
        addMemberWithPermissions(owner, limitedHome.accountId(), "FLOW",
                List.of("VIEW_RUNTIME", "VIEW_ALL", "HANDLE"), "FLOW_RUNTIME_OBSERVER");
        Session limited = enterTenant(limitedHome, owner.systemId(), owner.tenantId(),
                platformLogin(limitedHome.username()));
        assertThat(exchange("/api/flow-runtime/instances/" + instanceId, HttpMethod.GET,
                limited.token(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> handlerDenied = exchange("/api/flow-runtime/tasks/" + taskId + "/actions",
                HttpMethod.POST, limited.token(), Map.of("actionCode", "APPROVE",
                        "idempotencyKey", "c36-wrong-handler", "variables", Map.of()));
        assertThat(handlerDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handlerDenied.getBody().get("code")).isEqualTo("FLOW_TASK_HANDLER_DENIED");

        ResponseEntity<Map> missingReason = exchange("/api/flow-runtime/tasks/" + taskId + "/actions",
                HttpMethod.POST, owner.token(), Map.of("actionCode", "REJECT",
                        "idempotencyKey", "c36-reject-without-reason", "variables", Map.of()));
        assertThat(missingReason.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(missingReason.getBody().get("code")).isEqualTo("FLOW_TASK_REASON_REQUIRED");

        Map<String, Object> approve = Map.of("actionCode", "APPROVE", "comment", "同意合同",
                "idempotencyKey", "c36-approve-once", "variables", Map.of("approved", true));
        ResponseEntity<Map> approved = exchange("/api/flow-runtime/tasks/" + taskId + "/actions",
                HttpMethod.POST, owner.token(), approve);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(approved)).containsEntry("replayed", false);
        assertThat(map(data(approved).get("instance"))).containsEntry("status", "COMPLETED")
                .containsEntry("flowVersionId", (int) versionId);
        ResponseEntity<Map> replayed = exchange("/api/flow-runtime/tasks/" + taskId + "/actions",
                HttpMethod.POST, owner.token(), approve);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(replayed)).containsEntry("replayed", true)
                .containsEntry("actionId", data(approved).get("actionId"));
        ResponseEntity<Map> secondAction = exchange("/api/flow-runtime/tasks/" + taskId + "/actions",
                HttpMethod.POST, owner.token(), Map.of("actionCode", "APPROVE", "comment", "重复",
                        "idempotencyKey", "c36-approve-twice", "variables", Map.of()));
        assertThat(secondAction.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondAction.getBody().get("code")).isEqualTo("FLOW_TASK_ALREADY_HANDLED");
        assertThat(count("flow_action", "instance_id=" + instanceId + " and action_code='APPROVE'")).isOne();
        assertThat(count("flow_task", "instance_id=" + instanceId + " and status='APPROVE'")).isOne();
        assertThat(count("flow_instance_variable", "instance_id=" + instanceId)).isEqualTo(2);

        ResponseEntity<Map> businessStarted = exchange("/api/flow-runtime/instances", HttpMethod.POST,
                owner.token(), Map.of("flowId", flowId, "title", "受限业务对象审批",
                        "businessType", "secret_contract", "businessId", "88",
                        "variables", Map.of(), "idempotencyKey", "c36-business-intersection"));
        long businessInstanceId = number(map(data(businessStarted).get("instance")).get("id"));
        ResponseEntity<Map> businessDenied = exchange("/api/flow-runtime/instances/" + businessInstanceId,
                HttpMethod.GET, limited.token(), null);
        assertThat(businessDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(businessDenied.getBody().get("code")).isEqualTo("FLOW_INSTANCE_VIEW_DENIED");

        ResponseEntity<Map> failingCreated = exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "runtime_failure", "name", "C36 服务异常"));
        long failingFlowId = number(data(failingCreated).get("id"));
        List<Map<String, Object>> failingNodes = List.of(
                node("start", "START", "开始", Map.of()),
                node("service", "WEBHOOK", "失败服务",
                        Map.of("url", "https://example.invalid/service", "simulateFailure", true)),
                node("end", "END", "结束", Map.of()));
        assertThat(exchange("/api/flows/" + failingFlowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0, "nodes", failingNodes,
                "edges", List.of(edge("e1", "start", "service", 10), edge("e2", "service", "end", 20))))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/flows/" + failingFlowId + "/publish", HttpMethod.POST, owner.token(), Map.of(
                "expectedDraftRevision", 2, "changeSummary", "异常策略验收")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> failed = exchange("/api/flow-runtime/instances", HttpMethod.POST, owner.token(), Map.of(
                "flowId", failingFlowId, "title", "失败实例", "variables", Map.of(),
                "idempotencyKey", "c36-start-failure"));
        Map<String, Object> failedInstance = map(data(failed).get("instance"));
        assertThat(failedInstance).containsEntry("status", "EXCEPTION")
                .containsEntry("errorCode", "FLOW_RUNTIME_SERVICE_FAILED");
        assertThat((List<?>) failedInstance.get("exceptions")).singleElement().satisfies(exception ->
                assertThat(map(exception)).containsEntry("policyAction", "MANUAL").containsEntry("status", "OPEN"));
        assertThat(count("flow_exception", "instance_id=" + number(failedInstance.get("id")))).isOne();
    }

    @Test
    void workConfigurationPublishesLayeredVersionsAndCalendarHidesUnauthorizedDetails() {
        Session owner = register("c36_work_config", "c36-work-config", "c36-work-config@example.com");
        grantPlatformPermissions(owner.accountId(), "WORK",
                List.of("VIEW_CONFIG", "CONFIGURE", "PUBLISH_CONFIG", "VIEW_CALENDAR", "VIEW_DETAIL"));
        String platformToken = platformLogin(owner.username());
        Map<String, Object> platformDraftPayload = Map.of("fieldName", "平台验收说明", "fieldType", "TEXTAREA",
                "required", false, "sortOrder", 20, "settings", Map.of("cardVisible", true));
        ResponseEntity<Map> platformDraft = exchange("/api/work/configuration/fields/TASK/acceptance_note",
                HttpMethod.PUT, platformToken, platformDraftPayload);
        assertThat(platformDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(platformDraft)).containsEntry("status", "DRAFT").containsEntry("rowVersion", 0);
        ResponseEntity<Map> platformPublished = exchange(
                "/api/work/configuration/fields/TASK/acceptance_note/publish", HttpMethod.POST, platformToken,
                Map.of("expectedVersion", 0, "changeSummary", "平台默认首版"));
        assertThat(data(platformPublished)).containsEntry("currentPublicationVersion", 1)
                .containsEntry("status", "PUBLISHED");

        ResponseEntity<Map> inherited = exchange("/api/work/configuration", HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) data(inherited).get("inheritedPlatformDefaults")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("source", "PLATFORM_DEFAULT")
                        .containsEntry("fieldName", "平台验收说明"));
        assertThat((List<?>) data(inherited).get("effectivePublished")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("source", "PLATFORM_DEFAULT"));

        Map<String, Object> systemDraftPayload = Map.of("fieldName", "系统验收说明", "fieldType", "TEXTAREA",
                "required", true, "sortOrder", 10, "settings", Map.of("cardVisible", false));
        assertThat(exchange("/api/work/configuration/fields/TASK/acceptance_note", HttpMethod.PUT,
                owner.token(), systemDraftPayload).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> beforeSystemPublish = exchange("/api/work/configuration", HttpMethod.GET,
                owner.token(), null);
        assertThat((List<?>) data(beforeSystemPublish).get("effectivePublished")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("source", "PLATFORM_DEFAULT"));
        ResponseEntity<Map> systemV1 = exchange("/api/work/configuration/fields/TASK/acceptance_note/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedVersion", 0, "changeSummary", "系统覆盖首版"));
        assertThat(data(systemV1)).containsEntry("currentPublicationVersion", 1).containsEntry("rowVersion", 1);

        ResponseEntity<Map> changedDraft = exchange("/api/work/configuration/fields/TASK/acceptance_note",
                HttpMethod.PUT, owner.token(), Map.of("expectedVersion", 1, "fieldName", "系统验收备注",
                        "fieldType", "TEXTAREA", "required", false, "sortOrder", 11,
                        "settings", Map.of("cardVisible", true)));
        assertThat(data(changedDraft)).containsEntry("status", "DRAFT").containsEntry("rowVersion", 2);
        ResponseEntity<Map> effectiveBeforeV2 = exchange("/api/work/configuration", HttpMethod.GET,
                owner.token(), null);
        assertThat((List<?>) data(effectiveBeforeV2).get("effectivePublished")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("fieldName", "系统验收说明")
                        .containsEntry("required", true).containsEntry("source", "SYSTEM_OVERRIDE"));
        ResponseEntity<Map> systemV2 = exchange("/api/work/configuration/fields/TASK/acceptance_note/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedVersion", 2, "changeSummary", "系统覆盖二版"));
        assertThat(data(systemV2)).containsEntry("currentPublicationVersion", 2).containsEntry("rowVersion", 3);
        ResponseEntity<Map> rolledBack = exchange("/api/work/configuration/fields/TASK/acceptance_note/rollback",
                HttpMethod.POST, owner.token(), Map.of("expectedVersion", 3,
                        "targetPublicationVersion", 1, "reason", "恢复首版必填规则"));
        assertThat(data(rolledBack)).containsEntry("currentPublicationVersion", 3)
                .containsEntry("required", true).containsEntry("rowVersion", 4);
        assertThat((List<?>) data(rolledBack).get("publicationVersions")).hasSize(3);

        ResponseEntity<Map> queryDraft = exchange("/api/work/configuration/fields/STATISTICS/team_delivery",
                HttpMethod.PUT, owner.token(), Map.of("fieldName", "团队交付统计", "fieldType", "QUERY",
                        "required", false, "sortOrder", 10, "settings", Map.of(
                                "groupBy", List.of("DATE", "PROJECT"),
                                "metrics", List.of("TASK_COUNT", "COMPLETED_COUNT", "LOG_MINUTES"))));
        assertThat(queryDraft.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/work/configuration/fields/STATISTICS/team_delivery/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedVersion", 0,
                        "changeSummary", "团队统计首版")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("work_field_config", "system_id=" + owner.systemId())).isEqualTo(2);

        Session viewerHome = register("c36_calendar_viewer", "c36-calendar-viewer", "c36-calendar-viewer@example.com");
        addMemberWithPermissions(owner, viewerHome.accountId(), "WORK", List.of("VIEW_CALENDAR"), "CALENDAR_VIEWER");
        String today = LocalDate.now().toString();
        ResponseEntity<Map> project = exchange("/api/work/projects", HttpMethod.POST, owner.token(), Map.of(
                "code", "c36_calendar", "name", "C36 日历项目", "startDate", today, "dueDate", today,
                "members", List.of(Map.of("accountId", viewerHome.accountId(), "projectRole", "MEMBER"))));
        long projectId = number(data(project).get("id"));
        ResponseEntity<Map> group = exchange("/api/work/projects/" + projectId + "/groups", HttpMethod.POST,
                owner.token(), Map.of("name", "日历阶段", "sortOrder", 10));
        long groupId = number(map(((List<?>) data(group).get("taskGroups")).getFirst()).get("id"));
        ResponseEntity<Map> task = exchange("/api/work/projects/" + projectId + "/tasks", HttpMethod.POST,
                owner.token(), Map.of("taskGroupId", groupId, "title", "C36 当日任务", "priority", "HIGH",
                        "ownerAccountId", owner.accountId(), "dueAt", today + "T23:00:00",
                        "collaboratorAccountIds", List.of(viewerHome.accountId()), "customValues", Map.of()));
        assertThat(task.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/work/logs", HttpMethod.POST, owner.token(), Map.of(
                "workDate", today, "title", "C36 日历日志", "content", "日历统计验收",
                "durationMinutes", 75, "customValues", Map.of("projectId", projectId))).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> calendar = exchange("/api/work/calendar?from=" + today + "&to=" + today,
                HttpMethod.GET, owner.token(), null);
        assertThat(calendar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(calendar).get("summary"))).containsEntry("tasksDue", 1)
                .containsEntry("logCount", 1).containsEntry("logMinutes", 75)
                .containsEntry("projectMilestones", 1);
        assertThat((List<?>) data(calendar).get("days")).singleElement().satisfies(day -> {
            assertThat(map(day)).containsEntry("detailAvailable", true);
            assertThat((List<?>) map(day).get("tasks")).hasSize(1);
            assertThat((List<?>) map(day).get("logs")).hasSize(1);
        });

        Session viewer = enterTenant(viewerHome, owner.systemId(), owner.tenantId(),
                platformLogin(viewerHome.username()));
        ResponseEntity<Map> aggregateOnly = exchange("/api/work/calendar?from=" + today + "&to=" + today,
                HttpMethod.GET, viewer.token(), null);
        assertThat(aggregateOnly.getStatusCode()).describedAs("aggregate calendar response: %s", aggregateOnly.getBody())
                .isEqualTo(HttpStatus.OK);
        assertThat(map(data(aggregateOnly).get("summary"))).containsEntry("tasksDue", 1)
                .containsEntry("logCount", 0);
        assertThat((List<?>) data(aggregateOnly).get("days")).singleElement().satisfies(day -> {
            assertThat(map(day)).containsEntry("detailAvailable", false);
            assertThat((List<?>) map(day).get("tasks")).isEmpty();
            assertThat((List<?>) map(day).get("logs")).isEmpty();
        });
        ResponseEntity<Map> othersDenied = exchange("/api/work/calendar?from=" + today + "&to=" + today
                + "&accountId=" + owner.accountId(), HttpMethod.GET, viewer.token(), null);
        assertThat(othersDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> platformCalendar = exchange("/api/work/calendar?from=" + today + "&to=" + today,
                HttpMethod.GET, platformToken, null);
        assertThat(map(data(platformCalendar).get("summary"))).containsEntry("tasksDue", 0)
                .containsEntry("logCount", 0).containsEntry("projectMilestones", 0);
        assertThat(count("work_task", "system_id=" + owner.systemId())).isOne();
        assertThat(count("work_log", "system_id=" + owner.systemId())).isOne();
    }

    private Map<String, Object> node(String key, String type, String name, Map<String, Object> config) {
        int positionX = switch (type) {
            case "START" -> 0;
            case "END" -> 400;
            default -> 200;
        };
        return Map.of("nodeKey", key, "nodeType", type, "name", name,
                "positionX", positionX, "positionY", 0, "config", config);
    }

    private Map<String, Object> edge(String key, String source, String target, int priority) {
        return Map.of("edgeKey", key, "sourceNodeKey", source, "targetNodeKey", target,
                "priorityOrder", priority, "config", Map.of());
    }

    private List<String> issueCodes(ResponseEntity<Map> response) {
        return ((List<Map<String, Object>>) data(response).get("issues")).stream()
                .map(issue -> String.valueOf(issue.get("code"))).toList();
    }

    private Map<String, Object> taskUpdate(Session owner, int version, String status, int progress, String comment) {
        return Map.of("expectedVersion", version, "status", status, "ownerAccountId", owner.accountId(),
                "priority", "HIGH", "progressPercent", new BigDecimal(progress),
                "startAt", "2026-08-22T09:00:00", "dueAt", "2026-08-22T18:00:00",
                "collaboratorAccountIds", List.of(), "customValues", Map.of("acceptance", "flow-and-work"),
                "comment", comment);
    }

    private PublishedFlow createPublishedFlow(Session owner, String code) {
        ResponseEntity<Map> created = exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", code, "name", code));
        long flowId = number(data(created).get("id"));
        assertThat(exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0,
                "nodes", List.of(node("start", "START", "开始", Map.of()),
                        node("end", "END", "结束", Map.of())),
                "edges", List.of(edge("e1", "start", "end", 10)))).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> published = exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 2, "changeSummary", code + " 首版"));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new PublishedFlow(flowId, number(data(published).get("versionId")));
    }

    @Test
    void manualFlowNodeSuspendsAndResumesApprovalWhileTodoExecutesRealTarget() {
        Session owner = register("c38_manual_todo", "c38-manual-todo", "c38-manual-todo@example.com");
        long flowId = number(data(exchange("/api/flows", HttpMethod.POST, owner.token(),
                Map.of("code", "manual_todo", "name", "C38 加签与待办"))).get("id"));
        List<Map<String, Object>> nodes = List.of(
                node("start", "START", "开始", Map.of()),
                Map.of("nodeKey", "approve", "nodeType", "APPROVAL", "name", "主审批",
                        "positionX", 200, "positionY", 0,
                        "assigneePolicy", Map.of("type", "ACCOUNT", "accountIds", List.of(owner.accountId())),
                        "config", Map.of()),
                node("end", "END", "结束", Map.of()));
        assertThat(exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0, "nodes", nodes,
                "edges", List.of(edge("e1", "start", "approve", 10), edge("e2", "approve", "end", 20))))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST, owner.token(), Map.of(
                "expectedDraftRevision", 2, "changeSummary", "C38 首版")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> started = exchange("/api/flow-runtime/instances", HttpMethod.POST, owner.token(), Map.of(
                "flowId", flowId, "title", "C38 手动加签实例", "variables", Map.of(),
                "idempotencyKey", "c38-manual-start"));
        Map<String, Object> instance = map(data(started).get("instance"));
        long instanceId = number(instance.get("id"));
        long originalTaskId = number(map(((List<?>) instance.get("tasks")).getFirst()).get("id"));
        int taskCountBeforeInvalid = count("flow_task", "instance_id=" + instanceId);
        Map<String, Object> incompatibleMapping = Map.of("assigneeAccountId", owner.accountId(),
                "position", "BEFORE_CURRENT", "reason", "映射不兼容验收",
                "statusMappings", Map.of("APPROVE", Map.of("businessAction", "APPROVE", "targetStatus", "APPROVED")),
                "idempotencyKey", "c38-invalid-mapping");
        ResponseEntity<Map> mappingDenied = exchange("/api/flow-runtime/instances/" + instanceId
                + "/manual-node-preview", HttpMethod.POST, owner.token(), incompatibleMapping);
        assertThat(mappingDenied.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mappingDenied.getBody().get("code")).isEqualTo("FLOW_STATUS_MAPPING_BUSINESS_REQUIRED");
        assertThat(count("flow_task", "instance_id=" + instanceId)).isEqualTo(taskCountBeforeInvalid);

        Map<String, Object> addRequest = Map.of("assigneeAccountId", owner.accountId(),
                "position", "BEFORE_CURRENT", "reason", "合同金额需增加复核人", "statusMappings", Map.of(),
                "idempotencyKey", "c38-add-manual");
        ResponseEntity<Map> preview = exchange("/api/flow-runtime/instances/" + instanceId
                + "/manual-node-preview", HttpMethod.POST, owner.token(), addRequest);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(preview)).containsEntry("allowed", true)
                .containsEntry("suspendedTaskId", (int) originalTaskId);
        ResponseEntity<Map> added = exchange("/api/flow-runtime/instances/" + instanceId
                + "/manual-nodes", HttpMethod.POST, owner.token(), addRequest);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        long manualTaskId = number(data(added).get("taskId"));
        assertThat(count("flow_task", "id=" + originalTaskId + " and status='SUSPENDED'")).isOne();
        assertThat(count("flow_task", "id=" + manualTaskId + " and task_type='MANUAL_APPROVAL' and status='PENDING'")).isOne();
        assertThat(count("flow_action", "instance_id=" + instanceId + " and action_code='MANUAL_NODE_ADDED'")).isOne();
        assertThat(count("audit_event", "object_type='FLOW_TASK' and object_id='" + manualTaskId
                + "' and event_code='FLOW_MANUAL_NODE_ADDED'")).isOne();

        ResponseEntity<Map> manualApproved = exchange("/api/flow-runtime/tasks/" + manualTaskId + "/actions",
                HttpMethod.POST, owner.token(), Map.of("actionCode", "APPROVE", "comment", "复核通过",
                        "idempotencyKey", "c38-manual-approve", "variables", Map.of()));
        assertThat(manualApproved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(data(manualApproved).get("instance"))).containsEntry("status", "WAITING")
                .containsEntry("currentNodeKey", "approve");
        assertThat(count("flow_task", "id=" + originalTaskId + " and status='PENDING'")).isOne();
        ResponseEntity<Map> resumedTodos = exchange("/api/todos?status=PENDING&type=APPROVAL",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) resumedTodos.getBody().get("data")).singleElement().satisfies(row ->
                assertThat(map(row)).containsEntry("sourceId", String.valueOf(originalTaskId))
                        .containsEntry("status", "PENDING"));
        assertThat(exchange("/api/flow-runtime/tasks/" + originalTaskId + "/actions", HttpMethod.POST,
                owner.token(), Map.of("actionCode", "APPROVE", "comment", "主审批通过",
                        "idempotencyKey", "c38-original-approve", "variables", Map.of())).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(count("flow_instance", "id=" + instanceId + " and status='COMPLETED'")).isOne();

        Session limitedHome = register("c38_todo_limited", "c38-todo-limited", "c38-todo-limited@example.com");
        addMemberWithPermissions(owner, limitedHome.accountId(), "TODO", List.of("VIEW", "HANDLE"), "TODO_ONLY");
        Session limited = enterTenant(limitedHome, owner.systemId(), owner.tenantId(), platformLogin(limitedHome.username()));
        ResponseEntity<Map> permissionTarget = exchange("/api/flow-runtime/instances", HttpMethod.POST,
                owner.token(), Map.of("flowId", flowId, "title", "C38 待办目标权限失效", "variables", Map.of(),
                        "idempotencyKey", "c38-todo-permission-start"));
        Map<String, Object> permissionInstance = map(data(permissionTarget).get("instance"));
        long permissionTaskId = number(map(((List<?>) permissionInstance.get("tasks")).getFirst()).get("id"));
        assertThat(exchange("/api/flow-runtime/tasks/" + permissionTaskId + "/actions", HttpMethod.POST,
                owner.token(), Map.of("actionCode", "TRANSFER", "targetAccountId", limitedHome.accountId(),
                        "idempotencyKey", "c38-transfer-limited", "variables", Map.of())).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> limitedTodos = exchange("/api/todos?status=PENDING&type=APPROVAL",
                HttpMethod.GET, limited.token(), null);
        long limitedTodoId = number(map(((List<?>) limitedTodos.getBody().get("data")).getFirst()).get("id"));
        ResponseEntity<Map> targetDenied = exchange("/api/todos/" + limitedTodoId + "/actions", HttpMethod.POST,
                limited.token(), Map.of("actionCode", "APPROVE", "comment", "无 Flow 动作权限",
                        "idempotencyKey", "c38-target-denied"));
        assertThat(targetDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(targetDenied.getBody().get("code")).isEqualTo("PERMISSION_DENIED");
        assertThat(count("todo_item", "id=" + limitedTodoId + " and status='PENDING'")).isOne();
        assertThat(count("flow_task", "id=" + permissionTaskId + " and status='PENDING'")).isOne();

        ResponseEntity<Map> todoStarted = exchange("/api/flow-runtime/instances", HttpMethod.POST, owner.token(), Map.of(
                "flowId", flowId, "title", "C38 待办处理实例", "variables", Map.of(),
                "idempotencyKey", "c38-todo-start"));
        long todoInstanceId = number(map(data(todoStarted).get("instance")).get("id"));
        ResponseEntity<Map> pendingTodos = exchange("/api/todos?status=PENDING&type=APPROVAL",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) pendingTodos.getBody().get("data")).singleElement().satisfies(row -> {
            assertThat(map(row)).containsEntry("sourceType", "FLOW_TASK").containsEntry("status", "PENDING");
            assertThat(((List<?>) map(row).get("availableActions")).stream().map(String::valueOf).toList())
                    .contains("APPROVE", "REJECT", "RETURN", "TRANSFER");
        });
        long todoId = number(map(((List<?>) pendingTodos.getBody().get("data")).getFirst()).get("id"));
        ResponseEntity<Map> handledTodo = exchange("/api/todos/" + todoId + "/actions", HttpMethod.POST,
                owner.token(), Map.of("actionCode", "APPROVE", "comment", "从待办同意",
                        "idempotencyKey", "c38-todo-approve"));
        assertThat(handledTodo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(data(handledTodo).get("resultReference")))
                .startsWith("FLOW_INSTANCE:" + todoInstanceId + ":ACTION:");
        assertThat(map(data(handledTodo).get("todo"))).containsEntry("status", "COMPLETED");
        assertThat((List<?>) exchange("/api/todos?status=PENDING&type=APPROVAL", HttpMethod.GET,
                owner.token(), null).getBody().get("data")).isEmpty();
        assertThat(count("todo_item", "id=" + todoId + " and status='COMPLETED' and target_route like '%actionId=%'")).isOne();
        assertThat(count("flow_instance", "id=" + todoInstanceId + " and status='COMPLETED'")).isOne();
    }

    @Test
    void applicationDraftPublishesImmutableGrantSnapshotRotatesCredentialAndDisablesWithoutDeletingHistory() {
        Session owner = register("c40_application_owner", "c40-application-system", "c40-application@example.com");
        seedPublishedModule(owner, "customer");
        ResponseEntity<Map> created = exchange("/api/applications", HttpMethod.POST, owner.token(), Map.of(
                "code", "customer_bridge", "name", "客户资料受控桥梁",
                "description", "只开放客户详情读取", "applicationType", "SERVICE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> createdApplication = map(data(created).get("application"));
        Map<String, Object> initialCredential = map(data(created).get("issuedCredential"));
        long applicationId = number(createdApplication.get("id"));
        String firstSecret = String.valueOf(initialCredential.get("clientSecret"));
        assertThat(createdApplication).containsEntry("contextType", "SYSTEM")
                .containsEntry("ownerSystemId", (int) owner.systemId())
                .containsEntry("ownerTenantId", (int) owner.tenantId())
                .containsEntry("status", "DRAFT");
        assertThat(initialCredential).containsEntry("credentialVersion", 1).containsEntry("shownOnce", true);
        assertThat(firstSecret).hasSizeGreaterThan(32);
        assertThat(String.valueOf(data(exchange("/api/applications/" + applicationId,
                HttpMethod.GET, owner.token(), null)))).doesNotContain(firstSecret);

        Map<String, Object> wrongScope = Map.of(
                "targetType", "SYSTEM", "targetSystemId", owner.systemId() + 999,
                "targetTenantId", owner.tenantId(), "resourceType", "MODULE", "resourceId", "customer",
                "actionCode", "DETAIL", "dataScope", Map.of("type", "SELF"),
                "rateLimit", Map.of("maxRequests", 100, "windowSeconds", 60), "fields", List.of());
        ResponseEntity<Map> rejectedScope = exchange("/api/applications/" + applicationId + "/draft",
                HttpMethod.PUT, owner.token(), Map.of(
                        "expectedVersion", 0, "name", "客户资料受控桥梁", "description", "不应保存",
                        "applicationType", "SERVICE", "callbacks", List.of(), "grants", List.of(wrongScope)));
        assertThat(rejectedScope.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rejectedScope.getBody().get("code")).isEqualTo("APPLICATION_SYSTEM_SCOPE_FIXED");
        assertThat(count("app_grant", "application_id=" + applicationId)).isZero();

        Map<String, Object> invalidSystemResource = Map.of(
                "targetType", "SYSTEM", "targetSystemId", owner.systemId(),
                "targetTenantId", owner.tenantId(), "resourceType", "AI", "resourceId", "customer_agent",
                "actionCode", "RUN", "dataScope", Map.of("type", "SELF"),
                "rateLimit", Map.of("maxRequests", 100, "windowSeconds", 60), "fields", List.of());
        ResponseEntity<Map> rejectedResource = exchange("/api/applications/" + applicationId + "/draft",
                HttpMethod.PUT, owner.token(), Map.of(
                        "expectedVersion", 0, "name", "客户资料受控桥梁", "description", "不应保存",
                        "applicationType", "SERVICE", "callbacks", List.of(), "grants", List.of(invalidSystemResource)));
        assertThat(rejectedResource.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rejectedResource.getBody().get("code")).isEqualTo("APPLICATION_SYSTEM_RESOURCE_INVALID");
        assertThat(count("app_grant", "application_id=" + applicationId)).isZero();

        Map<String, Object> grant = Map.of(
                "targetType", "SYSTEM", "targetSystemId", owner.systemId(), "targetTenantId", owner.tenantId(),
                "resourceType", "MODULE", "resourceId", "customer", "actionCode", "DETAIL",
                "dataScope", Map.of("type", "SELF"),
                "rateLimit", Map.of("maxRequests", 100, "windowSeconds", 60,
                        "allowedIps", List.of("127.0.0.1")),
                "fields", List.of(Map.of("fieldCode", "customer_name", "readable", true,
                        "writable", false, "maskStrategy", "NONE")));
        Map<String, Object> callback = Map.of(
                "callbackType", "RESULT", "url", "https://callback.example.com/application-result",
                "eventCodes", List.of("APPLICATION.CALL.COMPLETED"),
                "signingSecretRef", "secret://application/customer_bridge/callback/v1",
                "timeoutMillis", 5000, "maxAttempts", 3);
        ResponseEntity<Map> saved = exchange("/api/applications/" + applicationId + "/draft",
                HttpMethod.PUT, owner.token(), Map.of(
                        "expectedVersion", 0, "name", "客户资料受控桥梁", "description", "C40 可发布草稿",
                        "applicationType", "SERVICE", "callbacks", List.of(callback), "grants", List.of(grant)));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(saved)).containsEntry("draftRevision", 2).containsEntry("version", 1);
        assertThat(((List<?>) data(saved).get("grants"))).singleElement().satisfies(row ->
                assertThat(map(row)).containsEntry("targetSystemId", (int) owner.systemId())
                        .containsEntry("resourceType", "MODULE").containsEntry("actionCode", "DETAIL"));

        ResponseEntity<Map> check = exchange("/api/applications/" + applicationId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        assertThat(check.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(check)).containsEntry("valid", true).containsEntry("draftRevision", 2);
        ResponseEntity<Map> published = exchange("/api/applications/" + applicationId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", 2,
                        "changeSummary", "C40 首个受控访问版本"));
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(published)).containsEntry("versionNumber", 1);
        assertThat(String.valueOf(data(published).get("snapshotHash"))).hasSize(64);
        assertThat(map(data(published).get("application"))).containsEntry("status", "ACTIVE");
        String frozenSnapshot = jdbc.queryForObject("select snapshot_json from app_version where application_id=?",
                String.class, applicationId);
        assertThat(frozenSnapshot).contains("customer_name", "allowedIps", "credentialVersion");

        ResponseEntity<Map> rotated = exchange("/api/applications/" + applicationId + "/credentials",
                HttpMethod.POST, owner.token(), Map.of());
        assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> rotatedCredential = map(data(rotated).get("issuedCredential"));
        assertThat(rotatedCredential).containsEntry("credentialVersion", 2).containsEntry("shownOnce", true);
        assertThat(String.valueOf(rotatedCredential.get("clientSecret"))).isNotEqualTo(firstSecret);
        assertThat(count("app_credential", "application_id=" + applicationId + " and status='ACTIVE'")).isOne();
        assertThat(count("app_credential", "application_id=" + applicationId + " and status='REVOKED'")).isOne();
        assertThat(jdbc.queryForObject("select snapshot_json from app_version where application_id=?",
                String.class, applicationId)).isEqualTo(frozenSnapshot);

        ResponseEntity<Map> disabled = exchange("/api/applications/" + applicationId + "/disable",
                HttpMethod.POST, owner.token(), Map.of("reason", "完成 C40 停用验收"));
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(disabled)).containsEntry("status", "DISABLED");
        assertThat(count("app_credential", "application_id=" + applicationId + " and status='ACTIVE'")).isZero();
        assertThat(count("app_version", "application_id=" + applicationId)).isOne();
        assertThat(count("app_grant", "application_id=" + applicationId)).isOne();
        assertThat(count("audit_event", "object_type='APPLICATION' and object_id='" + applicationId
                + "' and event_code in ('APPLICATION_DRAFT_CREATED','APPLICATION_DRAFT_SAVED',"
                + "'APPLICATION_VERSION_PUBLISHED','APPLICATION_CREDENTIAL_ROTATED','APPLICATION_DISABLED')")).isEqualTo(5);

        grantPlatformPermissions(owner.accountId(), "APPLICATION", List.of("VIEW", "MANAGE", "PUBLISH"));
        String platformToken = platformLogin(owner.username());
        ResponseEntity<Map> platformCreated = exchange("/api/applications", HttpMethod.POST, platformToken, Map.of(
                "code", "platform_flow_bridge", "name", "平台 Flow 桥梁", "applicationType", "SERVICE"));
        assertThat(platformCreated.getStatusCode()).isEqualTo(HttpStatus.OK);
        long platformApplicationId = number(map(data(platformCreated).get("application")).get("id"));
        Map<String, Object> platformGrant = Map.of(
                "targetType", "PLATFORM", "resourceType", "FLOW", "resourceId", "platform-contract",
                "actionCode", "START", "dataScope", Map.of("type", "PLATFORM"),
                "rateLimit", Map.of("maxRequests", 20, "windowSeconds", 60,
                        "allowedIps", List.of("127.0.0.1")), "fields", List.of());
        ResponseEntity<Map> platformSaved = exchange("/api/applications/" + platformApplicationId + "/draft",
                HttpMethod.PUT, platformToken, Map.of(
                        "expectedVersion", 0, "name", "平台 Flow 桥梁", "description", "仅开放平台 Flow",
                        "applicationType", "SERVICE", "callbacks", List.of(), "grants", List.of(platformGrant)));
        assertThat(platformSaved.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> phantomTargetCheck = exchange(
                "/api/applications/" + platformApplicationId + "/publication-check",
                HttpMethod.GET, platformToken, null);
        assertThat(phantomTargetCheck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(phantomTargetCheck)).containsEntry("valid", false);
        assertThat(String.valueOf(data(phantomTargetCheck).get("issues")))
                .contains("APPLICATION_TARGET_RESOURCE_NOT_PUBLISHED", "platform-contract");
        assertThat(((List<?>) exchange("/api/applications", HttpMethod.GET, platformToken, null)
                .getBody().get("data"))).singleElement().satisfies(row ->
                assertThat(map(row)).containsEntry("id", (int) platformApplicationId)
                        .containsEntry("contextType", "PLATFORM").containsEntry("ownerSystemId", null));
        assertThat(((List<?>) exchange("/api/applications", HttpMethod.GET, owner.token(), null)
                .getBody().get("data"))).singleElement().satisfies(row ->
                assertThat(map(row)).containsEntry("id", (int) applicationId)
                        .containsEntry("contextType", "SYSTEM"));
    }

    @Test
    void signedApplicationCallIntersectsTargetMemberPermissionsAndPersistsIdempotentResultWithoutReplayWrites()
            throws Exception {
        Session owner = register("c41_bridge_owner", "c41-bridge-system", "c41-bridge@example.com");
        ResponseEntity<Map> flowCreated = exchange("/api/flows", HttpMethod.POST, owner.token(), Map.of(
                "code", "bridge_review", "name", "应用桥接复核", "description", "C41 target Flow"));
        long flowId = number(data(flowCreated).get("id"));
        ResponseEntity<Map> flowSaved = exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT,
                owner.token(), Map.of("expectedVersion", 0,
                        "nodes", List.of(node("start", "START", "开始", Map.of()),
                                node("end", "END", "结束", Map.of())),
                        "edges", List.of(edge("bridge-complete", "start", "end", 10))));
        assertThat(flowSaved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(exchange("/api/flows/" + flowId + "/publication-check", HttpMethod.GET,
                owner.token(), null))).containsEntry("valid", true);
        assertThat(exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST, owner.token(), Map.of(
                "expectedDraftRevision", 2, "changeSummary", "C41 应用调用目标版"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> appCreated = exchange("/api/applications", HttpMethod.POST, owner.token(), Map.of(
                "code", "bridge_caller", "name", "系统桥接调用方", "applicationType", "SERVICE"));
        Map<String, Object> issued = map(data(appCreated).get("issuedCredential"));
        long applicationId = number(map(data(appCreated).get("application")).get("id"));
        String clientId = String.valueOf(issued.get("clientId"));
        String clientSecret = String.valueOf(issued.get("clientSecret"));
        assertThat(issued).containsEntry("shownOnce", true);
        assertThat(String.valueOf(issued.get("secretReference"))).isEqualTo("app-secret/" + applicationId + "/v1");
        assertThat(jdbc.queryForObject("select signing_secret_ref from app_credential where application_id=?",
                String.class, applicationId)).startsWith("local-aesgcm:v1:").doesNotContain(clientSecret);

        Map<String, Object> grant = Map.of(
                "targetType", "SYSTEM", "targetSystemId", owner.systemId(), "targetTenantId", owner.tenantId(),
                "resourceType", "FLOW", "resourceId", "bridge_review", "actionCode", "START",
                "dataScope", Map.of("type", "SELF"),
                "rateLimit", Map.of("maxRequests", 1, "windowSeconds", 600,
                        "allowedIps", List.of("127.0.0.1")),
                "fields", List.of(
                        Map.of("fieldCode", "title", "readable", true, "writable", true),
                        Map.of("fieldCode", "variables", "readable", true, "writable", true)));
        assertThat(exchange("/api/applications/" + applicationId + "/draft", HttpMethod.PUT,
                owner.token(), Map.of("expectedVersion", 0, "name", "系统桥接调用方",
                        "description", "只允许发起 bridge_review", "applicationType", "SERVICE",
                        "callbacks", List.of(), "grants", List.of(grant))).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(exchange("/api/applications/" + applicationId + "/publication-check",
                HttpMethod.GET, owner.token(), null))).containsEntry("valid", true);
        assertThat(exchange("/api/applications/" + applicationId + "/publish", HttpMethod.POST,
                owner.token(), Map.of("expectedDraftRevision", 2, "changeSummary", "开放受控 Flow"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> callBody = new LinkedHashMap<>();
        callBody.put("resourceType", "FLOW");
        callBody.put("resourceId", "bridge_review");
        callBody.put("actionCode", "START");
        callBody.put("targetSystemId", owner.systemId());
        callBody.put("targetTenantId", owner.tenantId());
        callBody.put("requestedDataScope", Map.of("type", "SELF"));
        callBody.put("payload", Map.of("title", "C41 真实签名调用", "variables", Map.of("amount", 8800)));

        ResponseEntity<Map> success = signedApplicationCall(callBody, clientId, clientSecret,
                "c41-nonce-success", "c41-idempotent-flow", null);
        assertThat(success.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(success)).containsEntry("replayed", false).containsEntry("applicationId", (int) applicationId)
                .containsEntry("credentialVersion", 1).containsEntry("resourceId", "bridge_review");
        String requestId = String.valueOf(data(success).get("requestId"));
        long instanceId = number(map(map(data(success).get("result")).get("instance")).get("id"));
        assertThat(String.valueOf(data(success).get("targetReference"))).isEqualTo("FLOW_INSTANCE:" + instanceId);
        assertThat(count("flow_instance", "id=" + instanceId + " and business_type='APPLICATION_CALL' and business_id='"
                + requestId + "' and started_by_account_id=" + owner.accountId())).isOne();
        assertThat(count("app_call", "application_id=" + applicationId
                + " and status='SUCCESS' and target_reference='FLOW_INSTANCE:" + instanceId + "'"
                + " and permission_snapshot_json is not null and response_json is not null")).isOne();

        ResponseEntity<Map> idempotentReplay = signedApplicationCall(callBody, clientId, clientSecret,
                "c41-nonce-idempotent-replay", "c41-idempotent-flow", null);
        assertThat(idempotentReplay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(idempotentReplay)).containsEntry("replayed", true).containsEntry("requestId", requestId);
        assertThat(count("flow_instance", "flow_id=" + flowId)).isOne();
        assertThat(count("app_call", "application_id=" + applicationId + " and replay_count=1")).isOne();

        ResponseEntity<Map> nonceReplay = signedApplicationCall(callBody, clientId, clientSecret,
                "c41-nonce-idempotent-replay", "c41-idempotent-flow", null);
        assertThat(nonceReplay.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(nonceReplay.getBody().get("code")).isEqualTo("APPLICATION_REPLAY_DETECTED");

        Map<String, Object> expanded = new LinkedHashMap<>(callBody);
        expanded.put("targetTenantId", owner.tenantId() + 999);
        expanded.put("requestedDataScope", Map.of("type", "ALL"));
        ResponseEntity<Map> expandedScope = signedApplicationCall(expanded, clientId, clientSecret,
                "c41-nonce-expanded", "c41-expanded", null);
        assertThat(expandedScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(expandedScope.getBody().get("code")).isEqualTo("APPLICATION_TARGET_SCOPE_EXPANSION");

        Map<String, Object> expandedDataScopeBody = new LinkedHashMap<>(callBody);
        expandedDataScopeBody.put("requestedDataScope", Map.of("type", "ALL"));
        ResponseEntity<Map> expandedDataScope = signedApplicationCall(expandedDataScopeBody, clientId, clientSecret,
                "c41-nonce-expanded-data", "c41-expanded-data", null);
        assertThat(expandedDataScope.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(expandedDataScope.getBody().get("code")).isEqualTo("APPLICATION_DATA_SCOPE_EXPANSION");
        assertThat(count("flow_instance", "flow_id=" + flowId)).isOne();

        ResponseEntity<Map> invalidSignature = signedApplicationCall(callBody, clientId, clientSecret,
                "c41-nonce-bad-signature", "c41-bad-signature", "0".repeat(64));
        assertThat(invalidSignature.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalidSignature.getBody().get("code")).isEqualTo("APPLICATION_SIGNATURE_INVALID");

        ResponseEntity<Map> rateLimited = signedApplicationCall(callBody, clientId, clientSecret,
                "c41-nonce-rate", "c41-new-business-call", null);
        assertThat(rateLimited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(rateLimited.getBody().get("code")).isEqualTo("APPLICATION_RATE_LIMITED");

        Session outsider = register("c41_bridge_outsider", "c41-outsider-system", "c41-outsider@example.com");
        jdbc.update("update app_definition set created_by_account_id=? where id=?", outsider.accountId(), applicationId);
        ResponseEntity<Map> missingTargetMember = signedApplicationCall(callBody, clientId, clientSecret,
                "c41-nonce-no-member", "c41-no-target-member", null);
        assertThat(missingTargetMember.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missingTargetMember.getBody().get("code")).isEqualTo("APPLICATION_TARGET_MEMBER_MISSING");
        assertThat(count("flow_instance", "flow_id=" + flowId)).isOne();

        ResponseEntity<Map> callLogs = exchange("/api/applications/" + applicationId + "/calls",
                HttpMethod.GET, owner.token(), null);
        assertThat(callLogs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) callLogs.getBody().get("data")).hasSize(6);
        assertThat(count("app_call_nonce", "application_id=" + applicationId)).isEqualTo(7);
        assertThat(count("audit_event", "event_code in ('APPLICATION_CALL_SUCCEEDED','APPLICATION_CALL_REJECTED')"
                + " and object_id in ('" + applicationId + "','" + instanceId + "')")).isGreaterThanOrEqualTo(5);

        long expectedVersion = number(data(exchange("/api/applications/" + applicationId,
                HttpMethod.GET, owner.token(), null)).get("version"));
        ResponseEntity<Map> savedAfterCalls = exchange("/api/applications/" + applicationId + "/draft",
                HttpMethod.PUT, owner.token(), Map.of(
                        "expectedVersion", expectedVersion,
                        "name", "系统桥接调用方",
                        "description", "历史调用后仍可安全更新授权草稿",
                        "applicationType", "SERVICE",
                        "callbacks", List.of(),
                        "grants", List.of(grant)));
        assertThat(savedAfterCalls.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("app_call", "application_id=" + applicationId)).isEqualTo(6);
        assertThat(count("app_call", "application_id=" + applicationId + " and grant_id is not null")).isZero();
        assertThat(String.valueOf(exchange("/api/applications/" + applicationId + "/calls",
                HttpMethod.GET, owner.token(), null).getBody().get("data")))
                .contains("FLOW_INSTANCE:", "permissionSnapshot");
    }

    @Test
    void publishedMessageTemplateRendersIdempotentInboxDeliveryAndRechecksTargetPermissionOnOpen() {
        Session owner = register("c42_message_owner", "c42-message-system", "c42-owner@example.com");
        Session recipientHome = register("c42_message_recipient", "c42-recipient-home", "c42-recipient@example.com");
        long recipientRoleId = addMemberWithPermissions(
                owner, recipientHome.accountId(), "MESSAGE", List.of("VIEW"), "MESSAGE_VIEWER");
        Session recipient = enterTenant(recipientHome, owner.systemId(), owner.tenantId(),
                platformLogin(recipientHome.username()));

        long flowId = number(data(exchange("/api/flows", HttpMethod.POST, owner.token(), Map.of(
                "code", "c42_message_target", "name", "消息安全跳转目标", "description", "C42 message target"))).get("id"));
        assertThat(exchange("/api/flows/" + flowId + "/draft", HttpMethod.PUT, owner.token(), Map.of(
                "expectedVersion", 0,
                "nodes", List.of(node("start", "START", "开始", Map.of()), node("end", "END", "结束", Map.of())),
                "edges", List.of(edge("finish", "start", "end", 10)))).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange("/api/flows/" + flowId + "/publish", HttpMethod.POST, owner.token(), Map.of(
                "expectedDraftRevision", 2, "changeSummary", "C42 消息跳转目标版")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> started = exchange("/api/flow-runtime/instances", HttpMethod.POST, owner.token(), Map.of(
                "flowId", flowId, "title", "C42 消息关联流程", "variables", Map.of(),
                "idempotencyKey", "c42-message-target-instance"));
        long instanceId = number(map(data(started).get("instance")).get("id"));

        ResponseEntity<Map> savedTemplate = exchange("/api/message-templates", HttpMethod.POST, owner.token(), Map.of(
                "code", "FLOW_RESULT_NOTICE", "name", "流程结果通知", "channel", "EMAIL",
                "subjectTemplate", "流程 {{title}} 已更新", "contentTemplate", "{{actor}} 处理了流程 {{title}}",
                "requiredVariables", List.of("title", "actor")));
        assertThat(savedTemplate.getStatusCode()).isEqualTo(HttpStatus.OK);
        long templateId = number(data(savedTemplate).get("id"));
        assertThat(data(savedTemplate)).containsEntry("status", "DRAFT").containsEntry("draftRevision", 1);
        ResponseEntity<Map> publishedTemplate = exchange("/api/message-templates/" + templateId + "/publish",
                HttpMethod.POST, owner.token(), Map.of());
        assertThat(publishedTemplate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(publishedTemplate)).containsEntry("status", "PUBLISHED").containsEntry("publishedVersion", 1);

        int messagesBeforeInvalid = count("msg_message", "system_id=" + owner.systemId());
        ResponseEntity<Map> missingVariable = exchange("/api/messages/events", HttpMethod.POST, owner.token(), Map.of(
                "templateCode", "FLOW_RESULT_NOTICE", "sourceType", "FLOW_EVENT", "dedupKey", "c42-missing-variable",
                "recipientAccountIds", List.of(recipientHome.accountId()), "variables", Map.of("title", "缺少处理人"),
                "targetType", "FLOW_INSTANCE", "targetId", String.valueOf(instanceId), "sensitivity", "IMPORTANT"));
        assertThat(missingVariable.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(missingVariable.getBody().get("code")).isEqualTo("MESSAGE_TEMPLATE_VARIABLE_MISSING");
        assertThat(count("msg_message", "system_id=" + owner.systemId())).isEqualTo(messagesBeforeInvalid);

        Map<String, Object> event = Map.of(
                "templateCode", "FLOW_RESULT_NOTICE", "sourceType", "FLOW_EVENT", "dedupKey", "c42-flow-result-1",
                "recipientAccountIds", List.of(recipientHome.accountId()),
                "variables", Map.of("title", "合同复核", "actor", "流程引擎"),
                "targetType", "FLOW_INSTANCE", "targetId", String.valueOf(instanceId), "sensitivity", "IMPORTANT");
        ResponseEntity<Map> sent = exchange("/api/messages/events", HttpMethod.POST, owner.token(), event);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        long messageId = number(data(sent).get("id"));
        assertThat(data(sent)).containsEntry("subject", "流程 合同复核 已更新")
                .containsEntry("content", "流程引擎 处理了流程 合同复核")
                .containsEntry("recipientStatus", "UNREAD");
        List<?> deliveries = (List<?>) data(sent).get("deliveries");
        assertThat(deliveries).hasSize(2);
        assertThat(deliveries.toString()).contains("IN_APP", "DELIVERED", "EMAIL", "RETRY_PENDING");
        long externalDeliveryId = deliveries.stream().map(this::map)
                .filter(item -> "EMAIL".equals(item.get("channel"))).map(item -> number(item.get("id")))
                .findFirst().orElseThrow();

        ResponseEntity<Map> idempotent = exchange("/api/messages/events", HttpMethod.POST, owner.token(), event);
        assertThat(idempotent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(number(data(idempotent).get("id"))).isEqualTo(messageId);
        assertThat(count("msg_message", "system_id=" + owner.systemId() + " and source_id='c42-flow-result-1'")).isOne();
        assertThat(count("msg_recipient", "message_id=" + messageId)).isOne();

        ResponseEntity<Map> firstRetry = exchange("/api/messages/deliveries/" + externalDeliveryId + "/retry",
                HttpMethod.POST, owner.token(), Map.of());
        assertThat(firstRetry.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> finalRetry = exchange("/api/messages/deliveries/" + externalDeliveryId + "/retry",
                HttpMethod.POST, owner.token(), Map.of());
        assertThat(finalRetry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(count("msg_delivery", "id=" + externalDeliveryId
                + " and status='FAILED' and attempt_count=3")).isOne();
        ResponseEntity<Map> exhaustedRetry = exchange("/api/messages/deliveries/" + externalDeliveryId + "/retry",
                HttpMethod.POST, owner.token(), Map.of());
        assertThat(exhaustedRetry.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exhaustedRetry.getBody().get("code")).isEqualTo("MESSAGE_DELIVERY_NOT_RETRYABLE");
        assertThat(count("msg_delivery", "message_id=" + messageId + " and channel='IN_APP' and status='DELIVERED'")).isOne();

        ResponseEntity<Map> inbox = exchange("/api/messages?status=ACTIVE&sourceType=ALL",
                HttpMethod.GET, recipient.token(), null);
        assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(inbox)).containsEntry("unreadCount", 1);
        assertThat((List<?>) data(inbox).get("messages")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("id", (int) messageId)
                        .containsEntry("targetCurrentlyAccessible", false));
        ResponseEntity<Map> deniedOpen = exchange("/api/messages/" + messageId + "/open",
                HttpMethod.POST, recipient.token(), Map.of());
        assertThat(deniedOpen.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deniedOpen.getBody().get("code")).isEqualTo("MESSAGE_TARGET_PERMISSION_DENIED");
        assertThat(count("msg_recipient", "message_id=" + messageId + " and status='UNREAD'")).isOne();

        grantSystemRoleAction(owner.systemId(), owner.tenantId(), recipientRoleId, "FLOW", "SYSTEM", "VIEW");
        recipient = enterTenant(recipientHome, owner.systemId(), owner.tenantId(),
                platformLogin(recipientHome.username()));
        ResponseEntity<Map> opened = exchange("/api/messages/" + messageId + "/open",
                HttpMethod.POST, recipient.token(), Map.of());
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(data(opened).get("route"))).contains("/systems/" + owner.systemId(),
                "instanceId=" + instanceId);
        assertThat(data(exchange("/api/messages/unread-count", HttpMethod.GET, recipient.token(), null)))
                .containsEntry("count", 0);

        assertThat(exchange("/api/messages/" + messageId + "/archive", HttpMethod.POST,
                recipient.token(), Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) data(exchange("/api/messages?status=ACTIVE&sourceType=ALL", HttpMethod.GET,
                recipient.token(), null)).get("messages")).isEmpty();
        assertThat((List<?>) data(exchange("/api/messages?status=ARCHIVED&sourceType=ALL", HttpMethod.GET,
                recipient.token(), null)).get("messages")).singleElement();
        assertThat((List<?>) data(exchange("/api/messages?status=ALL&sourceType=ALL", HttpMethod.GET,
                recipientHome.token(), null)).get("messages")).isEmpty();
        assertThat(count("msg_template_version", "template_id=" + templateId)).isOne();
        assertThat(count("audit_event", "system_id=" + owner.systemId()
                + " and event_code in ('MESSAGE_TEMPLATE_SAVED','MESSAGE_TEMPLATE_PUBLISHED','MESSAGE_CREATED',"
                + "'MESSAGE_TARGET_OPENED','MESSAGE_ARCHIVED')")).isGreaterThanOrEqualTo(5);

        grantPlatformPermissions(owner.accountId(), "MESSAGE", List.of("VIEW", "MANAGE"));
        String platformToken = platformLogin(owner.username());
        long platformTemplateId = number(data(exchange("/api/message-templates", HttpMethod.POST, platformToken,
                Map.of("code", "PLATFORM_ROUTE_NOTICE", "name", "平台路由通知", "channel", "IN_APP",
                        "subjectTemplate", "平台入口已更新", "contentTemplate", "打开平台 Flow 工作区",
                        "requiredVariables", List.of()))).get("id"));
        assertThat(exchange("/api/message-templates/" + platformTemplateId + "/publish", HttpMethod.POST,
                platformToken, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> platformMessage = exchange("/api/messages/events", HttpMethod.POST, platformToken,
                Map.of("templateCode", "PLATFORM_ROUTE_NOTICE", "sourceType", "MANUAL_TEST",
                        "dedupKey", "c42-platform-route", "recipientAccountIds", List.of(owner.accountId()),
                        "variables", Map.of(), "targetType", "PLATFORM_ROUTE", "targetRoute", "/platform/flows",
                        "sensitivity", "NORMAL"));
        long platformMessageId = number(data(platformMessage).get("id"));
        ResponseEntity<Map> platformOpened = exchange("/api/messages/" + platformMessageId + "/open",
                HttpMethod.POST, platformToken, Map.of());
        assertThat(platformOpened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(platformOpened)).containsEntry("route", "/platform/flows")
                .containsEntry("switchSystemId", null).containsEntry("targetId", null);
    }

    @Test
    void controlledFileUploadScansPersistsReferencesAndProtectsBytesAcrossContexts() throws Exception {
        Session owner = register("c42_file_owner", "c42-file-system", "c42-file@example.com");
        Session outsider = register("c42_file_outsider", "c42-file-other", "c42-file-other@example.com");
        byte[] content = "C42 controlled file content\n".getBytes(StandardCharsets.UTF_8);
        String contentSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        ResponseEntity<Map> started = exchange("/api/files/uploads", HttpMethod.POST, owner.token(), Map.of(
                "originalName", "../contract-note.txt", "contentType", "text/plain",
                "expectedSize", content.length, "expectedSha256", contentSha));
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        long sessionId = number(data(started).get("id"));
        String uploadToken = String.valueOf(data(started).get("uploadToken"));
        assertThat(data(started)).containsEntry("status", "PENDING").containsEntry("shownOnce", true)
                .containsEntry("originalName", "contract-note.txt");
        assertThat(uploadToken).hasSize(64);

        ResponseEntity<Map> uploaded = uploadFile(owner.token(), sessionId, uploadToken, content);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        long fileId = number(data(uploaded).get("id"));
        assertThat(data(uploaded)).containsEntry("status", "ACTIVE").containsEntry("scanStatus", "CLEAN")
                .containsEntry("previewStatus", "AVAILABLE").containsEntry("sha256", contentSha);
        assertThat((List<?>) data(uploaded).get("scans")).singleElement().satisfies(item ->
                assertThat(map(item)).containsEntry("scanner", "BUILTIN_SIGNATURE").containsEntry("status", "CLEAN"));
        assertThat(count("file_upload_session", "id=" + sessionId + " and status='COMPLETED'")).isOne();
        assertThat(count("file_security_scan", "file_id=" + fileId + " and status='CLEAN'")).isOne();

        ResponseEntity<Map> referenced = exchange("/api/files/" + fileId + "/references", HttpMethod.POST,
                owner.token(), Map.of("ownerType", "ACCOUNT", "ownerId", String.valueOf(owner.accountId()),
                        "fieldCode", "profile_document", "referenceType", "DOCUMENT"));
        assertThat(referenced.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> references = (List<?>) data(referenced).get("references");
        assertThat(references).hasSize(1);
        long referenceId = number(map(references.getFirst()).get("id"));

        ResponseEntity<byte[]> preview = binary("/api/files/" + fileId + "/preview", owner.token());
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(preview.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline");
        assertThat(preview.getBody()).containsExactly(content);
        ResponseEntity<byte[]> download = binary("/api/files/" + fileId + "/download", owner.token());
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment");
        assertThat(download.getBody()).containsExactly(content);

        ResponseEntity<Map> inUse = exchange("/api/files/" + fileId, HttpMethod.DELETE, owner.token(), null);
        assertThat(inUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(inUse.getBody().get("code")).isEqualTo("FILE_IN_USE");
        assertThat(exchange("/api/files/" + fileId + "/references/" + referenceId, HttpMethod.DELETE,
                owner.token(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> deleted = exchange("/api/files/" + fileId, HttpMethod.DELETE, owner.token(), null);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(deleted)).containsEntry("status", "DELETED").containsEntry("bytesRemoved", true);
        assertThat(binary("/api/files/" + fileId + "/download", owner.token()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        byte[] expected = "expected-size".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<Map> mismatchSession = exchange("/api/files/uploads", HttpMethod.POST, owner.token(), Map.of(
                "originalName", "interrupted.txt", "contentType", "text/plain", "expectedSize", expected.length,
                "expectedSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected))));
        long mismatchSessionId = number(data(mismatchSession).get("id"));
        ResponseEntity<Map> mismatch = uploadFile(owner.token(), mismatchSessionId,
                String.valueOf(data(mismatchSession).get("uploadToken")), "short".getBytes(StandardCharsets.UTF_8));
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(mismatch.getBody().get("code")).isEqualTo("FILE_SIZE_MISMATCH");
        assertThat(count("file_upload_session", "id=" + mismatchSessionId + " and status='FAILED'")).isOne();
        assertThat(count("file_object", "upload_session_id=" + mismatchSessionId)).isZero();
        assertThat(count("audit_event", "event_code='FILE_UPLOAD_FAILED' and object_id='"
                + mismatchSessionId + "' and result_code='FILE_SIZE_MISMATCH'")).isOne();

        byte[] unsafe = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE"
                .getBytes(StandardCharsets.US_ASCII);
        String unsafeSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(unsafe));
        ResponseEntity<Map> unsafeSession = exchange("/api/files/uploads", HttpMethod.POST, owner.token(), Map.of(
                "originalName", "eicar.txt", "contentType", "text/plain", "expectedSize", unsafe.length,
                "expectedSha256", unsafeSha));
        ResponseEntity<Map> quarantined = uploadFile(owner.token(), number(data(unsafeSession).get("id")),
                String.valueOf(data(unsafeSession).get("uploadToken")), unsafe);
        assertThat(quarantined.getStatusCode()).isEqualTo(HttpStatus.OK);
        long unsafeFileId = number(data(quarantined).get("id"));
        assertThat(data(quarantined)).containsEntry("status", "QUARANTINED")
                .containsEntry("scanStatus", "BLOCKED").containsEntry("previewStatus", "BLOCKED");
        ResponseEntity<Map> blockedReference = exchange("/api/files/" + unsafeFileId + "/references",
                HttpMethod.POST, owner.token(), Map.of("ownerType", "ACCOUNT",
                        "ownerId", String.valueOf(owner.accountId()), "referenceType", "DOCUMENT"));
        assertThat(blockedReference.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blockedReference.getBody().get("code")).isEqualTo("FILE_NOT_PUBLISHABLE");
        assertThat(binary("/api/files/" + unsafeFileId + "/download", owner.token()).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> isolatedList = exchange("/api/files", HttpMethod.GET, outsider.token(), null);
        assertThat(isolatedList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) isolatedList.getBody().get("data")).isEmpty();
        assertThat(binary("/api/files/" + unsafeFileId + "/download", outsider.token()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(count("audit_event", "system_id=" + owner.systemId()
                + " and event_code in ('FILE_UPLOAD_COMPLETED','FILE_UPLOAD_QUARANTINED',"
                + "'FILE_REFERENCE_ADDED','FILE_REFERENCE_REMOVED','FILE_DELETED')")).isGreaterThanOrEqualTo(5);
    }

    private Map<String, Object> bindingRequest(
            long moduleId, long flowId, int priority, boolean replaceExisting, boolean nonExclusive) {
        return Map.of("moduleId", moduleId, "triggerEvent", "CREATE", "flowId", flowId,
                "executionMode", "AFTER_EXECUTION", "priorityOrder", priority,
                "conditionExpression", "amount > 10", "mutuallyExclusive", !nonExclusive,
                "replaceExisting", replaceExisting);
    }

    private long seedPublishedModule(Session owner, String code) {
        long memberId = jdbc.queryForObject("select id from sys_member where system_id=? and account_id=?",
                Long.class, owner.systemId(), owner.accountId());
        jdbc.update("insert into cfg_module_group(system_id,owner_tenant_id,code,name,sort_order,status,"
                        + "created_by_member_id,version) values(?,?,?,?,?,?,?,0)",
                owner.systemId(), owner.tenantId(), code + "_group", code + " group", 10, "ACTIVE", memberId);
        long groupId = jdbc.queryForObject("select id from cfg_module_group where owner_tenant_id=? and code=?",
                Long.class, owner.tenantId(), code + "_group");
        jdbc.update("insert into cfg_module(system_id,owner_tenant_id,group_id,code,name,status,draft_revision,"
                        + "created_by_member_id,version) values(?,?,?,?,?,'DRAFT',2,?,0)",
                owner.systemId(), owner.tenantId(), groupId, code, code, memberId);
        long moduleId = jdbc.queryForObject("select id from cfg_module where owner_tenant_id=? and code=?",
                Long.class, owner.tenantId(), code);
        jdbc.update("insert into cfg_module_version(system_id,owner_tenant_id,module_id,version_number,draft_revision,"
                        + "schema_hash,snapshot_json,change_summary,published_by_member_id) values(?,?,?,1,1,"
                        + "?,'{}','C34 published base',?)",
                owner.systemId(), owner.tenantId(), moduleId, "0".repeat(64), memberId);
        long versionId = jdbc.queryForObject("select id from cfg_module_version where module_id=? and version_number=1",
                Long.class, moduleId);
        jdbc.update("insert into cfg_module_publication(system_id,owner_tenant_id,module_id,current_version_id,"
                        + "updated_by_member_id,version) values(?,?,?,?,?,0)",
                owner.systemId(), owner.tenantId(), moduleId, versionId, memberId);
        return moduleId;
    }

    private long addTenantWithWildcard(Session owner, String code) {
        jdbc.update("update sys_system set tenant_mode='MULTI' where id=?", owner.systemId());
        jdbc.update("insert into sys_tenant(system_id,code,name,is_main,main_marker,creator_account_id,status,version) "
                        + "values(?,?,?,0,null,?,'ACTIVE',0)",
                owner.systemId(), code, "C34 " + code, owner.accountId());
        long tenantId = jdbc.queryForObject("select id from sys_tenant where system_id=? and code=?",
                Long.class, owner.systemId(), code);
        long systemMemberId = jdbc.queryForObject("select id from sys_member where system_id=? and account_id=?",
                Long.class, owner.systemId(), owner.accountId());
        jdbc.update("insert into sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status,version) "
                + "values(?,?,?,1,'ACTIVE',0)", owner.systemId(), tenantId, systemMemberId);
        long tenantMemberId = jdbc.queryForObject("select id from sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, tenantId, systemMemberId);
        jdbc.update("insert into sys_role(system_id,tenant_id,code,name,built_in,status,version) "
                + "values(?,?,?,'C34 tenant admin',0,'ACTIVE',0)", owner.systemId(), tenantId, "C34_" + code);
        long roleId = jdbc.queryForObject("select id from sys_role where tenant_id=? and code=?",
                Long.class, tenantId, "C34_" + code);
        jdbc.update("insert into sys_member_role(tenant_id,tenant_member_id,role_id) values(?,?,?)",
                tenantId, tenantMemberId, roleId);
        grantSystemRoleAction(owner.systemId(), tenantId, roleId, "*", "*", "*");
        return tenantId;
    }

    private long addMemberWithPermissions(
            Session owner, long accountId, String resourceType, List<String> actions, String rolePrefix) {
        jdbc.update("update sys_system set tenant_mode='MULTI' where id=?", owner.systemId());
        jdbc.update("insert into sys_member(system_id,account_id,display_name,status,version) "
                + "values(?,?,?,'ACTIVE',0)", owner.systemId(), accountId, "C34 viewer");
        long systemMemberId = jdbc.queryForObject("select id from sys_member where system_id=? and account_id=?",
                Long.class, owner.systemId(), accountId);
        jdbc.update("insert into sys_tenant_member(system_id,tenant_id,system_member_id,tenant_admin,status,version) "
                + "values(?,?,?,0,'ACTIVE',0)", owner.systemId(), owner.tenantId(), systemMemberId);
        long tenantMemberId = jdbc.queryForObject("select id from sys_tenant_member where tenant_id=? and system_member_id=?",
                Long.class, owner.tenantId(), systemMemberId);
        String code = "C34_" + rolePrefix + "_" + accountId;
        jdbc.update("insert into sys_role(system_id,tenant_id,code,name,built_in,status,version) "
                + "values(?,?,?,'C34 log viewer',0,'ACTIVE',0)", owner.systemId(), owner.tenantId(), code);
        long roleId = jdbc.queryForObject("select id from sys_role where tenant_id=? and code=?",
                Long.class, owner.tenantId(), code);
        jdbc.update("insert into sys_member_role(tenant_id,tenant_member_id,role_id) values(?,?,?)",
                owner.tenantId(), tenantMemberId, roleId);
        for (String action : actions) {
            grantSystemRoleAction(owner.systemId(), owner.tenantId(), roleId, resourceType, "SYSTEM", action);
        }
        return roleId;
    }

    private void grantSystemRoleAction(
            long systemId, long tenantId, long roleId, String resourceType, String resourceCode, String action) {
        jdbc.update("insert ignore into sys_role_permission(system_id,tenant_id,role_id,resource_type,resource_code,"
                        + "action_code,data_scope_type,effect,version) values(?,?,?,?,? ,?,'TENANT','ALLOW',0)",
                systemId, tenantId, roleId, resourceType, resourceCode, action);
        jdbc.update("insert into core_cache_epoch(context_key,cache_namespace,epoch_value,reason,version) "
                        + "values(?,'AUTHORIZATION',1,'C34 test permission change',0) "
                        + "on duplicate key update epoch_value=epoch_value+1,reason=values(reason),version=version+1",
                "system:" + systemId + ":tenant:" + tenantId);
    }

    private Session enterTenant(Session session, long tenantId) {
        return enterTenant(session, session.systemId(), tenantId, session.token());
    }

    private Session enterTenant(Session session, long systemId, long tenantId, String entryToken) {
        ResponseEntity<Map> entered = exchange("/api/systems/" + systemId + "/tenants/" + tenantId
                + "/enter", HttpMethod.POST, entryToken, Map.of(
                        "previousSystemId", session.systemId(), "previousTenantId", session.tenantId()));
        assertThat(entered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = String.valueOf(((Map<?, ?>) data(entered).get("tokens")).get("accessToken"));
        return new Session(token, session.username(), session.accountId(), systemId, tenantId);
    }

    private Map<String, Object> logUpdate(
            int version, String date, String title, String content, String status,
            Map<String, Object> customValues, String reason) {
        return Map.of("expectedVersion", version, "workDate", date, "title", title, "content", content,
                "durationMinutes", 150, "status", status, "customValues", customValues,
                "revisionReason", reason);
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> result = data(response);
        return new Session((String) ((Map<?, ?>) result.get("tokens")).get("accessToken"), username,
                number(result.get("accountId")), number(result.get("systemId")), number(result.get("tenantId")));
    }

    private void grantPlatformPermissions(long accountId, String resourceType, List<String> actions) {
        long platformId = jdbc.queryForObject("select platform_id from plat_member where account_id=?", Long.class,
                accountId);
        long memberId = jdbc.queryForObject("select id from plat_member where account_id=?", Long.class, accountId);
        String roleCode = "C32_" + resourceType + "_" + accountId;
        List<Long> roleIds = jdbc.queryForList("select id from plat_role where platform_id=? and code=?",
                Long.class, platformId, roleCode);
        long roleId;
        if (roleIds.isEmpty()) {
            jdbc.update("insert into plat_role(platform_id,code,name,built_in,status,version) values(?,?,?,?,?,?)",
                    platformId, roleCode, "C32 " + resourceType, false, "ACTIVE", 0);
            roleId = jdbc.queryForObject("select id from plat_role where platform_id=? and code=?", Long.class,
                    platformId, roleCode);
            jdbc.update("insert into plat_member_role(platform_id,member_id,role_id) values(?,?,?)",
                    platformId, memberId, roleId);
        } else {
            roleId = roleIds.getFirst();
        }
        for (String action : actions) {
            jdbc.update("insert ignore into plat_role_permission(platform_id,role_id,resource_type,resource_code,"
                            + "action_code,data_scope_type,effect,version) values(?,?,?,?,?,?,?,?)",
                    platformId, roleId, resourceType, "PLATFORM", action, "PLATFORM", "ALLOW", 0);
        }
    }

    private String platformLogin(String username) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/login",
                Map.of("username", username, "password", "correct-password"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return String.valueOf(data(response).get("accessToken"));
    }

    private ResponseEntity<Map> signedApplicationCall(
            Map<String, Object> body, String clientId, String clientSecret, String nonce,
            String idempotencyKey, String signatureOverride) throws Exception {
        String rawBody = objectMapper.writeValueAsString(body);
        String timestamp = Long.toString(System.currentTimeMillis());
        String requestHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawBody.getBytes(StandardCharsets.UTF_8)));
        String canonical = clientId + "\n" + timestamp + "\n" + nonce + "\n" + idempotencyKey + "\n" + requestHash;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = signatureOverride == null
                ? HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)))
                : signatureOverride;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-App-Client-Id", clientId);
        headers.set("X-App-Timestamp", timestamp);
        headers.set("X-App-Nonce", nonce);
        headers.set("X-App-Idempotency-Key", idempotencyKey);
        headers.set("X-App-Signature", signature);
        return http.exchange("/api/application-access/v1/calls", HttpMethod.POST,
                new HttpEntity<>(rawBody, headers), Map.class);
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> uploadFile(String token, long sessionId, String uploadToken, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("X-Upload-Token", uploadToken);
        return http.exchange("/api/files/uploads/" + sessionId + "/content", HttpMethod.PUT,
                new HttpEntity<>(content, headers), Map.class);
    }

    private ResponseEntity<byte[]> binary(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(null, headers), byte[].class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }

    private record Session(String token, String username, long accountId, long systemId, long tenantId) {
    }

    private record PublishedFlow(long flowId, long versionId) {
    }
}
