package com.unique.examine.flow.repository.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompensationAttempt;
import com.unique.examine.flow.domain.ApprovalCompensationSubflowRun;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.domain.CompletionFailurePolicy;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicies;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicy;
import com.unique.examine.flow.domain.ApprovalDeadlineState;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplateVersion;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalDecisionEvidenceFile;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalBranchExecution;
import com.unique.examine.flow.domain.ApprovalHistoryEvent;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalQuorumRule;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.domain.ApprovalStartContext;
import com.unique.examine.flow.domain.ApprovalTaskAssignment;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.FlowTriggerDispatchInstance;
import com.unique.examine.flow.domain.FlowPeriodicScheduleState;
import com.unique.examine.flow.domain.PeriodicSchedule;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;
import com.unique.examine.flow.repository.ApprovalRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.PERSISTENCE_CONFLICT;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.VERSION_ALREADY_EXISTS;

public final class JdbcApprovalRepository implements ApprovalRepository {
    private static final ObjectMapper TRIGGER_JSON = new ObjectMapper();
    static final RowMapper<ApprovalDefinitionDraft> DRAFT_MAPPER = (result, row) ->
            new ApprovalDefinitionDraft(
                    result.getLong("definition_id"),
                    result.getString("name"),
                    approverIds(result),
                    result.getInt("revision"),
                    result.getTimestamp("updated_at").toInstant(),
                    triggerBinding(result),
                    recordStatusMapping(result),
                    gateway(result),
                    ApprovalMode.valueOf(result.getString("approval_mode")),
                    parallelGateway(result),
                    inclusiveGateway(result),
                    approverSources(result),
                    quorumRules(result),
                    deadlinePolicies(result),
                    decisionCommentPolicies(result),
                    approvalStages(result),
                    decisionEvidencePolicies(result),
                    completionSteps(result),
                    completionFailurePolicy(result)
            );

    static final RowMapper<ApprovalDefinitionVersion> VERSION_MAPPER = (result, row) ->
            new ApprovalDefinitionVersion(
                    result.getLong("definition_id"),
                    result.getInt("version_no"),
                    result.getString("name"),
                    approverIds(result),
                    result.getInt("source_revision"),
                    result.getTimestamp("published_at").toInstant(),
                    triggerBinding(result),
                    recordStatusMapping(result),
                    gateway(result),
                    ApprovalMode.valueOf(result.getString("approval_mode")),
                    parallelGateway(result),
                    inclusiveGateway(result),
                    approverSources(result),
                    quorumRules(result),
                    deadlinePolicies(result),
                    decisionCommentPolicies(result),
                    approvalStages(result),
                    decisionEvidencePolicies(result),
                    completionSteps(result),
                    completionFailurePolicy(result)
            );

    static final RowMapper<FlowTriggerDispatch> TRIGGER_DISPATCH_MAPPER = (result, row) ->
            new FlowTriggerDispatch(
                    result.getString("event_key"),
                    nullableLong(result, "definition_id"),
                    nullableInteger(result, "definition_version"),
                    nullableLong(result, "instance_id")
            );

    static final RowMapper<FlowTriggerDispatchInstance> TRIGGER_DISPATCH_INSTANCE_MAPPER =
            (result, row) -> new FlowTriggerDispatchInstance(
                    result.getLong("definition_id"),
                    result.getInt("definition_version"),
                    result.getLong("instance_id")
            );

    static final RowMapper<FlowPeriodicScheduleState> PERIODIC_SCHEDULE_MAPPER =
            (result, row) -> new FlowPeriodicScheduleState(
                    result.getLong("definition_id"),
                    result.getInt("definition_version"),
                    result.getLong("requester_id"),
                    result.getInt("interval_minutes"),
                    result.getTimestamp("start_at").toInstant(),
                    result.getTimestamp("next_fire_at").toInstant(),
                    nullableTimestamp(result.getTimestamp("last_scheduled_at")),
                    nullableLong(result, "last_instance_id"),
                    FlowPeriodicScheduleState.Status.valueOf(result.getString("status")),
                    result.getString("pause_reason"),
                    result.getTimestamp("updated_at").toInstant()
            );

    static final RowMapper<ApprovalHistoryEvent> HISTORY_MAPPER = (result, row) ->
            new ApprovalHistoryEvent(
                    ApprovalHistoryEvent.Type.valueOf(result.getString("event_type")),
                    result.getLong("actor_id"),
                    nullableStatus(result.getString("from_status")),
                    ApprovalInstance.Status.valueOf(result.getString("to_status")),
                    result.getString("comment"),
                    result.getTimestamp("occurred_at").toInstant(),
                    nullableLong(result, "target_member_id"),
                    nullablePosition(result.getString("assignment_position")),
                    nullableInteger(result, "target_step_index"),
                    nullableLong(result, "represented_member_id"),
                    nullableLong(result, "delegation_id")
            );

    static final RowMapper<ApprovalDelegationRule> DELEGATION_MAPPER = (result, row) ->
            new ApprovalDelegationRule(
                    result.getLong("delegation_id"),
                    result.getLong("tenant_id"),
                    result.getLong("delegator_member_id"),
                    result.getLong("delegate_member_id"),
                    result.getTimestamp("starts_at").toInstant(),
                    result.getTimestamp("ends_at").toInstant(),
                    nullableLong(result, "definition_id"),
                    ApprovalDelegationRule.Status.valueOf(result.getString("status")),
                    result.getLong("created_by"),
                    result.getTimestamp("created_at").toInstant(),
                    nullableLong(result, "revoked_by"),
                    nullableTimestamp(result.getTimestamp("revoked_at"))
            );

    static final RowMapper<InstanceRow> INSTANCE_MAPPER = (result, row) ->
            new InstanceRow(
                    result.getLong("instance_id"),
                    result.getLong("definition_id"),
                    result.getInt("definition_version"),
                    result.getString("business_key"),
                    result.getLong("requester_id"),
                    result.getLong("approver_id"),
                    approverIds(result),
                    result.getInt("current_step_index"),
                    ApprovalInstance.ClaimState.valueOf(result.getString("claim_state")),
                    ApprovalInstance.Status.valueOf(result.getString("status")),
                    completionPhase(result),
                    completionFailurePolicy(result),
                    optionalColumnNullableInteger(
                            result, "active_completion_ordinal"),
                    result.getTimestamp("started_at").toInstant(),
                    nullableTimestamp(result.getTimestamp("completed_at")),
                    recordBinding(result),
                    ApprovalMode.valueOf(result.getString("approval_mode")),
                    runtimeRequiredApprovals(result),
                    decisions(result),
                    deadlineState(result),
                    decisionCommentPolicy(result),
                    optionalColumnInteger(result, "current_stage_index", 0),
                    approvalStageState(result),
                    startContext(result),
                    decisionEvidencePolicy(result)
            );

    static final RowMapper<ApprovalBranchExecution> PARALLEL_BRANCH_MAPPER = (result, row) ->
            new ApprovalBranchExecution(
                    result.getString("branch_code"),
                    result.getString("branch_name"),
                    approverIds(result),
                    ApprovalMode.valueOf(result.getString("approval_mode")),
                    runtimeRequiredApprovals(result),
                    result.getLong("approver_id"),
                    result.getInt("current_step_index"),
                    ApprovalBranchExecution.Status.valueOf(result.getString("status")),
                    decisions(result),
                    result.getTimestamp("started_at").toInstant(),
                    nullableTimestamp(result.getTimestamp("completed_at")),
                    deadlineState(result),
                    decisionCommentPolicy(result),
                    optionalColumnInteger(result, "current_stage_index", 0),
                    approvalStageState(result),
                    decisionEvidencePolicy(result)
            );

    static final RowMapper<ApprovalDecisionCommentTemplate> COMMENT_TEMPLATE_MAPPER =
            (result, row) -> new ApprovalDecisionCommentTemplate(
                    result.getLong("template_id"),
                    result.getString("name"),
                    result.getString("comment_body"),
                    result.getInt("current_version"),
                    ApprovalDecisionCommentTemplate.Status.valueOf(
                            result.getString("status")),
                    result.getLong("created_by"),
                    result.getTimestamp("created_at").toInstant(),
                    result.getLong("updated_by"),
                    result.getTimestamp("updated_at").toInstant()
            );

    static final RowMapper<ApprovalDecisionCommentTemplateVersion>
    COMMENT_TEMPLATE_VERSION_MAPPER =
            (result, row) -> new ApprovalDecisionCommentTemplateVersion(
                    result.getLong("template_id"),
                    result.getInt("version_no"),
                    result.getString("name"),
                    result.getString("comment_body"),
                    result.getLong("created_by"),
                    result.getTimestamp("created_at").toInstant()
            );

    static final RowMapper<DecisionEvidenceRow> DECISION_EVIDENCE_MAPPER =
            (result, row) -> new DecisionEvidenceRow(
                    result.getLong("evidence_id"),
                    result.getLong("instance_id"),
                    result.getInt("history_sequence"),
                    result.getString("branch_code"),
                    result.getInt("stage_index"),
                    ApprovalInstance.Decision.valueOf(
                            result.getString("decision")),
                    result.getString("signature_kind"),
                    nullableLong(result, "signature_file_id"),
                    result.getString("typed_signature"),
                    nullableLong(result, "template_id"),
                    nullableInteger(result, "template_version"),
                    result.getString("template_name"),
                    result.getLong("actor_id"),
                    result.getLong("represented_member_id"),
                    nullableLong(result, "delegation_id"),
                    result.getTimestamp("decided_at").toInstant()
            );

    static final RowMapper<DecisionEvidenceFileRow>
    DECISION_EVIDENCE_FILE_MAPPER =
            (result, row) -> new DecisionEvidenceFileRow(
                    new ApprovalDecisionEvidenceFile(
                            result.getLong("file_id"),
                            result.getString("original_name"),
                            result.getString("content_type"),
                            result.getLong("size_bytes"),
                            result.getString("sha256")),
                    result.getBoolean("is_attachment"),
                    result.getBoolean("is_signature"),
                    nullableInteger(result, "attachment_order")
            );

    static final RowMapper<ApprovalCompletionExecution>
    COMPLETION_EXECUTION_MAPPER =
            (result, row) -> new ApprovalCompletionExecution(
                    result.getLong("execution_id"),
                    result.getLong("instance_id"),
                    result.getLong("definition_id"),
                    result.getInt("definition_version"),
                    result.getInt("ordinal"),
                    completionStep(result),
                    result.getString("payload_json"),
                    ApprovalCompletionExecution.Status.valueOf(
                            result.getString("status")),
                    result.getInt("attempt_count"),
                    result.getInt("state_version"),
                    nullableTimestamp(result.getTimestamp("available_at")),
                    completionLease(result),
                    result.getTimestamp("created_at").toInstant(),
                    nullableTimestamp(result.getTimestamp("started_at")),
                    nullableTimestamp(result.getTimestamp("terminal_at")),
                    result.getString("result_json"),
                    completionFailure(result)
            );

    static final RowMapper<ApprovalCompletionAttempt>
    COMPLETION_ATTEMPT_MAPPER =
            (result, row) -> new ApprovalCompletionAttempt(
                    result.getLong("attempt_id"),
                    result.getLong("execution_id"),
                    result.getInt("attempt_number"),
                    result.getInt("event_sequence"),
                    ApprovalCompletionAttempt.Event.valueOf(
                            result.getString("event_type")),
                    nullableLong(result, "actor_member_id"),
                    result.getString("lease_owner"),
                    result.getString("idempotency_key_hash"),
                    result.getString("result_json"),
                    result.getString("failure_code"),
                    result.getString("failure_message"),
                    nullableInteger(result, "http_status"),
                    nullableLong(result, "duration_ms"),
                    result.getString("response_sha256"),
                    nullableTimestamp(result.getTimestamp("started_at")),
                    nullableTimestamp(result.getTimestamp("completed_at")),
                    result.getTimestamp("occurred_at").toInstant()
            );

    static final RowMapper<ApprovalSubflowRun> SUBFLOW_RUN_MAPPER =
            (result, row) -> new ApprovalSubflowRun(
                    result.getLong("subflow_run_id"),
                    result.getLong("execution_id"),
                    result.getInt("attempt_number"),
                    result.getString("launch_key"),
                    result.getLong("child_instance_id"),
                    result.getLong("target_definition_id"),
                    result.getInt("target_definition_version"),
                    result.getLong("root_instance_id"),
                    result.getInt("subflow_depth"),
                    ApprovalSubflowRun.Status.valueOf(
                            result.getString("child_status")),
                    result.getTimestamp("launched_at").toInstant(),
                    nullableTimestamp(result.getTimestamp("terminal_at")),
                    result.getString("result_code"),
                    nullableTimestamp(result.getTimestamp("result_applied_at")),
                    result.getInt("state_version")
            );

    static final RowMapper<ApprovalCompletionCompensation>
    COMPENSATION_MAPPER = (result, row) ->
            new ApprovalCompletionCompensation(
                    result.getLong("compensation_id"),
                    result.getLong("instance_id"),
                    result.getLong("original_execution_id"),
                    result.getInt("original_ordinal"),
                    result.getInt("reverse_ordinal"),
                    COMPLETION_EXECUTION_MAPPER.mapRow(result, row));

    static final RowMapper<ApprovalCompensationAttempt>
    COMPENSATION_ATTEMPT_MAPPER = (result, row) -> {
        var compensationId = result.getLong("compensation_id");
        return new ApprovalCompensationAttempt(
                compensationId,
                COMPLETION_ATTEMPT_MAPPER.mapRow(result, row));
    };

