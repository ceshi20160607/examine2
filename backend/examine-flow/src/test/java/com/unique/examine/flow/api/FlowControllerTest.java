package com.unique.examine.flow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.interaction.FlowInteractionMutationService;
import com.unique.examine.flow.interaction.FlowInteractionService;
import com.unique.examine.flow.interaction.FlowInteractionServiceFactory;
import com.unique.examine.flow.interaction.memory.InMemoryFlowInteractionRepository;
import com.unique.examine.flow.service.ApprovalWorkflowService;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import com.unique.examine.flow.service.FlowMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FlowControllerTest {
    private static final long SYSTEM = 100;
    private static final long TENANT = 200;
    private static final Set<String> ALL_PERMISSIONS = Set.of(
            FlowPermissions.DEFINITION_MANAGE,
            FlowPermissions.INSTANCE_START,
            FlowPermissions.INSTANCE_DECIDE,
            FlowPermissions.INSTANCE_READ,
            FlowPermissions.INSTANCE_WITHDRAW,
            FlowPermissions.INSTANCE_TERMINATE,
            FlowPermissions.INSTANCE_URGE,
            FlowPermissions.INSTANCE_COMMENT,
            FlowPermissions.INSTANCE_TRANSFER,
            FlowPermissions.INSTANCE_ADD_SIGN,
            FlowPermissions.INSTANCE_RETURN,
            FlowPermissions.INSTANCE_CLAIM,
            FlowPermissions.INSTANCE_CANCEL_CLAIM,
            FlowPermissions.INSTANCE_REDUCE_SIGN,
            FlowPermissions.INSTANCE_COPY,
            FlowPermissions.DELEGATION_MANAGE
    );

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private MemoryMemberMessages messages;
    private MemoryRecordFlows recordFlows;
    private MemoryApproverDirectory approverDirectory;
    private Set<Long> inactiveMemberIds;

    @BeforeEach
    void setUp() {
        var services = new MemoryRequestServices();
        var interactions = new MemoryInteractionServices(services);
        messages = new MemoryMemberMessages();
        recordFlows = new MemoryRecordFlows();
        approverDirectory = new MemoryApproverDirectory();
        inactiveMemberIds = ConcurrentHashMap.newKeySet();
        mvc = standaloneSetup(new FlowController(
                        services,
                        new FlowMutationService(
                                services,
                                new MemoryIdempotency(),
                                json,
                                (systemId, tenantId, memberId) ->
                                        inactiveMemberIds.contains(memberId)
                                                ? Optional.empty()
                                                : Optional.of(
                                                        new RuntimeActiveMemberFacade.ActiveMember(
                                                                memberId,
                                                                null
                                                        )
                                                ),
                                approverDirectory,
                                recordFlows),
                        interactions,
                        new FlowInteractionMutationService(
                                interactions,
                                new MemoryIdempotency(),
                                json,
                                messages,
                                (systemId, tenantId, memberId) -> Optional.of(
                                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null)
                                ))))
                .setControllerAdvice(new TestErrorHandler())
                .build();
    }

    @Test
    void recordMemberFieldCatalogHasFrozenPathEnvelopeAndManagePermission() throws Exception {
        var allowed = perform(
                get("/api/v1/systems/{systemId}/flow/approver-sources/"
                                + "record-member-fields", SYSTEM)
                        .param("moduleCode", "work_order"),
                session(10, Set.of(FlowPermissions.DEFINITION_MANAGE))
        );
        var denied = perform(
                get("/api/v1/systems/{systemId}/flow/approver-sources/"
                                + "record-member-fields", SYSTEM)
                        .param("moduleCode", "work_order"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );

        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.body().at("/data/items").isArray()).isTrue();
        assertThat(denied.status()).isEqualTo(403);
    }

    @Test
    void decisionEvidencePolicyAndCommentTemplateLifecycleUseFrozenApi()
            throws Exception {
        var manager = session(
                10, Set.of(FlowPermissions.DEFINITION_MANAGE));
        var createdDefinition = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Evidence approval",
                                  "approverId":"20",
                                  "decisionEvidencePolicy":{
                                    "minimumAttachments":1,
                                    "maximumAttachments":3,
                                    "allowedMimeFamilies":["PDF","IMAGE"],
                                    "signatureMode":"REQUIRED"
                                  }
                                }
                                """),
                manager
        );
        assertThat(createdDefinition.status()).isEqualTo(201);
        assertThat(createdDefinition.body().at(
                "/data/decisionEvidencePolicy/minimumAttachments").asInt())
                .isEqualTo(1);
        assertThat(createdDefinition.body().at(
                "/data/decisionEvidencePolicy/signatureMode").asText())
                .isEqualTo("REQUIRED");
        var definitionId = createdDefinition.body()
                .at("/data/definitionId").asLong();
        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/"
                                + "{definitionId}:publish",
                        SYSTEM, definitionId),
                manager
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().at(
                "/data/decisionEvidencePolicy/allowedMimeFamilies"))
                .extracting(JsonNode::asText)
                .containsExactly("IMAGE", "PDF");

        var createdTemplate = perform(
                post("/api/v1/systems/{systemId}/flow/"
                                + "decision-comment-templates", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Approve",
                                  "body":"Approved with evidence"
                                }
                                """),
                manager
        );
        assertThat(createdTemplate.status()).isEqualTo(201);
        var templateId = createdTemplate.body()
                .at("/data/templateId").asLong();
        var revisedTemplate = perform(
                put("/api/v1/systems/{systemId}/flow/"
                                + "decision-comment-templates/{templateId}",
                        SYSTEM, templateId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Approve v2",
                                  "body":"Approved from v2"
                                }
                                """),
                manager
        );
        assertThat(revisedTemplate.status()).isEqualTo(200);
        assertThat(revisedTemplate.body().at(
                "/data/currentVersion").asInt()).isEqualTo(2);

        var deactivated = perform(
                post("/api/v1/systems/{systemId}/flow/"
                                + "decision-comment-templates/{templateId}:deactivate",
                        SYSTEM, templateId),
                manager
        );
        assertThat(deactivated.body().at("/data/status").asText())
                .isEqualTo("INACTIVE");

        var approverCatalog = perform(
                get("/api/v1/systems/{systemId}/flow/"
                                + "decision-comment-templates", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertPage(approverCatalog, 1, 20, 0);
        var managerCatalog = perform(
                get("/api/v1/systems/{systemId}/flow/"
                                + "decision-comment-templates", SYSTEM),
                manager
        );
        assertPage(managerCatalog, 1, 20, 1);
        assertThat(managerCatalog.body().at("/data/items/0/status").asText())
                .isEqualTo("INACTIVE");
    }

    @Test
    void boundStartAndTerminalDecisionExposeTheImmutableRecordSnapshot() throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var startPermissions = Set.of(
                FlowPermissions.INSTANCE_START,
                "module.record.view.purchase_order"
        );

        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "bound-start-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "businessKey":"PO-2026-0008",
                                  "recordBinding":{
                                    "moduleCode":"purchase_order",
                                    "recordId":"901"
                                  }
                                }
                                """),
                session(10, startPermissions)
        );

        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/recordBinding/moduleCode").asText())
                .isEqualTo("purchase_order");
        assertThat(started.body().at("/data/recordBinding/recordId").asText())
                .isEqualTo("901");
        assertThat(recordFlows.binds).singleElement().satisfies(binding -> {
            assertThat(binding.effectivePermissions()).isEqualTo(startPermissions);
            assertThat(binding.moduleCode()).isEqualTo("purchase_order");
            assertThat(binding.recordId()).isEqualTo(901L);
        });

        var instanceId = started.body().at("/data/instanceId").asLong();
        var approved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"approved\"}"),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(approved.body().at("/data/recordBinding/recordId").asText())
                .isEqualTo("901");
        assertThat(recordFlows.transitions).singleElement().satisfies(transition -> {
            assertThat(transition.instanceId()).isEqualTo(instanceId);
            assertThat(transition.status())
                    .isEqualTo(RuntimeRecordFlowFacade.FlowStatus.APPROVED);
            assertThat(transition.actorMemberId()).isEqualTo(20L);
        });

        var detail = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                        SYSTEM, instanceId),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        );
        assertThat(detail.body().at("/data/recordBinding/moduleCode").asText())
                .isEqualTo("purchase_order");
        assertThat(detail.body().at("/data/recordBinding/recordId").asText())
                .isEqualTo("901");
    }

    @Test
    void partialRecordBindingFailsWithTheFrozenUnprocessableCode() throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);

        var response = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "invalid-binding-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "businessKey":"invalid-binding",
                                  "recordBinding":{"moduleCode":"purchase_order"}
                                }
                                """),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().path("code").asText())
                .isEqualTo("FLOW_RECORD_BINDING_INVALID");
        assertThat(recordFlows.binds).isEmpty();
    }

    @Test
    void startableCatalogUsesStartPermissionAndExposesOnlyLatestPublishedTenantVersions() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var definitionId = createAndPublish(owner, 20);
        var revised = perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Expense approval v2","approverId":"20"}
                                """),
                owner
        );
        assertThat(revised.status()).isEqualTo(200);
        var republished = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(republished.body().at("/data/version").asInt()).isEqualTo(2);

        perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {"name":"Unpublished draft","approverId":"20"}
                                """),
                owner
        );
        createAndPublish(session(30, 300, ALL_PERMISSIONS), 40);

        var catalog = perform(
                get("/api/v1/systems/{systemId}/flow/startable-definitions?page=1&size=100", SYSTEM),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        assertPage(catalog, 1, 100, 1);
        assertThat(catalog.body().at("/data/items/0/definitionId").asText())
                .isEqualTo(Long.toString(definitionId));
        assertThat(catalog.body().at("/data/items/0/name").asText())
                .isEqualTo("Expense approval v2");
        assertThat(catalog.body().at("/data/items/0/latestVersion").asInt()).isEqualTo(2);
        assertThat(catalog.body().at("/data/items/0/publishedAt").asText()).isNotBlank();

        var denied = perform(
                get("/api/v1/systems/{systemId}/flow/startable-definitions", SYSTEM),
                session(10, Set.of(FlowPermissions.DEFINITION_MANAGE))
        );
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.body().path("code").asText()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void manualStartRequiresKeyAndReplaysOnlyTheSameCanonicalPayload() throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var startOnly = session(10, Set.of(FlowPermissions.INSTANCE_START));
        var path = "/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances";

        var missing = perform(
                post(path, SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("{\"businessKey\":\"expense-key-required\"}"),
                startOnly
        );
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");

        var first = perform(
                post(path, SYSTEM, definitionId)
                        .header("Idempotency-Key", "manual-replay-1")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"expense-replay\"}"),
                startOnly
        );
        var replay = perform(
                post(path, SYSTEM, definitionId)
                        .header("Idempotency-Key", "manual-replay-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "recordBinding": null,
                                  "businessKey": "expense-replay",
                                  "definitionVersion": null
                                }
                                """),
                startOnly
        );
        assertThat(first.status()).isEqualTo(201);
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body().path("data")).isEqualTo(first.body().path("data"));

        var conflict = perform(
                post(path, SYSTEM, definitionId)
                        .header("Idempotency-Key", "manual-replay-1")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"expense-changed\"}"),
                startOnly
        );
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.body().path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void definitionTriggerCanBeCreatedPublishedChangedAndRemovedAsASnapshot() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Purchase activation",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_ACTIVATED",
                                    "priority":200,
                                    "exclusive":false,
                                    "conditions":[
                                      {"fieldCode":"amount","operator":"GTE","value":100},
                                      {"fieldCode":"remark","operator":"NOT_EMPTY"}
                                    ]
                                  },
                                  "recordStatusMapping":{
                                    "fieldCode":"approval_status",
                                    "approvedValue":"101",
                                    "rejectedValue":"102",
                                    "withdrawnValue":"103",
                                    "terminatedValue":"104"
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/triggerBinding/moduleCode").asText())
                .isEqualTo("purchase_order");
        assertThat(created.body().at("/data/triggerBinding/priority").asInt())
                .isEqualTo(200);
        assertThat(created.body().at("/data/triggerBinding/exclusive").asBoolean())
                .isFalse();
        assertThat(created.body().at("/data/triggerBinding/conditions/0/value").asInt())
                .isEqualTo(100);
        assertThat(created.body().at("/data/triggerBinding/conditions/1/operator").asText())
                .isEqualTo("NOT_EMPTY");
        assertThat(created.body().at("/data/triggerBinding/conditions/1/value").isNull())
                .isTrue();
        assertThat(created.body().at("/data/recordStatusMapping/fieldCode").asText())
                .isEqualTo("approval_status");
        assertThat(created.body().at("/data/recordStatusMapping/terminatedValue").asText())
                .isEqualTo("104");
        var definitionId = created.body().at("/data/definitionId").asLong();

        var firstPublished = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(firstPublished.body().at("/data/triggerBinding/event").asText())
                .isEqualTo("RECORD_ACTIVATED");
        assertThat(firstPublished.body().at("/data/triggerBinding/exclusive").asBoolean())
                .isFalse();
        assertThat(firstPublished.body().at("/data/triggerBinding/conditions").size())
                .isEqualTo(2);
        assertThat(firstPublished.body().at("/data/recordStatusMapping/approvedValue").asText())
                .isEqualTo("101");

        var revised = perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Manual purchase approval",
                                  "approverId":"20",
                                  "triggerBinding":null
                                }
                                """),
                owner
        );
        assertThat(revised.status()).isEqualTo(200);
        assertThat(revised.body().at("/data/triggerBinding").isNull()).isTrue();
        assertThat(revised.body().at("/data/recordStatusMapping").isNull()).isTrue();

        var secondPublished = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(secondPublished.body().at("/data/version").asInt()).isEqualTo(2);
        assertThat(secondPublished.body().at("/data/triggerBinding").isNull()).isTrue();
        assertThat(secondPublished.body().at("/data/recordStatusMapping").isNull()).isTrue();
    }

    @Test
    void conditionalGatewayChecksSimulatesPublishesAndPinsTheResolvedRuntimeRoute()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Conditional purchase approval",
                                  "approverIds":["30"],
                                  "gateway":{
                                    "branches":[
                                      {
                                        "code":"urgent",
                                        "name":"Urgent",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"urgent","operator":"EQ","value":true}
                                        ],
                                        "approverIds":["20"]
                                      },
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "conditions":[],
                                        "approverIds":["30"]
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/gateway/branches/0/code").asText())
                .isEqualTo("urgent");
        assertThat(created.body().at("/data/gateway/branches/1/defaultBranch").asBoolean())
                .isTrue();
        var definitionId = created.body().at("/data/definitionId").asLong();

        var simulation = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"gateway-preview",
                                  "values":{"urgent":true}
                                }
                                """),
                owner
        );
        assertThat(simulation.status()).isEqualTo(200);
        assertThat(simulation.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(simulation.body().at("/data/route/branchCode").asText())
                .isEqualTo("urgent");
        assertThat(simulation.body().at("/data/steps/0/approverId").asText())
                .isEqualTo("20");

        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().at("/data/gateway/branches/0/code").asText())
                .isEqualTo("urgent");

        var selected = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "gateway-selected-1")
                        .contentType("application/json")
                        .content("""
                                {"businessKey":"gateway-selected","values":{"urgent":true}}
                                """),
                owner
        );
        var fallback = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "gateway-default-1")
                        .contentType("application/json")
                        .content("""
                                {"businessKey":"gateway-default"}
                                """),
                owner
        );
        assertThat(selected.status()).isEqualTo(201);
        assertThat(selected.body().at("/data/approverIds/0").asText()).isEqualTo("20");
        assertThat(fallback.body().at("/data/approverIds/0").asText()).isEqualTo("30");

        perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {"name":"Sequential again","approverIds":["30"]}
                                """),
                owner
        );
        var restored = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/versions/1:restore",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(restored.body().at("/data/gateway/branches/0/code").asText())
                .isEqualTo("urgent");
    }

    @Test
    void approvalModesExposeConcurrentTasksAndCompleteByThePublishedRule()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Concurrent purchase approval",
                                  "approverIds":["30","40"],
                                  "approvalMode":"ALL",
                                  "gateway":{
                                    "branches":[
                                      {
                                        "code":"urgent",
                                        "name":"Urgent",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"urgent","operator":"EQ","value":true}
                                        ],
                                        "approverIds":["20","30"],
                                        "approvalMode":"ANY"
                                      },
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "conditions":[],
                                        "approverIds":["30","40"],
                                        "approvalMode":"ALL"
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/approvalMode").asText()).isEqualTo("ALL");
        assertThat(created.body().at("/data/gateway/branches/0/approvalMode").asText())
                .isEqualTo("ANY");
        var definitionId = created.body().at("/data/definitionId").asLong();

        var simulation = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"mode-preview",
                                  "values":{"urgent":true}
                                }
                                """),
                owner
        );
        assertThat(simulation.status()).isEqualTo(200);
        assertThat(simulation.body().at("/data/route/approvalMode").asText())
                .isEqualTo("ANY");
        assertThat(simulation.body().at("/data/route/activeApproverIds/0").asText())
                .isEqualTo("20");
        assertThat(simulation.body().at("/data/route/activeApproverIds/1").asText())
                .isEqualTo("30");

        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        ).status()).isEqualTo(200);
        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "approval-mode-any-1")
                        .contentType("application/json")
                        .content("""
                                {"businessKey":"approval-mode-any","values":{"urgent":true}}
                                """),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/approvalMode").asText()).isEqualTo("ANY");
        assertThat(started.body().at("/data/activeApproverIds").size()).isEqualTo(2);
        var instanceId = started.body().at("/data/instanceId").asLong();

        for (var memberId : List.of(20L, 30L)) {
            var tasks = perform(
                    get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING", SYSTEM),
                    session(memberId, ALL_PERMISSIONS)
            );
            assertThat(tasks.body().at("/data/items/0/instanceId").asLong())
                    .isEqualTo(instanceId);
        }

        var rejected = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:reject",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"reason\":\"not mine\"}"),
                session(20, ALL_PERMISSIONS)
        );
        assertThat(rejected.status()).isEqualTo(200);
        assertThat(rejected.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(rejected.body().at("/data/rejectedApproverIds/0").asText())
                .isEqualTo("20");
        assertThat(rejected.body().at("/data/activeApproverIds/0").asText())
                .isEqualTo("30");

        assertThat(perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING", SYSTEM),
                session(20, ALL_PERMISSIONS)
        ).body().at("/data/total").asLong()).isZero();

        var approved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"accepted\"}"),
                session(30, ALL_PERMISSIONS)
        );
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.body().at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(approved.body().at("/data/approvedApproverIds/0").asText())
                .isEqualTo("30");
    }

    @Test
    void quorumGatewaySavesChecksSimulatesPublishesAndKeepsTheStartedThreshold()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Missing quorum rule",
                                  "approverIds":["20","30"],
                                  "approvalMode":"QUORUM"
                                }
                                """),
                owner
        );
        assertThat(invalid.status()).isEqualTo(422);

        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Urgent two of three approval",
                                  "approverIds":["40"],
                                  "approvalMode":"SEQUENTIAL",
                                  "gateway":{
                                    "branches":[
                                      {
                                        "code":"urgent",
                                        "name":"Urgent",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"urgent","operator":"EQ","value":true}
                                        ],
                                        "approverIds":["20","30","40"],
                                        "approvalMode":"QUORUM",
                                        "quorumRule":{"type":"COUNT","value":2}
                                      },
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "conditions":[],
                                        "approverIds":["40"],
                                        "approvalMode":"SEQUENTIAL"
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/gateway/branches/0/quorumRule/type").asText())
                .isEqualTo("COUNT");
        assertThat(created.body().at("/data/gateway/branches/0/quorumRule/value").asInt())
                .isEqualTo(2);
        var definitionId = created.body().at("/data/definitionId").asLong();

        var checked = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(checked.status()).isEqualTo(200);
        assertThat(checked.body().at("/data/verdict").asText()).isEqualTo("READY");

        var simulated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"quorum-preview",
                                  "values":{"urgent":true}
                                }
                                """),
                owner
        );
        assertThat(simulated.status()).isEqualTo(200);
        assertThat(simulated.body().at("/data/route/branchCode").asText())
                .isEqualTo("urgent");
        assertThat(simulated.body().at("/data/route/approvalMode").asText())
                .isEqualTo("QUORUM");
        assertThat(simulated.body().at("/data/route/requiredApprovals").asInt())
                .isEqualTo(2);

        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().at("/data/gateway/branches/0/quorumRule/value").asInt())
                .isEqualTo(2);

        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "quorum-gateway-start")
                        .contentType("application/json")
                        .content("""
                                {"businessKey":"quorum-gateway","values":{"urgent":true}}
                                """),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/approvalMode").asText()).isEqualTo("QUORUM");
        assertThat(started.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);
        assertThat(started.body().at("/data/activeApproverIds").size()).isEqualTo(3);
        var instanceId = started.body().at("/data/instanceId").asLong();

        var revised = perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Urgent unanimous approval",
                                  "approverIds":["20","30","40"],
                                  "approvalMode":"QUORUM",
                                  "quorumRule":{"type":"PERCENTAGE","value":100}
                                }
                                """),
                owner
        );
        assertThat(revised.status()).isEqualTo(200);

        var first = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"first vote\"}"),
                session(20, ALL_PERMISSIONS)
        );
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(first.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);

        var second = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"threshold reached\"}"),
                session(30, ALL_PERMISSIONS)
        );
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(second.body().at("/data/activeApproverIds").size()).isZero();
        assertThat(second.body().at("/data/requiredApprovals").asInt()).isEqualTo(2);

        var cancelled = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING", SYSTEM),
                session(40, ALL_PERMISSIONS)
        );
        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.body().at("/data/total").asLong()).isZero();
    }

    @Test
    void deadlinePoliciesSaveCheckSimulatePublishAndStartWithAbsoluteSnapshots()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid deadline",
                                  "approverIds":["20"],
                                  "deadlinePolicy":{
                                    "timeoutMinutes":0,
                                    "timeoutAction":"AUTO_APPROVE"
                                  }
                                }
                                """),
                owner
        );
        assertThat(invalid.status()).isEqualTo(422);
        assertThat(invalid.body().path("code").asText())
                .isEqualTo("FLOW_DEADLINE_POLICY_INVALID");

        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Gateway deadline approval",
                                  "approverIds":["30"],
                                  "approvalMode":"SEQUENTIAL",
                                  "deadlinePolicy":{
                                    "timeoutMinutes":30,
                                    "timeoutAction":"NONE"
                                  },
                                  "gateway":{
                                    "branches":[
                                      {
                                        "code":"urgent",
                                        "name":"Urgent",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"urgent","operator":"EQ","value":true}
                                        ],
                                        "approverIds":["20"],
                                        "approvalMode":"SEQUENTIAL",
                                        "deadlinePolicy":{
                                          "timeoutMinutes":15,
                                          "remindBeforeMinutes":5,
                                          "timeoutAction":"AUTO_REJECT"
                                        }
                                      },
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "conditions":[],
                                        "approverIds":["30"],
                                        "approvalMode":"SEQUENTIAL",
                                        "deadlinePolicy":{
                                          "timeoutMinutes":30,
                                          "timeoutAction":"NONE"
                                        }
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status())
                .withFailMessage("deadline create response: %s", created.body())
                .isEqualTo(201);
        assertThat(created.body().at("/data/deadlinePolicy/timeoutMinutes").asInt())
                .isEqualTo(30);
        assertThat(created.body()
                .at("/data/gateway/branches/1/deadlinePolicy/timeoutAction").asText())
                .isEqualTo("NONE");
        var definitionId = created.body().at("/data/definitionId").asLong();

        var checked = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(checked.status()).isEqualTo(200);
        assertThat(checked.body().at("/data/verdict").asText()).isEqualTo("READY");

        var simulated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"deadline-preview",
                                  "values":{"urgent":true}
                                }
                                """),
                owner
        );
        assertThat(simulated.status()).isEqualTo(200);
        assertThat(simulated.body().at("/data/route/branchCode").asText())
                .isEqualTo("urgent");
        assertThat(simulated.body().at("/data/route/deadlinePolicy/timeoutMinutes").asInt())
                .isEqualTo(15);
        assertThat(simulated.body().at("/data/route/deadlinePolicy/timeoutAction").asText())
                .isEqualTo("AUTO_REJECT");

        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body()
                .at("/data/gateway/branches/0/deadlinePolicy/remindBeforeMinutes").asInt())
                .isEqualTo(5);

        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "deadline-gateway-start")
                        .contentType("application/json")
                        .content("""
                                {"businessKey":"deadline-gateway","values":{"urgent":true}}
                                """),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/deadline/policy/timeoutAction").asText())
                .isEqualTo("AUTO_REJECT");
        assertThat(started.body().at("/data/deadline/remindAt").asText())
                .isEqualTo("2026-07-25T08:10:00Z");
        assertThat(started.body().at("/data/deadline/dueAt").asText())
                .isEqualTo("2026-07-25T08:15:00Z");
        assertThat(started.body().at("/data/deadline/overdue").asBoolean()).isFalse();
    }

    @Test
    void decisionCommentPoliciesSaveCheckSimulatePublishStartAndRetryWithoutSideEffects()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid comment policy",
                                  "approverIds":["20"],
                                  "decisionCommentPolicy":{
                                    "approveRequired":true,
                                    "rejectRequired":true,
                                    "minimumLength":0
                                  }
                                }
                                """),
                owner
        );
        assertThat(invalid.status()).isEqualTo(422);
        assertThat(invalid.body().path("code").asText())
                .isEqualTo("FLOW_DECISION_COMMENT_POLICY_INVALID");

        var mismatched = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Mismatched comment policy",
                                  "approverIds":["20"],
                                  "decisionCommentPolicy":{
                                    "approveRequired":true,
                                    "rejectRequired":true,
                                    "minimumLength":5
                                  },
                                  "gateway":{"branches":[
                                    {
                                      "code":"urgent",
                                      "name":"Urgent",
                                      "defaultBranch":false,
                                      "conditions":[
                                        {"fieldCode":"urgent","operator":"EQ","value":true}
                                      ],
                                      "approverIds":["20"]
                                    },
                                    {
                                      "code":"default",
                                      "name":"Default",
                                      "defaultBranch":true,
                                      "conditions":[],
                                      "approverIds":["20"],
                                      "decisionCommentPolicy":{
                                        "approveRequired":false,
                                        "rejectRequired":true,
                                        "minimumLength":1
                                      }
                                    }
                                  ]}
                                }
                                """),
                owner
        );
        assertThat(mismatched.status()).isEqualTo(422);
        assertThat(mismatched.body().path("code").asText())
                .isEqualTo("FLOW_DECISION_COMMENT_POLICY_INVALID");

        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Gateway decision comment approval",
                                  "approverIds":["30"],
                                  "approvalMode":"SEQUENTIAL",
                                  "decisionCommentPolicy":{
                                    "approveRequired":false,
                                    "rejectRequired":true,
                                    "minimumLength":3
                                  },
                                  "gateway":{
                                    "branches":[
                                      {
                                        "code":"urgent",
                                        "name":"Urgent",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"urgent","operator":"EQ","value":true}
                                        ],
                                        "approverIds":["20"],
                                        "approvalMode":"SEQUENTIAL",
                                        "decisionCommentPolicy":{
                                          "approveRequired":true,
                                          "rejectRequired":true,
                                          "minimumLength":4
                                        }
                                      },
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "conditions":[],
                                        "approverIds":["30"],
                                        "approvalMode":"SEQUENTIAL",
                                        "decisionCommentPolicy":{
                                          "approveRequired":false,
                                          "rejectRequired":true,
                                          "minimumLength":3
                                        }
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status())
                .withFailMessage("decision comment create response: %s", created.body())
                .isEqualTo(201);
        assertThat(created.body()
                .at("/data/decisionCommentPolicy/rejectRequired").asBoolean()).isTrue();
        assertThat(created.body()
                .at("/data/gateway/branches/0/decisionCommentPolicy/minimumLength").asInt())
                .isEqualTo(4);
        var definitionId = created.body().at("/data/definitionId").asLong();

        var checked = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(checked.status()).isEqualTo(200);
        assertThat(checked.body().at("/data/verdict").asText()).isEqualTo("READY");

        var simulated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"decision-comment-preview",
                                  "values":{"urgent":true}
                                }
                                """),
                owner
        );
        assertThat(simulated.status()).isEqualTo(200);
        assertThat(simulated.body().at("/data/route/branchCode").asText())
                .isEqualTo("urgent");
        assertThat(simulated.body()
                .at("/data/route/decisionCommentPolicy/approveRequired").asBoolean()).isTrue();
        assertThat(simulated.body()
                .at("/data/route/decisionCommentPolicy/minimumLength").asInt()).isEqualTo(4);

        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body()
                .at("/data/gateway/branches/0/decisionCommentPolicy/minimumLength").asInt())
                .isEqualTo(4);

        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "decision-comment-gateway-start")
                        .contentType("application/json")
                        .content("""
                                {"businessKey":"decision-comment-gateway","values":{"urgent":true}}
                                """),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body()
                .at("/data/decisionCommentPolicy/approveRequired").asBoolean()).isTrue();
        assertThat(started.body()
                .at("/data/decisionCommentPolicy/minimumLength").asInt()).isEqualTo(4);
        var instanceId = started.body().at("/data/instanceId").asLong();

        var underLength = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"好\"}"),
                session(20, ALL_PERMISSIONS)
        );
        assertThat(underLength.status()).isEqualTo(400);
        assertThat(underLength.body().path("code").asText())
                .isEqualTo("FLOW_APPROVAL_COMMENT_REQUIRED");

        var unchanged = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                        SYSTEM, instanceId),
                owner
        );
        assertThat(unchanged.status()).isEqualTo(200);
        assertThat(unchanged.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(unchanged.body().at("/data/approvedApproverIds").size()).isZero();
        assertThat(unchanged.body().at("/data/currentStepIndex").asInt()).isZero();

        var retried = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"处理完成\"}"),
                session(20, ALL_PERMISSIONS)
        );
        assertThat(retried.status()).isEqualTo(200);
        assertThat(retried.body().at("/data/status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void delegationCreateListTaskDecisionAuditRevokeAndStableAuthorizationErrors()
            throws Exception {
        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/delegations", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "delegatorMemberId":"20",
                                  "delegateMemberId":"20",
                                  "startsAt":"2026-07-25T07:00:00Z",
                                  "endsAt":"2026-07-25T12:00:00Z"
                                }
                                """),
                session(20, Set.of())
        );
        assertThat(invalid.status()).isEqualTo(422);
        assertThat(invalid.body().path("code").asText())
                .isEqualTo("FLOW_DELEGATION_RULE_INVALID");

        var forbidden = perform(
                post("/api/v1/systems/{systemId}/flow/delegations", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "delegatorMemberId":"20",
                                  "delegateMemberId":"30",
                                  "startsAt":"2026-07-25T07:00:00Z",
                                  "endsAt":"2026-07-25T12:00:00Z"
                                }
                                """),
                session(30, Set.of())
        );
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().path("code").asText())
                .isEqualTo("FLOW_DELEGATION_FORBIDDEN");

        var created = perform(
                post("/api/v1/systems/{systemId}/flow/delegations", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "delegateMemberId":"30",
                                  "startsAt":"2026-07-25T07:00:00Z",
                                  "endsAt":"2026-07-25T12:00:00Z"
                                }
                                """),
                session(20, Set.of())
        );
        assertThat(created.status())
                .withFailMessage("delegation create response: %s", created.body())
                .isEqualTo(201);
        assertThat(created.body().at("/data/delegatorMemberId").asText())
                .isEqualTo("20");
        assertThat(created.body().at("/data/delegateMemberId").asText())
                .isEqualTo("30");
        assertThat(created.body().at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(created.body().at("/data/createdByMemberId").asText())
                .isEqualTo("20");
        var delegationRuleId = created.body().at("/data/delegationRuleId").asLong();

        var overlapping = perform(
                post("/api/v1/systems/{systemId}/flow/delegations", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "delegateMemberId":"30",
                                  "startsAt":"2026-07-25T08:00:00Z",
                                  "endsAt":"2026-07-25T10:00:00Z"
                                }
                                """),
                session(20, Set.of())
        );
        assertThat(overlapping.status()).isEqualTo(409);
        assertThat(overlapping.body().path("code").asText())
                .isEqualTo("FLOW_DELEGATION_CONFLICT");

        var ownRules = perform(
                get("/api/v1/systems/{systemId}/flow/delegations?page=1&size=20",
                        SYSTEM),
                session(20, Set.of())
        );
        assertPage(ownRules, 1, 20, 1);
        assertThat(ownRules.body().at("/data/items/0/delegationRuleId").asLong())
                .isEqualTo(delegationRuleId);

        var deniedList = perform(
                get("/api/v1/systems/{systemId}/flow/delegations"
                                + "?delegatorMemberId=20&page=1&size=20",
                        SYSTEM),
                session(30, Set.of())
        );
        assertThat(deniedList.status()).isEqualTo(403);
        assertThat(deniedList.body().path("code").asText())
                .isEqualTo("FLOW_DELEGATION_FORBIDDEN");

        var managedList = perform(
                get("/api/v1/systems/{systemId}/flow/delegations"
                                + "?delegatorMemberId=20&page=1&size=20",
                        SYSTEM),
                session(10, Set.of(FlowPermissions.DELEGATION_MANAGE))
        );
        assertPage(managedList, 1, 20, 1);

        var missing = perform(
                post("/api/v1/systems/{systemId}/flow/delegations/{delegationRuleId}/revoke",
                        SYSTEM, 999_999),
                session(10, Set.of(FlowPermissions.DELEGATION_MANAGE))
        );
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body().path("code").asText())
                .isEqualTo("FLOW_DELEGATION_NOT_FOUND");

        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var instanceId = startInstance(definitionId, "delegated-approval");
        var tasks = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING&page=1&size=20",
                        SYSTEM),
                session(30, Set.of(FlowPermissions.INSTANCE_READ))
        );
        assertPage(tasks, 1, 20, 1);
        assertThat(tasks.body().at("/data/items/0/instanceId").asLong())
                .isEqualTo(instanceId);
        assertThat(tasks.body().at("/data/items/0/approverIds/0").asText())
                .isEqualTo("20");
        assertThat(tasks.body()
                .at("/data/items/0/representedAuthorities/0/representedMemberId").asText())
                .isEqualTo("20");
        assertThat(tasks.body()
                .at("/data/items/0/representedAuthorities/0/delegationRuleId").asLong())
                .isEqualTo(delegationRuleId);

        var delegatedApproval = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "comment":"approved on behalf",
                                  "representedMemberId":"20"
                                }
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(delegatedApproval.status()).isEqualTo(200);
        assertThat(delegatedApproval.body().at("/data/status").asText())
                .isEqualTo("APPROVED");
        assertThat(delegatedApproval.body().at("/data/approverIds/0").asText())
                .isEqualTo("20");

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history",
                        SYSTEM, instanceId),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        );
        assertThat(history.status()).isEqualTo(200);
        var decision = history.body().at("/data/events/1");
        assertThat(decision.path("type").asText()).isEqualTo("APPROVED");
        assertThat(decision.path("actorId").asText()).isEqualTo("30");
        assertThat(decision.path("representedMemberId").asText()).isEqualTo("20");
        assertThat(decision.path("delegationRuleId").asLong()).isEqualTo(delegationRuleId);

        var revoked = perform(
                post("/api/v1/systems/{systemId}/flow/delegations/{delegationRuleId}/revoke",
                        SYSTEM, delegationRuleId),
                session(10, Set.of(FlowPermissions.DELEGATION_MANAGE))
        );
        assertThat(revoked.status()).isEqualTo(200);
        assertThat(revoked.body().at("/data/status").asText()).isEqualTo("REVOKED");
        assertThat(revoked.body().at("/data/revokedByMemberId").asText())
                .isEqualTo("10");

        var secondInstanceId = startInstance(definitionId, "revoked-delegation");
        var inactive = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:reject",
                        SYSTEM, secondInstanceId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "reason":"no longer authorized",
                                  "representedMemberId":"20"
                                }
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(inactive.status()).isEqualTo(403);
        assertThat(inactive.body().path("code").asText())
                .isEqualTo("FLOW_DELEGATION_INACTIVE");

        var unchanged = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                        SYSTEM, secondInstanceId),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        );
        assertThat(unchanged.status()).isEqualTo(200);
        assertThat(unchanged.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(unchanged.body().at("/data/rejectedApproverIds").size()).isZero();
    }

    @Test
    void parallelBranchesSimulateStartAndJoinThroughExplicitBranchDecisions()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Parallel purchase approval",
                                  "approverIds":["20","30"],
                                  "approvalMode":"SEQUENTIAL",
                                  "parallelGateway":{
                                    "branches":[
                                      {
                                        "code":"finance",
                                        "name":"Finance",
                                        "approverIds":["20","30"],
                                        "approvalMode":"SEQUENTIAL"
                                      },
                                      {
                                        "code":"owner",
                                        "name":"Owner",
                                        "approverIds":["40","50"],
                                        "approvalMode":"ANY"
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/parallelGateway/branches/0/code").asText())
                .isEqualTo("finance");
        var definitionId = created.body().at("/data/definitionId").asLong();

        var simulation = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {"requesterId":"10","businessKey":"parallel-preview"}
                                """),
                owner
        );
        assertThat(simulation.status()).isEqualTo(200);
        assertThat(simulation.body().at("/data/parallelRoutes").size()).isEqualTo(2);
        assertThat(simulation.body().at(
                "/data/parallelRoutes/0/activeApproverIds/0").asText()).isEqualTo("20");
        assertThat(simulation.body().at(
                "/data/parallelRoutes/1/activeApproverIds").size()).isEqualTo(2);

        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        ).status()).isEqualTo(200);
        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "parallel-start-1")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"parallel-1\"}"),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/parallelBranches").size()).isEqualTo(2);
        assertThat(started.body().at("/data/activeApproverIds").size()).isEqualTo(3);
        var instanceId = started.body().at("/data/instanceId").asLong();

        var financeStep = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}"
                                + "/branches/finance:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"finance one\"}"),
                session(20, ALL_PERMISSIONS)
        );
        assertThat(financeStep.status()).isEqualTo(200);
        assertThat(financeStep.body().at(
                "/data/parallelBranches/0/activeApproverIds/0").asText()).isEqualTo("30");

        var ownerDone = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}"
                                + "/branches/owner:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"owner accepted\"}"),
                session(40, ALL_PERMISSIONS)
        );
        assertThat(ownerDone.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(ownerDone.body().at("/data/parallelBranches/1/status").asText())
                .isEqualTo("APPROVED");

        var joined = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}"
                                + "/branches/finance:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"finance complete\"}"),
                session(30, ALL_PERMISSIONS)
        );
        assertThat(joined.body().at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(joined.body().at("/data/parallelBranches/0/status").asText())
                .isEqualTo("APPROVED");
    }

    @Test
    void inclusiveGatewaySelectsEveryMatchAndFallsBackOnlyOnZeroMatches()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Inclusive purchase approval",
                                  "approverIds":["20"],
                                  "approvalMode":"SEQUENTIAL",
                                  "inclusiveGateway":{
                                    "branches":[
                                      {
                                        "code":"urgent",
                                        "name":"Urgent",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"urgent","operator":"EQ","value":true}
                                        ],
                                        "approverIds":["20"],
                                        "approvalMode":"SEQUENTIAL"
                                      },
                                      {
                                        "code":"large",
                                        "name":"Large",
                                        "defaultBranch":false,
                                        "conditions":[
                                          {"fieldCode":"amount","operator":"GTE","value":1000}
                                        ],
                                        "approverIds":["30","40"],
                                        "approvalMode":"ANY"
                                      },
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "conditions":[],
                                        "approverIds":["50"],
                                        "approvalMode":"SEQUENTIAL"
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/inclusiveGateway/branches").size()).isEqualTo(3);
        var definitionId = created.body().at("/data/definitionId").asLong();

        var simulated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"inclusive-preview",
                                  "values":{"urgent":true,"amount":5000}
                                }
                                """),
                owner
        );
        assertThat(simulated.status()).isEqualTo(200);
        assertThat(simulated.body().at("/data/parallelRoutes"))
                .extracting(route -> route.path("branchCode").asText())
                .containsExactly("urgent", "large");

        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        ).status()).isEqualTo(200);
        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "inclusive-start-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "businessKey":"inclusive-1",
                                  "values":{"urgent":true,"amount":5000}
                                }
                                """),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("urgent", "large");
        var instanceId = started.body().at("/data/instanceId").asLong();

        var urgentApproved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}"
                                + "/branches/urgent:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"urgent accepted\"}"),
                session(20, ALL_PERMISSIONS)
        );
        assertThat(urgentApproved.body().at("/data/status").asText()).isEqualTo("PENDING");
        var joined = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}"
                                + "/branches/large:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"large accepted\"}"),
                session(30, ALL_PERMISSIONS)
        );
        assertThat(joined.body().at("/data/status").asText()).isEqualTo("APPROVED");

        var defaultStarted = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "inclusive-start-default")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"inclusive-default\",\"values\":{}}"),
                owner
        );
        assertThat(defaultStarted.status()).isEqualTo(201);
        assertThat(defaultStarted.body().at("/data/parallelBranches"))
                .extracting(branch -> branch.path("code").asText())
                .containsExactly("default");
    }

    @Test
    void periodicTriggerFreezesTheSavingMemberAndRejectsRecordFanoutFields() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Hourly approval",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":null,
                                    "event":"PERIODIC",
                                    "priority":0,
                                    "exclusive":true,
                                    "conditions":[],
                                    "startAt":"2026-07-30T01:00:00Z",
                                    "intervalMinutes":60
                                  }
                                }
                                """),
                owner
        );

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/triggerBinding/event").asText())
                .isEqualTo("PERIODIC");
        assertThat(created.body().at("/data/triggerBinding/moduleCode").isNull()).isTrue();
        assertThat(created.body().at("/data/triggerBinding/requesterMemberId").asText())
                .isEqualTo("10");
        assertThat(created.body().at("/data/triggerBinding/intervalMinutes").asInt())
                .isEqualTo(60);
        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM,
                        created.body().at("/data/definitionId").asLong()),
                owner
        );
        assertThat(published.body().at("/data/triggerBinding/startAt").asText())
                .isEqualTo("2026-07-30T01:00:00Z");
        assertThat(published.body().at("/data/triggerBinding/requesterMemberId").asText())
                .isEqualTo("10");

        var definitionId = created.body().at("/data/definitionId").asLong();
        assertThat(perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Manual replacement",
                                  "approverId":"30",
                                  "triggerBinding":null
                                }
                                """),
                owner
        ).status()).isEqualTo(200);
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        ).body().at("/data/version").asInt()).isEqualTo(2);

        var restored = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}"
                                + "/versions/{version}:restore",
                        SYSTEM, definitionId, 1),
                session(11, ALL_PERMISSIONS)
        );
        assertThat(restored.status()).isEqualTo(200);
        assertThat(restored.body().at("/data/revision").asInt()).isEqualTo(3);
        assertThat(restored.body().at("/data/name").asText()).isEqualTo("Hourly approval");
        assertThat(restored.body().at("/data/triggerBinding/requesterMemberId").asText())
                .isEqualTo("11");
        assertThat(published.body().at("/data/triggerBinding/requesterMemberId").asText())
                .isEqualTo("10");

        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid periodic approval",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"PERIODIC",
                                    "priority":0,
                                    "exclusive":true,
                                    "conditions":[],
                                    "startAt":"2026-07-30T01:00:00Z",
                                    "intervalMinutes":60
                                  }
                                }
                                """),
                owner
        );
        assertThat(invalid.status()).isEqualTo(422);
        assertThat(invalid.body().path("code").asText())
                .isEqualTo("FLOW_TRIGGER_BINDING_INVALID");
    }

    @Test
    void versionHistoryIsPagedAndRestoreCreatesANewDraftWithoutRewritingHistory() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Version one",
                                  "approverIds":["20","21"]
                                }
                                """),
                owner
        );
        var definitionId = created.body().at("/data/definitionId").asLong();
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        ).body().at("/data/version").asInt()).isOne();
        assertThat(perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Version two",
                                  "approverId":"30"
                                }
                                """),
                owner
        ).status()).isEqualTo(200);
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        ).body().at("/data/version").asInt()).isEqualTo(2);

        var firstPage = perform(
                get("/api/v1/systems/{systemId}/flow/definitions/{definitionId}"
                                + "/versions?page=1&size=1",
                        SYSTEM, definitionId),
                owner
        );
        var secondPage = perform(
                get("/api/v1/systems/{systemId}/flow/definitions/{definitionId}"
                                + "/versions?page=2&size=1",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(firstPage.status()).isEqualTo(200);
        assertThat(firstPage.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(firstPage.body().at("/data/items/0/version").asInt()).isEqualTo(2);
        assertThat(firstPage.body().at("/data/items/0/name").asText())
                .isEqualTo("Version two");
        assertThat(secondPage.body().at("/data/items/0/version").asInt()).isOne();
        assertThat(secondPage.body().at("/data/items/0/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "21");

        var restored = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}"
                                + "/versions/{version}:restore",
                        SYSTEM, definitionId, 1),
                session(11, ALL_PERMISSIONS)
        );
        assertThat(restored.status()).isEqualTo(200);
        assertThat(restored.body().at("/data/revision").asInt()).isEqualTo(3);
        assertThat(restored.body().at("/data/name").asText()).isEqualTo("Version one");
        assertThat(restored.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "21");

        var historyAfterRestore = perform(
                get("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/versions",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(historyAfterRestore.body().at("/data/total").asLong()).isEqualTo(2);
        var publishedRestore = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(publishedRestore.body().at("/data/version").asInt()).isEqualTo(3);
        assertThat(publishedRestore.body().at("/data/sourceRevision").asInt()).isEqualTo(3);
        assertThat(publishedRestore.body().at("/data/name").asText()).isEqualTo("Version one");

        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "restored-version-start")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"restored-version\"}"),
                owner
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/definitionVersion").asInt()).isEqualTo(3);

        assertThat(perform(
                get("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/versions",
                        SYSTEM, definitionId),
                session(10, TENANT + 1, ALL_PERMISSIONS)
        ).body().at("/code").asText()).isEqualTo("FLOW_DRAFT_NOT_FOUND");
        assertThat(perform(
                get("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/versions",
                        SYSTEM, definitionId),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        ).status()).isEqualTo(403);
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}"
                                + "/versions/{version}:restore",
                        SYSTEM, definitionId, 99),
                owner
        ).body().at("/code").asText()).isEqualTo("FLOW_VERSION_NOT_FOUND");
    }

    @Test
    void draftCheckAndSimulationSharePublishRulesAndDoNotCreateRuntimeState() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Preflight purchase approval",
                                  "approverIds":["20","30"],
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_ACTIVATED",
                                    "priority":80,
                                    "exclusive":true,
                                    "conditions":[{
                                      "fieldCode":"amount",
                                      "operator":"GTE",
                                      "value":100
                                    }]
                                  },
                                  "recordStatusMapping":{
                                    "fieldCode":"approval_status",
                                    "approvedValue":"101",
                                    "rejectedValue":"102",
                                    "withdrawnValue":"103",
                                    "terminatedValue":"104"
                                  }
                                }
                                """),
                owner
        );
        var definitionId = created.body().at("/data/definitionId").asLong();

        var ready = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                owner
        );
        var simulation = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "requesterId":"10",
                                  "businessKey":"PO-2026-0099",
                                  "trigger":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_ACTIVATED",
                                    "values":{"amount":150}
                                  }
                                }
                                """),
                owner
        );

        assertThat(ready.status()).isEqualTo(200);
        assertThat(ready.body().at("/data/verdict").asText()).isEqualTo("READY");
        assertThat(ready.body().at("/data/blockerCount").asInt()).isZero();
        assertThat(simulation.status()).isEqualTo(200);
        assertThat(simulation.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(simulation.body().at("/data/reason").asText())
                .isEqualTo("TRIGGER_MATCHED");
        assertThat(simulation.body().at("/data/steps"))
                .extracting(item -> item.path("approverId").asText())
                .containsExactly("20", "30");
        assertThat(simulation.body().at("/data/statusEffects/approvedValue").asText())
                .isEqualTo("101");
        assertThat(perform(
                get("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/versions",
                        SYSTEM, definitionId),
                owner
        ).body().at("/data/total").asLong()).isZero();
        assertThat(perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM),
                owner
        ).body().at("/data/total").asLong()).isZero();

        inactiveMemberIds.add(30L);
        var blocked = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                owner
        );
        var rejectedPublish = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(blocked.body().at("/data/verdict").asText()).isEqualTo("BLOCKED");
        assertThat(blocked.body().at("/data/issues/0/code").asText())
                .isEqualTo("APPROVER_INACTIVE");
        assertThat(blocked.body().at("/data/issues/0/path").asText())
                .isEqualTo("/approverIds/1");
        assertThat(rejectedPublish.status()).isEqualTo(422);
        assertThat(rejectedPublish.body().at("/code").asText())
                .isEqualTo("FLOW_DRAFT_CHECK_BLOCKED");

        var denied = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("{}"),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        );
        var foreignTenant = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                session(10, TENANT + 1, ALL_PERMISSIONS)
        );
        assertThat(denied.status()).isEqualTo(403);
        assertThat(foreignTenant.status()).isEqualTo(404);
        assertThat(foreignTenant.body().at("/code").asText())
                .isEqualTo("FLOW_DRAFT_NOT_FOUND");

        inactiveMemberIds.remove(30L);
        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().at("/data/version").asInt()).isOne();
    }

    @Test
    void organizationApproverSourcesResolveContextAndKeepStartedSnapshotsImmutable()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        approverDirectory.departmentLeaders.put(7L, 30L);
        approverDirectory.requesterManagers.put(10L, 20L);

        var leaderCreated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Department leader approval",
                                  "approverSource":{
                                    "kind":"DEPARTMENT_LEADER",
                                    "sourceId":"7"
                                  },
                                  "approvalMode":"ALL"
                                }
                                """),
                owner
        );
        var managerCreated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Requester manager approval",
                                  "approverSource":{"kind":"REQUESTER_MANAGER"},
                                  "approvalMode":"ANY"
                                }
                                """),
                owner
        );
        assertThat(leaderCreated.status()).isEqualTo(201);
        assertThat(leaderCreated.body().at("/data/approverSource/kind").asText())
                .isEqualTo("DEPARTMENT_LEADER");
        assertThat(leaderCreated.body().at("/data/approverSource/sourceId").asText())
                .isEqualTo("7");
        assertThat(managerCreated.status()).isEqualTo(201);
        assertThat(managerCreated.body().at("/data/approverSource/kind").asText())
                .isEqualTo("REQUESTER_MANAGER");
        assertThat(managerCreated.body().at("/data/approverSource/sourceId").isNull())
                .isTrue();
        var leaderDefinitionId = leaderCreated.body().at("/data/definitionId").asLong();
        var managerDefinitionId = managerCreated.body().at("/data/definitionId").asLong();

        var managerSimulation = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, managerDefinitionId)
                        .contentType("application/json")
                        .content("{\"requesterId\":\"10\"}"),
                owner
        );
        assertThat(managerSimulation.status()).isEqualTo(200);
        assertThat(managerSimulation.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(managerSimulation.body().at("/data/steps/0/approverId").asText())
                .isEqualTo("20");

        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, leaderDefinitionId),
                owner
        ).status()).isEqualTo(200);
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, managerDefinitionId),
                owner
        ).status()).isEqualTo(200);

        var firstLeader = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, leaderDefinitionId)
                        .header("Idempotency-Key", "org-leader-first")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"leader-first\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        var firstManager = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, managerDefinitionId)
                        .header("Idempotency-Key", "org-manager-first")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"manager-first\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        assertThat(firstLeader.body().at("/data/approverIds/0").asText()).isEqualTo("30");
        assertThat(firstManager.body().at("/data/approverIds/0").asText()).isEqualTo("20");

        approverDirectory.departmentLeaders.put(7L, 40L);
        approverDirectory.requesterManagers.put(10L, 30L);
        var secondLeader = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, leaderDefinitionId)
                        .header("Idempotency-Key", "org-leader-second")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"leader-second\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        var secondManager = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, managerDefinitionId)
                        .header("Idempotency-Key", "org-manager-second")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"manager-second\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        assertThat(secondLeader.body().at("/data/approverIds/0").asText()).isEqualTo("40");
        assertThat(secondManager.body().at("/data/approverIds/0").asText()).isEqualTo("30");

        var pinnedLeader = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                        SYSTEM, firstLeader.body().at("/data/instanceId").asLong()),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        );
        var pinnedManager = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                        SYSTEM, firstManager.body().at("/data/instanceId").asLong()),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        );
        assertThat(pinnedLeader.body().at("/data/approverIds/0").asText()).isEqualTo("30");
        assertThat(pinnedManager.body().at("/data/approverIds/0").asText()).isEqualTo("20");

        approverDirectory.requesterManagers.remove(10L);
        var totalBefore = perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        ).body().at("/data/total").asLong();
        var missingManager = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, managerDefinitionId)
                        .header("Idempotency-Key", "org-manager-missing")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"manager-missing\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        assertThat(missingManager.status()).isEqualTo(422);
        assertThat(missingManager.body().at("/code").asText())
                .isEqualTo("FLOW_APPROVER_SOURCE_EMPTY");
        assertThat(perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM),
                session(10, Set.of(FlowPermissions.INSTANCE_READ))
        ).body().at("/data/total").asLong()).isEqualTo(totalBefore);
    }

    @Test
    void organizationApproverSourceShapeAndPeriodicContextFailuresAreStable()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        approverDirectory.requesterManagers.put(10L, 20L);
        var managerWithSourceId = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid manager source",
                                  "approverSource":{
                                    "kind":"REQUESTER_MANAGER",
                                    "sourceId":"7"
                                  }
                                }
                                """),
                owner
        );
        var leaderWithoutDepartment = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid leader source",
                                  "approverSource":{"kind":"DEPARTMENT_LEADER"}
                                }
                                """),
                owner
        );
        assertThat(managerWithSourceId.status()).isEqualTo(422);
        assertThat(managerWithSourceId.body().at("/code").asText())
                .isEqualTo("FLOW_APPROVER_SOURCE_INVALID");
        assertThat(leaderWithoutDepartment.status()).isEqualTo(422);
        assertThat(leaderWithoutDepartment.body().at("/code").asText())
                .isEqualTo("FLOW_APPROVER_SOURCE_INVALID");

        var periodic = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid periodic manager",
                                  "approverSource":{"kind":"REQUESTER_MANAGER"},
                                  "triggerBinding":{
                                    "event":"PERIODIC",
                                    "priority":0,
                                    "exclusive":true,
                                    "startAt":"2026-08-01T00:00:00Z",
                                    "intervalMinutes":60
                                  }
                                }
                                """),
                owner
        );
        assertThat(periodic.status()).isEqualTo(201);
        var check = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, periodic.body().at("/data/definitionId").asLong()),
                owner
        );
        assertThat(check.body().at("/data/verdict").asText()).isEqualTo("BLOCKED");
        assertThat(check.body().at("/data/issues/0/code").asText())
                .isEqualTo("PERIODIC_REQUESTER_CONTEXT_UNAVAILABLE");
        assertThat(check.body().at("/data/issues/0/path").asText())
                .isEqualTo("/approverSource");
    }

    @Test
    void invalidDefinitionTriggerUsesTheFrozenUnprocessableCode() throws Exception {
        var response = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid trigger",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_ACTIVATED",
                                    "priority":1001,
                                    "exclusive":false
                                  }
                                }
                                """),
                session(10, ALL_PERMISSIONS)
        );

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().path("code").asText())
                .isEqualTo("FLOW_TRIGGER_BINDING_INVALID");
    }

    @Test
    void acceptsNewRecordEventsAndRejectsTheirTerminalStatusMapping() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Created record trigger",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_CREATED",
                                    "priority":100,
                                    "exclusive":false
                                  }
                                }
                                """),
                owner
        );

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/triggerBinding/event").asText())
                .isEqualTo("RECORD_CREATED");
        for (var event : List.of(
                "RECORD_UPDATED",
                "RECORD_DELETED",
                "RECORD_STATUS_CHANGED",
                "IMPORT_COMPLETED")) {
            var accepted = perform(
                    post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                            .contentType("application/json")
                            .content("""
                                    {
                                      "name":"%s trigger",
                                      "approverId":"20",
                                      "triggerBinding":{
                                        "moduleCode":"purchase_order",
                                        "event":"%s",
                                        "priority":100,
                                        "exclusive":false
                                      }
                                    }
                                    """.formatted(event, event)),
                    owner
            );
            assertThat(accepted.status()).isEqualTo(201);
            assertThat(accepted.body().at("/data/triggerBinding/event").asText())
                    .isEqualTo(event);
        }

        var incompatible = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid update mapping",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_UPDATED",
                                    "priority":100,
                                    "exclusive":false
                                  },
                                  "recordStatusMapping":{
                                    "fieldCode":"approval_status",
                                    "approvedValue":"101",
                                    "rejectedValue":"102",
                                    "withdrawnValue":"103",
                                    "terminatedValue":"104"
                                  }
                                }
                                """),
                owner
        );

        assertThat(incompatible.status()).isEqualTo(422);
        assertThat(incompatible.body().path("code").asText())
                .isEqualTo("FLOW_RECORD_STATUS_MAPPING_INVALID");
    }

    @Test
    void triggerConditionOperatorsEnforceTheirFrozenValueShapes() throws Exception {
        var response = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid condition",
                                  "approverId":"20",
                                  "triggerBinding":{
                                    "moduleCode":"purchase_order",
                                    "event":"RECORD_ACTIVATED",
                                    "priority":100,
                                    "exclusive":false,
                                    "conditions":[
                                      {"fieldCode":"amount","operator":"GTE","value":null}
                                    ]
                                  }
                                }
                                """),
                session(10, ALL_PERMISSIONS)
        );

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().path("code").asText())
                .isEqualTo("FLOW_TRIGGER_BINDING_INVALID");
    }

    @Test
    void invalidRecordStatusMappingUsesTheFrozenUnprocessableCode() throws Exception {
        var response = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Invalid status mapping",
                                  "approverId":"20",
                                  "recordStatusMapping":{
                                    "fieldCode":"approval_status",
                                    "approvedValue":"01",
                                    "rejectedValue":"102",
                                    "withdrawnValue":"103",
                                    "terminatedValue":"104"
                                  }
                                }
                                """),
                session(10, ALL_PERMISSIONS)
        );

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.body().path("code").asText())
                .isEqualTo("FLOW_RECORD_STATUS_MAPPING_INVALID");
    }

    @Test
    void authenticatedMembersCanCompleteTheDefinitionInstanceAndHistoryJourney() throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);

        var start = perform(post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                SYSTEM, definitionId)
                .header("Idempotency-Key", "journey-start-1")
                .contentType("application/json")
                .content("""
                        {"businessKey":"expense-001"}
                        """), session(10, Set.of(FlowPermissions.INSTANCE_START)));
        assertThat(start.status()).isEqualTo(201);
        assertThat(start.body().path("code").asText()).isEqualTo("OK");
        assertThat(start.body().path("data").path("requesterId").asText()).isEqualTo("10");
        assertThat(start.body().path("data").path("approverId").asText()).isEqualTo("20");
        var instanceId = start.body().path("data").path("instanceId").asLong();

        var approve = perform(post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("""
                        {"comment":"approved"}
                        """), session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(approve.status()).isEqualTo(200);
        assertThat(approve.body().path("data").path("status").asText()).isEqualTo("APPROVED");

        var history = perform(get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history",
                SYSTEM, instanceId), session(20, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body().path("data").path("events")).hasSize(2);
        assertThat(history.body().path("data").path("events").get(0).path("type").asText()).isEqualTo("STARTED");
        assertThat(history.body().path("data").path("events").get(1).path("type").asText()).isEqualTo("APPROVED");
    }

    @Test
    void refreshReadsReturnStablePagesStringIdsAndInstanceDetail() throws Exception {
        var firstDefinitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var secondDefinitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var firstInstanceId = startInstance(firstDefinitionId, "refresh-001");
        var secondInstanceId = startInstance(secondDefinitionId, "refresh-002");

        var definitions = perform(get("/api/v1/systems/{systemId}/flow/definitions?page=1&size=1",
                SYSTEM), session(10, Set.of(FlowPermissions.DEFINITION_MANAGE)));
        assertThat(definitions.status()).isEqualTo(200);
        assertThat(definitions.body().path("data").path("page").asInt()).isEqualTo(1);
        assertThat(definitions.body().path("data").path("size").asInt()).isEqualTo(1);
        assertThat(definitions.body().path("data").path("total").asLong()).isEqualTo(2);
        assertThat(definitions.body().path("data").path("items")).hasSize(1);
        assertThat(definitions.body().path("data").path("items").get(0).path("definitionId").isTextual()).isTrue();
        assertThat(definitions.body().path("data").path("items").get(0).path("definitionId").asLong())
                .isEqualTo(secondDefinitionId);

        var instances = perform(get("/api/v1/systems/{systemId}/flow/instances?page=1&size=1",
                SYSTEM), session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(instances.status()).isEqualTo(200);
        assertThat(instances.body().path("data").path("total").asLong()).isEqualTo(2);
        assertThat(instances.body().path("data").path("items")).hasSize(1);
        assertThat(instances.body().path("data").path("items").get(0).path("instanceId").isTextual()).isTrue();
        assertThat(instances.body().path("data").path("items").get(0).path("instanceId").asLong())
                .isEqualTo(secondInstanceId);

        var detail = perform(get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                SYSTEM, firstInstanceId), session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().path("data").path("instanceId").isTextual()).isTrue();
        assertThat(detail.body().path("data").path("definitionId").isTextual()).isTrue();
        assertThat(detail.body().path("data").path("requesterId").isTextual()).isTrue();
        assertThat(detail.body().path("data").path("approverId").isTextual()).isTrue();
        assertThat(detail.body().path("data").path("businessKey").asText()).isEqualTo("refresh-001");
        assertThat(detail.body().path("data").path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void instanceListAcceptsNativeStatusAndUtcCompletionDateFilters() throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var approvedId = startInstance(definitionId, "filtered-approved");
        var pendingId = startInstance(definitionId, "filtered-pending");
        var approved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, approvedId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"approved"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(approved.status()).isEqualTo(200);

        var terminal = perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM)
                        .param("status", "APPROVED")
                        .param("from", "2026-07-25")
                        .param("to", "2026-07-26")
                        .param("page", "1")
                        .param("size", "20"),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        var pending = perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM)
                        .param("status", "PENDING"),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        var exclusiveUpperBound = perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM)
                        .param("status", "APPROVED")
                        .param("to", "2026-07-25"),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        var invalidRange = perform(
                get("/api/v1/systems/{systemId}/flow/instances", SYSTEM)
                        .param("from", "2026-07-25")
                        .param("to", "2026-07-25"),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));

        assertThat(terminal.status()).isEqualTo(200);
        assertThat(terminal.body().at("/data/total").asLong()).isOne();
        assertThat(terminal.body().at("/data/items/0/instanceId").asLong())
                .isEqualTo(approvedId);
        assertThat(pending.body().at("/data/total").asLong()).isOne();
        assertThat(pending.body().at("/data/items/0/instanceId").asLong())
                .isEqualTo(pendingId);
        assertThat(exclusiveUpperBound.body().at("/data/total").asLong()).isZero();
        assertThat(invalidRange.status()).isEqualTo(400);
        assertThat(invalidRange.body().at("/code").asText())
                .isEqualTo("FLOW_REQUEST_INVALID");
    }

    @Test
    void approvalTasksAreApproverScopedFilteredAndStablyPaged() throws Exception {
        var ownDefinition = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var first = startInstance(ownDefinition, "task-001");
        var second = startInstance(ownDefinition, "task-002");
        var third = startInstance(ownDefinition, "task-003");
        var otherDefinition = createAndPublish(session(10, ALL_PERMISSIONS), 21);
        startInstance(otherDefinition, "other-task");

        var approver = session(20, Set.of(
                FlowPermissions.INSTANCE_READ,
                FlowPermissions.INSTANCE_DECIDE
        ));
        assertThat(perform(post(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, first)
                .contentType("application/json")
                .content("{\"comment\":\"approved\"}"), approver).status()).isEqualTo(200);
        assertThat(perform(post(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}:reject",
                SYSTEM, second)
                .contentType("application/json")
                .content("{\"reason\":\"rejected\"}"), approver).status()).isEqualTo(200);

        var pending = perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=PENDING&page=1&size=20",
                SYSTEM), approver);
        assertPage(pending, 1, 20, 1);
        assertThat(pending.body().at("/data/items/0/instanceId").asLong()).isEqualTo(third);

        var completedPageOne = perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=COMPLETED&page=1&size=1",
                SYSTEM), approver);
        var completedPageTwo = perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=COMPLETED&page=2&size=1",
                SYSTEM), approver);
        assertPage(completedPageOne, 1, 1, 2);
        assertPage(completedPageTwo, 2, 1, 2);
        assertThat(completedPageOne.body().at("/data/items/0/instanceId").asLong())
                .isEqualTo(second);
        assertThat(completedPageTwo.body().at("/data/items/0/instanceId").asLong())
                .isEqualTo(first);

        var all = perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=ALL&page=1&size=20",
                SYSTEM), approver);
        assertPage(all, 1, 20, 3);
        assertThat(all.body().at("/data/items"))
                .allMatch(item -> item.path("approverId").asLong() == 20);

        var otherTenant = perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=ALL&page=1&size=20",
                SYSTEM), session(20, TENANT + 1, Set.of(FlowPermissions.INSTANCE_READ)));
        assertPage(otherTenant, 1, 20, 0);

        var denied = perform(get("/api/v1/systems/{systemId}/flow/tasks", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.body().path("code").asText()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void sequentialApprovalPublishesASnapshotAndHandsOffTheCurrentTask() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "name", "Sequential approval",
                        "approverIds", List.of("20", "30")
                ))), owner);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/approverId").asText()).isEqualTo("20");
        assertThat(created.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "30");
        var definitionId = created.body().at("/data/definitionId").asLong();

        var revised = perform(put(
                "/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                SYSTEM, definitionId)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "name", "Sequential approval v2",
                        "approverIds", List.of("20", "30", "40")
                ))), owner);
        assertThat(revised.status()).isEqualTo(200);
        assertThat(revised.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "30", "40");

        var published = perform(post(
                "/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                SYSTEM, definitionId), owner);
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "30", "40");

        var started = perform(post(
                "/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                SYSTEM, definitionId)
                .header("Idempotency-Key", "sequential-start-1")
                .contentType("application/json")
                .content("{\"businessKey\":\"sequential-api\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START)));
        assertThat(started.status()).isEqualTo(201);
        var instanceId = started.body().at("/data/instanceId").asLong();
        assertThat(started.body().at("/data/approverId").asText()).isEqualTo("20");
        assertThat(started.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "30", "40");

        var firstApprover = session(20, Set.of(
                FlowPermissions.INSTANCE_READ,
                FlowPermissions.INSTANCE_DECIDE
        ));
        var secondApprover = session(30, Set.of(
                FlowPermissions.INSTANCE_READ,
                FlowPermissions.INSTANCE_DECIDE
        ));
        var thirdApprover = session(40, Set.of(
                FlowPermissions.INSTANCE_READ,
                FlowPermissions.INSTANCE_DECIDE
        ));
        assertPage(perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=PENDING",
                SYSTEM), firstApprover), 1, 20, 1);

        var firstDecision = perform(post(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("{\"comment\":\"step one\"}"), firstApprover);
        assertThat(firstDecision.status()).isEqualTo(200);
        assertThat(firstDecision.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(firstDecision.body().at("/data/approverId").asText()).isEqualTo("30");
        assertThat(firstDecision.body().at("/data/completedAt").isNull()).isTrue();
        assertPage(perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=PENDING",
                SYSTEM), firstApprover), 1, 20, 0);
        assertPage(perform(get(
                "/api/v1/systems/{systemId}/flow/tasks?status=PENDING",
                SYSTEM), secondApprover), 1, 20, 1);

        var staleApprover = perform(post(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("{\"comment\":\"stale\"}"), firstApprover);
        assertThat(staleApprover.status()).isEqualTo(403);
        assertThat(staleApprover.body().path("code").asText())
                .isEqualTo("FLOW_APPROVER_FORBIDDEN");

        var secondDecision = perform(post(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("{\"comment\":\"step two\"}"), secondApprover);
        assertThat(secondDecision.status()).isEqualTo(200);
        assertThat(secondDecision.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(secondDecision.body().at("/data/approverId").asText()).isEqualTo("40");

        var finalDecision = perform(post(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("{\"comment\":\"step three\"}"), thirdApprover);
        assertThat(finalDecision.status()).isEqualTo(200);
        assertThat(finalDecision.body().at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(finalDecision.body().at("/data/completedAt").isTextual()).isTrue();

        var history = perform(get(
                "/api/v1/systems/{systemId}/flow/instances/{instanceId}/history",
                SYSTEM, instanceId), thirdApprover);
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body().at("/data/events")).hasSize(4);
        assertThat(history.body().at("/data/events/1/toStatus").asText()).isEqualTo("PENDING");
        assertThat(history.body().at("/data/events/2/toStatus").asText()).isEqualTo("PENDING");
        assertThat(history.body().at("/data/events/3/toStatus").asText()).isEqualTo("APPROVED");
    }

    @Test
    void invalidOrMixedApproverSequencesReturnTheFrozen400() throws Exception {
        var invalidBodies = List.of(
                Map.<String, Object>of("name", "Missing"),
                Map.<String, Object>of(
                        "name", "Mixed",
                        "approverId", "20",
                        "approverIds", List.of("20", "30")),
                Map.<String, Object>of("name", "Empty", "approverIds", List.of()),
                Map.<String, Object>of("name", "Invalid", "approverIds", List.of("20", "0")),
                Map.<String, Object>of(
                        "name", "Oversized",
                        "approverIds",
                        java.util.stream.LongStream.rangeClosed(1, 11)
                                .mapToObj(Long::toString)
                                .toList())
        );
        for (var body : invalidBodies) {
            var response = perform(post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                    .contentType("application/json")
                    .content(json.writeValueAsString(body)), session(10, ALL_PERMISSIONS));
            assertThat(response.status()).isEqualTo(400);
            assertThat(response.body().path("code").asText())
                    .isEqualTo("FLOW_APPROVER_SEQUENCE_INVALID");
        }
    }

    @Test
    void orderedStagesRoundTripSimulateAndAdvanceWithCompatibilityProjection()
            throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var stagePlan = """
                [
                  {
                    "code":"manager",
                    "name":"Manager",
                    "approverIds":["20"],
                    "approvalMode":"SEQUENTIAL",
                    "approverSource":{"kind":"FIXED"}
                  },
                  {
                    "code":"finance",
                    "name":"Finance",
                    "approverIds":["30"],
                    "approvalMode":"SEQUENTIAL",
                    "approverSource":{"kind":"FIXED"}
                  },
                  {
                    "code":"confirmation",
                    "name":"Confirmation",
                    "approvalMode":"SEQUENTIAL",
                    "approverSource":{"kind":"PREVIOUS_HANDLER"}
                  }
                ]
                """;
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Ordered approval",
                                  "approvalStages":%s
                                }
                                """.formatted(stagePlan)),
                owner
        );
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().at("/data/approverId").asText()).isEqualTo("20");
        assertThat(created.body().at("/data/approverIds/0").asText()).isEqualTo("20");
        assertThat(created.body().at("/data/approvalMode").asText())
                .isEqualTo("SEQUENTIAL");
        assertThat(created.body().at("/data/approverSource/kind").asText())
                .isEqualTo("FIXED");
        assertThat(created.body().at("/data/approvalStages")).hasSize(3);
        var definitionId = created.body().at("/data/definitionId").asLong();

        var revised = perform(
                put("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Ordered approval revised",
                                  "approvalStages":%s
                                }
                                """.formatted(stagePlan)),
                owner
        );
        assertThat(revised.status()).isEqualTo(200);
        assertThat(revised.body().at("/data/revision").asInt()).isEqualTo(2);
        assertThat(revised.body().at("/data/approvalStages/2/approverSource/kind").asText())
                .isEqualTo("PREVIOUS_HANDLER");

        var check = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:check",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(check.status()).isEqualTo(200);
        assertThat(check.body().at("/data/verdict").asText()).isEqualTo("READY");

        var simulated = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft:simulate",
                        SYSTEM, definitionId)
                        .contentType("application/json")
                        .content("{}"),
                owner
        );
        assertThat(simulated.status()).isEqualTo(200);
        assertThat(simulated.body().at("/data/startable").asBoolean()).isTrue();
        assertThat(simulated.body().at("/data/approvalStages")).hasSize(3);
        assertThat(simulated.body().at("/data/route/activeApproverIds/0").asText())
                .isEqualTo("20");

        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner
        );
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().at("/data/approvalStages")).hasSize(3);

        var started = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "ordered-stages-start")
                        .contentType("application/json")
                        .content("{\"businessKey\":\"ordered-stages-001\"}"),
                session(10, Set.of(FlowPermissions.INSTANCE_START))
        );
        assertThat(started.status()).isEqualTo(201);
        assertThat(started.body().at("/data/currentStageIndex").asInt()).isZero();
        assertThat(started.body().at("/data/currentStageCode").asText())
                .isEqualTo("manager");
        assertThat(started.body().at("/data/stages/0/status").asText())
                .isEqualTo("ACTIVE");
        assertThat(started.body().at("/data/stages/1/status").asText())
                .isEqualTo("WAITING");
        var instanceId = started.body().at("/data/instanceId").asLong();

        inactiveMemberIds.add(30L);
        var failedActivation = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("{\"comment\":\"manager approved\"}"),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(failedActivation.status()).isEqualTo(422);
        assertThat(failedActivation.body().path("code").asText())
                .isEqualTo("FLOW_APPROVER_SOURCE_INACTIVE");
        var unchanged = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                        SYSTEM, instanceId),
                session(20, Set.of(FlowPermissions.INSTANCE_READ))
        );
        assertThat(unchanged.status()).isEqualTo(200);
        assertThat(unchanged.body().at("/data/currentStageIndex").asInt()).isZero();
        assertThat(unchanged.body().at("/data/stages/0/status").asText())
                .isEqualTo("ACTIVE");
        assertThat(unchanged.body().at("/data/stages/0/actualHandlerIds"))
                .isEmpty();
        inactiveMemberIds.remove(30L);

        var finance = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "ordered-stage-manager")
                        .contentType("application/json")
                        .content("{\"comment\":\"manager approved\"}"),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(finance.status()).isEqualTo(200);
        assertThat(finance.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(finance.body().at("/data/currentStageIndex").asInt()).isEqualTo(1);
        assertThat(finance.body().at("/data/currentStageCode").asText())
                .isEqualTo("finance");
        assertThat(finance.body().at("/data/approverId").asText()).isEqualTo("30");
        assertThat(finance.body().at("/data/stages/0/actualHandlerIds/0").asText())
                .isEqualTo("20");

        var confirmation = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "ordered-stage-finance")
                        .contentType("application/json")
                        .content("{\"comment\":\"finance approved\"}"),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(confirmation.status()).isEqualTo(200);
        assertThat(confirmation.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(confirmation.body().at("/data/currentStageIndex").asInt())
                .isEqualTo(2);
        assertThat(confirmation.body().at("/data/currentStageCode").asText())
                .isEqualTo("confirmation");
        assertThat(confirmation.body().at("/data/approverIds/0").asText())
                .isEqualTo("30");
        assertThat(confirmation.body().at("/data/stages/1/actualHandlerIds/0").asText())
                .isEqualTo("30");

        var retriedFinance = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "ordered-stage-finance")
                        .contentType("application/json")
                        .content("{\"comment\":\"finance approved\"}"),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(retriedFinance.status()).isEqualTo(200);
        assertThat(retriedFinance.body().at("/data/status").asText())
                .isEqualTo("PENDING");
        assertThat(retriedFinance.body().at("/data/currentStageIndex").asInt())
                .isEqualTo(2);
        assertThat(retriedFinance.body().at("/data/stages/2/status").asText())
                .isEqualTo("ACTIVE");

        var completed = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "ordered-stage-confirmation")
                        .contentType("application/json")
                        .content("{\"comment\":\"confirmed\"}"),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE))
        );
        assertThat(completed.status()).isEqualTo(200);
        assertThat(completed.body().at("/data/status").asText()).isEqualTo("APPROVED");
        assertThat(completed.body().at("/data/currentStageIndex").asInt()).isEqualTo(2);
        assertThat(completed.body().at("/data/stages/2/status").asText())
                .isEqualTo("APPROVED");
    }

    @Test
    void orderedStagesRejectLegacyMirrorMismatchAndGatewayMix() throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var mismatch = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Mismatch",
                                  "approverId":"21",
                                  "approvalStages":[
                                    {
                                      "code":"first",
                                      "name":"First",
                                      "approverIds":["20"],
                                      "approverSource":{"kind":"FIXED"}
                                    },
                                    {
                                      "code":"second",
                                      "name":"Second",
                                      "approverIds":["30"],
                                      "approverSource":{"kind":"FIXED"}
                                    }
                                  ]
                                }
                                """),
                owner
        );
        assertThat(mismatch.status()).isEqualTo(400);
        assertThat(mismatch.body().path("code").asText())
                .isEqualTo("FLOW_APPROVER_SEQUENCE_INVALID");

        var gatewayMix = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Gateway mix",
                                  "approvalStages":[
                                    {
                                      "code":"first",
                                      "name":"First",
                                      "approverIds":["20"],
                                      "approverSource":{"kind":"FIXED"}
                                    },
                                    {
                                      "code":"second",
                                      "name":"Second",
                                      "approverIds":["30"],
                                      "approverSource":{"kind":"FIXED"}
                                    }
                                  ],
                                  "gateway":{
                                    "branches":[
                                      {
                                        "code":"default",
                                        "name":"Default",
                                        "defaultBranch":true,
                                        "approverIds":["20"]
                                      }
                                    ]
                                  }
                                }
                                """),
                owner
        );
        assertThat(gatewayMix.status()).isEqualTo(422);
        assertThat(gatewayMix.body().path("code").asText())
                .isEqualTo("FLOW_APPROVAL_STAGES_GATEWAY_UNSUPPORTED");
    }

    @Test
    void refreshReadsRemainBoundToTheAuthenticatedTenant() throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), 20);
        var instanceId = startInstance(definitionId, "tenant-private");
        var otherTenant = session(10, TENANT + 1, ALL_PERMISSIONS);

        var definitions = perform(get("/api/v1/systems/{systemId}/flow/definitions", SYSTEM), otherTenant);
        assertThat(definitions.status()).isEqualTo(200);
        assertThat(definitions.body().path("data").path("total").asLong()).isZero();
        assertThat(definitions.body().path("data").path("items")).isEmpty();

        var instances = perform(get("/api/v1/systems/{systemId}/flow/instances", SYSTEM), otherTenant);
        assertThat(instances.status()).isEqualTo(200);
        assertThat(instances.body().path("data").path("total").asLong()).isZero();
        assertThat(instances.body().path("data").path("items")).isEmpty();

        var detail = perform(get("/api/v1/systems/{systemId}/flow/instances/{instanceId}",
                SYSTEM, instanceId), otherTenant);
        assertThat(detail.status()).isEqualTo(404);
        assertThat(detail.body().path("code").asText()).isEqualTo("FLOW_INSTANCE_NOT_FOUND");
    }

    @Test
    void refreshReadsRejectUnboundedPageSizes() throws Exception {
        var response = perform(get("/api/v1/systems/{systemId}/flow/instances?size=101", SYSTEM),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("code").asText()).isEqualTo("FLOW_REQUEST_INVALID");
    }

    @Test
    void deniesAFlowDecisionByAMemberWhoIsNotTheAssignedApprover() throws Exception {
        var instanceId = startPendingInstance(20);

        var response = perform(post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:reject",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("""
                        {"reason":"not mine"}
                        """), session(21, Set.of(FlowPermissions.INSTANCE_DECIDE)));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body().path("code").asText()).isEqualTo("FLOW_APPROVER_FORBIDDEN");
    }

    @Test
    void failsClosedWhenTheRequiredPermissionIsMissing() throws Exception {
        var response = perform(post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                .contentType("application/json")
                .content("""
                        {"name":"Expense approval","approverId":"20"}
                        """), session(10, Set.of()));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body().path("code").asText()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void rejectsAPathSystemThatDoesNotMatchTheAuthenticatedSession() throws Exception {
        var response = perform(post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM + 1)
                .contentType("application/json")
                .content("""
                        {"name":"Expense approval","approverId":"20"}
                        """), session(10, ALL_PERMISSIONS));

        assertThat(response.status()).isEqualTo(403);
        assertThat(response.body().path("code").asText()).isEqualTo("CONTEXT_SYSTEM_MISMATCH");
    }

    @Test
    void reportsAStableConflictWhenATerminalInstanceIsDecidedAgain() throws Exception {
        var instanceId = startPendingInstance(20);
        var approver = session(20, Set.of(FlowPermissions.INSTANCE_DECIDE));

        assertThat(perform(post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("""
                        {"comment":"approved"}
                        """), approver).status()).isEqualTo(200);

        var repeated = perform(post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:reject",
                SYSTEM, instanceId)
                .contentType("application/json")
                .content("""
                        {"reason":"too late"}
                        """), approver);
        assertThat(repeated.status()).isEqualTo(409);
        assertThat(repeated.body().path("code").asText()).isEqualTo("FLOW_INSTANCE_STATE_INVALID");
    }

    @Test
    void requesterCanWithdrawAndThePendingTaskAndHistoryRefreshImmediately() throws Exception {
        var instanceId = startPendingInstance(20);
        var withdrawn = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:withdraw", SYSTEM, instanceId)
                        .header("Idempotency-Key", "withdraw-1")
                        .contentType("application/json")
                        .content("""
                                {"reason":"  Submitted with the wrong amount  "}
                                """),
                session(10, Set.of(FlowPermissions.INSTANCE_WITHDRAW)));

        assertThat(withdrawn.status()).isEqualTo(200);
        assertThat(withdrawn.body().at("/data/status").asText()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.body().at("/data/completedAt").asText()).isNotBlank();

        var pendingTasks = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING&page=1&size=20", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(pendingTasks.body().at("/data/total").asLong()).isZero();

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history", SYSTEM, instanceId),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events")).hasSize(2);
        assertThat(history.body().at("/data/events/1/type").asText()).isEqualTo("WITHDRAWN");
        assertThat(history.body().at("/data/events/1/actorId").asText()).isEqualTo("10");
        assertThat(history.body().at("/data/events/1/comment").asText())
                .isEqualTo("Submitted with the wrong amount");
    }

    @Test
    void withdrawalRequiresReasonAndRequester() throws Exception {
        var instanceId = startPendingInstance(20);
        var blank = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:withdraw", SYSTEM, instanceId)
                        .header("Idempotency-Key", "withdraw-blank")
                        .contentType("application/json")
                        .content("""
                                {"reason":" "}
                                """),
                session(10, Set.of(FlowPermissions.INSTANCE_WITHDRAW)));
        assertThat(blank.status()).isEqualTo(422);
        assertThat(blank.body().path("code").asText()).isEqualTo("FLOW_WITHDRAW_REASON_REQUIRED");
    }

    @Test
    void withdrawalRejectsNonRequester() throws Exception {
        var instanceId = startPendingInstance(20);
        var forbidden = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:withdraw", SYSTEM, instanceId)
                        .header("Idempotency-Key", "withdraw-other")
                        .contentType("application/json")
                        .content("""
                                {"reason":"not my request"}
                                """),
                session(11, Set.of(FlowPermissions.INSTANCE_WITHDRAW)));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().path("code").asText()).isEqualTo("FLOW_REQUESTER_FORBIDDEN");
    }

    @Test
    void permittedOperatorCanTerminateAndRefreshTerminalHistoryAndTasks() throws Exception {
        var instanceId = startPendingInstance(20);
        var terminated = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:terminate", SYSTEM, instanceId)
                        .header("Idempotency-Key", "terminate-1")
                        .contentType("application/json")
                        .content("""
                                {"reason":"  Duplicate request  "}
                                """),
                session(99, Set.of(FlowPermissions.INSTANCE_TERMINATE)));

        assertThat(terminated.status()).isEqualTo(200);
        assertThat(terminated.body().at("/data/status").asText()).isEqualTo("TERMINATED");
        assertThat(terminated.body().at("/data/completedAt").asText()).isNotBlank();

        var pendingTasks = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING&page=1&size=20", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(pendingTasks.body().at("/data/total").asLong()).isZero();

        var completedTasks = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=COMPLETED&page=1&size=20", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(completedTasks.body().at("/data/items/0/status").asText())
                .isEqualTo("TERMINATED");

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history", SYSTEM, instanceId),
                session(99, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events")).hasSize(2);
        assertThat(history.body().at("/data/events/1/type").asText()).isEqualTo("TERMINATED");
        assertThat(history.body().at("/data/events/1/actorId").asText()).isEqualTo("99");
        assertThat(history.body().at("/data/events/1/comment").asText()).isEqualTo("Duplicate request");

        var terminalConflict = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:terminate", SYSTEM, instanceId)
                        .header("Idempotency-Key", "terminate-2")
                        .contentType("application/json")
                        .content("""
                                {"reason":"another attempt"}
                                """),
                session(99, Set.of(FlowPermissions.INSTANCE_TERMINATE)));
        assertThat(terminalConflict.status()).isEqualTo(409);
        assertThat(terminalConflict.body().path("code").asText())
                .isEqualTo("FLOW_INSTANCE_STATE_INVALID");

        var decisionConflict = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"too late"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(decisionConflict.status()).isEqualTo(409);
        assertThat(decisionConflict.body().path("code").asText())
                .isEqualTo("FLOW_INSTANCE_STATE_INVALID");
    }

    @Test
    void terminationRequiresReasonAndDedicatedPermission() throws Exception {
        var instanceId = startPendingInstance(20);
        var blank = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:terminate", SYSTEM, instanceId)
                        .header("Idempotency-Key", "terminate-blank")
                        .contentType("application/json")
                        .content("""
                                {"reason":" "}
                                """),
                session(99, Set.of(FlowPermissions.INSTANCE_TERMINATE)));
        assertThat(blank.status()).isEqualTo(422);
        assertThat(blank.body().path("code").asText())
                .isEqualTo("FLOW_TERMINATE_REASON_REQUIRED");

        var forbidden = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:terminate", SYSTEM, instanceId)
                        .header("Idempotency-Key", "terminate-forbidden")
                        .contentType("application/json")
                        .content("""
                                {"reason":"operator action"}
                                """),
                session(99, Set.of()));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().path("code").asText()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void requesterCanUrgeCurrentApproverAndReadAscendingTimeline() throws Exception {
        var instanceId = startPendingInstance(20);
        var requester = session(10, Set.of(FlowPermissions.INSTANCE_URGE));

        var first = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:urge", SYSTEM, instanceId)
                        .header("Idempotency-Key", "urge-1")
                        .contentType("application/json")
                        .content("""
                                {"message":"  Please review before noon  "}
                                """),
                requester);
        var second = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:urge", SYSTEM, instanceId)
                        .header("Idempotency-Key", "urge-2")
                        .contentType("application/json")
                        .content("""
                                {"message":" "}
                                """),
                requester);

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().at("/data/actorId").asText()).isEqualTo("10");
        assertThat(first.body().at("/data/recipientId").asText()).isEqualTo("20");
        assertThat(first.body().at("/data/message").asText()).isEqualTo("Please review before noon");
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().at("/data/message").asText()).isEmpty();
        assertThat(messages.commands).hasSize(2);
        assertThat(messages.commands.getFirst().templateCode()).isEqualTo("FLOW_INSTANCE_URGED");
        assertThat(messages.commands.getFirst().recipientMemberId()).isEqualTo(20);
        assertThat(messages.commands.getFirst().target().type()).isEqualTo("FLOW_INSTANCE");
        assertThat(messages.commands.getFirst().target().id()).isEqualTo(Long.toString(instanceId));

        var timeline = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/urges?page=1&size=20",
                        SYSTEM, instanceId),
                session(10, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(timeline.status()).isEqualTo(200);
        assertThat(timeline.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(timeline.body().at("/data/items/0/urgeId").asLong())
                .isLessThan(timeline.body().at("/data/items/1/urgeId").asLong());
    }

    @Test
    void urgeRequiresRequesterPendingStateAndValidMessage() throws Exception {
        var instanceId = startPendingInstance(20);
        var nonRequester = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:urge", SYSTEM, instanceId)
                        .header("Idempotency-Key", "urge-other")
                        .contentType("application/json")
                        .content("""
                                {"message":"review"}
                                """),
                session(11, Set.of(FlowPermissions.INSTANCE_URGE)));
        assertThat(nonRequester.status()).isEqualTo(403);
        assertThat(nonRequester.body().path("code").asText()).isEqualTo("FLOW_REQUESTER_FORBIDDEN");

        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:urge", SYSTEM, instanceId)
                        .header("Idempotency-Key", "urge-long")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("message", "x".repeat(501)))),
                session(10, Set.of(FlowPermissions.INSTANCE_URGE)));
        assertThat(invalid.status()).isEqualTo(422);
        assertThat(invalid.body().path("code").asText()).isEqualTo("FLOW_URGE_MESSAGE_INVALID");

        var approver = session(20, Set.of(FlowPermissions.INSTANCE_DECIDE));
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"done"}
                                """),
                approver).status()).isEqualTo(200);
        var terminal = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:urge", SYSTEM, instanceId)
                        .header("Idempotency-Key", "urge-terminal")
                        .contentType("application/json")
                        .content("""
                                {"message":"late"}
                                """),
                session(10, Set.of(FlowPermissions.INSTANCE_URGE)));
        assertThat(terminal.status()).isEqualTo(409);
        assertThat(terminal.body().path("code").asText()).isEqualTo("FLOW_INSTANCE_STATE_INVALID");
    }

    @Test
    void permittedMemberCanCommentOnTerminalInstanceAndReadAscendingTimeline() throws Exception {
        var instanceId = startPendingInstance(20);
        assertThat(perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"approved"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE))).status()).isEqualTo(200);

        var author = session(99, Set.of(FlowPermissions.INSTANCE_COMMENT));
        var first = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/comments", SYSTEM, instanceId)
                        .header("Idempotency-Key", "comment-1")
                        .contentType("application/json")
                        .content("""
                                {"body":"  Supporting documents checked.  "}
                                """),
                author);
        var second = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/comments", SYSTEM, instanceId)
                        .header("Idempotency-Key", "comment-2")
                        .contentType("application/json")
                        .content("""
                                {"body":"Archived."}
                                """),
                author);

        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().at("/data/authorId").asText()).isEqualTo("99");
        assertThat(first.body().at("/data/body").asText()).isEqualTo("Supporting documents checked.");
        assertThat(second.status()).isEqualTo(200);

        var timeline = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/comments?page=1&size=20",
                        SYSTEM, instanceId),
                session(99, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(timeline.status()).isEqualTo(200);
        assertThat(timeline.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(timeline.body().at("/data/items/0/commentId").asLong())
                .isLessThan(timeline.body().at("/data/items/1/commentId").asLong());

        var detail = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}", SYSTEM, instanceId),
                session(99, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(detail.body().at("/data/status").asText()).isEqualTo("APPROVED");
        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history", SYSTEM, instanceId),
                session(99, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events")).hasSize(2);
    }

    @Test
    void commentRequiresBodyAndInteractionListsUseExistingPageBounds() throws Exception {
        var instanceId = startPendingInstance(20);
        var blank = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/comments", SYSTEM, instanceId)
                        .header("Idempotency-Key", "comment-blank")
                        .contentType("application/json")
                        .content("""
                                {"body":" "}
                                """),
                session(99, Set.of(FlowPermissions.INSTANCE_COMMENT)));
        assertThat(blank.status()).isEqualTo(422);
        assertThat(blank.body().path("code").asText()).isEqualTo("FLOW_COMMENT_BODY_REQUIRED");

        var invalidPage = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/comments?size=101",
                        SYSTEM, instanceId),
                session(99, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(invalidPage.status()).isEqualTo(400);
        assertThat(invalidPage.body().path("code").asText()).isEqualTo("FLOW_REQUEST_INVALID");
    }

    @Test
    void currentApproverCanTransferTaskAndLaterApprovalUsesRuntimeSnapshot() throws Exception {
        var instanceId = startPendingInstance(20);
        var transferred = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:transfer", SYSTEM, instanceId)
                        .header("Idempotency-Key", "transfer-1")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","reason":"  Owning reviewer  "}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_TRANSFER)));

        assertThat(transferred.status()).isEqualTo(200);
        assertThat(transferred.body().at("/data/approverId").asText()).isEqualTo("30");
        assertThat(transferred.body().at("/data/approverIds/0").asText()).isEqualTo("30");

        var oldTasks = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_READ)));
        var targetTasks = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING", SYSTEM),
                session(30, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(oldTasks.body().at("/data/total").asLong()).isZero();
        assertThat(targetTasks.body().at("/data/total").asLong()).isEqualTo(1);

        var approved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"approved"}
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(approved.body().at("/data/status").asText()).isEqualTo("APPROVED");

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history", SYSTEM, instanceId),
                session(30, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events")).hasSize(3);
        assertThat(history.body().at("/data/events/1/type").asText()).isEqualTo("TRANSFERRED");
        assertThat(history.body().at("/data/events/1/targetMemberId").asText()).isEqualTo("30");
        assertThat(history.body().at("/data/events/1/position").isNull()).isTrue();
        assertThat(history.body().at("/data/events/1/comment").asText()).isEqualTo("Owning reviewer");
    }

    @Test
    void addSignBeforeAndAfterPreserveFrozenHandoffSemantics() throws Exception {
        var beforeId = startPendingInstance(20);
        var before = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:add-sign", SYSTEM, beforeId)
                        .header("Idempotency-Key", "add-before")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","position":"BEFORE","reason":"Security first"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_ADD_SIGN)));
        assertThat(before.body().at("/data/approverId").asText()).isEqualTo("30");
        assertThat(before.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("30", "20");

        var targetApproved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, beforeId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"security done"}
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(targetApproved.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(targetApproved.body().at("/data/approverId").asText()).isEqualTo("20");

        var afterId = startPendingInstance(20);
        var after = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:add-sign", SYSTEM, afterId)
                        .header("Idempotency-Key", "add-after")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","position":"AFTER","reason":"Security next"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_ADD_SIGN)));
        assertThat(after.body().at("/data/approverId").asText()).isEqualTo("20");
        assertThat(after.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "30");

        var currentApproved = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, afterId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"owner done"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(currentApproved.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(currentApproved.body().at("/data/approverId").asText()).isEqualTo("30");

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history", SYSTEM, afterId),
                session(30, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events/1/type").asText()).isEqualTo("ADD_SIGNED");
        assertThat(history.body().at("/data/events/1/targetMemberId").asText()).isEqualTo("30");
        assertThat(history.body().at("/data/events/1/position").asText()).isEqualTo("AFTER");
    }

    @Test
    void assignmentRejectsNonCurrentActorAndInvalidRequest() throws Exception {
        var instanceId = startPendingInstance(20);
        var forbidden = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:transfer", SYSTEM, instanceId)
                        .header("Idempotency-Key", "transfer-forbidden")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","reason":"not mine"}
                                """),
                session(21, Set.of(FlowPermissions.INSTANCE_TRANSFER)));
        assertThat(forbidden.status()).isEqualTo(403);
        assertThat(forbidden.body().path("code").asText()).isEqualTo("FLOW_APPROVER_FORBIDDEN");

        var duplicate = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:add-sign", SYSTEM, instanceId)
                        .header("Idempotency-Key", "add-duplicate")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"20","position":"BEFORE","reason":"duplicate"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_ADD_SIGN)));
        assertThat(duplicate.status()).isEqualTo(422);
        assertThat(duplicate.body().path("code").asText())
                .isEqualTo("FLOW_ASSIGNMENT_REQUEST_INVALID");

        var invalidPosition = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:add-sign", SYSTEM, instanceId)
                        .header("Idempotency-Key", "add-position")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","position":"before","reason":"invalid"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_ADD_SIGN)));
        assertThat(invalidPosition.status()).isEqualTo(422);
        assertThat(invalidPosition.body().path("code").asText())
                .isEqualTo("FLOW_ASSIGNMENT_REQUEST_INVALID");
    }

    @Test
    void returnUsesDurableCursorAndReapprovalTraversesTheReturnedPath() throws Exception {
        var definitionId = createAndPublishSequence(List.of("20", "30"));
        var instanceId = startInstance(definitionId, "return-001");

        var first = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"first"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(first.body().at("/data/currentStepIndex").asInt()).isEqualTo(1);
        assertThat(first.body().at("/data/approverId").asText()).isEqualTo("30");

        var returned = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:return", SYSTEM, instanceId)
                        .header("Idempotency-Key", "return-api")
                        .contentType("application/json")
                        .content("""
                                {"reason":"  Correct supporting data  "}
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_RETURN)));
        assertThat(returned.status()).isEqualTo(200);
        assertThat(returned.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(returned.body().at("/data/claimState").asText()).isEqualTo("CLAIMED");
        assertThat(returned.body().at("/data/approverId").asText()).isEqualTo("20");

        var previousAgain = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"corrected"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(previousAgain.body().at("/data/status").asText()).isEqualTo("PENDING");
        assertThat(previousAgain.body().at("/data/currentStepIndex").asInt()).isEqualTo(1);

        var completed = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"final"}
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(completed.body().at("/data/status").asText()).isEqualTo("APPROVED");

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history", SYSTEM, instanceId),
                session(30, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events/2/type").asText()).isEqualTo("RETURNED");
        assertThat(history.body().at("/data/events/2/targetMemberId").asText()).isEqualTo("20");
        assertThat(history.body().at("/data/events/2/comment").asText())
                .isEqualTo("Correct supporting data");
        assertThat(history.body().at("/data/events")).hasSize(5);
    }

    @Test
    void cancelClaimMovesTaskToClaimPoolAndOpenStateBlocksDecisionAndUrge() throws Exception {
        var instanceId = startPendingInstance(20);
        var invalidCancel = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:cancel-claim",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "cancel-invalid")
                        .contentType("application/json")
                        .content("""
                                {"reason":" "}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_CANCEL_CLAIM)));
        assertThat(invalidCancel.status()).isEqualTo(422);
        assertThat(invalidCancel.body().path("code").asText())
                .isEqualTo("FLOW_CANCEL_CLAIM_REQUEST_INVALID");

        var open = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:cancel-claim",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "cancel-api")
                        .contentType("application/json")
                        .content("""
                                {"reason":"Candidate pool"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_CANCEL_CLAIM)));
        assertThat(open.status()).isEqualTo(200);
        assertThat(open.body().at("/data/claimState").asText()).isEqualTo("OPEN");
        assertThat(open.body().at("/data/approverId").asText()).isEqualTo("20");

        var formerOwner = perform(
                get("/api/v1/systems/{systemId}/flow/tasks?status=PENDING", SYSTEM),
                session(20, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(formerOwner.body().at("/data/total").asLong()).isZero();

        var pool = perform(
                get("/api/v1/systems/{systemId}/flow/claimable-tasks?page=1&size=20", SYSTEM),
                session(40, Set.of(FlowPermissions.INSTANCE_CLAIM)));
        assertThat(pool.status()).isEqualTo(200);
        assertThat(pool.body().at("/data/total").asLong()).isEqualTo(1);
        assertThat(pool.body().at("/data/items/0/claimState").asText()).isEqualTo("OPEN");

        var blockedDecision = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve", SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"late"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(blockedDecision.status()).isEqualTo(409);
        assertThat(blockedDecision.body().path("code").asText())
                .isEqualTo("FLOW_INSTANCE_STATE_INVALID");

        var blockedUrge = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:urge", SYSTEM, instanceId)
                        .header("Idempotency-Key", "urge-open")
                        .contentType("application/json")
                        .content("""
                                {"message":"please"}
                                """),
                session(10, Set.of(FlowPermissions.INSTANCE_URGE)));
        assertThat(blockedUrge.status()).isEqualTo(409);
        assertThat(messages.commands).isEmpty();

        var invalidComment = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:claim", SYSTEM, instanceId)
                        .header("Idempotency-Key", "claim-invalid")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("comment", "x".repeat(501)))),
                session(40, Set.of(FlowPermissions.INSTANCE_CLAIM)));
        assertThat(invalidComment.status()).isEqualTo(422);
        assertThat(invalidComment.body().path("code").asText())
                .isEqualTo("FLOW_CLAIM_REQUEST_INVALID");

        var claimed = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:claim", SYSTEM, instanceId)
                        .header("Idempotency-Key", "claim-api")
                        .contentType("application/json")
                        .content("""
                                {"comment":"  Mine  "}
                                """),
                session(40, Set.of(FlowPermissions.INSTANCE_CLAIM)));
        assertThat(claimed.status()).isEqualTo(200);
        assertThat(claimed.body().at("/data/claimState").asText()).isEqualTo("CLAIMED");
        assertThat(claimed.body().at("/data/approverId").asText()).isEqualTo("40");
        assertThat(claimed.body().at("/data/approverIds/0").asText()).isEqualTo("40");

        var poolAfter = perform(
                get("/api/v1/systems/{systemId}/flow/claimable-tasks", SYSTEM),
                session(40, Set.of(FlowPermissions.INSTANCE_CLAIM)));
        assertThat(poolAfter.body().at("/data/total").asLong()).isZero();
    }

    @Test
    void reduceSignRemovesFutureStepAndExposesHistoryTargetIndex() throws Exception {
        var definitionId = createAndPublishSequence(List.of("20", "30", "40"));
        var instanceId = startInstance(definitionId, "reduce-001");

        var reduced = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:reduce-sign",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "reduce-api")
                        .contentType("application/json")
                        .content("""
                                {"targetStepIndex":2,"reason":"  Finance review is not required  "}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_REDUCE_SIGN)));
        assertThat(reduced.status()).isEqualTo(200);
        assertThat(reduced.body().at("/data/approverIds"))
                .extracting(JsonNode::asText)
                .containsExactly("20", "30");
        assertThat(reduced.body().at("/data/currentStepIndex").asInt()).isZero();
        assertThat(reduced.body().at("/data/approverId").asText()).isEqualTo("20");

        var first = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"first"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(first.body().at("/data/approverId").asText()).isEqualTo("30");
        var completed = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"final"}
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        assertThat(completed.body().at("/data/status").asText()).isEqualTo("APPROVED");

        var history = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/history",
                        SYSTEM, instanceId),
                session(30, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(history.body().at("/data/events/1/type").asText())
                .isEqualTo("SIGN_REMOVED");
        assertThat(history.body().at("/data/events/1/targetMemberId").asText())
                .isEqualTo("40");
        assertThat(history.body().at("/data/events/1/targetStepIndex").asInt())
                .isEqualTo(2);
        assertThat(history.body().at("/data/events/0/targetStepIndex").isNull()).isTrue();

        var invalid = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:reduce-sign",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "reduce-invalid")
                        .contentType("application/json")
                        .content("""
                                {"targetStepIndex":1,"reason":"late"}
                                """),
                session(30, Set.of(FlowPermissions.INSTANCE_REDUCE_SIGN)));
        assertThat(invalid.status()).isEqualTo(409);
    }

    @Test
    void copyWorksForPendingAndTerminalInstancesWithUniqueRecipientAndStableTimeline()
            throws Exception {
        var instanceId = startPendingInstance(20);
        var actor = session(99, Set.of(FlowPermissions.INSTANCE_COPY));

        var first = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/copies",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "copy-api")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","message":"  Follow this outcome  "}
                                """),
                actor);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body().at("/data/recipientId").asText()).isEqualTo("30");
        assertThat(first.body().at("/data/message").asText()).isEqualTo("Follow this outcome");
        assertThat(messages.commands).hasSize(1);
        assertThat(messages.commands.getFirst().templateCode()).isEqualTo("FLOW_INSTANCE_COPIED");

        var duplicate = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/copies",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "copy-api-2")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"30","message":"again"}
                                """),
                actor);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.body().path("code").asText())
                .isEqualTo("FLOW_COPY_ALREADY_EXISTS");
        assertThat(messages.commands).hasSize(1);

        perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}:approve",
                        SYSTEM, instanceId)
                        .contentType("application/json")
                        .content("""
                                {"comment":"done"}
                                """),
                session(20, Set.of(FlowPermissions.INSTANCE_DECIDE)));
        var terminalCopy = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/copies",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "copy-terminal")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"40","message":null}
                                """),
                actor);
        assertThat(terminalCopy.status()).isEqualTo(200);
        assertThat(terminalCopy.body().at("/data/message").asText()).isEmpty();

        var copies = perform(
                get("/api/v1/systems/{systemId}/flow/instances/{instanceId}/copies?page=1&size=1",
                        SYSTEM, instanceId),
                session(99, Set.of(FlowPermissions.INSTANCE_READ)));
        assertThat(copies.status()).isEqualTo(200);
        assertThat(copies.body().at("/data/total").asLong()).isEqualTo(2);
        assertThat(copies.body().at("/data/items/0/recipientId").asText()).isEqualTo("30");

        var self = perform(
                post("/api/v1/systems/{systemId}/flow/instances/{instanceId}/copies",
                        SYSTEM, instanceId)
                        .header("Idempotency-Key", "copy-self-api")
                        .contentType("application/json")
                        .content("""
                                {"targetMemberId":"99","message":""}
                                """),
                actor);
        assertThat(self.status()).isEqualTo(422);
        assertThat(self.body().path("code").asText()).isEqualTo("FLOW_COPY_TARGET_INVALID");
    }

    private long createAndPublishSequence(List<String> approverIds) throws Exception {
        var owner = session(10, ALL_PERMISSIONS);
        var created = perform(
                post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "name", "Sequential approval",
                                "approverIds", approverIds
                        ))),
                owner);
        assertThat(created.status()).isEqualTo(201);
        var definitionId = created.body().at("/data/definitionId").asLong();
        var published = perform(
                post("/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                        SYSTEM, definitionId),
                owner);
        assertThat(published.status()).isEqualTo(200);
        return definitionId;
    }

    private long createAndPublish(TestSession owner, long approverId) throws Exception {
        var created = perform(post("/api/v1/systems/{systemId}/flow/definitions", SYSTEM)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "name", "Expense approval",
                        "approverId", Long.toString(approverId)
                ))), owner);
        assertThat(created.status()).isEqualTo(201);
        var definitionId = created.body().path("data").path("definitionId").asLong();

        var revised = perform(put(
                "/api/v1/systems/{systemId}/flow/definitions/{definitionId}/draft",
                SYSTEM, definitionId)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                        "name", "Expense approval",
                        "approverId", Long.toString(approverId)
                ))), owner);
        assertThat(revised.status()).isEqualTo(200);
        assertThat(revised.body().path("data").path("revision").asInt()).isEqualTo(2);

        var published = perform(post(
                "/api/v1/systems/{systemId}/flow/definitions/{definitionId}:publish",
                SYSTEM, definitionId), owner);
        assertThat(published.status()).isEqualTo(200);
        assertThat(published.body().path("data").path("version").asInt()).isEqualTo(1);
        return definitionId;
    }

    private long startPendingInstance(long approverId) throws Exception {
        var definitionId = createAndPublish(session(10, ALL_PERMISSIONS), approverId);
        return startInstance(definitionId, "expense-conflict");
    }

    private long startInstance(long definitionId, String businessKey) throws Exception {
        var response = perform(post(
                        "/api/v1/systems/{systemId}/flow/definitions/{definitionId}/instances",
                        SYSTEM, definitionId)
                        .header("Idempotency-Key", "start-" + businessKey)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("businessKey", businessKey))),
                session(10, Set.of(FlowPermissions.INSTANCE_START)));
        assertThat(response.status()).isEqualTo(201);
        return response.body().path("data").path("instanceId").asLong();
    }

    private Result perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            TestSession session
    ) throws Exception {
        var response = mvc.perform(request
                        .requestAttr(RequestSession.REQUEST_ATTRIBUTE, session)
                        .requestAttr(WebRequestAttributes.REQUEST_ID, "request-flow-test")
                        .requestAttr(WebRequestAttributes.TRACE_ID, "trace-flow-test"))
                .andReturn()
                .getResponse();
        return new Result(response.getStatus(), json.readTree(response.getContentAsString()));
    }

    private static void assertPage(Result result, int page, int size, long total) {
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().at("/data/page").asInt()).isEqualTo(page);
        assertThat(result.body().at("/data/size").asInt()).isEqualTo(size);
        assertThat(result.body().at("/data/total").asLong()).isEqualTo(total);
        assertThat(result.body().at("/data/items").isArray()).isTrue();
    }

    private static TestSession session(long memberId, Set<String> permissions) {
        return session(memberId, TENANT, permissions);
    }

    private static TestSession session(long memberId, long tenantId, Set<String> permissions) {
        return new TestSession(
                1, 2, ContextType.SYSTEM, SYSTEM, tenantId, memberId, 1, permissions
        );
    }

    private record Result(int status, JsonNode body) {
    }

    private record Scope(long systemId, long tenantId) {
    }

    private static final class MemoryRequestServices implements FlowRequestServiceFactory {
        private final Map<Scope, InMemoryApprovalRepository> repositories = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong(1000);
        private final IdService ids = new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
        private final Clock clock = Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC);

        @Override
        public ApprovalWorkflowService forTenant(long systemId, long tenantId) {
            var repository = repositories.computeIfAbsent(
                    new Scope(systemId, tenantId),
                    ignored -> new InMemoryApprovalRepository()
            );
            return new ApprovalWorkflowService(repository, ids, clock);
        }
    }

    private static final class MemoryInteractionServices implements FlowInteractionServiceFactory {
        private final FlowRequestServiceFactory workflows;
        private final Map<Scope, InMemoryFlowInteractionRepository> repositories = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong(10_000);
        private final Clock clock = Clock.fixed(
                Instant.parse("2026-07-27T10:00:00Z"),
                ZoneOffset.UTC
        );

        private MemoryInteractionServices(FlowRequestServiceFactory workflows) {
            this.workflows = workflows;
        }

        @Override
        public FlowInteractionService forTenant(long systemId, long tenantId) {
            var repository = repositories.computeIfAbsent(
                    new Scope(systemId, tenantId),
                    ignored -> new InMemoryFlowInteractionRepository()
            );
            return new FlowInteractionService(
                    workflows.forTenant(systemId, tenantId),
                    repository,
                    new IdService() {
                        @Override
                        public long nextId() {
                            return sequence.incrementAndGet();
                        }
                    },
                    clock
            );
        }
    }

    private static final class MemoryMemberMessages implements MemberMessageFacade {
        private final List<Command> commands = new java.util.ArrayList<>();

        @Override
        public long send(Command command) {
            commands.add(command);
            return commands.size();
        }
    }

    private static final class MemoryRecordFlows implements RuntimeRecordFlowFacade {
        private final List<BindRequest> binds = new java.util.ArrayList<>();
        private final List<TransitionRequest> transitions = new java.util.ArrayList<>();

        @Override
        public RecordFlowState bind(BindRequest request) {
            binds.add(request);
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState bindAdditional(AdditionalBindRequest request) {
            return new RecordFlowState(
                    request.instanceId(),
                    FlowStatus.PENDING,
                    0L,
                    request.occurredAt()
            );
        }

        @Override
        public RecordFlowState transition(TransitionRequest request) {
            transitions.add(request);
            return new RecordFlowState(
                    request.instanceId(),
                    request.status(),
                    1L,
                    request.occurredAt()
            );
        }
    }

    private static final class MemoryApproverDirectory
            implements RuntimeApproverDirectoryFacade {
        private final Map<Long, Long> departmentLeaders = new ConcurrentHashMap<>();
        private final Map<Long, Long> requesterManagers = new ConcurrentHashMap<>();

        @Override
        public Resolution resolveRoleMembers(long systemId, long tenantId, long roleId) {
            return Resolution.missing();
        }

        @Override
        public Resolution resolveDepartmentMembers(
                long systemId,
                long tenantId,
                long departmentId
        ) {
            return Resolution.missing();
        }

        @Override
        public Resolution resolveDepartmentLeader(
                long systemId,
                long tenantId,
                long departmentId
        ) {
            var leader = departmentLeaders.get(departmentId);
            return departmentId == 7
                    ? Resolution.active(leader == null ? List.of() : List.of(leader))
                    : Resolution.missing();
        }

        @Override
        public Resolution resolveRequesterManager(
                long systemId,
                long tenantId,
                long requesterMemberId
        ) {
            var manager = requesterManagers.get(requesterMemberId);
            return Resolution.active(manager == null ? List.of() : List.of(manager));
        }
    }

    private static final class MemoryIdempotency implements IdempotencyFacade {
        private final Map<IdempotencyKey, IdempotencyRecord> records = new ConcurrentHashMap<>();
        private final Map<Long, IdempotencyKey> keysById = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        @Override
        public Optional<IdempotencyRecord> find(String scopeType, String scopeKey, String key) {
            return Optional.ofNullable(records.get(new IdempotencyKey(scopeType, scopeKey, key)));
        }

        @Override
        public long begin(String scopeType, String scopeKey, String key, String requestHash, Duration ttl) {
            var id = sequence.incrementAndGet();
            var idempotencyKey = new IdempotencyKey(scopeType, scopeKey, key);
            records.put(idempotencyKey, new IdempotencyRecord(id, requestHash, "PROCESSING", null));
            keysById.put(id, idempotencyKey);
            return id;
        }

        @Override
        public void complete(long id, int httpStatus, String responseCode, String responseBody) {
            var key = keysById.get(id);
            var record = records.get(key);
            records.put(key, new IdempotencyRecord(id, record.requestHash(), "COMPLETED", responseBody));
        }
    }

    private record IdempotencyKey(String scopeType, String scopeKey, String key) {
    }

    private record TestSession(
            long sessionId,
            long accountId,
            ContextType contextType,
            Long systemId,
            Long tenantId,
            Long memberId,
            long permissionVersion,
            Set<String> permissions
    ) implements RequestSession {
        private TestSession {
            permissions = Set.copyOf(permissions);
        }
    }

    @RestControllerAdvice
    private static final class TestErrorHandler {
        @ExceptionHandler(BusinessException.class)
        ResponseEntity<ApiResponse<Void>> business(BusinessException exception) {
            return ResponseEntity.status(exception.status()).body(ApiResponse.failure(
                    exception.code(),
                    exception.getMessage(),
                    "request-flow-test",
                    "trace-flow-test",
                    exception.errors()
            ));
        }
    }
}
