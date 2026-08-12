package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.domain.ApprovalStartContext;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.FlowTriggerDispatchInstance;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcApprovalMappingTest {
    @Test
    void keepsLegacyOneStageRuntimeAsNullInsteadOfRewritingHistory() {
        var started = ApprovalInstance.start(
                201L,
                new ApprovalDefinitionVersion(
                        101L, 1, "Legacy", 11L, 1,
                        Instant.parse("2026-07-31T01:00:00Z")),
                "legacy-runtime",
                9L,
                Instant.parse("2026-07-31T01:01:00Z")
        );

        assertThat(started.stages()).singleElement()
                .extracting(ApprovalStageExecution::code)
                .isEqualTo("legacy");
        assertThat(JdbcApprovalRepository.approvalStageState(started.stages()))
                .isNull();
    }

    @Test
    void writesExplicitBranchPlansWithoutRewritingLegacyBranches() throws Exception {
        var plan = List.of(new ApprovalStage(
                "review", "Review", List.of(11L), ApprovalMode.SEQUENTIAL,
                ApprovalApproverSource.fixed(), null, null, null));
        var condition = new TriggerCondition(
                "amount", TriggerCondition.Operator.GTE, "100");
        var conditional = new ApprovalGateway(List.of(
                new ApprovalGateway.Branch(
                        "review", "Review", false, List.of(condition),
                        List.of(11L), ApprovalMode.SEQUENTIAL, plan),
                new ApprovalGateway.Branch(
                        "default", "Default", true, List.of(),
                        List.of(21L), ApprovalMode.SEQUENTIAL)
        ));
        var parallel = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "review", "Review", List.of(11L),
                        ApprovalMode.SEQUENTIAL, plan),
                new ApprovalParallelGateway.Branch(
                        "owner", "Owner", List.of(21L),
                        ApprovalMode.SEQUENTIAL)
        ));
        var inclusive = new ApprovalInclusiveGateway(List.of(
                new ApprovalInclusiveGateway.Branch(
                        "review", "Review", false, List.of(condition),
                        List.of(11L), ApprovalMode.SEQUENTIAL, plan),
                new ApprovalInclusiveGateway.Branch(
                        "default", "Default", true, List.of(),
                        List.of(21L), ApprovalMode.SEQUENTIAL)
        ));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (var json : List.of(
                JdbcApprovalRepository.gateway(conditional),
                JdbcApprovalRepository.parallelGateway(parallel),
                JdbcApprovalRepository.inclusiveGateway(inclusive))) {
            var branches = mapper.readTree(json);
            assertThat(branches.get(0).path("approvalStages").isArray()).isTrue();
            assertThat(branches.get(0).path("approvalStages")).hasSize(1);
            assertThat(branches.get(1).has("approvalStages")).isFalse();
        }
    }

    @Test
    void mapsOrderedDefinitionStagesAndRestartSafeExecutionState() throws Exception {
        var stagesJson = """
                [
                  {
                    "code":"review",
                    "name":"Review",
                    "approverIds":[11],
                    "approvalMode":"SEQUENTIAL",
                    "approverSource":{"kind":"FIXED"},
                    "quorumRule":null,
                    "deadlinePolicy":null,
                    "decisionCommentPolicy":{
                      "approveRequired":false,
                      "rejectRequired":true,
                      "minimumLength":1
                    }
                  },
                  {
                    "code":"confirm",
                    "name":"Confirm",
                    "approverIds":[],
                    "approvalMode":"SEQUENTIAL",
                    "approverSource":{"kind":"PREVIOUS_HANDLER"},
                    "quorumRule":null,
                    "deadlinePolicy":null,
                    "decisionCommentPolicy":{
                      "approveRequired":false,
                      "rejectRequired":true,
                      "minimumLength":1
                    }
                  }
                ]
                """;
        var draftRow = row(
                column("definition_id", Types.BIGINT, 101L),
                column("name", Types.VARCHAR, "Ordered"),
                column("approver_id", Types.BIGINT, 11L),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("approver_ids", Types.VARCHAR, "11"),
                column("revision", Types.INTEGER, 1),
                column("updated_at", Types.TIMESTAMP, Timestamp.from(Instant.parse(
                        "2026-07-31T01:00:00Z"))),
                column("trigger_module_code", Types.VARCHAR, null),
                column("trigger_event", Types.VARCHAR, null),
                column("trigger_priority", Types.INTEGER, null),
                column("trigger_exclusive", Types.BOOLEAN, null),
                column("trigger_conditions", Types.VARCHAR, null),
                column("trigger_start_at", Types.TIMESTAMP, null),
                column("trigger_interval_minutes", Types.INTEGER, null),
                column("trigger_requester_id", Types.BIGINT, null),
                column("gateway_branches", Types.VARCHAR, null),
                column("parallel_branches", Types.VARCHAR, null),
                column("inclusive_branches", Types.VARCHAR, null),
                column("status_field_code", Types.VARCHAR, null),
                column("status_approved_value", Types.VARCHAR, null),
                column("status_rejected_value", Types.VARCHAR, null),
                column("status_withdrawn_value", Types.VARCHAR, null),
                column("status_terminated_value", Types.VARCHAR, null),
                column("approval_stages", Types.VARCHAR, stagesJson)
        );
        var draft = JdbcApprovalRepository.DRAFT_MAPPER.mapRow(draftRow, 0);

        assertThat(draft.approvalStages()).hasSize(2);
        assertThat(draft.approvalStages().get(1).approverSource().kind())
                .isEqualTo(ApprovalApproverSource.Kind.PREVIOUS_HANDLER);

        var stateJson = """
                [
                  {
                    "stageIndex":0,
                    "code":"review",
                    "name":"Review",
                    "status":"APPROVED",
                    "approverIds":[11],
                    "approvalMode":"SEQUENTIAL",
                    "requiredApprovals":1,
                    "actualHandlerIds":[99],
                    "startedAt":"2026-07-31T01:01:00Z",
                    "completedAt":"2026-07-31T01:01:01Z",
                    "deadline":null,
                    "decisionCommentPolicy":{
                      "approveRequired":false,
                      "rejectRequired":true,
                      "minimumLength":1
                    },
                    "handlerActorsByParticipantId":{"11":99}
                  },
                  {
                    "stageIndex":1,
                    "code":"confirm",
                    "name":"Confirm",
                    "status":"ACTIVE",
                    "approverIds":[99],
                    "approvalMode":"SEQUENTIAL",
                    "requiredApprovals":1,
                    "actualHandlerIds":[],
                    "startedAt":"2026-07-31T01:01:02Z",
                    "completedAt":null,
                    "deadline":null,
                    "decisionCommentPolicy":{
                      "approveRequired":false,
                      "rejectRequired":true,
                      "minimumLength":1
                    },
                    "handlerActorsByParticipantId":{}
                  }
                ]
                """;
        var instanceRow = row(
                column("instance_id", Types.BIGINT, 201L),
                column("definition_id", Types.BIGINT, 101L),
                column("definition_version", Types.INTEGER, 1),
                column("business_key", Types.VARCHAR, "ordered-1"),
                column("requester_id", Types.BIGINT, 9L),
                column("approver_id", Types.BIGINT, 99L),
                column("approver_ids", Types.VARCHAR, "[99]"),
                column("current_step_index", Types.INTEGER, 0),
                column("claim_state", Types.VARCHAR, "CLAIMED"),
                column("status", Types.VARCHAR, "PENDING"),
                column("started_at", Types.TIMESTAMP, Timestamp.from(Instant.parse(
                        "2026-07-31T01:01:00Z"))),
                column("completed_at", Types.TIMESTAMP, null),
                column("module_code", Types.VARCHAR, null),
                column("record_id", Types.BIGINT, null),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("required_approvals", Types.INTEGER, 1),
                column("approved_approver_ids_json", Types.VARCHAR, "[]"),
                column("rejected_approver_ids_json", Types.VARCHAR, "[]"),
                column("current_stage_index", Types.INTEGER, 1),
                column("approval_stage_state", Types.VARCHAR, stateJson)
        );
        var stored = JdbcApprovalRepository.INSTANCE_MAPPER.mapRow(instanceRow, 0);
        var instance = stored.toDomain(java.util.List.of());

        assertThat(instance.currentStageIndex()).isEqualTo(1);
        assertThat(instance.stages())
                .extracting(ApprovalStageExecution::status)
                .containsExactly(
                        ApprovalStageExecution.Status.APPROVED,
                        ApprovalStageExecution.Status.ACTIVE
                );
        assertThat(instance.stages().getFirst().actualHandlerIds())
                .containsExactly(99L);
        assertThat(instance.currentStage().actualHandlerIds()).isEmpty();
    }

    @Test
    void mapsDelegationRuleAndImmutableRevokeAudit() throws Exception {
        var revokedAt = Instant.parse("2026-07-30T10:00:00Z");
        var delegationRow = row(
                column("tenant_id", Types.BIGINT, 20L),
                column("delegation_id", Types.BIGINT, 501L),
                column("delegator_member_id", Types.BIGINT, 30L),
                column("delegate_member_id", Types.BIGINT, 40L),
                column("definition_id", Types.BIGINT, 101L),
                column(
                        "starts_at",
                        Types.TIMESTAMP,
                        Timestamp.from(Instant.parse("2026-07-30T08:00:00Z"))
                ),
                column(
                        "ends_at",
                        Types.TIMESTAMP,
                        Timestamp.from(Instant.parse("2026-07-31T08:00:00Z"))
                ),
                column("status", Types.VARCHAR, "REVOKED"),
                column("created_by", Types.BIGINT, 30L),
                column(
                        "created_at",
                        Types.TIMESTAMP,
                        Timestamp.from(Instant.parse("2026-07-30T07:00:00Z"))
                ),
                column("revoked_by", Types.BIGINT, 30L),
                column("revoked_at", Types.TIMESTAMP, Timestamp.from(revokedAt))
        );

        var rule = JdbcApprovalRepository.DELEGATION_MAPPER.mapRow(
                delegationRow,
                0
        );

        assertThat(rule.id()).isEqualTo(501L);
        assertThat(rule.tenantId()).isEqualTo(20L);
        assertThat(rule.delegatorMemberId()).isEqualTo(30L);
        assertThat(rule.delegateMemberId()).isEqualTo(40L);
        assertThat(rule.definitionId()).isEqualTo(101L);
        assertThat(rule.status()).isEqualTo(ApprovalDelegationRule.Status.REVOKED);
        assertThat(rule.revokedByMemberId()).isEqualTo(30L);
        assertThat(rule.revokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void mapsDefinitionDraftAndPublishedVersionWithoutLosingSnapshotFields() throws Exception {
        var draftRow = row(
                column("definition_id", Types.BIGINT, 101L),
                column("name", Types.VARCHAR, "Expense approval"),
                column("approver_id", Types.BIGINT, 20L),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("approver_ids", Types.VARCHAR, "20,30"),
                column("revision", Types.INTEGER, 3),
                column("updated_at", Types.TIMESTAMP, Timestamp.from(Instant.parse("2026-07-25T08:00:00Z"))),
                column("trigger_module_code", Types.VARCHAR, "purchase_order"),
                column("trigger_event", Types.VARCHAR, "RECORD_ACTIVATED"),
                column("trigger_priority", Types.INTEGER, 100),
                column("trigger_exclusive", Types.BOOLEAN, false),
                column(
                        "trigger_conditions",
                        Types.VARCHAR,
                        "[{\"fieldCode\":\"amount\",\"operator\":\"GTE\",\"value\":100}]"
                ),
                column("trigger_start_at", Types.TIMESTAMP, null),
                column("trigger_interval_minutes", Types.INTEGER, null),
                column("trigger_requester_id", Types.BIGINT, null),
                column(
                        "gateway_branches",
                        Types.VARCHAR,
                        "[{\"code\":\"urgent\",\"name\":\"Urgent\",\"defaultBranch\":false,"
                                + "\"conditions\":[{\"fieldCode\":\"urgent\",\"operator\":\"EQ\","
                                + "\"value\":true}],\"approverIds\":[40]},"
                                + "{\"code\":\"default\",\"name\":\"Default\",\"defaultBranch\":true,"
                                + "\"conditions\":[],\"approverIds\":[20,30]}]"
                ),
                column(
                        "decision_comment_policies",
                        Types.VARCHAR,
                        "{\"route\":{\"approveRequired\":true,\"rejectRequired\":true,"
                                + "\"minimumLength\":4},\"branches\":{\"default\":"
                                + "{\"approveRequired\":true,\"rejectRequired\":true,"
                                + "\"minimumLength\":4}}}"
                ),
                column("status_field_code", Types.VARCHAR, "approval_status"),
                column("status_approved_value", Types.VARCHAR, "101"),
                column("status_rejected_value", Types.VARCHAR, "102"),
                column("status_withdrawn_value", Types.VARCHAR, "103"),
                column("status_terminated_value", Types.VARCHAR, "104")
        );
        var draft = JdbcApprovalRepository.DRAFT_MAPPER.mapRow(draftRow, 0);

        var versionRow = row(
                column("definition_id", Types.BIGINT, 101L),
                column("version_no", Types.INTEGER, 2),
                column("name", Types.VARCHAR, "Expense approval v2"),
                column("approver_id", Types.BIGINT, 30L),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("approver_ids", Types.VARCHAR, "30,40"),
                column("source_revision", Types.INTEGER, 3),
                column("published_at", Types.TIMESTAMP, Timestamp.from(Instant.parse("2026-07-25T08:05:00Z"))),
                column("trigger_module_code", Types.VARCHAR, "purchase_order"),
                column("trigger_event", Types.VARCHAR, "RECORD_ACTIVATED"),
                column("trigger_priority", Types.INTEGER, 200),
                column("trigger_exclusive", Types.BOOLEAN, true),
                column(
                        "trigger_conditions",
                        Types.VARCHAR,
                        "[{\"fieldCode\":\"remark\",\"operator\":\"NOT_EMPTY\"}]"
                ),
                column("trigger_start_at", Types.TIMESTAMP, null),
                column("trigger_interval_minutes", Types.INTEGER, null),
                column("trigger_requester_id", Types.BIGINT, null),
                column(
                        "gateway_branches",
                        Types.VARCHAR,
                        "[{\"code\":\"remark\",\"name\":\"Has remark\",\"defaultBranch\":false,"
                                + "\"conditions\":[{\"fieldCode\":\"remark\","
                                + "\"operator\":\"NOT_EMPTY\"}],\"approverIds\":[20]},"
                                + "{\"code\":\"default\",\"name\":\"Default\",\"defaultBranch\":true,"
                                + "\"conditions\":[],\"approverIds\":[30,40]}]"
                ),
                column(
                        "decision_comment_policies",
                        Types.VARCHAR,
                        "{\"route\":{\"approveRequired\":false,\"rejectRequired\":true,"
                                + "\"minimumLength\":6},\"branches\":{\"default\":"
                                + "{\"approveRequired\":false,\"rejectRequired\":true,"
                                + "\"minimumLength\":6}}}"
                ),
                column("status_field_code", Types.VARCHAR, "approval_status"),
                column("status_approved_value", Types.VARCHAR, "201"),
                column("status_rejected_value", Types.VARCHAR, "202"),
                column("status_withdrawn_value", Types.VARCHAR, "203"),
                column("status_terminated_value", Types.VARCHAR, "204")
        );
        var version = JdbcApprovalRepository.VERSION_MAPPER.mapRow(versionRow, 0);

        assertThat(draft.id()).isEqualTo(101);
        assertThat(draft.revision()).isEqualTo(3);
        assertThat(draft.approverIds()).containsExactly(20L, 30L);
        assertThat(draft.updatedAt()).isEqualTo(Instant.parse("2026-07-25T08:00:00Z"));
        assertThat(draft.triggerBinding())
                .isEqualTo(new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        100,
                        false,
                        java.util.List.of(new TriggerCondition(
                                "amount",
                                TriggerCondition.Operator.GTE,
                                "100"
                        ))
                ));
        assertThat(draft.recordStatusMapping()).isEqualTo(new RecordStatusMapping(
                "approval_status",
                "101",
                "102",
                "103",
                "104"
        ));
        assertThat(draft.gateway().branches())
                .extracting(ApprovalGateway.Branch::code)
                .containsExactly("urgent", "default");
        assertThat(draft.decisionCommentPolicies().primary())
                .isEqualTo(new ApprovalDecisionCommentPolicy(true, true, 4));
        assertThat(version.definitionId()).isEqualTo(101);
        assertThat(version.version()).isEqualTo(2);
        assertThat(version.sourceRevision()).isEqualTo(3);
        assertThat(version.name()).isEqualTo("Expense approval v2");
        assertThat(version.approverId()).isEqualTo(30);
        assertThat(version.approverIds()).containsExactly(30L, 40L);
        assertThat(version.triggerBinding())
                .isEqualTo(new TriggerBinding(
                        "purchase_order",
                        TriggerBinding.Event.RECORD_ACTIVATED,
                        200,
                        true,
                        java.util.List.of(new TriggerCondition(
                                "remark",
                                TriggerCondition.Operator.NOT_EMPTY,
                                null
                        ))
                ));
        assertThat(version.recordStatusMapping()).isEqualTo(new RecordStatusMapping(
                "approval_status",
                "201",
                "202",
                "203",
                "204"
        ));
        assertThat(version.gateway().defaultBranch().approverIds()).containsExactly(30L, 40L);
        assertThat(version.decisionCommentPolicies().primary())
                .isEqualTo(new ApprovalDecisionCommentPolicy(false, true, 6));
    }

    @Test
    void mapsPendingInstanceAndHistoryStatusTransitions() throws Exception {
        var instanceRow = row(
                column("instance_id", Types.BIGINT, 201L),
                column("definition_id", Types.BIGINT, 101L),
                column("definition_version", Types.INTEGER, 2),
                column("business_key", Types.VARCHAR, "expense-001"),
                column("requester_id", Types.BIGINT, 10L),
                column("approver_id", Types.BIGINT, 30L),
                column("approver_ids", Types.VARCHAR, "[20,30]"),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("approved_approver_ids_json", Types.VARCHAR, "[]"),
                column("rejected_approver_ids_json", Types.VARCHAR, "[]"),
                column("current_step_index", Types.INTEGER, 1),
                column("claim_state", Types.VARCHAR, "CLAIMED"),
                column("status", Types.VARCHAR, "PENDING"),
                column("started_at", Types.TIMESTAMP, Timestamp.from(Instant.parse("2026-07-25T08:10:00Z"))),
                column("completed_at", Types.TIMESTAMP, null),
                column("module_code", Types.VARCHAR, null),
                column("record_id", Types.BIGINT, null),
                column(
                        "decision_comment_policy",
                        Types.VARCHAR,
                        "{\"approveRequired\":true,\"rejectRequired\":true,"
                                + "\"minimumLength\":5}"
                )
        );
        var instance = JdbcApprovalRepository.INSTANCE_MAPPER.mapRow(instanceRow, 0);

        var historyRow = row(
                column("event_sequence", Types.INTEGER, 2),
                column("event_type", Types.VARCHAR, "APPROVED"),
                column("actor_id", Types.BIGINT, 30L),
                column("represented_member_id", Types.BIGINT, 20L),
                column("delegation_id", Types.BIGINT, 501L),
                column("from_status", Types.VARCHAR, "PENDING"),
                column("to_status", Types.VARCHAR, "APPROVED"),
                column("comment", Types.VARCHAR, "approved"),
                column("occurred_at", Types.TIMESTAMP, Timestamp.from(Instant.parse("2026-07-25T08:11:00Z"))),
                column("target_member_id", Types.BIGINT, null),
                column("assignment_position", Types.VARCHAR, null),
                column("target_step_index", Types.INTEGER, null)
        );
        var history = JdbcApprovalRepository.HISTORY_MAPPER.mapRow(historyRow, 0);

        assertThat(instance.id()).isEqualTo(201);
        assertThat(instance.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(instance.approverIds()).containsExactly(20L, 30L);
        assertThat(instance.currentStepIndex()).isEqualTo(1);
        assertThat(instance.claimState()).isEqualTo(ApprovalInstance.ClaimState.CLAIMED);
        assertThat(instance.completedAt()).isNull();
        assertThat(instance.recordBinding()).isNull();
        assertThat(instance.decisionCommentPolicy())
                .isEqualTo(new ApprovalDecisionCommentPolicy(true, true, 5));
        assertThat(history.type()).isEqualTo(ApprovalHistoryEvent.Type.APPROVED);
        assertThat(history.fromStatus()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(history.toStatus()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(history.comment()).isEqualTo("approved");
        assertThat(history.actorId()).isEqualTo(30L);
        assertThat(history.representedMemberId()).isEqualTo(20L);
        assertThat(history.delegationRuleId()).isEqualTo(501L);
        assertThat(history.targetMemberId()).isNull();
        assertThat(history.assignmentPosition()).isNull();
    }

    @Test
    void mapsBoundInstanceWithoutLosingTheImmutableRecordSnapshot() throws Exception {
        var instanceRow = row(
                column("instance_id", Types.BIGINT, 201L),
                column("definition_id", Types.BIGINT, 101L),
                column("definition_version", Types.INTEGER, 2),
                column("business_key", Types.VARCHAR, "expense-bound"),
                column("requester_id", Types.BIGINT, 10L),
                column("approver_id", Types.BIGINT, 20L),
                column("approver_ids", Types.VARCHAR, "[20]"),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("approved_approver_ids_json", Types.VARCHAR, "[]"),
                column("rejected_approver_ids_json", Types.VARCHAR, "[]"),
                column("current_step_index", Types.INTEGER, 0),
                column("claim_state", Types.VARCHAR, "CLAIMED"),
                column("status", Types.VARCHAR, "PENDING"),
                column("started_at", Types.TIMESTAMP,
                        Timestamp.from(Instant.parse("2026-07-27T12:00:00Z"))),
                column("completed_at", Types.TIMESTAMP, null),
                column("module_code", Types.VARCHAR, "purchase_order"),
                column("record_id", Types.BIGINT, 901L),
                column(
                        "start_context",
                        Types.VARCHAR,
                        "{\"requesterMemberId\":10,\"moduleCode\":\"purchase_order\","
                                + "\"recordId\":901,\"valuesJson\":"
                                + "{\"amount\":{\"currency\":\"CNY\",\"value\":100},"
                                + "\"owner_id\":20}}"
                )
        );

        var instance = JdbcApprovalRepository.INSTANCE_MAPPER.mapRow(instanceRow, 0)
                .toDomain(java.util.List.of());

        assertThat(instance.recordBinding())
                .isEqualTo(new ApprovalInstance.RecordBinding("purchase_order", 901L));
        assertThat(instance.startContext()).isEqualTo(new ApprovalStartContext(
                10L,
                "purchase_order",
                901L,
                java.util.Map.of(
                        "amount", "{\"currency\":\"CNY\",\"value\":100}",
                        "owner_id", "20"
                )
        ));
        assertThat(JdbcApprovalRepository.startContext(instance.startContext()))
                .contains("\"requesterMemberId\":10")
                .contains("\"valuesJson\"");
    }

    @Test
    void mapsIndependentBranchStageCursorAndExecutionSnapshot() throws Exception {
        var stageState = """
                [
                  {
                    "stageIndex":0,
                    "code":"finance_review",
                    "name":"Finance review",
                    "status":"APPROVED",
                    "approverIds":[11],
                    "approvalMode":"SEQUENTIAL",
                    "requiredApprovals":1,
                    "actualHandlerIds":[99],
                    "startedAt":"2026-07-31T02:01:00Z",
                    "completedAt":"2026-07-31T02:01:01Z",
                    "deadline":null,
                    "decisionCommentPolicy":{
                      "approveRequired":false,
                      "rejectRequired":true,
                      "minimumLength":1
                    },
                    "handlerActorsByParticipantId":{"11":99}
                  },
                  {
                    "stageIndex":1,
                    "code":"finance_confirm",
                    "name":"Finance confirm",
                    "status":"ACTIVE",
                    "approverIds":[99],
                    "approvalMode":"SEQUENTIAL",
                    "requiredApprovals":1,
                    "actualHandlerIds":[],
                    "startedAt":"2026-07-31T02:01:02Z",
                    "completedAt":null,
                    "deadline":null,
                    "decisionCommentPolicy":{
                      "approveRequired":false,
                      "rejectRequired":true,
                      "minimumLength":1
                    },
                    "handlerActorsByParticipantId":{}
                  }
                ]
                """;
        var branchRow = row(
                column("branch_code", Types.VARCHAR, "finance"),
                column("branch_name", Types.VARCHAR, "Finance"),
                column("approver_id", Types.BIGINT, 99L),
                column("approver_ids", Types.VARCHAR, "[99]"),
                column("current_step_index", Types.INTEGER, 0),
                column("approval_mode", Types.VARCHAR, "SEQUENTIAL"),
                column("required_approvals", Types.INTEGER, 1),
                column("approved_approver_ids_json", Types.VARCHAR, "[]"),
                column("rejected_approver_ids_json", Types.VARCHAR, "[]"),
                column("status", Types.VARCHAR, "PENDING"),
                column("started_at", Types.TIMESTAMP, Timestamp.from(
                        Instant.parse("2026-07-31T02:01:00Z"))),
                column("completed_at", Types.TIMESTAMP, null),
                column("approval_stage_state", Types.VARCHAR, stageState),
                column("current_stage_index", Types.INTEGER, 1)
        );

        var branch = JdbcApprovalRepository.PARALLEL_BRANCH_MAPPER.mapRow(
                branchRow, 0);

        assertThat(branch.currentStageIndex()).isEqualTo(1);
        assertThat(branch.currentStage().code()).isEqualTo("finance_confirm");
        assertThat(branch.stages())
                .extracting(ApprovalStageExecution::status)
                .containsExactly(
                        ApprovalStageExecution.Status.APPROVED,
                        ApprovalStageExecution.Status.ACTIVE
                );
        assertThat(branch.stages().getFirst().actualHandlerIds())
                .containsExactly(99L);
        assertThat(JdbcApprovalRepository.approvalStageState(branch.stages()))
                .contains("\"finance_confirm\"");
    }

    @Test
    void mapsAssignmentHistoryFacts() throws Exception {
        var historyRow = row(
                column("event_sequence", Types.INTEGER, 3),
                column("event_type", Types.VARCHAR, "ADD_SIGNED"),
                column("actor_id", Types.BIGINT, 30L),
                column("represented_member_id", Types.BIGINT, null),
                column("delegation_id", Types.BIGINT, null),
                column("from_status", Types.VARCHAR, "PENDING"),
                column("to_status", Types.VARCHAR, "PENDING"),
                column("comment", Types.VARCHAR, "security review"),
                column("occurred_at", Types.TIMESTAMP, Timestamp.from(Instant.parse("2026-07-25T08:11:00Z"))),
                column("target_member_id", Types.BIGINT, 40L),
                column("assignment_position", Types.VARCHAR, "BEFORE"),
                column("target_step_index", Types.INTEGER, null)
        );

        var history = JdbcApprovalRepository.HISTORY_MAPPER.mapRow(historyRow, 0);

        assertThat(history.type()).isEqualTo(ApprovalHistoryEvent.Type.ADD_SIGNED);
        assertThat(history.targetMemberId()).isEqualTo(40);
        assertThat(history.assignmentPosition())
                .isEqualTo(ApprovalHistoryEvent.AssignmentPosition.BEFORE);
        assertThat(history.targetStepIndex()).isNull();
    }

    @Test
    void mapsReduceSignHistoryTargetStep() throws Exception {
        var historyRow = row(
                column("event_sequence", Types.INTEGER, 3),
                column("event_type", Types.VARCHAR, "SIGN_REMOVED"),
                column("actor_id", Types.BIGINT, 30L),
                column("represented_member_id", Types.BIGINT, null),
                column("delegation_id", Types.BIGINT, null),
                column("from_status", Types.VARCHAR, "PENDING"),
                column("to_status", Types.VARCHAR, "PENDING"),
                column("comment", Types.VARCHAR, "not required"),
                column("occurred_at", Types.TIMESTAMP,
                        Timestamp.from(Instant.parse("2026-07-25T08:12:00Z"))),
                column("target_member_id", Types.BIGINT, 40L),
                column("assignment_position", Types.VARCHAR, null),
                column("target_step_index", Types.INTEGER, 2)
        );

        var history = JdbcApprovalRepository.HISTORY_MAPPER.mapRow(historyRow, 0);

        assertThat(history.type()).isEqualTo(ApprovalHistoryEvent.Type.SIGN_REMOVED);
        assertThat(history.targetMemberId()).isEqualTo(40);
        assertThat(history.targetStepIndex()).isEqualTo(2);
    }

    @Test
    void mapsMatchedAndExplicitNoMatchTriggerDispatches() throws Exception {
        var matchedRow = row(
                column("event_key", Types.VARCHAR, "event-matched"),
                column("definition_id", Types.BIGINT, 101L),
                column("definition_version", Types.INTEGER, 2),
                column("instance_id", Types.BIGINT, 201L)
        );
        var noMatchRow = row(
                column("event_key", Types.VARCHAR, "event-empty"),
                column("definition_id", Types.BIGINT, null),
                column("definition_version", Types.INTEGER, null),
                column("instance_id", Types.BIGINT, null)
        );

        assertThat(JdbcApprovalRepository.TRIGGER_DISPATCH_MAPPER.mapRow(matchedRow, 0))
                .isEqualTo(FlowTriggerDispatch.matched("event-matched", 101L, 2, 201L));
        assertThat(JdbcApprovalRepository.TRIGGER_DISPATCH_MAPPER.mapRow(noMatchRow, 0))
                .isEqualTo(FlowTriggerDispatch.noMatch("event-empty"));
    }

    @Test
    void mapsOrderedTriggerDispatchChildren() throws Exception {
        var row = row(
                column("ordinal", Types.INTEGER, 1),
                column("definition_id", Types.BIGINT, 102L),
                column("definition_version", Types.INTEGER, 3),
                column("instance_id", Types.BIGINT, 202L)
        );

        assertThat(JdbcApprovalRepository.TRIGGER_DISPATCH_INSTANCE_MAPPER.mapRow(row, 0))
                .isEqualTo(new FlowTriggerDispatchInstance(102L, 3, 202L));
    }

    private static CachedRowSet row(Column... columns) throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(columns.length);
        for (var index = 0; index < columns.length; index++) {
            metadata.setColumnName(index + 1, columns[index].name());
            metadata.setColumnLabel(index + 1, columns[index].name());
            metadata.setColumnType(index + 1, columns[index].type());
        }
        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.moveToInsertRow();
        for (var index = 0; index < columns.length; index++) {
            rowSet.updateObject(index + 1, columns[index].value());
        }
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();
        assertThat(rowSet.next()).isTrue();
        return rowSet;
    }

    private static Column column(String name, int type, Object value) {
        return new Column(name, type, value);
    }

    private record Column(String name, int type, Object value) {
    }
}
