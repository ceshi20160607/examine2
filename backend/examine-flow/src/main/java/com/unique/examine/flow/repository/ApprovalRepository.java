package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplateVersion;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompensationAttempt;
import com.unique.examine.flow.domain.ApprovalCompensationSubflowRun;
import com.unique.examine.flow.domain.ApprovalTaskAssignment;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.FlowPeriodicScheduleState;
import com.unique.examine.flow.domain.TriggerBinding;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApprovalRepository {
    ApprovalDefinitionDraft saveDraft(ApprovalDefinitionDraft draft);

    Optional<ApprovalDefinitionDraft> findDraft(long definitionId);

    List<ApprovalDefinitionDraft> findDrafts(int offset, int limit);

    long countDrafts();

    ApprovalDefinitionVersion saveVersion(ApprovalDefinitionVersion version);

    Optional<ApprovalDefinitionVersion> findVersion(long definitionId, int version);

    Optional<ApprovalDefinitionVersion> findLatestVersion(long definitionId);

    default List<ApprovalDefinitionVersion> findVersions(
            long definitionId,
            int offset,
            int limit
    ) {
        return List.of();
    }

    default long countVersions(long definitionId) {
        return 0;
    }

    default Optional<FlowPeriodicScheduleState> findPeriodicSchedule(long definitionId) {
        return Optional.empty();
    }

    default List<ApprovalDefinitionVersion> findStartableDefinitions(int offset, int limit) {
        return List.of();
    }

    default long countStartableDefinitions() {
        return 0;
    }

    List<ApprovalDefinitionVersion> findTriggerCandidates(
            String moduleCode,
            TriggerBinding.Event event
    );

    Optional<FlowTriggerDispatch> findTriggerDispatchForUpdate(String eventKey);

    FlowTriggerDispatch saveTriggerDispatch(FlowTriggerDispatch dispatch);

    ApprovalInstance saveInstance(ApprovalInstance instance);

    default ApprovalInstance saveCompletionProgress(
            ApprovalInstance instance
    ) {
        return saveInstance(instance);
    }

    Optional<ApprovalInstance> findInstance(long instanceId);

    default Optional<ApprovalInstance> findInstanceForUpdate(long instanceId) {
        return findInstance(instanceId);
    }

    List<ApprovalInstance> findInstances(int offset, int limit);

    long countInstances();

    default List<ApprovalInstance> findInstances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive,
            int offset,
            int limit
    ) {
        if (status == null && fromInclusive == null && toExclusive == null) {
            return findInstances(offset, limit);
        }
        throw new UnsupportedOperationException(
                "Filtered approval-instance reads are not configured");
    }

    default long countInstances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        if (status == null && fromInclusive == null && toExclusive == null) {
            return countInstances();
        }
        throw new UnsupportedOperationException(
                "Filtered approval-instance counts are not configured");
    }

    List<ApprovalInstance> findApprovalTasks(
            long approverId,
            ApprovalTaskStatus status,
            int offset,
            int limit
    );

    long countApprovalTasks(long approverId, ApprovalTaskStatus status);

    List<ApprovalInstance> findClaimableTasks(int offset, int limit);

    long countClaimableTasks();

    default List<ApprovalCompletionExecution> materializeCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        throw new UnsupportedOperationException(
                "Approval completion-execution storage is not configured");
    }

    default Optional<ApprovalCompletionExecution> findCompletionExecution(
            long executionId
    ) {
        return Optional.empty();
    }

    default Optional<ApprovalCompletionExecution> findCompletionExecutionForUpdate(
            long executionId
    ) {
        return findCompletionExecution(executionId);
    }

    default List<ApprovalCompletionExecution> findCompletionExecutionsByInstance(
            long instanceId
    ) {
        return List.of();
    }

    default List<ApprovalCompletionExecution> findCompletionStageForUpdate(
            long instanceId,
            int stageCursor
    ) {
        return List.of();
    }

    default List<ApprovalCompletionExecution> findAvailableExternalTasks(
            String topic,
            Instant dueAt,
            int offset,
            int limit
    ) {
        return List.of();
    }

    default long countAvailableExternalTasks(String topic, Instant dueAt) {
        return 0;
    }

    /**
     * Locks due webhook rows with {@code FOR UPDATE SKIP LOCKED}. Callers must
     * claim and persist the returned executions before their transaction ends.
     */
    default List<ApprovalCompletionExecution> findDueWebhookExecutionsForUpdate(
            Instant dueAt,
            int limit
    ) {
        return List.of();
    }

    default List<ApprovalCompletionExecution> findDueSubflowExecutionsForUpdate(
            Instant dueAt,
            int limit
    ) {
        return List.of();
    }

    /** Unlocked poll candidate scan; workers then lock parent, execution, run. */
    default List<ApprovalCompletionExecution> findDueSubflowExecutions(
            Instant dueAt,
            int limit
    ) {
        return List.of();
    }

    default ApprovalCompletionExecution saveCompletionExecution(
            ApprovalCompletionExecution execution
    ) {
        throw new UnsupportedOperationException(
                "Approval completion-execution storage is not configured");
    }

    default List<ApprovalCompletionExecution> saveCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        return executions.stream().map(this::saveCompletionExecution).toList();
    }

    default ApprovalCompletionAttempt appendCompletionAttempt(
            ApprovalCompletionAttempt attempt
    ) {
        throw new UnsupportedOperationException(
                "Approval completion-attempt storage is not configured");
    }

    default List<ApprovalCompletionAttempt> findCompletionAttempts(
            long executionId
    ) {
        return List.of();
    }

    default ApprovalSubflowRun appendSubflowRun(ApprovalSubflowRun run) {
        throw new UnsupportedOperationException(
                "Approval subflow-run storage is not configured");
    }

    default Optional<ApprovalSubflowRun>
    findSubflowRunByExecutionAndAttempt(long executionId, int attemptNumber) {
        return Optional.empty();
    }

    default Optional<ApprovalSubflowRun> findSubflowRunByChildInstance(
            long childInstanceId
    ) {
        return Optional.empty();
    }

    default List<ApprovalSubflowRun> findSubflowRunsByExecution(
            long executionId
    ) {
        return List.of();
    }

    default List<ApprovalSubflowRun> findPendingSubflowResultsForUpdate(
            int limit
    ) {
        return List.of();
    }

    default List<ApprovalSubflowRun> findPendingSubflowResults(int limit) {
        return List.of();
    }

    default List<ApprovalSubflowRun> findRunningSubflowRunsForUpdate(
            int limit
    ) {
        return List.of();
    }

    default List<ApprovalSubflowRun> findRunningSubflowRuns(int limit) {
        return List.of();
    }

    default ApprovalSubflowRun saveSubflowRun(ApprovalSubflowRun run) {
        throw new UnsupportedOperationException(
                "Approval subflow-run storage is not configured");
    }

    default List<ApprovalCompletionCompensation> materializeCompensationPlan(
            List<ApprovalCompletionCompensation> plan
    ) {
        throw new UnsupportedOperationException(
                "Approval compensation storage is not configured");
    }

    default Optional<ApprovalCompletionCompensation> findCompensation(long id) {
        return Optional.empty();
    }

    default Optional<ApprovalCompletionCompensation> findCompensationForUpdate(
            long id
    ) {
        return findCompensation(id);
    }

    default List<ApprovalCompletionCompensation> findCompensationsByInstance(
            long instanceId
    ) {
        return List.of();
    }

    default List<ApprovalCompletionCompensation> findCompensationsForUpdate(
            long instanceId
    ) {
        return findCompensationsByInstance(instanceId);
    }

    default List<ApprovalCompletionCompensation>
    findAvailableCompensationExternalTasks(
            String topic, Instant dueAt, int offset, int limit
    ) {
        return List.of();
    }

    default long countAvailableCompensationExternalTasks(
            String topic, Instant dueAt
    ) {
        return 0;
    }

    default List<ApprovalCompletionCompensation>
    findDueWebhookCompensationsForUpdate(Instant dueAt, int limit) {
        return List.of();
    }

    default List<ApprovalCompletionCompensation>
    findDueSubflowCompensations(Instant dueAt, int limit) {
        return List.of();
    }

    default ApprovalCompletionCompensation saveCompensation(
            ApprovalCompletionCompensation compensation
    ) {
        throw new UnsupportedOperationException(
                "Approval compensation storage is not configured");
    }

    default List<ApprovalCompletionCompensation> saveCompensations(
            List<ApprovalCompletionCompensation> compensations
    ) {
        return compensations.stream().map(this::saveCompensation).toList();
    }

    default ApprovalCompensationAttempt appendCompensationAttempt(
            ApprovalCompensationAttempt attempt
    ) {
        throw new UnsupportedOperationException(
                "Approval compensation-attempt storage is not configured");
    }

    default List<ApprovalCompensationAttempt> findCompensationAttempts(
            long compensationId
    ) {
        return List.of();
    }

    default ApprovalCompensationSubflowRun appendCompensationSubflowRun(
            ApprovalCompensationSubflowRun run
    ) {
        throw new UnsupportedOperationException(
                "Approval compensation subflow storage is not configured");
    }

    default Optional<ApprovalCompensationSubflowRun>
    findCompensationSubflowRunByAttempt(long compensationId, int attempt) {
        return Optional.empty();
    }

    default List<ApprovalCompensationSubflowRun>
    findCompensationSubflowRuns(long compensationId) {
        return List.of();
    }

    default List<ApprovalCompensationSubflowRun>
    findPendingCompensationSubflowRuns(int limit) {
        return List.of();
    }

    default ApprovalCompensationSubflowRun saveCompensationSubflowRun(
            ApprovalCompensationSubflowRun run
    ) {
        throw new UnsupportedOperationException(
                "Approval compensation subflow storage is not configured");
    }

    default ApprovalDecisionCommentTemplate saveDecisionCommentTemplate(
            ApprovalDecisionCommentTemplate template
    ) {
        throw new UnsupportedOperationException(
                "Approval decision comment-template storage is not configured");
    }

    default Optional<ApprovalDecisionCommentTemplate> findDecisionCommentTemplate(
            long templateId
    ) {
        return Optional.empty();
    }

    default Optional<ApprovalDecisionCommentTemplateVersion>
    findDecisionCommentTemplateVersion(long templateId, int version) {
        return Optional.empty();
    }

    default List<ApprovalDecisionCommentTemplate> findDecisionCommentTemplates(
            boolean activeOnly,
            int offset,
            int limit
    ) {
        return List.of();
    }

    default long countDecisionCommentTemplates(boolean activeOnly) {
        return 0;
    }

    default ApprovalDecisionEvidence saveDecisionEvidence(
            ApprovalDecisionEvidence evidence
    ) {
        throw new UnsupportedOperationException(
                "Approval decision-evidence storage is not configured");
    }

    default Optional<ApprovalDecisionEvidence> findDecisionEvidence(
            long instanceId,
            int historySequence
    ) {
        return Optional.empty();
    }

    default List<ApprovalDecisionEvidence> findDecisionEvidenceByInstance(
            long instanceId
    ) {
        return List.of();
    }

    default ApprovalDelegationRule saveDelegation(ApprovalDelegationRule rule) {
        throw new UnsupportedOperationException("Approval delegation storage is not configured");
    }

    default Optional<ApprovalDelegationRule> findDelegation(long delegationRuleId) {
        return Optional.empty();
    }

    default Optional<ApprovalDelegationRule> findDelegationForUpdate(
            long delegationRuleId
    ) {
        return findDelegation(delegationRuleId);
    }

    default List<ApprovalDelegationRule> findDelegationsByDelegator(
            long delegatorMemberId,
            int offset,
            int limit
    ) {
        return List.of();
    }

    default long countDelegationsByDelegator(long delegatorMemberId) {
        return 0;
    }

    /**
     * Locks non-revoked rules involving either candidate endpoint whose time
     * windows can overlap. The service performs scope/chain classification.
     */
    default List<ApprovalDelegationRule> findDelegationConflictsForUpdate(
            long delegatorMemberId,
            long delegateMemberId,
            Instant startsAt,
            Instant endsAt
    ) {
        return List.of();
    }

    /**
     * Locks the exact currently-effective direct authority used by a decision.
     */
    default Optional<ApprovalDelegationRule> findActiveDelegationForUpdate(
            long delegateMemberId,
            long representedMemberId,
            long definitionId,
            Instant effectiveAt
    ) {
        return Optional.empty();
    }

    default List<ApprovalTaskAssignment> findApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            Instant effectiveAt,
            int offset,
            int limit
    ) {
        return findApprovalTasks(actorMemberId, status, offset, limit).stream()
                .map(instance -> ApprovalTaskAssignment.direct(instance, actorMemberId))
                .toList();
    }

    default long countApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            Instant effectiveAt
    ) {
        return countApprovalTasks(actorMemberId, status);
    }
}