    static final RowMapper<ApprovalCompensationSubflowRun>
    COMPENSATION_SUBFLOW_MAPPER = (result, row) -> {
        var compensationId = result.getLong("compensation_id");
        return new ApprovalCompensationSubflowRun(
                compensationId,
                SUBFLOW_RUN_MAPPER.mapRow(result, row));
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final FlowTenantScope scope;

    public JdbcApprovalRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowTenantScope scope
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    @Override
    public ApprovalDefinitionDraft saveDraft(ApprovalDefinitionDraft draft) {
        return transactions.execute(status -> {
            if (draft.revision() == 1) {
                jdbc.update(
                        JdbcApprovalSql.INSERT_DRAFT,
                        scope.systemId(), scope.tenantId(), draft.id(), draft.name(),
                        draft.approverId(), draft.approvalMode().name(),
                        triggerModuleCode(draft.triggerBinding()),
                        triggerEvent(draft.triggerBinding()),
                        triggerPriority(draft.triggerBinding()),
                        triggerExclusive(draft.triggerBinding()),
                        triggerConditions(draft.triggerBinding()),
                        triggerStartAt(draft.triggerBinding()),
                        triggerIntervalMinutes(draft.triggerBinding()),
                        triggerRequesterId(draft.triggerBinding()),
                        gateway(draft.gateway()),
                        parallelGateway(draft.parallelGateway()),
                        inclusiveGateway(draft.inclusiveGateway()),
                        approverSources(draft.approverSources()),
                        quorumRules(draft.quorumRules()),
                        deadlinePolicies(draft.deadlinePolicies()),
                        decisionCommentPolicies(draft.decisionCommentPolicies()),
                        decisionEvidencePolicies(draft.decisionEvidencePolicies()),
                        draft.completionFailurePolicy().name(),
                        completionSteps(draft.completionSteps()),
                        approvalStages(draft.approvalStages()),
                        statusFieldCode(draft.recordStatusMapping()),
                        statusApprovedValue(draft.recordStatusMapping()),
                        statusRejectedValue(draft.recordStatusMapping()),
                        statusWithdrawnValue(draft.recordStatusMapping()),
                        statusTerminatedValue(draft.recordStatusMapping()),
                        draft.revision(), timestamp(draft.updatedAt()), timestamp(draft.updatedAt())
                );
            } else {
                var updated = jdbc.update(
                        JdbcApprovalSql.UPDATE_DRAFT,
                        draft.name(), draft.approverId(), draft.approvalMode().name(),
                        triggerModuleCode(draft.triggerBinding()),
                        triggerEvent(draft.triggerBinding()),
                        triggerPriority(draft.triggerBinding()),
                        triggerExclusive(draft.triggerBinding()),
                        triggerConditions(draft.triggerBinding()),
                        triggerStartAt(draft.triggerBinding()),
                        triggerIntervalMinutes(draft.triggerBinding()),
                        triggerRequesterId(draft.triggerBinding()),
                        gateway(draft.gateway()),
                        parallelGateway(draft.parallelGateway()),
                        inclusiveGateway(draft.inclusiveGateway()),
                        approverSources(draft.approverSources()),
                        quorumRules(draft.quorumRules()),
                        deadlinePolicies(draft.deadlinePolicies()),
                        decisionCommentPolicies(draft.decisionCommentPolicies()),
                        decisionEvidencePolicies(draft.decisionEvidencePolicies()),
                        draft.completionFailurePolicy().name(),
                        completionSteps(draft.completionSteps()),
                        approvalStages(draft.approvalStages()),
                        statusFieldCode(draft.recordStatusMapping()),
                        statusApprovedValue(draft.recordStatusMapping()),
                        statusRejectedValue(draft.recordStatusMapping()),
                        statusWithdrawnValue(draft.recordStatusMapping()),
                        statusTerminatedValue(draft.recordStatusMapping()),
                        draft.revision(), timestamp(draft.updatedAt()),
                        scope.systemId(), scope.tenantId(), draft.id(), draft.revision() - 1
                );
                requireOne(updated, "Approval draft revision changed concurrently");
                jdbc.update(
                        JdbcApprovalSql.DELETE_DRAFT_STEPS,
                        scope.systemId(), scope.tenantId(), draft.id()
                );
            }
            insertDraftSteps(draft);
            return draft;
        });
    }

    @Override
    public Optional<ApprovalDefinitionDraft> findDraft(long definitionId) {
        return optional(JdbcApprovalSql.SELECT_DRAFT, DRAFT_MAPPER,
                scope.systemId(), scope.tenantId(), definitionId);
    }

    @Override
    public List<ApprovalDefinitionDraft> findDrafts(int offset, int limit) {
        return List.copyOf(jdbc.query(
                JdbcApprovalSql.SELECT_DRAFTS,
                DRAFT_MAPPER,
                scope.systemId(), scope.tenantId(), limit, offset
        ));
    }

    @Override
    public long countDrafts() {
        return count(JdbcApprovalSql.COUNT_DRAFTS);
    }

    @Override
    public ApprovalDefinitionVersion saveVersion(ApprovalDefinitionVersion version) {
        try {
            return transactions.execute(status -> {
                jdbc.update(
                        JdbcApprovalSql.INSERT_VERSION,
                        scope.systemId(), scope.tenantId(), version.definitionId(), version.version(), version.name(),
                        version.approverId(), version.approvalMode().name(),
                        triggerModuleCode(version.triggerBinding()),
                        triggerEvent(version.triggerBinding()),
                        triggerPriority(version.triggerBinding()),
                        triggerExclusive(version.triggerBinding()),
                        triggerConditions(version.triggerBinding()),
                        triggerStartAt(version.triggerBinding()),
                        triggerIntervalMinutes(version.triggerBinding()),
                        triggerRequesterId(version.triggerBinding()),
                        gateway(version.gateway()),
                        parallelGateway(version.parallelGateway()),
                        inclusiveGateway(version.inclusiveGateway()),
                        approverSources(version.approverSources()),
                        quorumRules(version.quorumRules()),
                        deadlinePolicies(version.deadlinePolicies()),
                        decisionCommentPolicies(version.decisionCommentPolicies()),
                        decisionEvidencePolicies(version.decisionEvidencePolicies()),
                        version.completionFailurePolicy().name(),
                        completionSteps(version.completionSteps()),
                        approvalStages(version.approvalStages()),
                        statusFieldCode(version.recordStatusMapping()),
                        statusApprovedValue(version.recordStatusMapping()),
                        statusRejectedValue(version.recordStatusMapping()),
                        statusWithdrawnValue(version.recordStatusMapping()),
                        statusTerminatedValue(version.recordStatusMapping()),
                        version.sourceRevision(), timestamp(version.publishedAt())
                );
                insertVersionSteps(version);
                syncPeriodicSchedule(version);
                return version;
            });
        } catch (DuplicateKeyException exception) {
            throw new ApprovalDomainException(
                    VERSION_ALREADY_EXISTS,
                    "Published approval versions and source revisions are immutable"
            );
        }
    }

    @Override
    public Optional<ApprovalDefinitionVersion> findVersion(long definitionId, int version) {
        return optional(JdbcApprovalSql.SELECT_VERSION, VERSION_MAPPER,
                scope.systemId(), scope.tenantId(), definitionId, version);
    }

    @Override
    public Optional<ApprovalDefinitionVersion> findLatestVersion(long definitionId) {
        return optional(JdbcApprovalSql.SELECT_LATEST_VERSION, VERSION_MAPPER,
                scope.systemId(), scope.tenantId(), definitionId);
    }

    @Override
    public List<ApprovalDefinitionVersion> findVersions(
            long definitionId,
            int offset,
            int limit
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalSql.SELECT_VERSIONS,
                VERSION_MAPPER,
                scope.systemId(), scope.tenantId(), definitionId, limit, offset
        ));
    }

    @Override
    public long countVersions(long definitionId) {
        var value = jdbc.queryForObject(
                JdbcApprovalSql.COUNT_VERSIONS,
                Long.class,
                scope.systemId(),
                scope.tenantId(),
                definitionId
        );
        if (value == null || value < 0) {
            throw conflict("Scoped Flow version count returned an invalid result");
        }
        return value;
    }

    @Override
    public Optional<FlowPeriodicScheduleState> findPeriodicSchedule(long definitionId) {
        return optional(
                JdbcFlowPeriodicSql.SELECT_SCHEDULE,
                PERIODIC_SCHEDULE_MAPPER,
                scope.systemId(), scope.tenantId(), definitionId
        );
    }

    @Override
    public List<ApprovalDefinitionVersion> findStartableDefinitions(int offset, int limit) {
        return List.copyOf(jdbc.query(
                JdbcApprovalSql.SELECT_STARTABLE_DEFINITIONS,
                VERSION_MAPPER,
                scope.systemId(), scope.tenantId(),
                scope.systemId(), scope.tenantId(),
                limit, offset
        ));
    }

    @Override
    public long countStartableDefinitions() {
        return count(JdbcApprovalSql.COUNT_STARTABLE_DEFINITIONS);
    }

    @Override
    public List<ApprovalDefinitionVersion> findTriggerCandidates(
            String moduleCode,
            TriggerBinding.Event event
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalSql.SELECT_TRIGGER_CANDIDATES,
                VERSION_MAPPER,
                scope.systemId(), scope.tenantId(),
                scope.systemId(), scope.tenantId(),
                moduleCode, event.name()
        ));
    }

    @Override
    public Optional<FlowTriggerDispatch> findTriggerDispatchForUpdate(String eventKey) {
        var parent = optional(
                JdbcApprovalSql.SELECT_TRIGGER_DISPATCH_FOR_UPDATE,
                TRIGGER_DISPATCH_MAPPER,
                scope.systemId(), scope.tenantId(), eventKey
        );
        if (parent.isEmpty()) {
            return Optional.empty();
        }
        var instances = jdbc.query(
                JdbcApprovalSql.SELECT_TRIGGER_DISPATCH_INSTANCES,
                TRIGGER_DISPATCH_INSTANCE_MAPPER,
                scope.systemId(), scope.tenantId(), eventKey
        );
        return Optional.of(parent.get().withInstances(instances));
    }

    @Override
    public FlowTriggerDispatch saveTriggerDispatch(FlowTriggerDispatch dispatch) {
        try {
            jdbc.update(
                    JdbcApprovalSql.INSERT_TRIGGER_DISPATCH,
                    scope.systemId(), scope.tenantId(), dispatch.eventKey(),
                    dispatch.definitionId(), dispatch.definitionVersion(), dispatch.instanceId()
            );
            for (var ordinal = 0; ordinal < dispatch.instances().size(); ordinal++) {
                var instance = dispatch.instances().get(ordinal);
                jdbc.update(
                        JdbcApprovalSql.INSERT_TRIGGER_DISPATCH_INSTANCE,
                        scope.systemId(), scope.tenantId(), dispatch.eventKey(), ordinal,
                        instance.definitionId(), instance.definitionVersion(), instance.instanceId()
                );
            }
            return dispatch;
        } catch (DuplicateKeyException exception) {
            throw conflict("Flow trigger event was dispatched concurrently");
        }
    }

    @Override
    public ApprovalInstance saveInstance(ApprovalInstance instance) {
        return transactions.execute(status -> {
            if (instance.history().size() == 1
                    && instance.history().getFirst().type() == ApprovalHistoryEvent.Type.STARTED) {
                insertInstance(instance);
                insertParallelBranches(instance);
                insertHistory(instance.id(), 1, instance.history().getFirst());
                return instance;
            }
            var stateVersion = instance.history().size() - 1;
            var updated = jdbc.update(
                    JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                    instance.approverId(), approverIdsJson(instance.approverIds()),
                    instance.currentStepIndex(), instance.claimState().name(),
                    instance.approvalMode().name(),
                    instance.requiredApprovals(),
                    deadlinePolicy(instance.deadline()),
                    deadlineRemindAt(instance.deadline()),
                    deadlineDueAt(instance.deadline()),
                    deadlineRemindedAt(instance.deadline()),
                    deadlineProcessedAt(instance.deadline()),
                    decisionCommentPolicy(instance.decisionCommentPolicy()),
                    decisionEvidencePolicy(instance.decisionEvidencePolicy()),
                    approvalStageState(instance.stages()),
                    instance.currentStageIndex(),
                    approverIdsJson(instance.approvedApproverIds()),
                    approverIdsJson(instance.rejectedApproverIds()),
                    instance.status().name(),
                    instance.completionPhase().name(),
                    instance.activeCompletionOrdinal(),
                    stateVersion,
                    nullableTimestamp(instance.completedAt()),
                    scope.systemId(), scope.tenantId(), instance.id(), stateVersion - 1
            );
            requireOne(updated, "Approval instance is no longer pending at the expected version");
            updateParallelBranches(instance);
            insertHistory(instance.id(), instance.history().size(), instance.history().getLast());
            return instance;
        });
    }

    @Override
    public ApprovalInstance saveCompletionProgress(
            ApprovalInstance instance
    ) {
        if (instance.status() != ApprovalInstance.Status.PENDING
                || instance.completionPhase()
                != ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION
                && instance.completionPhase()
                != ApprovalInstance.CompletionPhase.COMPENSATING
                || instance.completionPhase()
                == ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION
                && instance.activeCompletionOrdinal() == null) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only active completion or compensation can advance"
            );
        }
        var stateVersion = instance.history().size() - 1;
        var updated = jdbc.update(
                JdbcApprovalSql.UPDATE_INSTANCE_COMPLETION_PROGRESS,
                instance.completionPhase().name(),
                instance.activeCompletionOrdinal(),
                scope.systemId(), scope.tenantId(), instance.id(),
                stateVersion
        );
        requireOne(updated, "Approval completion cursor changed concurrently");
        return instance;
    }

    @Override
    public Optional<ApprovalInstance> findInstance(long instanceId) {
        return findInstance(instanceId, false);
    }

    @Override
    public Optional<ApprovalInstance> findInstanceForUpdate(long instanceId) {
        return findInstance(instanceId, true);
    }

    private Optional<ApprovalInstance> findInstance(
            long instanceId,
            boolean forUpdate
    ) {
        var base = jdbc.query(
                forUpdate
                        ? JdbcApprovalSql.SELECT_INSTANCE_FOR_UPDATE
                        : JdbcApprovalSql.SELECT_INSTANCE,
                INSTANCE_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId
        );
        if (base.isEmpty()) {
            return Optional.empty();
        }
        var history = jdbc.query(
                JdbcApprovalSql.SELECT_HISTORY,
                HISTORY_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId
        );
        var instance = toDomain(base.getFirst(), history);
        for (var evidence : findDecisionEvidenceByInstance(instanceId)) {
            instance = instance.withDecisionEvidence(evidence);
        }
        return Optional.of(instance);
    }

    @Override
    public List<ApprovalInstance> findInstances(int offset, int limit) {
        return jdbc.query(
                JdbcApprovalSql.SELECT_INSTANCES,
                INSTANCE_MAPPER,
                scope.systemId(), scope.tenantId(), limit, offset
        ).stream().map(row -> toDomain(row, List.of())).toList();
    }

    @Override
    public List<ApprovalInstance> findInstances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive,
            int offset,
            int limit
    ) {
        var statusName = status == null ? null : status.name();
        var from = nullableTimestamp(fromInclusive);
        var to = nullableTimestamp(toExclusive);
        return jdbc.query(
                JdbcApprovalSql.SELECT_INSTANCES_FILTERED,
                INSTANCE_MAPPER,
                scope.systemId(), scope.tenantId(),
                statusName, statusName, from, from, to, to,
                limit, offset
        ).stream().map(row -> toDomain(row, List.of())).toList();
    }

    @Override
    public long countInstances() {
        return count(JdbcApprovalSql.COUNT_INSTANCES);
    }

    @Override
    public long countInstances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        var statusName = status == null ? null : status.name();
        var from = nullableTimestamp(fromInclusive);
        var to = nullableTimestamp(toExclusive);
        var value = jdbc.queryForObject(
                JdbcApprovalSql.COUNT_INSTANCES_FILTERED,
                Long.class,
                scope.systemId(), scope.tenantId(),
                statusName, statusName, from, from, to, to
        );
        if (value == null || value < 0) {
            throw conflict("Scoped flow count query returned an invalid result");
        }
        return value;
    }

    @Override
    public List<ApprovalInstance> findApprovalTasks(
            long approverId,
            ApprovalTaskStatus status,
            int offset,
            int limit
    ) {
        return jdbc.query(
                JdbcApprovalSql.approvalTasks(status),
                INSTANCE_MAPPER,
                scope.systemId(), scope.tenantId(),
                approverId, approverId, approverId, approverId, approverId, approverId,
                approverId, approverId, approverId, approverId, approverId,
                limit, offset
        ).stream().map(row -> toDomain(row, List.of())).toList();
    }

    @Override
    public long countApprovalTasks(long approverId, ApprovalTaskStatus status) {
        var value = jdbc.queryForObject(
                JdbcApprovalSql.countApprovalTasks(status),
                Long.class,
                scope.systemId(), scope.tenantId(),
                approverId, approverId, approverId, approverId, approverId, approverId,
                approverId, approverId, approverId, approverId, approverId
        );
        if (value == null || value < 0) {
            throw conflict("Scoped approval task count query returned an invalid result");
        }
        return value;
    }

    @Override
    public List<ApprovalInstance> findClaimableTasks(int offset, int limit) {
        return jdbc.query(
                JdbcApprovalSql.SELECT_CLAIMABLE_TASKS,
                INSTANCE_MAPPER,
                scope.systemId(), scope.tenantId(), limit, offset
        ).stream().map(row -> toDomain(row, List.of())).toList();
    }

    @Override
    public long countClaimableTasks() {
        return count(JdbcApprovalSql.COUNT_CLAIMABLE_TASKS);
    }

    @Override
    public List<ApprovalCompletionExecution>
    materializeCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        Objects.requireNonNull(executions, "executions");
        var snapshot = List.copyOf(executions);
        if (snapshot.isEmpty()
                || snapshot.size() > ApprovalCompletionStep.MAX_STEPS) {
            throw conflict("Completion execution plan must contain 1 to 8 steps");
        }
        if (snapshot.stream().map(ApprovalCompletionExecution::id)
                .distinct().count() != snapshot.size()) {
            throw conflict("Completion execution ids must be unique");
        }
        try {
            return transactions.execute(status -> {
                var first = snapshot.getFirst();
                var firstStage = com.unique.examine.flow.domain
                        .ApprovalCompletionStage.stageAtOrdinal(snapshot, 0);
                for (var ordinal = 0; ordinal < snapshot.size(); ordinal++) {
                    var execution = snapshot.get(ordinal);
                    if (execution.instanceId() != first.instanceId()
                            || execution.definitionId() != first.definitionId()
                            || execution.definitionVersion()
                            != first.definitionVersion()
                            || execution.ordinal() != ordinal
                            || execution.stateVersion() != 0
                            || ordinal < firstStage.endExclusive()
                            && execution.status()
                            != ApprovalCompletionExecution.Status.AVAILABLE
                            || ordinal >= firstStage.endExclusive()
                            && execution.status()
                            != ApprovalCompletionExecution.Status.WAITING) {
                        throw conflict(
                                "Completion execution plan is not immutable or ordered");
                    }
                    insertCompletionExecution(execution);
                }
                return snapshot;
            });
        } catch (DuplicateKeyException exception) {
            throw conflict("Completion execution plan already exists");
        }
    }

    @Override
    public Optional<ApprovalCompletionExecution> findCompletionExecution(
            long executionId
    ) {
        return optional(
                JdbcApprovalCompletionSql.SELECT_EXECUTION,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), executionId);
    }

    @Override
    public Optional<ApprovalCompletionExecution>
    findCompletionExecutionForUpdate(long executionId) {
        return optional(
                JdbcApprovalCompletionSql.SELECT_EXECUTION_FOR_UPDATE,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), executionId);
    }

    @Override
    public List<ApprovalCompletionExecution>
    findCompletionExecutionsByInstance(long instanceId) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_BY_INSTANCE,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId));
    }

    @Override
    public List<ApprovalCompletionExecution> findCompletionStageForUpdate(
            long instanceId,
            int stageCursor
    ) {
        var values = List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_STAGE_FOR_UPDATE,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId,
                stageCursor, stageCursor));
        if (values.isEmpty() || values.getFirst().ordinal() != stageCursor) {
            throw conflict("Completion stage cursor was not found");
        }
        return values;
    }

    @Override
    public List<ApprovalCompletionExecution> findAvailableExternalTasks(
            String topic,
            java.time.Instant dueAt,
            int offset,
            int limit
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        topic = topic == null || topic.isBlank() ? null : topic.strip();
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_AVAILABLE_EXTERNAL,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), topic, topic,
                timestamp(dueAt), timestamp(dueAt), limit, offset));
    }

    @Override
    public long countAvailableExternalTasks(
            String topic,
            java.time.Instant dueAt
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        topic = topic == null || topic.isBlank() ? null : topic.strip();
        var count = jdbc.queryForObject(
                JdbcApprovalCompletionSql.COUNT_AVAILABLE_EXTERNAL,
                Long.class,
                scope.systemId(), scope.tenantId(), topic, topic,
                timestamp(dueAt), timestamp(dueAt));
        if (count == null || count < 0) {
            throw conflict(
                    "Scoped completion external-task count is invalid");
        }
        return count;
    }

    @Override
    public List<ApprovalCompletionExecution>
    findDueWebhookExecutionsForUpdate(
            java.time.Instant dueAt,
            int limit
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_DUE_WEBHOOK_FOR_UPDATE,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(),
                timestamp(dueAt), timestamp(dueAt), limit));
    }

    @Override
    public List<ApprovalCompletionExecution>
    findDueSubflowExecutionsForUpdate(
            java.time.Instant dueAt,
            int limit
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_DUE_SUBFLOW_FOR_UPDATE,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), timestamp(dueAt), limit));
    }

    @Override
    public List<ApprovalCompletionExecution> findDueSubflowExecutions(
            java.time.Instant dueAt,
            int limit
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_DUE_SUBFLOW,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), timestamp(dueAt), limit));
    }

    @Override
    public ApprovalCompletionExecution saveCompletionExecution(
            ApprovalCompletionExecution execution
    ) {
        Objects.requireNonNull(execution, "execution");
        if (execution.stateVersion() < 1) {
            throw conflict(
                    "Completion execution must be materialized before transition");
        }
        var failure = execution.failure();
        var lease = execution.lease();
        var updated = jdbc.update(
                JdbcApprovalCompletionSql.UPDATE_EXECUTION,
                execution.status().name(),
                execution.attemptCount(),
                execution.stateVersion(),
                nullableTimestamp(execution.availableAt()),
                lease == null ? null : lease.owner(),
                lease == null ? null : lease.tokenHash(),
                lease == null ? null : timestamp(lease.expiresAt()),
                nullableTimestamp(execution.startedAt()),
                nullableTimestamp(execution.terminalAt()),
                execution.resultJson(),
                failure == null ? null : failure.code(),
                failure == null ? null : failure.message(),
                failure == null ? null : failure.retryable(),
                scope.systemId(), scope.tenantId(), execution.id(),
                execution.stateVersion() - 1);
        requireOne(updated, "Completion execution changed concurrently");
        return execution;
    }

    @Override
    public List<ApprovalCompletionExecution> saveCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        var snapshot = List.copyOf(executions);
        return transactions.execute(status -> {
            snapshot.forEach(this::saveCompletionExecution);
            return snapshot;
        });
    }

    @Override
    public ApprovalCompletionAttempt appendCompletionAttempt(
            ApprovalCompletionAttempt attempt
    ) {
        Objects.requireNonNull(attempt, "attempt");
        try {
            jdbc.update(
                    JdbcApprovalCompletionSql.INSERT_ATTEMPT,
                    scope.systemId(), scope.tenantId(),
                    attempt.id(), attempt.executionId(),
                    attempt.attemptNumber(), attempt.eventSequence(),
                    attempt.event().name(), attempt.actorMemberId(),
                    attempt.leaseOwner(), attempt.idempotencyKeyHash(),
                    attempt.resultJson(), attempt.failureCode(),
                    attempt.failureMessage(), attempt.httpStatus(),
                    attempt.durationMs(), attempt.responseSha256(),
                    nullableTimestamp(attempt.startedAt()),
                    nullableTimestamp(attempt.completedAt()),
                    timestamp(attempt.occurredAt()));
            return attempt;
        } catch (DuplicateKeyException exception) {
            throw conflict("Completion attempt fact already exists");
        }
    }

    @Override
    public List<ApprovalCompletionAttempt> findCompletionAttempts(
            long executionId
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_ATTEMPTS,
                COMPLETION_ATTEMPT_MAPPER,
                scope.systemId(), scope.tenantId(), executionId));
    }

    @Override
    public ApprovalSubflowRun appendSubflowRun(ApprovalSubflowRun run) {
        Objects.requireNonNull(run, "run");
        try {
            jdbc.update(
                    JdbcApprovalCompletionSql.INSERT_SUBFLOW_RUN,
                    scope.systemId(), scope.tenantId(), run.id(),
                    run.executionId(), run.attemptNumber(), run.launchKey(),
                    run.childInstanceId(), run.targetDefinitionId(),
                    run.targetVersion(), run.rootInstanceId(), run.depth(),
                    run.status().name(), timestamp(run.launchedAt()),
                    nullableTimestamp(run.terminalAt()), run.resultCode(),
                    nullableTimestamp(run.resultAppliedAt()),
                    run.stateVersion());
            return run;
        } catch (DuplicateKeyException exception) {
            var existing = findSubflowRunByExecutionAndAttempt(
                    run.executionId(), run.attemptNumber());
            if (existing.isPresent()) {
                var value = existing.get();
                if (value.launchKey().equals(run.launchKey())
                        && value.targetDefinitionId()
                        == run.targetDefinitionId()
                        && value.targetVersion() == run.targetVersion()
                        && value.rootInstanceId() == run.rootInstanceId()
                        && value.depth() == run.depth()) {
                    return value;
                }
            }
            throw conflict("Subflow run identity already exists");
        }
    }

    @Override
    public Optional<ApprovalSubflowRun>
    findSubflowRunByExecutionAndAttempt(
            long executionId,
            int attemptNumber
    ) {
        return optional(
                JdbcApprovalCompletionSql.SELECT_SUBFLOW_BY_EXECUTION_ATTEMPT,
                SUBFLOW_RUN_MAPPER, scope.systemId(), scope.tenantId(),
                executionId, attemptNumber);
    }

    @Override
    public Optional<ApprovalSubflowRun> findSubflowRunByChildInstance(
            long childInstanceId
    ) {
        return optional(
                JdbcApprovalCompletionSql.SELECT_SUBFLOW_BY_CHILD,
                SUBFLOW_RUN_MAPPER, scope.systemId(), scope.tenantId(),
                childInstanceId);
    }

    @Override
    public List<ApprovalSubflowRun> findSubflowRunsByExecution(
            long executionId
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_SUBFLOWS_BY_EXECUTION,
                SUBFLOW_RUN_MAPPER, scope.systemId(), scope.tenantId(),
                executionId));
    }

    @Override
    public List<ApprovalSubflowRun> findPendingSubflowResultsForUpdate(
            int limit
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_PENDING_SUBFLOW_RESULTS_FOR_UPDATE,
                SUBFLOW_RUN_MAPPER,
                scope.systemId(), scope.tenantId(), limit));
    }

    @Override
    public List<ApprovalSubflowRun> findPendingSubflowResults(int limit) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_PENDING_SUBFLOW_RESULTS,
                SUBFLOW_RUN_MAPPER,
                scope.systemId(), scope.tenantId(), limit));
    }

    @Override
    public List<ApprovalSubflowRun> findRunningSubflowRunsForUpdate(int limit) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_RUNNING_SUBFLOWS_FOR_UPDATE,
                SUBFLOW_RUN_MAPPER,
                scope.systemId(), scope.tenantId(), limit));
    }

    @Override
    public List<ApprovalSubflowRun> findRunningSubflowRuns(int limit) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompletionSql.SELECT_RUNNING_SUBFLOWS,
                SUBFLOW_RUN_MAPPER,
                scope.systemId(), scope.tenantId(), limit));
    }

    @Override
    public ApprovalSubflowRun saveSubflowRun(ApprovalSubflowRun run) {
        Objects.requireNonNull(run, "run");
        if (run.stateVersion() < 1) {
            throw conflict("Subflow run must be appended before transition");
        }
        var updated = jdbc.update(
                JdbcApprovalCompletionSql.UPDATE_SUBFLOW_RUN,
                run.status().name(), nullableTimestamp(run.terminalAt()),
                run.resultCode(), nullableTimestamp(run.resultAppliedAt()),
                run.stateVersion(), scope.systemId(), scope.tenantId(),
                run.id(), run.stateVersion() - 1);
        requireOne(updated, "Subflow run changed concurrently");
        return run;
    }

    @Override
    public List<ApprovalCompletionCompensation> materializeCompensationPlan(
            List<ApprovalCompletionCompensation> plan
    ) {
        Objects.requireNonNull(plan, "plan");
        var snapshot = List.copyOf(plan);
        if (snapshot.isEmpty()
                || snapshot.size() > ApprovalCompletionStep.MAX_STEPS) {
            throw conflict("Compensation plan must contain 1 to 8 steps");
        }
        try {
            return transactions.execute(status -> {
                for (var reverseOrdinal = 0;
                     reverseOrdinal < snapshot.size(); reverseOrdinal++) {
                    var compensation = snapshot.get(reverseOrdinal);
                    var execution = compensation.execution();
                    if (compensation.reverseOrdinal() != reverseOrdinal
                            || execution.stateVersion() != 0
                            || reverseOrdinal == 0
                            && execution.status()
                            != ApprovalCompletionExecution.Status.AVAILABLE
                            || reverseOrdinal > 0
                            && execution.status()
                            != ApprovalCompletionExecution.Status.WAITING
                            || reverseOrdinal > 0
                            && snapshot.get(reverseOrdinal - 1).originalOrdinal()
                            <= compensation.originalOrdinal()) {
                        throw conflict(
                                "Compensation plan is not immutable or reverse ordered");
                    }
                    insertCompensation(compensation);
                }
                return snapshot;
            });
        } catch (DuplicateKeyException exception) {
            var existing = findCompensationsByInstance(
                    snapshot.getFirst().instanceId());
            if (existing.size() == snapshot.size()) {
                var sameIdentity = true;
                for (var index = 0; index < snapshot.size(); index++) {
                    var left = snapshot.get(index);
                    var right = existing.get(index);
                    sameIdentity &= left.id() == right.id()
                            && left.originalExecutionId()
                            == right.originalExecutionId()
                            && left.originalOrdinal() == right.originalOrdinal()
                            && left.reverseOrdinal() == right.reverseOrdinal()
                            && left.step().equals(right.step());
                }
                if (sameIdentity) {
                    return existing;
                }
            }
            throw conflict("Compensation plan already exists");
        }
    }

    @Override
    public Optional<ApprovalCompletionCompensation> findCompensation(long id) {
        return optional(
                JdbcApprovalCompensationSql.SELECT, COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), id);
    }

    @Override
    public Optional<ApprovalCompletionCompensation> findCompensationForUpdate(
            long id
    ) {
        return optional(
                JdbcApprovalCompensationSql.SELECT_FOR_UPDATE,
                COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), id);
    }

    @Override
    public List<ApprovalCompletionCompensation> findCompensationsByInstance(
            long instanceId
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_BY_INSTANCE,
                COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId));
    }

    @Override
    public List<ApprovalCompletionCompensation> findCompensationsForUpdate(
            long instanceId
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_PLAN_FOR_UPDATE,
                COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId));
    }

    @Override
    public List<ApprovalCompletionCompensation>
    findAvailableCompensationExternalTasks(
            String topic, java.time.Instant dueAt, int offset, int limit
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        topic = topic == null || topic.isBlank() ? null : topic.strip();
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_AVAILABLE_EXTERNAL,
                COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), topic, topic,
                timestamp(dueAt), timestamp(dueAt), limit, offset));
    }

    @Override
    public long countAvailableCompensationExternalTasks(
            String topic, java.time.Instant dueAt
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        topic = topic == null || topic.isBlank() ? null : topic.strip();
        var count = jdbc.queryForObject(
                JdbcApprovalCompensationSql.COUNT_AVAILABLE_EXTERNAL,
                Long.class, scope.systemId(), scope.tenantId(), topic, topic,
                timestamp(dueAt), timestamp(dueAt));
        if (count == null || count < 0) {
            throw conflict("Scoped compensation count is invalid");
        }
        return count;
    }

    @Override
    public List<ApprovalCompletionCompensation>
    findDueWebhookCompensationsForUpdate(
            java.time.Instant dueAt, int limit
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_DUE_WEBHOOK_FOR_UPDATE,
                COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), timestamp(dueAt),
                timestamp(dueAt), limit));
    }

    @Override
    public List<ApprovalCompletionCompensation> findDueSubflowCompensations(
            java.time.Instant dueAt, int limit
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_DUE_SUBFLOW,
                COMPENSATION_MAPPER,
                scope.systemId(), scope.tenantId(), timestamp(dueAt), limit));
    }

    @Override
    public ApprovalCompletionCompensation saveCompensation(
            ApprovalCompletionCompensation compensation
    ) {
        Objects.requireNonNull(compensation, "compensation");
        var execution = compensation.execution();
        if (execution.stateVersion() < 1) {
            throw conflict("Compensation must be materialized before transition");
        }
        var failure = execution.failure();
        var lease = execution.lease();
        var updated = jdbc.update(
                JdbcApprovalCompensationSql.UPDATE,
                execution.status().name(), execution.attemptCount(),
                execution.stateVersion(), nullableTimestamp(execution.availableAt()),
                lease == null ? null : lease.owner(),
                lease == null ? null : lease.tokenHash(),
                lease == null ? null : timestamp(lease.expiresAt()),
                nullableTimestamp(execution.startedAt()),
                nullableTimestamp(execution.terminalAt()), execution.resultJson(),
                failure == null ? null : failure.code(),
                failure == null ? null : failure.message(),
                failure == null ? null : failure.retryable(),
                scope.systemId(), scope.tenantId(), compensation.id(),
                execution.stateVersion() - 1);
        requireOne(updated, "Compensation changed concurrently");
        return compensation;
    }

    @Override
    public List<ApprovalCompletionCompensation> saveCompensations(
            List<ApprovalCompletionCompensation> compensations
    ) {
        var snapshot = List.copyOf(compensations);
        return transactions.execute(status -> {
            snapshot.forEach(this::saveCompensation);
            return snapshot;
        });
    }

    @Override
    public ApprovalCompensationAttempt appendCompensationAttempt(
            ApprovalCompensationAttempt attempt
    ) {
        Objects.requireNonNull(attempt, "attempt");
        var fact = attempt.fact();
        try {
            jdbc.update(
                    JdbcApprovalCompensationSql.INSERT_ATTEMPT,
                    scope.systemId(), scope.tenantId(), fact.id(),
                    attempt.compensationId(), fact.attemptNumber(),
                    fact.eventSequence(), fact.event().name(),
                    fact.actorMemberId(), fact.leaseOwner(),
                    fact.idempotencyKeyHash(), fact.resultJson(),
                    fact.failureCode(), fact.failureMessage(), fact.httpStatus(),
                    fact.durationMs(), fact.responseSha256(),
                    nullableTimestamp(fact.startedAt()),
                    nullableTimestamp(fact.completedAt()),
                    timestamp(fact.occurredAt()));
            return attempt;
        } catch (DuplicateKeyException exception) {
            throw conflict("Compensation attempt fact already exists");
        }
    }

    @Override
    public List<ApprovalCompensationAttempt> findCompensationAttempts(
            long compensationId
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_ATTEMPTS,
                COMPENSATION_ATTEMPT_MAPPER,
                scope.systemId(), scope.tenantId(), compensationId));
    }

    @Override
    public ApprovalCompensationSubflowRun appendCompensationSubflowRun(
            ApprovalCompensationSubflowRun value
    ) {
        Objects.requireNonNull(value, "value");
        var run = value.run();
        try {
            jdbc.update(
                    JdbcApprovalCompensationSql.INSERT_SUBFLOW,
                    scope.systemId(), scope.tenantId(), run.id(),
                    value.compensationId(), run.attemptNumber(), run.launchKey(),
                    run.childInstanceId(), run.targetDefinitionId(),
                    run.targetVersion(), run.rootInstanceId(), run.depth(),
                    run.status().name(), timestamp(run.launchedAt()),
                    nullableTimestamp(run.terminalAt()), run.resultCode(),
                    nullableTimestamp(run.resultAppliedAt()), run.stateVersion());
            return value;
        } catch (DuplicateKeyException exception) {
            return findCompensationSubflowRunByAttempt(
                    value.compensationId(), run.attemptNumber())
                    .filter(existing -> existing.run().launchKey()
                            .equals(run.launchKey()))
                    .orElseThrow(() -> conflict(
                            "Compensation subflow identity already exists"));
        }
    }

    @Override
    public Optional<ApprovalCompensationSubflowRun>
    findCompensationSubflowRunByAttempt(long compensationId, int attempt) {
        return optional(
                JdbcApprovalCompensationSql.SELECT_SUBFLOW_ATTEMPT,
                COMPENSATION_SUBFLOW_MAPPER,
                scope.systemId(), scope.tenantId(), compensationId, attempt);
    }

    @Override
    public List<ApprovalCompensationSubflowRun> findCompensationSubflowRuns(
            long compensationId
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_SUBFLOWS,
                COMPENSATION_SUBFLOW_MAPPER,
                scope.systemId(), scope.tenantId(), compensationId));
    }

    @Override
    public List<ApprovalCompensationSubflowRun>
    findPendingCompensationSubflowRuns(int limit) {
        return List.copyOf(jdbc.query(
                JdbcApprovalCompensationSql.SELECT_PENDING_SUBFLOWS,
                COMPENSATION_SUBFLOW_MAPPER,
                scope.systemId(), scope.tenantId(), limit));
    }

    @Override
    public ApprovalCompensationSubflowRun saveCompensationSubflowRun(
            ApprovalCompensationSubflowRun value
    ) {
        Objects.requireNonNull(value, "value");
        var run = value.run();
        if (run.stateVersion() < 1) {
            throw conflict(
                    "Compensation subflow run must be appended before transition");
        }
        var updated = jdbc.update(
                JdbcApprovalCompensationSql.UPDATE_SUBFLOW,
                run.status().name(), nullableTimestamp(run.terminalAt()),
                run.resultCode(), nullableTimestamp(run.resultAppliedAt()),
                run.stateVersion(), scope.systemId(), scope.tenantId(),
                run.id(), run.stateVersion() - 1);
        requireOne(updated, "Compensation subflow run changed concurrently");
        return value;
    }

    @Override
    public ApprovalDecisionCommentTemplate saveDecisionCommentTemplate(
            ApprovalDecisionCommentTemplate template
    ) {
        Objects.requireNonNull(template, "template");
        try {
            return transactions.execute(status -> {
                var existing = optional(
                        JdbcApprovalSql.SELECT_DECISION_COMMENT_TEMPLATE_FOR_UPDATE,
                        COMMENT_TEMPLATE_MAPPER,
                        scope.systemId(), scope.tenantId(), template.id());
                if (existing.isEmpty()) {
                    if (template.currentVersion() != 1) {
                        throw conflict(
                                "New comment template must start at version one");
                    }
                    jdbc.update(
                            JdbcApprovalSql.INSERT_DECISION_COMMENT_TEMPLATE,
                            scope.systemId(), scope.tenantId(), template.id(),
                            template.name(), template.status().name(),
                            template.currentVersion(), template.createdBy(),
                            timestamp(template.createdAt()), template.updatedBy(),
                            timestamp(template.updatedAt()));
                    insertDecisionCommentTemplateVersion(
                            template.currentVersionSnapshot());
                    return template;
                }
                var stored = existing.get();
                if (template.createdBy() != stored.createdBy()
                        || !template.createdAt().equals(stored.createdAt())) {
                    throw conflict(
                            "Comment template creation facts are immutable");
                }
                if (template.currentVersion() == stored.currentVersion()) {
                    if (!template.name().equals(stored.name())
                            || !template.body().equals(stored.body())) {
                        throw conflict(
                                "Comment template content requires a new version");
                    }
                } else if (template.currentVersion()
                        == stored.currentVersion() + 1) {
                    insertDecisionCommentTemplateVersion(
                            template.currentVersionSnapshot());
                } else {
                    throw conflict(
                            "Comment template version changed concurrently");
                }
                var updated = jdbc.update(
                        JdbcApprovalSql.UPDATE_DECISION_COMMENT_TEMPLATE,
                        template.name(), template.status().name(),
                        template.currentVersion(), template.updatedBy(),
                        timestamp(template.updatedAt()),
                        scope.systemId(), scope.tenantId(), template.id(),
                        stored.currentVersion());
                requireOne(
                        updated,
                        "Comment template version changed concurrently");
                return template;
            });
        } catch (DuplicateKeyException exception) {
            throw conflict("Comment template id, name or version already exists");
        }
    }

    @Override
    public Optional<ApprovalDecisionCommentTemplate>
    findDecisionCommentTemplate(long templateId) {
        return optional(
                JdbcApprovalSql.SELECT_DECISION_COMMENT_TEMPLATE,
                COMMENT_TEMPLATE_MAPPER,
                scope.systemId(), scope.tenantId(), templateId);
    }

    @Override
    public Optional<ApprovalDecisionCommentTemplateVersion>
    findDecisionCommentTemplateVersion(long templateId, int version) {
        return optional(
                JdbcApprovalSql.SELECT_DECISION_COMMENT_TEMPLATE_VERSION,
                COMMENT_TEMPLATE_VERSION_MAPPER,
                scope.systemId(), scope.tenantId(), templateId, version);
    }

    @Override
    public List<ApprovalDecisionCommentTemplate> findDecisionCommentTemplates(
            boolean activeOnly,
            int offset,
            int limit
    ) {
        return List.copyOf(jdbc.query(
                JdbcApprovalSql.decisionCommentTemplates(activeOnly),
                COMMENT_TEMPLATE_MAPPER,
                scope.systemId(), scope.tenantId(), limit, offset));
    }

    @Override
    public long countDecisionCommentTemplates(boolean activeOnly) {
        var value = jdbc.queryForObject(
                JdbcApprovalSql.countDecisionCommentTemplates(activeOnly),
                Long.class,
                scope.systemId(), scope.tenantId());
        if (value == null || value < 0) {
            throw conflict("Scoped comment-template count is invalid");
        }
        return value;
    }

    @Override
    public ApprovalDecisionEvidence saveDecisionEvidence(
            ApprovalDecisionEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence");
        if (evidence.template() != null) {
            var version = findDecisionCommentTemplateVersion(
                    evidence.template().templateId(),
                    evidence.template().version());
            if (version.isEmpty()
                    || !version.get().name().equals(
                    evidence.template().name())) {
                throw conflict(
                        "Decision evidence template snapshot does not exist");
            }
        }
        try {
            return transactions.execute(status -> {
                jdbc.update(
                        JdbcApprovalSql.INSERT_DECISION_EVIDENCE,
                        scope.systemId(), scope.tenantId(), evidence.id(),
                        evidence.instanceId(), evidence.historySequence(),
                        evidence.branchCode(), evidence.stageIndex(),
                        evidence.decision().name(),
                        evidence.signature() == null
                                ? null : evidence.signature().kind().name(),
                        evidence.signature() == null
                                || evidence.signature().file() == null
                                ? null : evidence.signature().file().fileId(),
                        evidence.signature() == null
                                ? null : evidence.signature().typedValue(),
                        evidence.template() == null
                                ? null : evidence.template().templateId(),
                        evidence.template() == null
                                ? null : evidence.template().version(),
                        evidence.template() == null
                                ? null : evidence.template().name(),
                        evidence.actorId(), evidence.representedMemberId(),
                        evidence.delegationRuleId(),
                        timestamp(evidence.decidedAt()));
                insertDecisionEvidenceFiles(evidence);
                return evidence;
            });
        } catch (DuplicateKeyException exception) {
            var byIdentity = findDecisionEvidence(
                    evidence.instanceId(), evidence.historySequence());
            var byId = findDecisionEvidenceById(evidence.id());
            if (byIdentity.isPresent()
                    && byId.isPresent()
                    && evidence.equals(byIdentity.get())
                    && evidence.equals(byId.get())) {
                return evidence;
            }
            throw conflict(
                    "Decision evidence identity already has another snapshot");
        }
    }

    @Override
    public Optional<ApprovalDecisionEvidence> findDecisionEvidence(
            long instanceId,
            int historySequence
    ) {
        return evidence(optional(
                JdbcApprovalSql.SELECT_DECISION_EVIDENCE,
                DECISION_EVIDENCE_MAPPER,
                scope.systemId(), scope.tenantId(),
                instanceId, historySequence));
    }

    @Override
    public List<ApprovalDecisionEvidence> findDecisionEvidenceByInstance(
            long instanceId
    ) {
        return jdbc.query(
                JdbcApprovalSql.SELECT_DECISION_EVIDENCE_BY_INSTANCE,
                DECISION_EVIDENCE_MAPPER,
                scope.systemId(), scope.tenantId(), instanceId
        ).stream().map(row -> evidence(Optional.of(row)).orElseThrow()).toList();
    }

    @Override
    public ApprovalDelegationRule saveDelegation(ApprovalDelegationRule rule) {
        Objects.requireNonNull(rule, "rule");
        if (rule.tenantId() != scope.tenantId()) {
            throw conflict("Approval delegation escaped the repository tenant scope");
        }
        if (rule.status() == ApprovalDelegationRule.Status.REVOKED) {
            var updated = jdbc.update(
                    JdbcApprovalSql.REVOKE_DELEGATION,
                    rule.revokedByMemberId(), timestamp(rule.revokedAt()),
                    scope.systemId(), scope.tenantId(), rule.id()
            );
            if (updated == 1) {
                return rule;
            }
            var existing = findDelegation(rule.id());
            if (existing.isPresent()
                    && existing.get().status() == ApprovalDelegationRule.Status.REVOKED) {
                return existing.get();
            }
            throw conflict("Approval delegation is no longer active");
        }
        try {
            jdbc.update(
                    JdbcApprovalSql.INSERT_DELEGATION,
                    scope.systemId(), scope.tenantId(), rule.id(),
                    rule.delegatorMemberId(), rule.delegateMemberId(),
                    rule.definitionId(), timestamp(rule.startsAt()), timestamp(rule.endsAt()),
                    rule.status().name(), rule.createdByMemberId(), timestamp(rule.createdAt()),
                    rule.revokedByMemberId(), nullableTimestamp(rule.revokedAt())
            );
            return rule;
        } catch (DuplicateKeyException exception) {
            throw conflict("Approval delegation id already exists");
        }
    }

    @Override
    public Optional<ApprovalDelegationRule> findDelegation(long delegationRuleId) {
        return optional(
                JdbcApprovalSql.SELECT_DELEGATION,
                DELEGATION_MAPPER,
                scope.systemId(), scope.tenantId(), delegationRuleId
        );
    }

    @Override
    public Optional<ApprovalDelegationRule> findDelegationForUpdate(
            long delegationRuleId
    ) {
        return optional(
                JdbcApprovalSql.SELECT_DELEGATION_FOR_UPDATE,
                DELEGATION_MAPPER,
                scope.systemId(), scope.tenantId(), delegationRuleId
        );
    }

    @Override
    public List<ApprovalDelegationRule> findDelegationsByDelegator(
            long delegatorMemberId,
            int offset,
            int limit
    ) {
        return jdbc.query(
                JdbcApprovalSql.SELECT_DELEGATIONS_BY_DELEGATOR,
                DELEGATION_MAPPER,
                scope.systemId(), scope.tenantId(), delegatorMemberId, limit, offset
        );
    }

    @Override
    public long countDelegationsByDelegator(long delegatorMemberId) {
        var value = jdbc.queryForObject(
                JdbcApprovalSql.COUNT_DELEGATIONS_BY_DELEGATOR,
                Long.class,
                scope.systemId(), scope.tenantId(), delegatorMemberId
        );
        if (value == null || value < 0) {
            throw conflict("Scoped approval delegation count is invalid");
        }
        return value;
    }

    @Override
    public List<ApprovalDelegationRule> findDelegationConflictsForUpdate(
            long delegatorMemberId,
            long delegateMemberId,
            java.time.Instant startsAt,
            java.time.Instant endsAt
    ) {
        return jdbc.query(
                JdbcApprovalSql.SELECT_DELEGATION_CONFLICTS_FOR_UPDATE,
                DELEGATION_MAPPER,
                scope.systemId(), scope.tenantId(),
                timestamp(endsAt), timestamp(startsAt),
                delegatorMemberId, delegateMemberId,
                delegatorMemberId, delegateMemberId
        );
    }

    @Override
    public Optional<ApprovalDelegationRule> findActiveDelegationForUpdate(
            long delegateMemberId,
            long representedMemberId,
            long definitionId,
            java.time.Instant effectiveAt
    ) {
        return optional(
                JdbcApprovalSql.SELECT_ACTIVE_DELEGATION_FOR_UPDATE,
                DELEGATION_MAPPER,
                scope.systemId(), scope.tenantId(),
                delegateMemberId, representedMemberId,
                timestamp(effectiveAt), timestamp(effectiveAt),
                definitionId, definitionId
        ).map(rule -> rule.at(effectiveAt));
    }

    @Override
    public List<ApprovalTaskAssignment> findApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            java.time.Instant effectiveAt,
            int offset,
            int limit
    ) {
        var assignments = allApprovalTaskAssignments(actorMemberId, status, effectiveAt);
        return assignments.stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            java.time.Instant effectiveAt
    ) {
        return allApprovalTaskAssignments(actorMemberId, status, effectiveAt).size();
    }

    private List<ApprovalTaskAssignment> allApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            java.time.Instant effectiveAt
    ) {
        var instances = new LinkedHashMap<Long, ApprovalInstance>();
        var authorities = new LinkedHashMap<
                Long,
                LinkedHashMap<Long, ApprovalTaskAssignment.RepresentedAuthority>
                >();
        for (var instance : findApprovalTasks(
                actorMemberId, status, 0, Integer.MAX_VALUE
        )) {
            instances.put(instance.id(), instance);
            authorities.computeIfAbsent(instance.id(), ignored -> new LinkedHashMap<>())
                    .put(
                            actorMemberId,
                            new ApprovalTaskAssignment.RepresentedAuthority(
                                    actorMemberId,
                                    null
                            )
                    );
        }
        var delegations = jdbc.query(
                JdbcApprovalSql.SELECT_ACTIVE_DELEGATIONS_BY_DELEGATE,
                DELEGATION_MAPPER,
                scope.systemId(), scope.tenantId(), actorMemberId,
                timestamp(effectiveAt), timestamp(effectiveAt)
        );
        for (var storedRule : delegations) {
            var rule = storedRule.at(effectiveAt);
            for (var instance : findApprovalTasks(
                    rule.delegatorMemberId(), status, 0, Integer.MAX_VALUE
            )) {
                if (rule.definitionId() != null
                        && rule.definitionId() != instance.definitionId()) {
                    continue;
                }
                instances.put(instance.id(), instance);
                authorities.computeIfAbsent(instance.id(), ignored -> new LinkedHashMap<>())
                        .put(
                                rule.delegatorMemberId(),
                                new ApprovalTaskAssignment.RepresentedAuthority(
                                        rule.delegatorMemberId(),
                                        rule.id()
                                )
                        );
            }
        }
        var assignments = new ArrayList<ApprovalTaskAssignment>();
        instances.forEach((instanceId, instance) ->
                assignments.add(new ApprovalTaskAssignment(
                        instance,
                        actorMemberId,
                        List.copyOf(authorities.get(instanceId).values())
                )));
        assignments.sort(
                Comparator
                        .comparing(
                                (ApprovalTaskAssignment value) ->
                                        value.instance().startedAt()
                        )
                        .reversed()
                        .thenComparing(
                                Comparator.comparingLong(
                                        (ApprovalTaskAssignment value) ->
                                                value.instance().id()
                                ).reversed()
                        )
        );
        return List.copyOf(assignments);
    }

    private void insertDecisionCommentTemplateVersion(
            ApprovalDecisionCommentTemplateVersion version
    ) {
        jdbc.update(
                JdbcApprovalSql.INSERT_DECISION_COMMENT_TEMPLATE_VERSION,
                scope.systemId(), scope.tenantId(), version.templateId(),
                version.version(), version.name(), version.body(),
                version.createdBy(), timestamp(version.createdAt()));
    }

    private void insertDecisionEvidenceFiles(
            ApprovalDecisionEvidence evidence
    ) {
        var attachmentOrder = new LinkedHashMap<Long, Integer>();
        for (var index = 0; index < evidence.attachments().size(); index++) {
            attachmentOrder.put(
                    evidence.attachments().get(index).fileId(), index);
        }
        var signatureFileId = evidence.signature() == null
                || evidence.signature().file() == null
                ? null : evidence.signature().file().fileId();
        for (var file : evidence.referencedFiles()) {
            var order = attachmentOrder.get(file.fileId());
            jdbc.update(
                    JdbcApprovalSql.INSERT_DECISION_EVIDENCE_FILE,
                    scope.systemId(), scope.tenantId(), evidence.id(),
                    file.fileId(), file.originalName(), file.contentType(),
                    file.sizeBytes(), file.sha256(), order != null,
                    signatureFileId != null
                            && signatureFileId == file.fileId(),
                    order);
        }
    }

    private Optional<ApprovalDecisionEvidence> findDecisionEvidenceById(
            long evidenceId
    ) {
        return evidence(optional(
                JdbcApprovalSql.SELECT_DECISION_EVIDENCE_BY_ID,
                DECISION_EVIDENCE_MAPPER,
                scope.systemId(), scope.tenantId(), evidenceId));
    }

    private Optional<ApprovalDecisionEvidence> evidence(
            Optional<DecisionEvidenceRow> row
    ) {
        if (row.isEmpty()) {
            return Optional.empty();
        }
        var files = jdbc.query(
                JdbcApprovalSql.SELECT_DECISION_EVIDENCE_FILES,
                DECISION_EVIDENCE_FILE_MAPPER,
                scope.systemId(), scope.tenantId(), row.get().id());
        return Optional.of(row.get().toDomain(files));
    }

    private void insertCompletionExecution(
            ApprovalCompletionExecution execution
    ) {
        var lease = execution.lease();
        var failure = execution.failure();
        jdbc.update(
                JdbcApprovalCompletionSql.INSERT_EXECUTION,
                scope.systemId(), scope.tenantId(),
                execution.id(), execution.instanceId(),
                execution.definitionId(), execution.definitionVersion(),
                execution.ordinal(), execution.step().code(),
                execution.step().name(), execution.step().type().name(),
                execution.step().parallelGroup(),
                completionConfig(execution.step()), execution.payloadJson(),
                execution.status().name(), execution.attemptCount(),
                execution.stateVersion(),
                nullableTimestamp(execution.availableAt()),
                lease == null ? null : lease.owner(),
                lease == null ? null : lease.tokenHash(),
                lease == null ? null : timestamp(lease.expiresAt()),
                timestamp(execution.createdAt()),
                nullableTimestamp(execution.startedAt()),
                nullableTimestamp(execution.terminalAt()),
                execution.resultJson(),
                failure == null ? null : failure.code(),
                failure == null ? null : failure.message(),
                failure == null ? null : failure.retryable());
    }

    private void insertCompensation(
            ApprovalCompletionCompensation compensation
    ) {
        var execution = compensation.execution();
        var lease = execution.lease();
        var failure = execution.failure();
        jdbc.update(
                JdbcApprovalCompensationSql.INSERT,
                scope.systemId(), scope.tenantId(), compensation.id(),
                compensation.instanceId(), compensation.originalExecutionId(),
                compensation.originalOrdinal(), compensation.reverseOrdinal(),
                execution.definitionId(), execution.definitionVersion(),
                execution.step().code(), execution.step().name(),
                execution.step().type().name(),
                completionConfig(execution.step()), execution.payloadJson(),
                execution.status().name(), execution.attemptCount(),
                execution.stateVersion(),
                nullableTimestamp(execution.availableAt()),
                lease == null ? null : lease.owner(),
                lease == null ? null : lease.tokenHash(),
                lease == null ? null : timestamp(lease.expiresAt()),
                timestamp(execution.createdAt()),
                nullableTimestamp(execution.startedAt()),
                nullableTimestamp(execution.terminalAt()), execution.resultJson(),
                failure == null ? null : failure.code(),
                failure == null ? null : failure.message(),
                failure == null ? null : failure.retryable());
    }

    private void insertInstance(ApprovalInstance instance) {
        try {
            jdbc.update(
                    JdbcApprovalSql.INSERT_INSTANCE,
                    scope.systemId(), scope.tenantId(), instance.id(), instance.definitionId(),
                    instance.definitionVersion(), instance.businessKey(),
                    instance.recordBinding() == null ? null : instance.recordBinding().moduleCode(),
                    instance.recordBinding() == null ? null : instance.recordBinding().recordId(),
                    instance.requesterId(),
                    instance.approverId(), approverIdsJson(instance.approverIds()),
                    instance.currentStepIndex(), instance.claimState().name(),
                    instance.approvalMode().name(),
                    instance.requiredApprovals(),
                    deadlinePolicy(instance.deadline()),
                    deadlineRemindAt(instance.deadline()),
                    deadlineDueAt(instance.deadline()),
                     deadlineRemindedAt(instance.deadline()),
                     deadlineProcessedAt(instance.deadline()),
                     decisionCommentPolicy(instance.decisionCommentPolicy()),
                    decisionEvidencePolicy(instance.decisionEvidencePolicy()),
                    approvalStageState(instance.stages()),
                    startContext(instance.startContext()),
                    instance.currentStageIndex(),
                    approverIdsJson(instance.approvedApproverIds()),
                    approverIdsJson(instance.rejectedApproverIds()),
                    instance.status().name(),
                    instance.completionPhase().name(),
                    instance.completionFailurePolicy().name(),
                    instance.activeCompletionOrdinal(),
                    0, timestamp(instance.startedAt()), null
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("Approval instance or business key already exists");
        }
    }

    private void insertParallelBranches(ApprovalInstance instance) {
        for (var index = 0; index < instance.parallelBranches().size(); index++) {
            var branch = instance.parallelBranches().get(index);
            jdbc.update(
                    JdbcApprovalSql.INSERT_PARALLEL_BRANCH,
                    scope.systemId(), scope.tenantId(), instance.id(),
                    branch.code(), branch.name(), index,
                    branch.approverId(), approverIdsJson(branch.approverIds()),
                    branch.currentStepIndex(), branch.approvalMode().name(),
                    branch.requiredApprovals(),
                    deadlinePolicy(branch.deadline()),
                    deadlineRemindAt(branch.deadline()),
                    deadlineDueAt(branch.deadline()),
                     deadlineRemindedAt(branch.deadline()),
                      deadlineProcessedAt(branch.deadline()),
                      decisionCommentPolicy(branch.decisionCommentPolicy()),
                    decisionEvidencePolicy(branch.decisionEvidencePolicy()),
                    approvalStageState(branch.stages()),
                    branch.currentStageIndex(),
                    approverIdsJson(branch.approvedApproverIds()),
                    approverIdsJson(branch.rejectedApproverIds()),
                    branch.status().name(), timestamp(branch.startedAt()),
                    nullableTimestamp(branch.completedAt())
            );
        }
    }

    private void updateParallelBranches(ApprovalInstance instance) {
        for (var branch : instance.parallelBranches()) {
            var updated = jdbc.update(
                    JdbcApprovalSql.UPDATE_PARALLEL_BRANCH,
                    branch.approverId(), approverIdsJson(branch.approverIds()),
                    branch.currentStepIndex(), branch.approvalMode().name(),
                    branch.requiredApprovals(),
                    deadlinePolicy(branch.deadline()),
                    deadlineRemindAt(branch.deadline()),
                    deadlineDueAt(branch.deadline()),
                     deadlineRemindedAt(branch.deadline()),
                      deadlineProcessedAt(branch.deadline()),
                      decisionCommentPolicy(branch.decisionCommentPolicy()),
                    decisionEvidencePolicy(branch.decisionEvidencePolicy()),
                    approvalStageState(branch.stages()),
                    branch.currentStageIndex(),
                    approverIdsJson(branch.approvedApproverIds()),
                    approverIdsJson(branch.rejectedApproverIds()),
                    branch.status().name(), nullableTimestamp(branch.completedAt()),
                    scope.systemId(), scope.tenantId(), instance.id(), branch.code()
            );
            requireOne(updated, "Parallel branch execution snapshot is missing");
        }
    }

    private ApprovalInstance toDomain(
            InstanceRow row,
            List<ApprovalHistoryEvent> history
    ) {
        var branches = jdbc.query(
                JdbcApprovalSql.SELECT_PARALLEL_BRANCHES,
                PARALLEL_BRANCH_MAPPER,
                scope.systemId(), scope.tenantId(), row.id()
        );
        var executions = jdbc.query(
                JdbcApprovalCompletionSql.SELECT_BY_INSTANCE,
                COMPLETION_EXECUTION_MAPPER,
                scope.systemId(), scope.tenantId(), row.id());
        return row.toDomain(history, branches, executions);
    }

    private void insertHistory(long instanceId, int sequence, ApprovalHistoryEvent event) {
        jdbc.update(
                JdbcApprovalSql.INSERT_HISTORY,
                scope.systemId(), scope.tenantId(), instanceId, sequence, event.type().name(), event.actorId(),
                event.representedMemberId(), event.delegationRuleId(),
                event.fromStatus() == null ? null : event.fromStatus().name(), event.toStatus().name(),
                event.comment(), timestamp(event.occurredAt()), event.targetMemberId(),
                event.assignmentPosition() == null ? null : event.assignmentPosition().name(),
                event.targetStepIndex()
        );
    }

    private void insertDraftSteps(ApprovalDefinitionDraft draft) {
        for (var index = 0; index < draft.approverIds().size(); index++) {
            jdbc.update(
                    JdbcApprovalSql.INSERT_DRAFT_STEP,
                    scope.systemId(), scope.tenantId(), draft.id(), index + 1,
                    draft.approverIds().get(index)
            );
        }
    }

    private void insertVersionSteps(ApprovalDefinitionVersion version) {
        for (var index = 0; index < version.approverIds().size(); index++) {
            jdbc.update(
                    JdbcApprovalSql.INSERT_VERSION_STEP,
                    scope.systemId(), scope.tenantId(), version.definitionId(), version.version(),
                    index + 1, version.approverIds().get(index)
            );
        }
    }

    private void syncPeriodicSchedule(ApprovalDefinitionVersion version) {
        var schedule = version.triggerBinding() == null
                ? null
                : version.triggerBinding().periodicSchedule();
        if (schedule == null) {
            jdbc.update(
                    JdbcFlowPeriodicSql.DELETE_SCHEDULE,
                    scope.systemId(), scope.tenantId(), version.definitionId()
            );
            return;
        }
        jdbc.update(
                JdbcFlowPeriodicSql.UPSERT_SCHEDULE,
                scope.systemId(), scope.tenantId(), version.definitionId(), version.version(),
                schedule.requesterMemberId(), schedule.intervalMinutes(),
                timestamp(schedule.startAt()), timestamp(schedule.startAt()),
                timestamp(version.publishedAt())
        );
    }

    private <T> Optional<T> optional(String sql, RowMapper<T> mapper, Object... arguments) {
        var rows = jdbc.query(sql, mapper, arguments);
        if (rows.size() > 1) {
            throw conflict("Scoped flow query returned more than one row");
        }
        return rows.stream().findFirst();
    }

    private long count(String sql) {
        var value = jdbc.queryForObject(sql, Long.class, scope.systemId(), scope.tenantId());
        if (value == null || value < 0) {
            throw conflict("Scoped flow count query returned an invalid result");
        }
        return value;
    }

    private static void requireOne(int updated, String message) {
        if (updated != 1) {
            throw conflict(message);
        }
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(PERSISTENCE_CONFLICT, message);
    }

    private static Timestamp timestamp(java.time.Instant value) {
        return Timestamp.from(Objects.requireNonNull(value, "timestamp"));
    }

    private static Timestamp nullableTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static java.time.Instant nullableTimestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static ApprovalInstance.Status nullableStatus(String value) {
        return value == null ? null : ApprovalInstance.Status.valueOf(value);
    }

    private static ApprovalHistoryEvent.AssignmentPosition nullablePosition(String value) {
        return value == null ? null : ApprovalHistoryEvent.AssignmentPosition.valueOf(value);
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        var value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        var value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static int runtimeRequiredApprovals(ResultSet result) throws SQLException {
        try {
            var value = result.getInt("required_approvals");
            if (!result.wasNull() && value > 0) {
                return value;
            }
        } catch (SQLException ignored) {
            // Pre-V8.34 mapping fixtures intentionally exercise legacy rows.
        }
        var mode = ApprovalMode.valueOf(result.getString("approval_mode"));
        var memberCount = approverIds(result).size();
        return ApprovalQuorumRules.requiredApprovals(mode, null, memberCount);
    }

    private static ApprovalInstance.RecordBinding recordBinding(ResultSet result) throws SQLException {
        var moduleCode = result.getString("module_code");
        var recordId = nullableLong(result, "record_id");
        if (moduleCode == null && recordId == null) {
            return null;
        }
        if (moduleCode == null || recordId == null) {
            throw new SQLException("Approval instance record binding is incomplete");
        }
        try {
            return new ApprovalInstance.RecordBinding(moduleCode, recordId);
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Approval instance record binding is invalid", exception);
        }
    }

    private static TriggerBinding triggerBinding(ResultSet result) throws SQLException {
        var moduleCode = result.getString("trigger_module_code");
        var event = result.getString("trigger_event");
        var priority = nullableInteger(result, "trigger_priority");
        var exclusive = nullableBoolean(result, "trigger_exclusive");
        var conditionsJson = result.getString("trigger_conditions");
        var startAt = nullableTimestamp(result.getTimestamp("trigger_start_at"));
        var intervalMinutes = nullableInteger(result, "trigger_interval_minutes");
        var requesterId = nullableLong(result, "trigger_requester_id");
        if (moduleCode == null
                && event == null
                && priority == null
                && exclusive == null
                && conditionsJson == null
                && startAt == null
                && intervalMinutes == null
                && requesterId == null) {
            return null;
        }
        if (event == null
                || priority == null
                || exclusive == null
                || conditionsJson == null) {
            throw new SQLException("Approval definition trigger binding is incomplete");
        }
        try {
            var type = TriggerBinding.Event.valueOf(event);
            if (type == TriggerBinding.Event.PERIODIC) {
                if (moduleCode != null
                        || startAt == null
                        || intervalMinutes == null
                        || requesterId == null) {
                    throw new SQLException("Periodic approval trigger binding is incomplete");
                }
                return new TriggerBinding(
                        null,
                        type,
                        priority,
                        exclusive,
                        triggerConditions(conditionsJson),
                        new PeriodicSchedule(startAt, intervalMinutes, requesterId)
                );
            }
            if (moduleCode == null
                    || startAt != null
                    || intervalMinutes != null
                    || requesterId != null) {
                throw new SQLException("Event approval trigger binding is incomplete");
            }
            return new TriggerBinding(
                    moduleCode,
                    type,
                    priority,
                    exclusive,
                    triggerConditions(conditionsJson)
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition trigger binding is invalid", exception);
        }
    }

    private static RecordStatusMapping recordStatusMapping(ResultSet result) throws SQLException {
        var fieldCode = result.getString("status_field_code");
        var approvedValue = result.getString("status_approved_value");
        var rejectedValue = result.getString("status_rejected_value");
        var withdrawnValue = result.getString("status_withdrawn_value");
        var terminatedValue = result.getString("status_terminated_value");
        if (fieldCode == null
                && approvedValue == null
                && rejectedValue == null
                && withdrawnValue == null
                && terminatedValue == null) {
            return null;
        }
        if (fieldCode == null
                || approvedValue == null
                || rejectedValue == null
                || withdrawnValue == null
                || terminatedValue == null) {
            throw new SQLException("Approval definition record status mapping is incomplete");
        }
        try {
            return new RecordStatusMapping(
                    fieldCode,
                    approvedValue,
                    rejectedValue,
                    withdrawnValue,
                    terminatedValue
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Approval definition record status mapping is invalid", exception);
        }
    }

    private static Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
        var value = result.getBoolean(column);
        return result.wasNull() ? null : value;
    }

    private static String triggerModuleCode(TriggerBinding value) {
        return value == null ? null : value.moduleCode();
    }

    private static String triggerEvent(TriggerBinding value) {
        return value == null ? null : value.event().name();
    }

    private static Integer triggerPriority(TriggerBinding value) {
        return value == null ? null : value.priority();
    }

    private static Boolean triggerExclusive(TriggerBinding value) {
        return value == null ? null : value.exclusive();
    }

    private static String statusFieldCode(RecordStatusMapping value) {
        return value == null ? null : value.fieldCode();
    }

    private static String statusApprovedValue(RecordStatusMapping value) {
        return value == null ? null : value.approvedValue();
    }

    private static String statusRejectedValue(RecordStatusMapping value) {
        return value == null ? null : value.rejectedValue();
    }

    private static String statusWithdrawnValue(RecordStatusMapping value) {
        return value == null ? null : value.withdrawnValue();
    }

    private static String statusTerminatedValue(RecordStatusMapping value) {
        return value == null ? null : value.terminatedValue();
    }

    private static String triggerConditions(TriggerBinding value) {
        if (value == null) {
            return null;
        }
        var conditions = TRIGGER_JSON.createArrayNode();
        for (var condition : value.conditions()) {
            var serialized = conditions.addObject();
            serialized.put("fieldCode", condition.fieldCode());
            serialized.put("operator", condition.operator().name());
            if (condition.valueJson() != null) {
                try {
                    serialized.set("value", TRIGGER_JSON.readTree(condition.valueJson()));
                } catch (JsonProcessingException exception) {
                    throw new IllegalArgumentException(
                            "Trigger condition JSON must be serializable",
                            exception
                    );
                }
            }
        }
        return conditions.toString();
    }

    static String gateway(ApprovalGateway value) {
        if (value == null) {
            return null;
        }
        var branches = TRIGGER_JSON.createArrayNode();
        for (var branch : value.branches()) {
            var serialized = branches.addObject();
            serialized.put("code", branch.code());
            serialized.put("name", branch.name());
            serialized.put("defaultBranch", branch.defaultBranch());
            serialized.put("approvalMode", branch.approvalMode().name());
            var conditions = serialized.putArray("conditions");
            for (var condition : branch.conditions()) {
                var conditionJson = conditions.addObject();
                conditionJson.put("fieldCode", condition.fieldCode());
                conditionJson.put("operator", condition.operator().name());
                if (condition.valueJson() != null) {
                    try {
                        conditionJson.set("value", TRIGGER_JSON.readTree(condition.valueJson()));
                    } catch (JsonProcessingException exception) {
                        throw new IllegalArgumentException(
                                "Gateway condition JSON must be serializable",
                                exception
                        );
                    }
                }
            }
            var approvers = serialized.putArray("approverIds");
            branch.approverIds().forEach(approvers::add);
            writeApprovalStages(serialized, branch.approvalStages());
        }
        return branches.toString();
    }

    private static ApprovalGateway gateway(ResultSet result) throws SQLException {
        var serialized = result.getString("gateway_branches");
        if (serialized == null) {
            return null;
        }
        try {
            var values = TRIGGER_JSON.readTree(serialized);
            if (!values.isArray()) {
                throw new IllegalArgumentException("Gateway JSON must be an array");
            }
            var branches = new java.util.ArrayList<ApprovalGateway.Branch>();
            for (var value : values) {
                var conditionsNode = value.path("conditions");
                var conditions = triggerConditions(conditionsNode.toString());
                var approversNode = value.path("approverIds");
                if (!approversNode.isArray()) {
                    throw new IllegalArgumentException("Gateway route must be an array");
                }
                var approvers = new java.util.ArrayList<Long>();
                approversNode.forEach(approver -> approvers.add(approver.longValue()));
                branches.add(new ApprovalGateway.Branch(
                        value.path("code").asText(""),
                        value.path("name").asText(""),
                      value.path("defaultBranch").asBoolean(false),
                      conditions,
                       approvers,
                       ApprovalMode.valueOf(
                               value.path("approvalMode").asText(ApprovalMode.SEQUENTIAL.name())
                       ),
                       readApprovalStages(value.get("approvalStages"), 1)
               ));
            }
            return new ApprovalGateway(branches);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition gateway is invalid", exception);
        }
    }

    static String parallelGateway(ApprovalParallelGateway value) {
        if (value == null) {
            return null;
        }
        var branches = TRIGGER_JSON.createArrayNode();
        for (var branch : value.branches()) {
            var serialized = branches.addObject();
            serialized.put("code", branch.code());
            serialized.put("name", branch.name());
            serialized.put("approvalMode", branch.approvalMode().name());
            var approvers = serialized.putArray("approverIds");
            branch.approverIds().forEach(approvers::add);
            writeApprovalStages(serialized, branch.approvalStages());
        }
        return branches.toString();
    }

    private static ApprovalParallelGateway parallelGateway(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "parallel_branches");
        if (serialized == null) {
            return null;
        }
        try {
            var values = TRIGGER_JSON.readTree(serialized);
            if (!values.isArray()) {
                throw new IllegalArgumentException("Parallel gateway JSON must be an array");
            }
            var branches = new java.util.ArrayList<ApprovalParallelGateway.Branch>();
            for (var value : values) {
                var approversNode = value.path("approverIds");
                if (!approversNode.isArray()) {
                    throw new IllegalArgumentException("Parallel branch route must be an array");
                }
                var approvers = new java.util.ArrayList<Long>();
                approversNode.forEach(approver -> approvers.add(approver.longValue()));
                branches.add(new ApprovalParallelGateway.Branch(
                        value.path("code").asText(""),
                        value.path("name").asText(""),
                        approvers,
                        ApprovalMode.valueOf(
                                value.path("approvalMode").asText(
                                        ApprovalMode.SEQUENTIAL.name()
                                )
                        ),
                        readApprovalStages(value.get("approvalStages"), 1)
                ));
            }
            return new ApprovalParallelGateway(branches);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition parallel gateway is invalid", exception);
        }
    }

    static String inclusiveGateway(ApprovalInclusiveGateway value) {
        if (value == null) {
            return null;
        }
        var branches = TRIGGER_JSON.createArrayNode();
        for (var branch : value.branches()) {
            var serialized = branches.addObject();
            serialized.put("code", branch.code());
            serialized.put("name", branch.name());
            serialized.put("defaultBranch", branch.defaultBranch());
            serialized.put("approvalMode", branch.approvalMode().name());
            var conditions = serialized.putArray("conditions");
            for (var condition : branch.conditions()) {
                var conditionJson = conditions.addObject();
                conditionJson.put("fieldCode", condition.fieldCode());
                conditionJson.put("operator", condition.operator().name());
                if (condition.valueJson() != null) {
                    try {
                        conditionJson.set("value", TRIGGER_JSON.readTree(condition.valueJson()));
                    } catch (JsonProcessingException exception) {
                        throw new IllegalArgumentException(
                                "Inclusive condition JSON must be serializable",
                                exception
                        );
                    }
                }
            }
            var approvers = serialized.putArray("approverIds");
            branch.approverIds().forEach(approvers::add);
            writeApprovalStages(serialized, branch.approvalStages());
        }
        return branches.toString();
    }

    private static ApprovalInclusiveGateway inclusiveGateway(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "inclusive_branches");
        if (serialized == null) {
            return null;
        }
        try {
            var values = TRIGGER_JSON.readTree(serialized);
            if (!values.isArray()) {
                throw new IllegalArgumentException("Inclusive gateway JSON must be an array");
            }
            var branches = new java.util.ArrayList<ApprovalInclusiveGateway.Branch>();
            for (var value : values) {
                var approversNode = value.path("approverIds");
                if (!approversNode.isArray()) {
                    throw new IllegalArgumentException("Inclusive branch route must be an array");
                }
                var approvers = new java.util.ArrayList<Long>();
                approversNode.forEach(approver -> approvers.add(approver.longValue()));
                branches.add(new ApprovalInclusiveGateway.Branch(
                        value.path("code").asText(""),
                        value.path("name").asText(""),
                        value.path("defaultBranch").asBoolean(false),
                        triggerConditions(value.path("conditions").toString()),
                        approvers,
                        ApprovalMode.valueOf(
                                value.path("approvalMode").asText(
                                        ApprovalMode.SEQUENTIAL.name()
                                )
                        ),
                        readApprovalStages(value.get("approvalStages"), 1)
                ));
            }
            return new ApprovalInclusiveGateway(branches);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition inclusive gateway is invalid", exception);
        }
    }

    private static String approverSources(ApprovalApproverSources value) {
        var serialized = TRIGGER_JSON.createObjectNode();
        writeApproverSource(serialized.putObject("route"), value.route());
        var branches = serialized.putObject("branches");
        value.branches().forEach((code, source) ->
                writeApproverSource(branches.putObject(code), source));
        return serialized.toString();
    }

    private static ApprovalApproverSources approverSources(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "approver_sources");
        if (serialized == null) {
            return null;
        }
        try {
            var value = TRIGGER_JSON.readTree(serialized);
            if (!value.isObject() || !value.path("branches").isObject()) {
                throw new IllegalArgumentException("Approver sources JSON must be an object");
            }
            var branches = new LinkedHashMap<String, ApprovalApproverSource>();
            value.path("branches").fields().forEachRemaining(entry ->
                    branches.put(entry.getKey(), readApproverSource(entry.getValue())));
            return new ApprovalApproverSources(
                    readApproverSource(value.path("route")),
                    branches
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition approver sources are invalid", exception);
        }
    }

    private static String quorumRules(ApprovalQuorumRules value) {
        if (value == null || (value.primary() == null && value.branches().isEmpty())) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        if (value.primary() != null) {
            writeQuorumRule(serialized.putObject("route"), value.primary());
        }
        var branches = serialized.putObject("branches");
        value.branches().forEach((code, rule) ->
                writeQuorumRule(branches.putObject(code), rule));
        return serialized.toString();
    }

    private static ApprovalQuorumRules quorumRules(ResultSet result) throws SQLException {
        var serialized = optionalColumnString(result, "quorum_rules");
        if (serialized == null) {
            return null;
        }
        try {
            var value = TRIGGER_JSON.readTree(serialized);
            if (!value.isObject() || !value.path("branches").isObject()) {
                throw new IllegalArgumentException("Quorum rules JSON must be an object");
            }
            var branches = new LinkedHashMap<String, ApprovalQuorumRule>();
            value.path("branches").fields().forEachRemaining(entry ->
                    branches.put(entry.getKey(), readQuorumRule(entry.getValue())));
            return new ApprovalQuorumRules(
                    value.path("route").isObject()
                            ? readQuorumRule(value.path("route"))
                            : null,
                    branches
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition quorum rules are invalid", exception);
        }
    }

    private static void writeQuorumRule(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            ApprovalQuorumRule rule
    ) {
        target.put("type", rule.type().name());
        target.put("value", rule.value());
    }

    private static ApprovalQuorumRule readQuorumRule(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (!value.isObject()) {
            throw new IllegalArgumentException("Quorum rule must be an object");
        }
        return new ApprovalQuorumRule(
                ApprovalQuorumRule.Type.valueOf(value.path("type").asText("")),
                value.path("value").asInt(0)
        );
    }

    private static String deadlinePolicies(ApprovalDeadlinePolicies value) {
        if (value == null || (value.primary() == null && value.branches().isEmpty())) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        if (value.primary() != null) {
            writeDeadlinePolicy(serialized.putObject("route"), value.primary());
        }
        var branches = serialized.putObject("branches");
        value.branches().forEach((code, policy) ->
                writeDeadlinePolicy(branches.putObject(code), policy));
        return serialized.toString();
    }

    private static ApprovalDeadlinePolicies deadlinePolicies(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "deadline_policies");
        if (serialized == null) {
            return null;
        }
        try {
            var value = TRIGGER_JSON.readTree(serialized);
            if (!value.isObject() || !value.path("branches").isObject()) {
                throw new IllegalArgumentException(
                        "Approval deadline policies JSON must be an object"
                );
            }
            var branches = new LinkedHashMap<String, ApprovalDeadlinePolicy>();
            value.path("branches").fields().forEachRemaining(entry ->
                    branches.put(entry.getKey(), readDeadlinePolicy(entry.getValue())));
            return new ApprovalDeadlinePolicies(
                    value.path("route").isObject()
                            ? readDeadlinePolicy(value.path("route"))
                            : null,
                    branches
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Approval definition deadline policies are invalid",
                    exception
            );
        }
    }

    private static String deadlinePolicy(ApprovalDeadlineState value) {
        if (value == null) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        writeDeadlinePolicy(serialized, value.policy());
        return serialized.toString();
    }

    private static ApprovalDeadlineState deadlineState(ResultSet result) throws SQLException {
        var serialized = optionalColumnString(result, "deadline_policy");
        if (serialized == null) {
            return null;
        }
        try {
            var dueAt = optionalColumnTimestamp(result, "deadline_due_at");
            if (dueAt == null) {
                throw new IllegalArgumentException(
                        "Approval runtime deadline requires a due timestamp"
                );
            }
            return new ApprovalDeadlineState(
                    readDeadlinePolicy(TRIGGER_JSON.readTree(serialized)),
                    optionalColumnTimestamp(result, "deadline_remind_at"),
                    dueAt,
                    optionalColumnTimestamp(result, "deadline_reminded_at"),
                    optionalColumnTimestamp(result, "deadline_processed_at")
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval runtime deadline is invalid", exception);
        }
    }

    private static void writeDeadlinePolicy(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            ApprovalDeadlinePolicy policy
    ) {
        target.put("timeoutMinutes", policy.timeoutMinutes());
        if (policy.remindBeforeMinutes() != null) {
            target.put("remindBeforeMinutes", policy.remindBeforeMinutes());
        }
        target.put("timeoutAction", policy.timeoutAction().name());
    }

    private static ApprovalDeadlinePolicy readDeadlinePolicy(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (!value.isObject()) {
            throw new IllegalArgumentException("Approval deadline policy must be an object");
        }
        return new ApprovalDeadlinePolicy(
                value.path("timeoutMinutes").asInt(0),
                value.hasNonNull("remindBeforeMinutes")
                        ? value.path("remindBeforeMinutes").asInt()
                        : null,
                ApprovalDeadlinePolicy.TimeoutAction.valueOf(
                        value.path("timeoutAction").asText("")
                )
        );
    }

    private static Timestamp deadlineRemindAt(ApprovalDeadlineState value) {
        return value == null ? null : nullableTimestamp(value.remindAt());
    }

    private static Timestamp deadlineDueAt(ApprovalDeadlineState value) {
        return value == null ? null : timestamp(value.dueAt());
    }

    private static Timestamp deadlineRemindedAt(ApprovalDeadlineState value) {
        return value == null ? null : nullableTimestamp(value.remindedAt());
    }

    private static Timestamp deadlineProcessedAt(ApprovalDeadlineState value) {
        return value == null ? null : nullableTimestamp(value.processedAt());
    }

    private static String decisionCommentPolicies(
            ApprovalDecisionCommentPolicies value
    ) {
        if (value == null) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        writeDecisionCommentPolicy(serialized.putObject("route"), value.primary());
        var branches = serialized.putObject("branches");
        value.branches().forEach((code, policy) ->
                writeDecisionCommentPolicy(branches.putObject(code), policy));
        return serialized.toString();
    }

    private static ApprovalDecisionCommentPolicies decisionCommentPolicies(
            ResultSet result
    ) throws SQLException {
        var serialized = optionalColumnString(result, "decision_comment_policies");
        if (serialized == null) {
            return null;
        }
        try {
            var value = TRIGGER_JSON.readTree(serialized);
            if (!value.isObject()
                    || !value.path("route").isObject()
                    || !value.path("branches").isObject()) {
                throw new IllegalArgumentException(
                        "Approval decision comment policies JSON must be an object"
                );
            }
            var branches =
                    new LinkedHashMap<String, ApprovalDecisionCommentPolicy>();
            value.path("branches").fields().forEachRemaining(entry ->
                    branches.put(
                            entry.getKey(),
                            readDecisionCommentPolicy(entry.getValue())
                    ));
            return new ApprovalDecisionCommentPolicies(
                    readDecisionCommentPolicy(value.path("route")),
                    branches
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Approval definition decision comment policies are invalid",
                    exception
            );
        }
    }

    private static String decisionCommentPolicy(
            ApprovalDecisionCommentPolicy value
    ) {
        if (value == null) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        writeDecisionCommentPolicy(serialized, value);
        return serialized.toString();
    }

    private static ApprovalDecisionCommentPolicy decisionCommentPolicy(
            ResultSet result
    ) throws SQLException {
        var serialized = optionalColumnString(result, "decision_comment_policy");
        if (serialized == null) {
            return null;
        }
        try {
            return readDecisionCommentPolicy(TRIGGER_JSON.readTree(serialized));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Approval runtime decision comment policy is invalid",
                    exception
            );
        }
    }

    private static void writeDecisionCommentPolicy(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            ApprovalDecisionCommentPolicy policy
    ) {
        target.put("approveRequired", policy.approveRequired());
        target.put("rejectRequired", policy.rejectRequired());
        target.put("minimumLength", policy.minimumLength());
    }

    private static ApprovalDecisionCommentPolicy readDecisionCommentPolicy(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (!value.isObject()) {
            throw new IllegalArgumentException(
                    "Approval decision comment policy must be an object"
            );
        }
        return new ApprovalDecisionCommentPolicy(
                value.path("approveRequired").asBoolean(false),
                value.path("rejectRequired").asBoolean(true),
                value.path("minimumLength").asInt(1)
        );
    }

    static String decisionEvidencePolicies(
            ApprovalDecisionEvidencePolicies value
    ) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        if (value.primary() == null) {
            serialized.putNull("route");
        } else {
            writeDecisionEvidencePolicy(
                    serialized.putObject("route"), value.primary());
        }
        var branches = serialized.putObject("branches");
        value.branches().forEach((code, policy) ->
                writeDecisionEvidencePolicy(
                        branches.putObject(code), policy));
        return serialized.toString();
    }

    private static ApprovalDecisionEvidencePolicies decisionEvidencePolicies(
            ResultSet result
    ) throws SQLException {
        var serialized = optionalColumnString(
                result, "decision_evidence_policies");
        if (serialized == null) {
            return null;
        }
        try {
            var value = TRIGGER_JSON.readTree(serialized);
            if (!value.isObject()
                    || !value.has("route")
                    || !value.path("branches").isObject()) {
                throw new IllegalArgumentException(
                        "Approval decision evidence policies must be an object");
            }
            var branches =
                    new LinkedHashMap<String, ApprovalDecisionEvidencePolicy>();
            value.path("branches").fields().forEachRemaining(entry ->
                    branches.put(
                            entry.getKey(),
                            readDecisionEvidencePolicy(entry.getValue())));
            return new ApprovalDecisionEvidencePolicies(
                    value.path("route").isNull()
                            ? null
                            : readDecisionEvidencePolicy(
                            value.path("route")),
                    branches
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Approval definition decision evidence policies are invalid",
                    exception);
        }
    }

    static String decisionEvidencePolicy(
            ApprovalDecisionEvidencePolicy value
    ) {
        if (value == null) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        writeDecisionEvidencePolicy(serialized, value);
        return serialized.toString();
    }

    private static ApprovalDecisionEvidencePolicy decisionEvidencePolicy(
            ResultSet result
    ) throws SQLException {
        var serialized = optionalColumnString(
                result, "decision_evidence_policy");
        if (serialized == null) {
            return null;
        }
        try {
            return readDecisionEvidencePolicy(
                    TRIGGER_JSON.readTree(serialized));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Approval runtime decision evidence policy is invalid",
                    exception);
        }
    }

    private static void writeDecisionEvidencePolicy(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            ApprovalDecisionEvidencePolicy policy
    ) {
        target.put("minimumAttachments", policy.minimumAttachments());
        target.put("maximumAttachments", policy.maximumAttachments());
        if (policy.allowedMimeFamilies() == null) {
            target.putNull("allowedMimeFamilies");
        } else {
            var families = target.putArray("allowedMimeFamilies");
            policy.allowedMimeFamilies().stream()
                    .sorted()
                    .forEach(family -> families.add(family.name()));
        }
        target.put("signatureMode", policy.signatureMode().name());
    }

    private static ApprovalDecisionEvidencePolicy readDecisionEvidencePolicy(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (!value.isObject()
                || !value.has("allowedMimeFamilies")) {
            throw new IllegalArgumentException(
                    "Approval decision evidence policy must be an object");
        }
        java.util.Set<ApprovalDecisionEvidencePolicy.MimeFamily> families = null;
        if (!value.path("allowedMimeFamilies").isNull()) {
            if (!value.path("allowedMimeFamilies").isArray()) {
                throw new IllegalArgumentException(
                        "Approval evidence MIME families must be an array");
            }
            var parsed = java.util.EnumSet.noneOf(
                    ApprovalDecisionEvidencePolicy.MimeFamily.class);
            value.path("allowedMimeFamilies").forEach(item ->
                    parsed.add(
                            ApprovalDecisionEvidencePolicy.MimeFamily.valueOf(
                                    item.asText(""))));
            families = parsed;
        }
        return new ApprovalDecisionEvidencePolicy(
                value.path("minimumAttachments").asInt(-1),
                value.path("maximumAttachments").asInt(-1),
                families,
                ApprovalDecisionEvidencePolicy.SignatureMode.valueOf(
                        value.path("signatureMode").asText(""))
        );
    }

    static String completionSteps(List<ApprovalCompletionStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        var serialized = TRIGGER_JSON.createArrayNode();
        for (var step : steps) {
            var value = serialized.addObject();
            value.put("code", step.code());
            value.put("name", step.name());
            value.put("type", step.type().name());
            if (step.parallelGroup() != null) {
                value.put("parallelGroup", step.parallelGroup());
            }
            if (step.type() == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
                var config = value.putObject("externalTask");
                config.put("topic", step.externalTask().topic());
                config.put("leaseSeconds", step.externalTask().leaseSeconds());
                config.put("maxAttempts", step.externalTask().maxAttempts());
                config.put(
                        "resultJsonLimitBytes",
                        step.externalTask().resultJsonLimitBytes());
            } else if (step.type() == ApprovalCompletionStep.Type.WEBHOOK) {
                var config = value.putObject("webhook");
                config.put("url", step.webhook().url());
                if (step.webhook().secretRef() == null) {
                    config.putNull("secretRef");
                } else {
                    config.put("secretRef", step.webhook().secretRef());
                }
                config.put("timeoutSeconds", step.webhook().timeoutSeconds());
                config.put("maxAttempts", step.webhook().maxAttempts());
                config.put(
                        "baseBackoffSeconds",
                        step.webhook().baseBackoffSeconds());
            } else {
                var config = value.putObject("subflow");
                config.put("definitionId", step.subflow().definitionId());
                config.put("version", step.subflow().version());
            }
            if (step.compensation() != null) {
                writeCompensation(
                        value.putObject("compensation"),
                        step.compensation());
            }
        }
        return serialized.toString();
    }

    private static String completionConfig(ApprovalCompletionStep step) {
        var config = TRIGGER_JSON.createObjectNode();
        if (step.type() == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
            config.put("topic", step.externalTask().topic());
            config.put("leaseSeconds", step.externalTask().leaseSeconds());
            config.put("maxAttempts", step.externalTask().maxAttempts());
            config.put(
                    "resultJsonLimitBytes",
                    step.externalTask().resultJsonLimitBytes());
        } else if (step.type() == ApprovalCompletionStep.Type.WEBHOOK) {
            config.put("url", step.webhook().url());
            if (step.webhook().secretRef() == null) {
                config.putNull("secretRef");
            } else {
                config.put("secretRef", step.webhook().secretRef());
            }
            config.put("timeoutSeconds", step.webhook().timeoutSeconds());
            config.put("maxAttempts", step.webhook().maxAttempts());
            config.put(
                    "baseBackoffSeconds",
                    step.webhook().baseBackoffSeconds());
        } else {
            config.put("definitionId", step.subflow().definitionId());
            config.put("version", step.subflow().version());
        }
        if (step.compensation() != null) {
            writeCompensation(
                    config.putObject("compensation"),
                    step.compensation());
        }
        return config.toString();
    }

    private static void writeCompensation(
            com.fasterxml.jackson.databind.node.ObjectNode value,
            ApprovalCompletionStep.Compensation compensation
    ) {
        value.put("type", compensation.type().name());
        if (compensation.type() == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
            var config = value.putObject("externalTask");
            config.put("topic", compensation.externalTask().topic());
            config.put("leaseSeconds", compensation.externalTask().leaseSeconds());
            config.put("maxAttempts", compensation.externalTask().maxAttempts());
            config.put("resultJsonLimitBytes",
                    compensation.externalTask().resultJsonLimitBytes());
        } else if (compensation.type() == ApprovalCompletionStep.Type.WEBHOOK) {
            var config = value.putObject("webhook");
            config.put("url", compensation.webhook().url());
            if (compensation.webhook().secretRef() == null) {
                config.putNull("secretRef");
            } else {
                config.put("secretRef", compensation.webhook().secretRef());
            }
            config.put("timeoutSeconds", compensation.webhook().timeoutSeconds());
            config.put("maxAttempts", compensation.webhook().maxAttempts());
            config.put("baseBackoffSeconds",
                    compensation.webhook().baseBackoffSeconds());
        } else {
            var config = value.putObject("subflow");
            config.put("definitionId", compensation.subflow().definitionId());
            config.put("version", compensation.subflow().version());
        }
    }

    private static ApprovalCompletionStep.Compensation readCompensation(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("Completion compensation must be an object");
        }
        var type = ApprovalCompletionStep.Type.valueOf(
                value.path("type").asText(""));
        if (type == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
            var config = value.path("externalTask");
            return ApprovalCompletionStep.Compensation.externalTask(
                    new ApprovalCompletionStep.ExternalTask(
                            config.path("topic").asText(""),
                            config.path("leaseSeconds").asInt(-1),
                            config.path("maxAttempts").asInt(-1),
                            config.path("resultJsonLimitBytes").asInt(-1)));
        }
        if (type == ApprovalCompletionStep.Type.WEBHOOK) {
            var config = value.path("webhook");
            return ApprovalCompletionStep.Compensation.webhook(
                    new ApprovalCompletionStep.Webhook(
                            config.path("url").asText(""),
                            config.path("secretRef").isNull()
                                    || config.path("secretRef").isMissingNode()
                                    ? null : config.path("secretRef").asText(),
                            config.path("timeoutSeconds").asInt(-1),
                            config.path("maxAttempts").asInt(-1),
                            config.path("baseBackoffSeconds").asInt(-1)));
        }
        var config = value.path("subflow");
        return ApprovalCompletionStep.Compensation.subflow(
                new ApprovalCompletionStep.Subflow(
                        config.path("definitionId").asLong(-1),
                        config.path("version").asInt(-1)));
    }

    private static ApprovalCompletionStep completionStep(ResultSet result)
            throws SQLException {
        try {
            var type = ApprovalCompletionStep.Type.valueOf(
                    result.getString("execution_type"));
            var config = TRIGGER_JSON.readTree(
                    result.getString("config_json"));
            if (!config.isObject()) {
                throw new IllegalArgumentException(
                        "Completion execution config must be an object");
            }
            ApprovalCompletionStep step;
            if (type == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
                step = ApprovalCompletionStep.externalTask(
                        result.getString("step_code"),
                        result.getString("step_name"),
                        new ApprovalCompletionStep.ExternalTask(
                                config.path("topic").asText(""),
                                config.path("leaseSeconds").asInt(-1),
                                config.path("maxAttempts").asInt(-1),
                                config.path("resultJsonLimitBytes").asInt(-1)))
                        .withParallelGroup(optionalColumnString(
                                result, "parallel_group"));
            } else if (type == ApprovalCompletionStep.Type.WEBHOOK) {
                step = ApprovalCompletionStep.webhook(
                        result.getString("step_code"),
                        result.getString("step_name"),
                        new ApprovalCompletionStep.Webhook(
                                config.path("url").asText(""),
                                config.path("secretRef").isNull()
                                        || config.path("secretRef").isMissingNode()
                                        ? null
                                        : config.path("secretRef").asText(),
                                config.path("timeoutSeconds").asInt(-1),
                                config.path("maxAttempts").asInt(-1),
                                config.path("baseBackoffSeconds").asInt(-1)))
                        .withParallelGroup(optionalColumnString(
                                result, "parallel_group"));
            } else {
                step = ApprovalCompletionStep.subflow(
                        result.getString("step_code"),
                        result.getString("step_name"),
                        new ApprovalCompletionStep.Subflow(
                                config.path("definitionId").asLong(-1),
                                config.path("version").asInt(-1)))
                        .withParallelGroup(optionalColumnString(
                                result, "parallel_group"));
            }
            return step.withCompensation(
                    readCompensation(config.path("compensation")));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Completion execution config is invalid",
                    exception);
        }
    }

    private static ApprovalCompletionExecution.Lease completionLease(
            ResultSet result
    ) throws SQLException {
        var owner = result.getString("lease_owner");
        if (owner == null) {
            return null;
        }
        return new ApprovalCompletionExecution.Lease(
                owner,
                result.getString("lease_token_hash"),
                result.getTimestamp("lease_expires_at").toInstant());
    }

    private static ApprovalCompletionExecution.Failure completionFailure(
            ResultSet result
    ) throws SQLException {
        var code = result.getString("failure_code");
        if (code == null) {
            return null;
        }
        return new ApprovalCompletionExecution.Failure(
                code,
                result.getString("failure_message"),
                Boolean.TRUE.equals(nullableBoolean(
                        result, "failure_retryable")));
    }

    private static List<ApprovalCompletionStep> completionSteps(
            ResultSet result
    ) throws SQLException {
        var serialized = optionalColumnString(result, "completion_steps");
        if (serialized == null) {
            return List.of();
        }
        try {
            return readCompletionSteps(TRIGGER_JSON.readTree(serialized));
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException(
                    "Approval definition completion steps are invalid",
                    exception);
        }
    }

    private static CompletionFailurePolicy completionFailurePolicy(
            ResultSet result
    ) throws SQLException {
        var stored = optionalColumnString(
                result, "completion_failure_policy");
        return stored == null
                ? CompletionFailurePolicy.MANUAL_RETRY
                : CompletionFailurePolicy.valueOf(stored);
    }

    private static List<ApprovalCompletionStep> readCompletionSteps(
            com.fasterxml.jackson.databind.JsonNode values
    ) {
        if (!values.isArray()) {
            throw new IllegalArgumentException(
                    "Approval completion steps must be an array");
        }
        var steps = new ArrayList<ApprovalCompletionStep>(values.size());
        for (var value : values) {
            var type = ApprovalCompletionStep.Type.valueOf(
                    value.path("type").asText(""));
            ApprovalCompletionStep.ExternalTask externalTask = null;
            ApprovalCompletionStep.Webhook webhook = null;
            ApprovalCompletionStep.Subflow subflow = null;
            var parallelGroup = value.path("parallelGroup").isNull()
                    || value.path("parallelGroup").isMissingNode()
                    ? null : value.path("parallelGroup").asText();
            if (type == ApprovalCompletionStep.Type.EXTERNAL_TASK) {
                var config = value.path("externalTask");
                if (!config.isObject()) {
                    throw new IllegalArgumentException(
                            "External-task completion config is missing");
                }
                externalTask = new ApprovalCompletionStep.ExternalTask(
                        config.path("topic").asText(""),
                        config.path("leaseSeconds").asInt(-1),
                        config.path("maxAttempts").asInt(-1),
                        config.path("resultJsonLimitBytes").asInt(-1));
            } else if (type == ApprovalCompletionStep.Type.WEBHOOK) {
                var config = value.path("webhook");
                if (!config.isObject()) {
                    throw new IllegalArgumentException(
                            "Webhook completion config is missing");
                }
                webhook = new ApprovalCompletionStep.Webhook(
                        config.path("url").asText(""),
                        config.path("secretRef").isNull()
                                || config.path("secretRef").isMissingNode()
                                ? null : config.path("secretRef").asText(),
                        config.path("timeoutSeconds").asInt(-1),
                        config.path("maxAttempts").asInt(-1),
                        config.path("baseBackoffSeconds").asInt(-1));
            } else {
                var config = value.path("subflow");
                if (!config.isObject()) {
                    throw new IllegalArgumentException(
                            "Subflow completion config is missing");
                }
                subflow = new ApprovalCompletionStep.Subflow(
                        config.path("definitionId").asLong(-1),
                        config.path("version").asInt(-1));
            }
            steps.add(new ApprovalCompletionStep(
                    value.path("code").asText(""),
                    value.path("name").asText(""),
                    type,
                    externalTask,
                    webhook,
                    subflow,
                    parallelGroup,
                    readCompensation(value.path("compensation"))));
        }
        return ApprovalCompletionStep.requireSteps(steps);
    }

    private static String approvalStages(List<ApprovalStage> stages) {
        if (stages == null) {
            return null;
        }
        var serialized = TRIGGER_JSON.createArrayNode();
        for (var stage : stages) {
            var value = serialized.addObject();
            value.put("code", stage.code());
            value.put("name", stage.name());
            writeIds(value.putArray("approverIds"), stage.approverIds());
            value.put("approvalMode", stage.approvalMode().name());
            writeApproverSource(value.putObject("approverSource"), stage.approverSource());
            if (stage.quorumRule() == null) {
                value.putNull("quorumRule");
            } else {
                writeQuorumRule(value.putObject("quorumRule"), stage.quorumRule());
            }
            if (stage.deadlinePolicy() == null) {
                value.putNull("deadlinePolicy");
            } else {
                writeDeadlinePolicy(value.putObject("deadlinePolicy"), stage.deadlinePolicy());
            }
            writeDecisionCommentPolicy(
                    value.putObject("decisionCommentPolicy"),
                    stage.decisionCommentPolicy()
            );
            if (stage.decisionEvidencePolicy() != null) {
                writeDecisionEvidencePolicy(
                        value.putObject("decisionEvidencePolicy"),
                        stage.decisionEvidencePolicy());
            }
        }
        return serialized.toString();
    }

    private static void writeApprovalStages(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            List<ApprovalStage> stages
    ) {
        if (stages == null) {
            return;
        }
        try {
            target.set("approvalStages", TRIGGER_JSON.readTree(
                    approvalStages(stages)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Approval branch stages must be serializable",
                    exception
            );
        }
    }

    private static List<ApprovalStage> approvalStages(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "approval_stages");
        if (serialized == null) {
            return null;
        }
        try {
            return readApprovalStages(TRIGGER_JSON.readTree(serialized), 2);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval definition stages are invalid", exception);
        }
    }

    private static List<ApprovalStage> readApprovalStages(
            com.fasterxml.jackson.databind.JsonNode values,
            int minimumStages
    ) {
        if (values == null || values.isNull() || values.isMissingNode()) {
            return null;
        }
        if (!values.isArray()
                || values.size() < minimumStages
                || values.size() > 10) {
            throw new IllegalArgumentException(
                    "Approval stages JSON must contain "
                            + minimumStages + "..10 stages");
        }
        var stages = new ArrayList<ApprovalStage>(values.size());
        for (var value : values) {
            if (!value.isObject()
                    || !value.path("approverSource").isObject()
                    || !value.path("decisionCommentPolicy").isObject()) {
                throw new IllegalArgumentException(
                        "Approval stage JSON must be an object");
            }
            stages.add(new ApprovalStage(
                    value.path("code").asText(""),
                    value.path("name").asText(""),
                    readIds(value.path("approverIds"),
                            "Approval stage approver ids"),
                    ApprovalMode.valueOf(value.path("approvalMode").asText("")),
                    readApproverSource(value.path("approverSource")),
                    value.path("quorumRule").isNull()
                            ? null
                            : readQuorumRule(value.path("quorumRule")),
                    value.path("deadlinePolicy").isNull()
                            ? null
                            : readDeadlinePolicy(value.path("deadlinePolicy")),
                    readDecisionCommentPolicy(
                            value.path("decisionCommentPolicy")),
                    value.has("decisionEvidencePolicy")
                            ? readDecisionEvidencePolicy(
                            value.path("decisionEvidencePolicy"))
                            : null
            ));
        }
        return List.copyOf(stages);
    }

    static String startContext(ApprovalStartContext context) {
        if (context == null) {
            return null;
        }
        var serialized = TRIGGER_JSON.createObjectNode();
        serialized.put("requesterMemberId", context.requesterMemberId());
        if (context.rootInstanceId() == null) {
            serialized.putNull("rootInstanceId");
        } else {
            serialized.put("rootInstanceId", context.rootInstanceId());
        }
        serialized.put("subflowDepth", context.subflowDepth());
        if (context.moduleCode() == null) {
            serialized.putNull("moduleCode");
            serialized.putNull("recordId");
        } else {
            serialized.put("moduleCode", context.moduleCode());
            serialized.put("recordId", context.recordId());
        }
        var values = serialized.putObject("valuesJson");
        context.valuesJson().forEach((code, canonicalJson) -> {
            try {
                values.set(code, TRIGGER_JSON.readTree(canonicalJson));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException(
                        "Approval start-context value must be serializable",
                        exception
                );
            }
        });
        return serialized.toString();
    }

    private static ApprovalStartContext startContext(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "start_context");
        if (serialized == null) {
            return null;
        }
        try {
            var value = TRIGGER_JSON.readTree(serialized);
            if (!value.isObject()
                    || value.size() != 4 && value.size() != 6
                    || !value.has("requesterMemberId")
                    || !value.has("moduleCode")
                    || !value.has("recordId")
                    || !value.path("valuesJson").isObject()
                    || !value.path("requesterMemberId").canConvertToLong()
                    || value.has("rootInstanceId")
                    && !value.path("rootInstanceId").isNull()
                    && !value.path("rootInstanceId").canConvertToLong()
                    || value.has("subflowDepth")
                    && !value.path("subflowDepth").canConvertToInt()) {
                throw new IllegalArgumentException(
                        "Approval start context must be a bounded canonical object");
            }
            var module = value.get("moduleCode");
            var record = value.get("recordId");
            if (module.isNull() != record.isNull()
                    || !module.isNull() && !module.isTextual()
                    || !record.isNull() && !record.canConvertToLong()) {
                throw new IllegalArgumentException(
                        "Approval start-context record identity is invalid");
            }
            var values = new LinkedHashMap<String, String>();
            value.path("valuesJson").fields().forEachRemaining(entry ->
                    values.put(entry.getKey(), entry.getValue().toString()));
            return new ApprovalStartContext(
                    value.path("requesterMemberId").longValue(),
                    module.isNull() ? null : module.textValue(),
                    record.isNull() ? null : record.longValue(),
                    values,
                    value.hasNonNull("rootInstanceId")
                            ? value.path("rootInstanceId").longValue()
                            : null,
                    value.path("subflowDepth").asInt(0)
            );
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval start context is invalid", exception);
        }
    }

    static String approvalStageState(List<ApprovalStageExecution> stages) {
        if (stages == null
                || stages.size() == 1
                && stages.getFirst().code().equals("legacy")) {
            return null;
        }
        var serialized = TRIGGER_JSON.createArrayNode();
        for (var stage : stages) {
            var value = serialized.addObject();
            value.put("stageIndex", stage.stageIndex());
            value.put("code", stage.code());
            value.put("name", stage.name());
            value.put("status", stage.status().name());
            writeIds(value.putArray("approverIds"), stage.approverIds());
            value.put("approvalMode", stage.approvalMode().name());
            value.put("requiredApprovals", stage.requiredApprovals());
            writeIds(value.putArray("actualHandlerIds"), stage.actualHandlerIds());
            writeInstant(value, "startedAt", stage.startedAt());
            writeInstant(value, "completedAt", stage.completedAt());
            if (stage.deadline() == null) {
                value.putNull("deadline");
            } else {
                writeDeadlineState(value.putObject("deadline"), stage.deadline());
            }
            writeDecisionCommentPolicy(
                    value.putObject("decisionCommentPolicy"),
                    stage.decisionCommentPolicy()
            );
            if (stage.decisionEvidencePolicy() != null) {
                writeDecisionEvidencePolicy(
                        value.putObject("decisionEvidencePolicy"),
                        stage.decisionEvidencePolicy());
            }
            var handlerSlots = value.putObject("handlerActorsByParticipantId");
            stage.handlerActorsByParticipantId().forEach(
                    (participantId, actorId) ->
                            handlerSlots.put(Long.toString(participantId), actorId)
            );
        }
        return serialized.toString();
    }

    private static List<ApprovalStageExecution> approvalStageState(ResultSet result)
            throws SQLException {
        var serialized = optionalColumnString(result, "approval_stage_state");
        if (serialized == null) {
            return null;
        }
        try {
            var values = TRIGGER_JSON.readTree(serialized);
            if (!values.isArray() || values.isEmpty() || values.size() > 10) {
                throw new IllegalArgumentException(
                        "Approval stage execution JSON must contain 1..10 stages");
            }
            var stages = new ArrayList<ApprovalStageExecution>(values.size());
            for (var value : values) {
                if (!value.isObject()
                        || !value.path("decisionCommentPolicy").isObject()) {
                    throw new IllegalArgumentException(
                            "Approval stage execution JSON must be an object");
                }
                var handlersByParticipant = new LinkedHashMap<Long, Long>();
                var handlerSlots = value.path("handlerActorsByParticipantId");
                if (!handlerSlots.isMissingNode() && !handlerSlots.isObject()) {
                    throw new IllegalArgumentException(
                            "Approval stage handler slots must be an object");
                }
                if (handlerSlots.isObject()) {
                    handlerSlots.fields().forEachRemaining(entry -> {
                        var participantId = Long.parseLong(entry.getKey());
                        if (!entry.getValue().canConvertToLong()) {
                            throw new IllegalArgumentException(
                                    "Approval stage handler actor id is invalid");
                        }
                        handlersByParticipant.put(
                                participantId, entry.getValue().longValue());
                    });
                }
                stages.add(new ApprovalStageExecution(
                        value.path("stageIndex").asInt(-1),
                        value.path("code").asText(""),
                        value.path("name").asText(""),
                        ApprovalStageExecution.Status.valueOf(
                                value.path("status").asText("")),
                        readIds(value.path("approverIds"), "Approval stage approver ids"),
                        ApprovalMode.valueOf(value.path("approvalMode").asText("")),
                        value.path("requiredApprovals").asInt(-1),
                        readIds(
                                value.path("actualHandlerIds"),
                                "Approval stage actual handler ids"),
                        readInstant(value, "startedAt"),
                        readInstant(value, "completedAt"),
                        value.path("deadline").isNull()
                                ? null
                                : readDeadlineState(value.path("deadline")),
                        readDecisionCommentPolicy(
                                value.path("decisionCommentPolicy")),
                        handlersByParticipant,
                        value.has("decisionEvidencePolicy")
                                ? readDecisionEvidencePolicy(
                                value.path("decisionEvidencePolicy"))
                                : null
                ));
            }
            return List.copyOf(stages);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new SQLException("Approval stage execution state is invalid", exception);
        }
    }

    private static void writeDeadlineState(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            ApprovalDeadlineState state
    ) {
        writeDeadlinePolicy(target.putObject("policy"), state.policy());
        writeInstant(target, "remindAt", state.remindAt());
        writeInstant(target, "dueAt", state.dueAt());
        writeInstant(target, "remindedAt", state.remindedAt());
        writeInstant(target, "processedAt", state.processedAt());
    }

    private static ApprovalDeadlineState readDeadlineState(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (!value.isObject() || !value.path("policy").isObject()) {
            throw new IllegalArgumentException(
                    "Approval stage deadline state must be an object");
        }
        var dueAt = readInstant(value, "dueAt");
        if (dueAt == null) {
            throw new IllegalArgumentException(
                    "Approval stage deadline requires dueAt");
        }
        return new ApprovalDeadlineState(
                readDeadlinePolicy(value.path("policy")),
                readInstant(value, "remindAt"),
                dueAt,
                readInstant(value, "remindedAt"),
                readInstant(value, "processedAt")
        );
    }

    private static void writeInstant(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            String field,
            java.time.Instant value
    ) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value.toString());
        }
    }

    private static java.time.Instant readInstant(
            com.fasterxml.jackson.databind.JsonNode source,
            String field
    ) {
        var value = source.path(field);
        return value.isNull() || value.isMissingNode()
                ? null
                : java.time.Instant.parse(value.asText());
    }

    private static void writeIds(
            com.fasterxml.jackson.databind.node.ArrayNode target,
            List<Long> values
    ) {
        values.forEach(target::add);
    }

    private static List<Long> readIds(
            com.fasterxml.jackson.databind.JsonNode values,
            String field
    ) {
        if (!values.isArray()
                || values.size() > ApprovalDefinitionDraft.MAX_APPROVERS) {
            throw new IllegalArgumentException(field + " must be a bounded array");
        }
        var ids = new ArrayList<Long>(values.size());
        for (var value : values) {
            if (!value.canConvertToLong() || value.longValue() <= 0) {
                throw new IllegalArgumentException(field + " contains an invalid id");
            }
            ids.add(value.longValue());
        }
        return List.copyOf(ids);
    }

    private static void writeApproverSource(
            com.fasterxml.jackson.databind.node.ObjectNode target,
            ApprovalApproverSource source
    ) {
        target.put("kind", source.kind().name());
        if (source.sourceId() != null) {
            target.put("sourceId", source.sourceId());
        }
        if (source.moduleCode() != null) {
            target.put("moduleCode", source.moduleCode());
        }
    }

    private static ApprovalApproverSource readApproverSource(
            com.fasterxml.jackson.databind.JsonNode value
    ) {
        if (!value.isObject()) {
            throw new IllegalArgumentException("Approver source must be an object");
        }
        var kind = ApprovalApproverSource.Kind.valueOf(value.path("kind").asText(""));
        return new ApprovalApproverSource(
                kind,
                value.hasNonNull("sourceId") ? value.path("sourceId").longValue() : null,
                value.hasNonNull("moduleCode") ? value.path("moduleCode").asText() : null
        );
    }

    private static String optionalColumnString(ResultSet result, String column)
            throws SQLException {
        var metadata = result.getMetaData();
        for (var index = 1; index <= metadata.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                return result.getString(index);
            }
        }
        return null;
    }

    private static java.time.Instant optionalColumnTimestamp(
            ResultSet result,
            String column
    ) throws SQLException {
        var metadata = result.getMetaData();
        for (var index = 1; index <= metadata.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                return nullableTimestamp(result.getTimestamp(index));
            }
        }
        return null;
    }

    private static int optionalColumnInteger(
            ResultSet result,
            String column,
            int defaultValue
    ) throws SQLException {
        var metadata = result.getMetaData();
        for (var index = 1; index <= metadata.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                var value = result.getObject(index);
                return value == null ? defaultValue : ((Number) value).intValue();
            }
        }
        return defaultValue;
    }

    private static Integer optionalColumnNullableInteger(
            ResultSet result,
            String column
    ) throws SQLException {
        var metadata = result.getMetaData();
        for (var index = 1; index <= metadata.getColumnCount(); index++) {
            if (column.equalsIgnoreCase(metadata.getColumnLabel(index))) {
                var value = result.getObject(index);
                return value == null ? null : ((Number) value).intValue();
            }
        }
        return null;
    }

    private static ApprovalInstance.CompletionPhase completionPhase(
            ResultSet result
    ) throws SQLException {
        var stored = optionalColumnString(result, "completion_phase");
        if (stored != null) {
            return ApprovalInstance.CompletionPhase.valueOf(stored);
        }
        return ApprovalInstance.Status.valueOf(result.getString("status"))
                == ApprovalInstance.Status.PENDING
                ? ApprovalInstance.CompletionPhase.HUMAN_APPROVAL
                : ApprovalInstance.CompletionPhase.COMPLETED;
    }

    private static Timestamp triggerStartAt(TriggerBinding value) {
        return value == null || value.periodicSchedule() == null
                ? null
                : timestamp(value.periodicSchedule().startAt());
    }

    private static Integer triggerIntervalMinutes(TriggerBinding value) {
        return value == null || value.periodicSchedule() == null
                ? null
                : value.periodicSchedule().intervalMinutes();
    }

    private static Long triggerRequesterId(TriggerBinding value) {
        return value == null || value.periodicSchedule() == null
                ? null
                : value.periodicSchedule().requesterMemberId();
    }

    private static List<TriggerCondition> triggerConditions(String serialized)
            throws JsonProcessingException {
        var values = TRIGGER_JSON.readTree(serialized);
        if (!values.isArray() || values.size() > 10) {
            throw new IllegalArgumentException("Trigger conditions JSON must be an array of at most 10 rules");
        }
        var conditions = new java.util.ArrayList<TriggerCondition>();
        for (var value : values) {
            var operator = TriggerCondition.Operator.valueOf(value.path("operator").asText(""));
            var expected = value.get("value");
            conditions.add(new TriggerCondition(
                    value.path("fieldCode").asText(""),
                    operator,
                    expected == null || expected.isNull() ? null : expected.toString()
            ));
        }
        return List.copyOf(conditions);
    }

    private static List<Long> approverIds(ResultSet result) throws SQLException {
        var serialized = result.getString("approver_ids");
        if (serialized == null || serialized.isBlank()) {
            throw new SQLException("Published approval sequence is missing");
        }
        try {
            var values = serialized.strip();
            if (values.startsWith("[") && values.endsWith("]")) {
                values = values.substring(1, values.length() - 1);
            }
            return Arrays.stream(values.split(","))
                    .map(String::strip)
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException exception) {
            throw new SQLException("Published approval sequence is invalid", exception);
        }
    }

    private static Map<Long, ApprovalInstance.Decision> decisions(ResultSet result)
            throws SQLException {
        var values = new LinkedHashMap<Long, ApprovalInstance.Decision>();
        for (var memberId : decisionIds(result, "approved_approver_ids_json")) {
            values.put(memberId, ApprovalInstance.Decision.APPROVED);
        }
        for (var memberId : decisionIds(result, "rejected_approver_ids_json")) {
            if (values.put(memberId, ApprovalInstance.Decision.REJECTED) != null) {
                throw new SQLException("Approval member decision is duplicated");
            }
        }
        return Map.copyOf(values);
    }

    private static List<Long> decisionIds(ResultSet result, String column) throws SQLException {
        var serialized = result.getString(column);
        if (serialized == null) {
            return List.of();
        }
        try {
            var values = TRIGGER_JSON.readTree(serialized);
            if (!values.isArray() || values.size() > ApprovalDefinitionDraft.MAX_APPROVERS) {
                throw new SQLException("Approval member decisions must be a bounded JSON array");
            }
            var ids = new java.util.ArrayList<Long>();
            for (var value : values) {
                if (!value.canConvertToLong() || value.longValue() <= 0) {
                    throw new SQLException("Approval member decision id is invalid");
                }
                ids.add(value.longValue());
            }
            return List.copyOf(ids);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Approval member decisions JSON is invalid", exception);
        }
    }

    private static String approverIdsJson(List<Long> values) {
        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    record DecisionEvidenceFileRow(
            ApprovalDecisionEvidenceFile file,
            boolean attachment,
            boolean signature,
            Integer attachmentOrder
    ) {
    }

    record DecisionEvidenceRow(
            long id,
            long instanceId,
            int historySequence,
            String branchCode,
            int stageIndex,
            ApprovalInstance.Decision decision,
            String signatureKind,
            Long signatureFileId,
            String typedSignature,
            Long templateId,
            Integer templateVersion,
            String templateName,
            long actorId,
            long representedMemberId,
            Long delegationId,
            java.time.Instant decidedAt
    ) {
        ApprovalDecisionEvidence toDomain(
                List<DecisionEvidenceFileRow> storedFiles
        ) {
            var filesById =
                    new LinkedHashMap<Long, ApprovalDecisionEvidenceFile>();
            storedFiles.forEach(row ->
                    filesById.put(row.file().fileId(), row.file()));
            var attachments = storedFiles.stream()
                    .filter(DecisionEvidenceFileRow::attachment)
                    .sorted(Comparator.comparingInt(row ->
                            Objects.requireNonNull(row.attachmentOrder())))
                    .map(DecisionEvidenceFileRow::file)
                    .toList();
            ApprovalDecisionEvidence.Signature signature = null;
            if (signatureKind != null) {
                signature = switch (
                        ApprovalDecisionEvidence.Signature.Kind.valueOf(
                                signatureKind)) {
                    case FILE -> ApprovalDecisionEvidence.Signature.file(
                            Objects.requireNonNull(
                                    filesById.get(signatureFileId),
                                    "Stored signature file snapshot is missing"));
                    case TYPED ->
                            ApprovalDecisionEvidence.Signature.typed(
                                    typedSignature);
                };
            }
            var template = templateId == null
                    ? null
                    : new ApprovalDecisionEvidence.TemplateSelection(
                    templateId, templateVersion, templateName);
            return new ApprovalDecisionEvidence(
                    id, instanceId, historySequence, branchCode, stageIndex,
                    decision, attachments, signature, template, actorId,
                    representedMemberId, delegationId, decidedAt);
        }
    }

    record InstanceRow(
            long id,
            long definitionId,
            int definitionVersion,
            String businessKey,
            long requesterId,
            long approverId,
            List<Long> approverIds,
            int currentStepIndex,
            ApprovalInstance.ClaimState claimState,
            ApprovalInstance.Status status,
            ApprovalInstance.CompletionPhase completionPhase,
            CompletionFailurePolicy completionFailurePolicy,
            Integer activeCompletionOrdinal,
            java.time.Instant startedAt,
            java.time.Instant completedAt,
            ApprovalInstance.RecordBinding recordBinding,
            ApprovalMode approvalMode,
            int requiredApprovals,
            Map<Long, ApprovalInstance.Decision> decisions,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            int currentStageIndex,
            List<ApprovalStageExecution> stages,
            ApprovalStartContext startContext,
            ApprovalDecisionEvidencePolicy decisionEvidencePolicy
    ) {
        ApprovalInstance toDomain(List<ApprovalHistoryEvent> history) {
            return toDomain(history, List.of(), List.of());
        }

        ApprovalInstance toDomain(
                List<ApprovalHistoryEvent> history,
                List<ApprovalBranchExecution> parallelBranches,
                List<ApprovalCompletionExecution> completionExecutions
        ) {
            return new ApprovalInstance(
                    id, definitionId, definitionVersion, businessKey, requesterId, approverId,
                    approverIds, status, startedAt, completedAt, history,
                    currentStepIndex, claimState, recordBinding,
                    approvalMode, requiredApprovals, decisions, parallelBranches, deadline,
                    decisionCommentPolicy, currentStageIndex, stages, startContext,
                    decisionEvidencePolicy, completionPhase,
                    activeCompletionOrdinal, completionExecutions,
                    completionFailurePolicy
            );
        }
    }
}
