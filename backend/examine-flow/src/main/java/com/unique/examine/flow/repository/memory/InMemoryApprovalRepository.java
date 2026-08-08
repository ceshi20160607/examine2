package com.unique.examine.flow.repository.memory;

import com.unique.examine.flow.domain.ApprovalDefinitionDraft;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalDelegationRule;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplateVersion;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalCompletionAttempt;
import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalSubflowRun;
import com.unique.examine.flow.domain.ApprovalCompletionCompensation;
import com.unique.examine.flow.domain.ApprovalCompensationAttempt;
import com.unique.examine.flow.domain.ApprovalCompensationSubflowRun;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.domain.ApprovalTaskAssignment;
import com.unique.examine.flow.domain.FlowTriggerDispatch;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.repository.ApprovalRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.VERSION_ALREADY_EXISTS;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.PERSISTENCE_CONFLICT;

public final class InMemoryApprovalRepository implements ApprovalRepository {
    private final Map<Long, ApprovalDefinitionDraft> drafts = new HashMap<>();
    private final Map<Long, TreeMap<Integer, ApprovalDefinitionVersion>> versions = new HashMap<>();
    private final Map<Long, ApprovalInstance> instances = new HashMap<>();
    private final Map<Long, ApprovalDelegationRule> delegations = new HashMap<>();
    private final Map<String, FlowTriggerDispatch> triggerDispatches = new HashMap<>();
    private final Map<Long, ApprovalDecisionCommentTemplate> commentTemplates =
            new HashMap<>();
    private final Map<Long, TreeMap<Integer, ApprovalDecisionCommentTemplateVersion>>
            commentTemplateVersions = new HashMap<>();
    private final Map<EvidenceIdentity, ApprovalDecisionEvidence> decisionEvidence =
            new HashMap<>();
    private final Map<Long, ApprovalDecisionEvidence> decisionEvidenceById =
            new HashMap<>();
    private final Map<Long, ApprovalCompletionExecution> completionExecutions =
            new HashMap<>();
    private final Map<Long, List<ApprovalCompletionAttempt>> completionAttempts =
            new HashMap<>();
    private final Map<Long, ApprovalSubflowRun> subflowRuns = new HashMap<>();
    private final Map<Long, ApprovalCompletionCompensation> compensations =
            new HashMap<>();
    private final Map<Long, List<ApprovalCompensationAttempt>> compensationAttempts =
            new HashMap<>();
    private final Map<Long, List<ApprovalCompensationSubflowRun>>
            compensationSubflowRuns = new HashMap<>();

    @Override
    public synchronized ApprovalDefinitionDraft saveDraft(ApprovalDefinitionDraft draft) {
        drafts.put(draft.id(), draft);
        return draft;
    }

    @Override
    public synchronized Optional<ApprovalDefinitionDraft> findDraft(long definitionId) {
        return Optional.ofNullable(drafts.get(definitionId));
    }

    @Override
    public synchronized List<ApprovalDefinitionDraft> findDrafts(int offset, int limit) {
        return drafts.values().stream()
                .sorted(Comparator.comparing(ApprovalDefinitionDraft::updatedAt)
                        .thenComparingLong(ApprovalDefinitionDraft::id)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countDrafts() {
        return drafts.size();
    }

    @Override
    public synchronized ApprovalDefinitionVersion saveVersion(ApprovalDefinitionVersion version) {
        var definitionVersions = versions.computeIfAbsent(version.definitionId(), ignored -> new TreeMap<>());
        if (definitionVersions.values().stream()
                .anyMatch(existing -> existing.sourceRevision() == version.sourceRevision())) {
            throw new ApprovalDomainException(
                    VERSION_ALREADY_EXISTS,
                    "The same draft revision cannot be published more than once"
            );
        }
        if (definitionVersions.putIfAbsent(version.version(), version) != null) {
            throw new ApprovalDomainException(
                    VERSION_ALREADY_EXISTS,
                    "Published approval versions are immutable and cannot be replaced"
            );
        }
        return version;
    }

    @Override
    public synchronized Optional<ApprovalDefinitionVersion> findVersion(long definitionId, int version) {
        return Optional.ofNullable(versions.getOrDefault(definitionId, new TreeMap<>()).get(version));
    }

    @Override
    public synchronized Optional<ApprovalDefinitionVersion> findLatestVersion(long definitionId) {
        var definitionVersions = versions.get(definitionId);
        return definitionVersions == null || definitionVersions.isEmpty()
                ? Optional.empty()
                : Optional.of(definitionVersions.lastEntry().getValue());
    }

    @Override
    public synchronized List<ApprovalDefinitionVersion> findVersions(
            long definitionId,
            int offset,
            int limit
    ) {
        var definitionVersions = versions.get(definitionId);
        return definitionVersions == null
                ? List.of()
                : definitionVersions.descendingMap().values().stream()
                        .skip(offset)
                        .limit(limit)
                        .toList();
    }

    @Override
    public synchronized long countVersions(long definitionId) {
        var definitionVersions = versions.get(definitionId);
        return definitionVersions == null ? 0 : definitionVersions.size();
    }

    @Override
    public synchronized List<ApprovalDefinitionVersion> findStartableDefinitions(int offset, int limit) {
        return versions.values().stream()
                .filter(values -> !values.isEmpty())
                .map(values -> values.lastEntry().getValue())
                .sorted(Comparator.comparing(ApprovalDefinitionVersion::publishedAt)
                        .thenComparingLong(ApprovalDefinitionVersion::definitionId)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countStartableDefinitions() {
        return versions.values().stream().filter(values -> !values.isEmpty()).count();
    }

    @Override
    public synchronized List<ApprovalDefinitionVersion> findTriggerCandidates(
            String moduleCode,
            TriggerBinding.Event event
    ) {
        return versions.values().stream()
                .filter(values -> !values.isEmpty())
                .map(values -> values.lastEntry().getValue())
                .filter(version -> version.triggerBinding() != null)
                .filter(version -> version.triggerBinding().moduleCode().equals(moduleCode))
                .filter(version -> version.triggerBinding().event() == event)
                .sorted(Comparator
                        .comparingInt((ApprovalDefinitionVersion version) ->
                                version.triggerBinding().priority())
                        .reversed()
                        .thenComparingLong(ApprovalDefinitionVersion::definitionId))
                .toList();
    }

    @Override
    public synchronized Optional<FlowTriggerDispatch> findTriggerDispatchForUpdate(
            String eventKey
    ) {
        return Optional.ofNullable(triggerDispatches.get(eventKey));
    }

    @Override
    public synchronized FlowTriggerDispatch saveTriggerDispatch(FlowTriggerDispatch dispatch) {
        if (triggerDispatches.putIfAbsent(dispatch.eventKey(), dispatch) != null) {
            throw new ApprovalDomainException(
                    PERSISTENCE_CONFLICT,
                    "Flow trigger event was dispatched concurrently"
            );
        }
        return dispatch;
    }

    @Override
    public synchronized ApprovalInstance saveInstance(ApprovalInstance instance) {
        var existing = instances.get(instance.id());
        if (instance.history().size() == 1) {
            if (existing != null) {
                throw new ApprovalDomainException(
                        PERSISTENCE_CONFLICT,
                        "Approval instance already exists"
                );
            }
        } else if (existing == null
                || existing.status() != ApprovalInstance.Status.PENDING
                || existing.history().size() != instance.history().size() - 1) {
            throw new ApprovalDomainException(
                    PERSISTENCE_CONFLICT,
                    "Approval instance is no longer pending at the expected version"
            );
        }
        instances.put(instance.id(), instance);
        return instance;
    }

    @Override
    public synchronized ApprovalInstance saveCompletionProgress(
            ApprovalInstance instance
    ) {
        var existing = instances.get(instance.id());
        if (existing == null
                || existing.status() != ApprovalInstance.Status.PENDING
                || existing.completionPhase()
                != ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION
                && existing.completionPhase()
                != ApprovalInstance.CompletionPhase.COMPENSATING
                || instance.status() != ApprovalInstance.Status.PENDING
                || instance.completionPhase()
                != ApprovalInstance.CompletionPhase.EXTERNAL_EXECUTION
                && instance.completionPhase()
                != ApprovalInstance.CompletionPhase.COMPENSATING
                || existing.history().size() != instance.history().size()) {
            throw new ApprovalDomainException(
                    PERSISTENCE_CONFLICT,
                    "Approval completion cursor changed concurrently"
            );
        }
        instances.put(instance.id(), instance);
        return instance;
    }

    @Override
    public synchronized Optional<ApprovalInstance> findInstance(long instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    @Override
    public synchronized Optional<ApprovalInstance> findInstanceForUpdate(long instanceId) {
        return findInstance(instanceId);
    }

    @Override
    public synchronized List<ApprovalInstance> findInstances(int offset, int limit) {
        return findInstances(null, null, null, offset, limit);
    }

    @Override
    public synchronized List<ApprovalInstance> findInstances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive,
            int offset,
            int limit
    ) {
        return instances.values().stream()
                .filter(instance -> matchesInstanceFilters(
                        instance, status, fromInclusive, toExclusive))
                .sorted(Comparator.comparing(ApprovalInstance::startedAt)
                        .thenComparingLong(ApprovalInstance::id)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countInstances() {
        return countInstances(null, null, null);
    }

    @Override
    public synchronized long countInstances(
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        return instances.values().stream()
                .filter(instance -> matchesInstanceFilters(
                        instance, status, fromInclusive, toExclusive))
                .count();
    }

    private static boolean matchesInstanceFilters(
            ApprovalInstance instance,
            ApprovalInstance.Status status,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        if (status != null && instance.status() != status) {
            return false;
        }
        if (fromInclusive == null && toExclusive == null) {
            return true;
        }
        var completedAt = instance.completedAt();
        return completedAt != null
                && (fromInclusive == null || !completedAt.isBefore(fromInclusive))
                && (toExclusive == null || completedAt.isBefore(toExclusive));
    }

    @Override
    public synchronized List<ApprovalInstance> findApprovalTasks(
            long approverId,
            ApprovalTaskStatus status,
            int offset,
            int limit
    ) {
        return approvalTasks(approverId, status)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countApprovalTasks(long approverId, ApprovalTaskStatus status) {
        return approvalTasks(approverId, status).count();
    }

    @Override
    public synchronized List<ApprovalInstance> findClaimableTasks(int offset, int limit) {
        return claimableTasks().skip(offset).limit(limit).toList();
    }

    @Override
    public synchronized long countClaimableTasks() {
        return claimableTasks().count();
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    materializeCompletionExecutions(
            List<ApprovalCompletionExecution> executions
    ) {
        Objects.requireNonNull(executions, "executions");
        if (executions.isEmpty()
                || executions.size() > ApprovalCompletionStep.MAX_STEPS) {
            throw conflict("Completion execution plan must contain 1 to 8 steps");
        }
        var snapshot = List.copyOf(executions);
        var first = snapshot.getFirst();
        var firstStage = com.unique.examine.flow.domain.ApprovalCompletionStage
                .stageAtOrdinal(snapshot, 0);
        if (snapshot.stream().map(ApprovalCompletionExecution::id)
                .distinct().count() != snapshot.size()) {
            throw conflict("Completion execution ids must be unique");
        }
        for (var ordinal = 0; ordinal < snapshot.size(); ordinal++) {
            var execution = snapshot.get(ordinal);
            if (execution.instanceId() != first.instanceId()
                    || execution.definitionId() != first.definitionId()
                    || execution.definitionVersion() != first.definitionVersion()
                    || execution.ordinal() != ordinal
                    || execution.stateVersion() != 0
                    || ordinal < firstStage.endExclusive()
                    && execution.status()
                    != ApprovalCompletionExecution.Status.AVAILABLE
                    || ordinal >= firstStage.endExclusive()
                    && execution.status()
                    != ApprovalCompletionExecution.Status.WAITING
                    || completionExecutions.containsKey(execution.id())
                    || completionExecutions.values().stream().anyMatch(existing ->
                    existing.instanceId() == execution.instanceId()
                            && existing.ordinal() == execution.ordinal())) {
                throw conflict("Completion execution plan is not immutable or ordered");
            }
        }
        snapshot.forEach(execution ->
                completionExecutions.put(execution.id(), execution));
        return snapshot;
    }

    @Override
    public synchronized Optional<ApprovalCompletionExecution>
    findCompletionExecution(long executionId) {
        return Optional.ofNullable(completionExecutions.get(executionId));
    }

    @Override
    public synchronized Optional<ApprovalCompletionExecution>
    findCompletionExecutionForUpdate(long executionId) {
        return findCompletionExecution(executionId);
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    findCompletionExecutionsByInstance(long instanceId) {
        return completionExecutions.values().stream()
                .filter(execution -> execution.instanceId() == instanceId)
                .sorted(Comparator.comparingInt(
                        ApprovalCompletionExecution::ordinal))
                .toList();
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    findCompletionStageForUpdate(long instanceId, int stageCursor) {
        var executions = findCompletionExecutionsByInstance(instanceId);
        var stage = com.unique.examine.flow.domain.ApprovalCompletionStage
                .stageAtOrdinal(executions, stageCursor);
        if (stage.cursor() != stageCursor) {
            throw conflict("Completion stage cursor must be its first ordinal");
        }
        return executions.subList(stage.cursor(), stage.endExclusive());
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    findAvailableExternalTasks(
            String topic,
            Instant dueAt,
            int offset,
            int limit
    ) {
        Objects.requireNonNull(dueAt, "dueAt");
        var normalizedTopic =
                topic == null || topic.isBlank() ? null : topic.strip();
        return completionExecutions.values().stream()
                .filter(execution ->
                        execution.step().type()
                                == ApprovalCompletionStep.Type.EXTERNAL_TASK)
                .filter(execution -> normalizedTopic == null
                        || execution.step().externalTask().topic()
                        .equals(normalizedTopic))
                .filter(execution -> execution.isDueAt(dueAt))
                .sorted(Comparator
                        .comparing(ApprovalCompletionExecution::availableAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(ApprovalCompletionExecution::id))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countAvailableExternalTasks(
            String topic,
            Instant dueAt
    ) {
        return findAvailableExternalTasks(
                topic, dueAt, 0, Integer.MAX_VALUE).size();
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    findDueWebhookExecutionsForUpdate(Instant dueAt, int limit) {
        Objects.requireNonNull(dueAt, "dueAt");
        return completionExecutions.values().stream()
                .filter(execution ->
                        execution.step().type()
                                == ApprovalCompletionStep.Type.WEBHOOK)
                .filter(execution -> execution.isDueAt(dueAt))
                .sorted(Comparator
                        .comparing(ApprovalCompletionExecution::availableAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(ApprovalCompletionExecution::id))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    findDueSubflowExecutionsForUpdate(Instant dueAt, int limit) {
        Objects.requireNonNull(dueAt, "dueAt");
        return completionExecutions.values().stream()
                .filter(execution -> execution.step().type()
                        == ApprovalCompletionStep.Type.SUBFLOW)
                .filter(execution -> execution.isDueAt(dueAt))
                .sorted(Comparator
                        .comparing(ApprovalCompletionExecution::availableAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(ApprovalCompletionExecution::id))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    findDueSubflowExecutions(Instant dueAt, int limit) {
        return findDueSubflowExecutionsForUpdate(dueAt, limit);
    }

    @Override
    public synchronized ApprovalCompletionExecution saveCompletionExecution(
            ApprovalCompletionExecution execution
    ) {
        Objects.requireNonNull(execution, "execution");
        var existing = completionExecutions.get(execution.id());
        if (existing == null
                || execution.stateVersion() != existing.stateVersion() + 1
                || execution.instanceId() != existing.instanceId()
                || execution.definitionId() != existing.definitionId()
                || execution.definitionVersion() != existing.definitionVersion()
                || execution.ordinal() != existing.ordinal()
                || !execution.step().equals(existing.step())
                || !execution.payloadJson().equals(existing.payloadJson())
                || !execution.createdAt().equals(existing.createdAt())) {
            throw conflict("Completion execution changed concurrently");
        }
        completionExecutions.put(execution.id(), execution);
        return execution;
    }

    @Override
    public synchronized List<ApprovalCompletionExecution>
    saveCompletionExecutions(List<ApprovalCompletionExecution> executions) {
        var snapshot = List.copyOf(executions);
        for (var execution : snapshot) {
            var existing = completionExecutions.get(execution.id());
            if (existing == null
                    || execution.stateVersion() != existing.stateVersion() + 1
                    || execution.instanceId() != existing.instanceId()
                    || execution.ordinal() != existing.ordinal()
                    || !execution.step().equals(existing.step())) {
                throw conflict("Completion execution group changed concurrently");
            }
        }
        snapshot.forEach(execution ->
                completionExecutions.put(execution.id(), execution));
        return snapshot;
    }

    @Override
    public synchronized ApprovalCompletionAttempt appendCompletionAttempt(
            ApprovalCompletionAttempt attempt
    ) {
        Objects.requireNonNull(attempt, "attempt");
        if (!completionExecutions.containsKey(attempt.executionId())) {
            throw conflict("Completion attempt execution does not exist");
        }
        var values = completionAttempts.computeIfAbsent(
                attempt.executionId(), ignored -> new ArrayList<>());
        if (values.stream().anyMatch(existing ->
                existing.id() == attempt.id()
                        || existing.eventSequence() == attempt.eventSequence()
                        || attempt.idempotencyKeyHash() != null
                        && attempt.idempotencyKeyHash().equals(
                        existing.idempotencyKeyHash()))) {
            throw conflict("Completion attempt fact already exists");
        }
        values.add(attempt);
        return attempt;
    }

    @Override
    public synchronized List<ApprovalCompletionAttempt>
    findCompletionAttempts(long executionId) {
        return completionAttempts
                .getOrDefault(executionId, List.of()).stream()
                .sorted(Comparator.comparingInt(
                        ApprovalCompletionAttempt::eventSequence))
                .toList();
    }

    @Override
    public synchronized ApprovalSubflowRun appendSubflowRun(
            ApprovalSubflowRun run
    ) {
        Objects.requireNonNull(run, "run");
        var execution = completionExecutions.get(run.executionId());
        if (execution == null
                || execution.step().type() != ApprovalCompletionStep.Type.SUBFLOW
                || !execution.step().subflow().equals(
                new ApprovalCompletionStep.Subflow(
                        run.targetDefinitionId(), run.targetVersion()))
                || execution.attemptCount() != run.attemptNumber()
                || execution.status() != ApprovalCompletionExecution.Status.RUNNING) {
            throw conflict("Subflow run does not match its running execution");
        }
        var existing = findSubflowRunByExecutionAndAttempt(
                run.executionId(), run.attemptNumber());
        if (existing.isPresent()) {
            if (existing.get().launchKey().equals(run.launchKey())
                    && existing.get().targetDefinitionId()
                    == run.targetDefinitionId()
                    && existing.get().targetVersion() == run.targetVersion()
                    && existing.get().rootInstanceId() == run.rootInstanceId()
                    && existing.get().depth() == run.depth()) {
                return existing.get();
            }
            throw conflict("Subflow execution attempt already has another child");
        }
        if (subflowRuns.containsKey(run.id())
                || subflowRuns.values().stream().anyMatch(value ->
                value.launchKey().equals(run.launchKey())
                        || value.childInstanceId() == run.childInstanceId())) {
            throw conflict("Subflow launch or child identity already exists");
        }
        subflowRuns.put(run.id(), run);
        return run;
    }

    @Override
    public synchronized Optional<ApprovalSubflowRun>
    findSubflowRunByExecutionAndAttempt(
            long executionId,
            int attemptNumber
    ) {
        return subflowRuns.values().stream()
                .filter(run -> run.executionId() == executionId
                        && run.attemptNumber() == attemptNumber)
                .findFirst();
    }

    @Override
    public synchronized Optional<ApprovalSubflowRun>
    findSubflowRunByChildInstance(long childInstanceId) {
        return subflowRuns.values().stream()
                .filter(run -> run.childInstanceId() == childInstanceId)
                .findFirst();
    }

    @Override
    public synchronized List<ApprovalSubflowRun> findSubflowRunsByExecution(
            long executionId
    ) {
        return subflowRuns.values().stream()
                .filter(run -> run.executionId() == executionId)
                .sorted(Comparator.comparingInt(
                        ApprovalSubflowRun::attemptNumber))
                .toList();
    }

    @Override
    public synchronized List<ApprovalSubflowRun>
    findPendingSubflowResultsForUpdate(int limit) {
        return subflowRuns.values().stream()
                .filter(ApprovalSubflowRun::pendingResult)
                .sorted(Comparator
                        .comparing(ApprovalSubflowRun::terminalAt)
                        .thenComparingLong(ApprovalSubflowRun::id))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<ApprovalSubflowRun> findPendingSubflowResults(
            int limit
    ) {
        return findPendingSubflowResultsForUpdate(limit);
    }

    @Override
    public synchronized List<ApprovalSubflowRun>
    findRunningSubflowRunsForUpdate(int limit) {
        return subflowRuns.values().stream()
                .filter(run -> run.status() == ApprovalSubflowRun.Status.RUNNING)
                .sorted(Comparator
                        .comparing(ApprovalSubflowRun::launchedAt)
                        .thenComparingLong(ApprovalSubflowRun::id))
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized List<ApprovalSubflowRun> findRunningSubflowRuns(
            int limit
    ) {
        return findRunningSubflowRunsForUpdate(limit);
    }

    @Override
    public synchronized ApprovalSubflowRun saveSubflowRun(
            ApprovalSubflowRun run
    ) {
        Objects.requireNonNull(run, "run");
        var existing = subflowRuns.get(run.id());
        if (existing == null
                || run.stateVersion() != existing.stateVersion() + 1
                || run.executionId() != existing.executionId()
                || run.attemptNumber() != existing.attemptNumber()
                || !run.launchKey().equals(existing.launchKey())
                || run.childInstanceId() != existing.childInstanceId()
                || run.targetDefinitionId() != existing.targetDefinitionId()
                || run.targetVersion() != existing.targetVersion()
                || run.rootInstanceId() != existing.rootInstanceId()
                || run.depth() != existing.depth()
                || !run.launchedAt().equals(existing.launchedAt())) {
            throw conflict("Subflow run changed concurrently");
        }
        subflowRuns.put(run.id(), run);
        return run;
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation>
    materializeCompensationPlan(
            List<ApprovalCompletionCompensation> plan
    ) {
        var snapshot = List.copyOf(plan);
        if (snapshot.isEmpty()
                || snapshot.size() > ApprovalCompletionStep.MAX_STEPS) {
            throw conflict("Compensation plan must contain 1 to 8 members");
        }
        var instanceId = snapshot.getFirst().instanceId();
        if (compensations.values().stream().anyMatch(value ->
                value.instanceId() == instanceId)) {
            var existing = findCompensationsByInstance(instanceId);
            if (existing.equals(snapshot)) {
                return existing;
            }
            throw conflict("Compensation plan already exists");
        }
        for (var reverse = 0; reverse < snapshot.size(); reverse++) {
            var value = snapshot.get(reverse);
            var original = completionExecutions.get(value.originalExecutionId());
            if (value.instanceId() != instanceId
                    || value.reverseOrdinal() != reverse
                    || value.stateVersion() != 0
                    || reverse == 0 && value.status()
                    != ApprovalCompletionExecution.Status.AVAILABLE
                    || reverse > 0 && value.status()
                    != ApprovalCompletionExecution.Status.WAITING
                    || original == null
                    || original.status()
                    != ApprovalCompletionExecution.Status.SUCCEEDED
                    || original.ordinal() != value.originalOrdinal()
                    || original.step().compensation() == null
                    || !original.step().compensation()
                    .asStep(original.step()).equals(value.step())
                    || reverse > 0 && snapshot.get(reverse - 1)
                    .originalOrdinal() <= value.originalOrdinal()
                    || compensations.containsKey(value.id())) {
                throw conflict("Compensation plan is not immutable or reverse ordered");
            }
        }
        snapshot.forEach(value -> compensations.put(value.id(), value));
        return snapshot;
    }

    @Override
    public synchronized Optional<ApprovalCompletionCompensation>
    findCompensation(long id) {
        return Optional.ofNullable(compensations.get(id));
    }

    @Override
    public synchronized Optional<ApprovalCompletionCompensation>
    findCompensationForUpdate(long id) {
        return findCompensation(id);
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation>
    findCompensationsByInstance(long instanceId) {
        return compensations.values().stream()
                .filter(value -> value.instanceId() == instanceId)
                .sorted(Comparator.comparingInt(
                        ApprovalCompletionCompensation::reverseOrdinal))
                .toList();
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation>
    findCompensationsForUpdate(long instanceId) {
        return compensations.values().stream()
                .filter(value -> value.instanceId() == instanceId)
                .sorted(Comparator
                        .comparingInt(ApprovalCompletionCompensation::originalOrdinal)
                        .reversed()
                        .thenComparing(
                                ApprovalCompletionCompensation::id,
                                Comparator.reverseOrder()))
                .toList();
    }

    @Override
    public synchronized ApprovalCompletionCompensation saveCompensation(
            ApprovalCompletionCompensation value
    ) {
        var existing = compensations.get(value.id());
        if (existing == null
                || value.stateVersion() != existing.stateVersion() + 1
                || value.instanceId() != existing.instanceId()
                || value.originalExecutionId() != existing.originalExecutionId()
                || value.originalOrdinal() != existing.originalOrdinal()
                || value.reverseOrdinal() != existing.reverseOrdinal()
                || !value.step().equals(existing.step())) {
            throw conflict("Compensation execution changed concurrently");
        }
        compensations.put(value.id(), value);
        return value;
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation> saveCompensations(
            List<ApprovalCompletionCompensation> values
    ) {
        var snapshot = List.copyOf(values);
        snapshot.forEach(value -> {
            var existing = compensations.get(value.id());
            if (existing == null
                    || value.stateVersion() != existing.stateVersion() + 1) {
                throw conflict("Compensation group changed concurrently");
            }
        });
        snapshot.forEach(value -> compensations.put(value.id(), value));
        return snapshot;
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation>
    findAvailableCompensationExternalTasks(
            String topic, Instant dueAt, int offset, int limit
    ) {
        var normalized = topic == null || topic.isBlank()
                ? null : topic.strip();
        return compensations.values().stream()
                .filter(value -> value.step().type()
                        == ApprovalCompletionStep.Type.EXTERNAL_TASK)
                .filter(value -> normalized == null
                        || normalized.equals(value.step().externalTask().topic()))
                .filter(value -> value.execution().isDueAt(dueAt))
                .sorted(Comparator.comparing(
                        value -> value.execution().availableAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .skip(offset).limit(limit).toList();
    }

    @Override
    public synchronized long countAvailableCompensationExternalTasks(
            String topic, Instant dueAt
    ) {
        return findAvailableCompensationExternalTasks(
                topic, dueAt, 0, Integer.MAX_VALUE).size();
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation>
    findDueWebhookCompensationsForUpdate(Instant dueAt, int limit) {
        return dueCompensations(
                ApprovalCompletionStep.Type.WEBHOOK, dueAt, limit);
    }

    @Override
    public synchronized List<ApprovalCompletionCompensation>
    findDueSubflowCompensations(Instant dueAt, int limit) {
        return dueCompensations(
                ApprovalCompletionStep.Type.SUBFLOW, dueAt, limit);
    }

    private List<ApprovalCompletionCompensation> dueCompensations(
            ApprovalCompletionStep.Type type, Instant dueAt, int limit
    ) {
        return compensations.values().stream()
                .filter(value -> value.step().type() == type)
                .filter(value -> value.execution().isDueAt(dueAt))
                .sorted(Comparator.comparingInt(
                        ApprovalCompletionCompensation::reverseOrdinal))
                .limit(limit).toList();
    }

    @Override
    public synchronized ApprovalCompensationAttempt appendCompensationAttempt(
            ApprovalCompensationAttempt attempt
    ) {
        if (!compensations.containsKey(attempt.compensationId())) {
            throw conflict("Compensation attempt execution does not exist");
        }
        var values = compensationAttempts.computeIfAbsent(
                attempt.compensationId(), ignored -> new ArrayList<>());
        if (values.stream().anyMatch(existing ->
                existing.fact().id() == attempt.fact().id()
                        || existing.fact().eventSequence()
                        == attempt.fact().eventSequence()
                        || attempt.fact().idempotencyKeyHash() != null
                        && attempt.fact().idempotencyKeyHash().equals(
                        existing.fact().idempotencyKeyHash()))) {
            throw conflict("Compensation attempt fact already exists");
        }
        values.add(attempt);
        return attempt;
    }

    @Override
    public synchronized List<ApprovalCompensationAttempt>
    findCompensationAttempts(long compensationId) {
        return compensationAttempts.getOrDefault(
                        compensationId, List.of()).stream()
                .sorted(Comparator.comparingInt(value ->
                        value.fact().eventSequence()))
                .toList();
    }

    @Override
    public synchronized ApprovalCompensationSubflowRun
    appendCompensationSubflowRun(ApprovalCompensationSubflowRun value) {
        if (!compensations.containsKey(value.compensationId())) {
            throw conflict("Compensation subflow execution does not exist");
        }
        var values = compensationSubflowRuns.computeIfAbsent(
                value.compensationId(), ignored -> new ArrayList<>());
        var existing = values.stream().filter(item ->
                item.run().attemptNumber() == value.run().attemptNumber())
                .findFirst();
        if (existing.isPresent()) {
            if (existing.get().run().launchKey().equals(
                    value.run().launchKey())) {
                return existing.get();
            }
            throw conflict("Compensation subflow attempt already exists");
        }
        if (compensationSubflowRuns.values().stream().flatMap(List::stream)
                .anyMatch(item -> item.run().childInstanceId()
                        == value.run().childInstanceId()
                        || item.run().launchKey().equals(
                        value.run().launchKey()))) {
            throw conflict("Compensation subflow identity already exists");
        }
        values.add(value);
        return value;
    }

    @Override
    public synchronized Optional<ApprovalCompensationSubflowRun>
    findCompensationSubflowRunByAttempt(long compensationId, int attempt) {
        return compensationSubflowRuns.getOrDefault(
                        compensationId, List.of()).stream()
                .filter(value -> value.run().attemptNumber() == attempt)
                .findFirst();
    }

    @Override
    public synchronized List<ApprovalCompensationSubflowRun>
    findCompensationSubflowRuns(long compensationId) {
        return compensationSubflowRuns.getOrDefault(
                        compensationId, List.of()).stream()
                .sorted(Comparator.comparingInt(value ->
                        value.run().attemptNumber()))
                .toList();
    }

    @Override
    public synchronized List<ApprovalCompensationSubflowRun>
    findPendingCompensationSubflowRuns(int limit) {
        return compensationSubflowRuns.values().stream()
                .flatMap(List::stream)
                .filter(value -> value.run().pendingResult())
                .sorted(Comparator.comparing(value ->
                        value.run().terminalAt()))
                .limit(limit).toList();
    }

    @Override
    public synchronized ApprovalCompensationSubflowRun
    saveCompensationSubflowRun(ApprovalCompensationSubflowRun value) {
        var values = compensationSubflowRuns.get(value.compensationId());
        if (values == null) {
            throw conflict("Compensation subflow run does not exist");
        }
        for (var index = 0; index < values.size(); index++) {
            var existing = values.get(index);
            if (existing.run().id() != value.run().id()) {
                continue;
            }
            var current = existing.run();
            var next = value.run();
            if (next.stateVersion() != current.stateVersion() + 1
                    || next.executionId() != current.executionId()
                    || next.attemptNumber() != current.attemptNumber()
                    || !next.launchKey().equals(current.launchKey())
                    || next.childInstanceId() != current.childInstanceId()) {
                throw conflict("Compensation subflow run changed concurrently");
            }
            values.set(index, value);
            return value;
        }
        throw conflict("Compensation subflow run does not exist");
    }

    private static boolean isActive(ApprovalCompletionExecution execution) {
        return execution.status()
                == ApprovalCompletionExecution.Status.AVAILABLE
                || execution.status()
                == ApprovalCompletionExecution.Status.LEASED
                || execution.status()
                == ApprovalCompletionExecution.Status.RETRYING
                || execution.status()
                == ApprovalCompletionExecution.Status.RUNNING
                || execution.status()
                == ApprovalCompletionExecution.Status.FAILED;
    }

    @Override
    public synchronized ApprovalDecisionCommentTemplate
    saveDecisionCommentTemplate(ApprovalDecisionCommentTemplate template) {
        var existing = commentTemplates.get(template.id());
        requireUniqueTemplateName(template);
        if (existing == null) {
            if (template.currentVersion() != 1) {
                throw conflict("New comment template must start at version one");
            }
            commentTemplates.put(template.id(), template);
            commentTemplateVersions
                    .computeIfAbsent(template.id(), ignored -> new TreeMap<>())
                    .put(1, template.currentVersionSnapshot());
            return template;
        }
        if (template.createdBy() != existing.createdBy()
                || !template.createdAt().equals(existing.createdAt())) {
            throw conflict("Comment template creation facts are immutable");
        }
        if (template.currentVersion() == existing.currentVersion()) {
            if (!template.name().equals(existing.name())
                    || !template.body().equals(existing.body())) {
                throw conflict(
                        "Comment template content requires a new immutable version");
            }
        } else if (template.currentVersion() == existing.currentVersion() + 1) {
            var templateVersions = commentTemplateVersions.get(template.id());
            if (templateVersions.putIfAbsent(
                    template.currentVersion(),
                    template.currentVersionSnapshot()) != null) {
                throw conflict("Comment template version already exists");
            }
        } else {
            throw conflict("Comment template version changed concurrently");
        }
        commentTemplates.put(template.id(), template);
        return template;
    }

    @Override
    public synchronized Optional<ApprovalDecisionCommentTemplate>
    findDecisionCommentTemplate(long templateId) {
        return Optional.ofNullable(commentTemplates.get(templateId));
    }

    @Override
    public synchronized Optional<ApprovalDecisionCommentTemplateVersion>
    findDecisionCommentTemplateVersion(long templateId, int version) {
        return Optional.ofNullable(
                commentTemplateVersions
                        .getOrDefault(templateId, new TreeMap<>())
                        .get(version));
    }

    @Override
    public synchronized List<ApprovalDecisionCommentTemplate>
    findDecisionCommentTemplates(boolean activeOnly, int offset, int limit) {
        return commentTemplates.values().stream()
                .filter(template -> !activeOnly
                        || template.status()
                        == ApprovalDecisionCommentTemplate.Status.ACTIVE)
                .sorted(Comparator
                        .comparing(ApprovalDecisionCommentTemplate::updatedAt)
                        .thenComparingLong(ApprovalDecisionCommentTemplate::id)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countDecisionCommentTemplates(boolean activeOnly) {
        return commentTemplates.values().stream()
                .filter(template -> !activeOnly
                        || template.status()
                        == ApprovalDecisionCommentTemplate.Status.ACTIVE)
                .count();
    }

    @Override
    public synchronized ApprovalDecisionEvidence saveDecisionEvidence(
            ApprovalDecisionEvidence evidence
    ) {
        var identity = new EvidenceIdentity(
                evidence.instanceId(), evidence.historySequence());
        var byIdentity = decisionEvidence.get(identity);
        var byId = decisionEvidenceById.get(evidence.id());
        if (byIdentity != null || byId != null) {
            if (evidence.equals(byIdentity) && evidence.equals(byId)) {
                return evidence;
            }
            throw conflict(
                    "Decision evidence identity already has another snapshot");
        }
        var instance = instances.get(evidence.instanceId());
        if (instance == null) {
            throw conflict("Decision evidence instance does not exist");
        }
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
        var attached = instance.withDecisionEvidence(evidence);
        decisionEvidence.put(identity, evidence);
        decisionEvidenceById.put(evidence.id(), evidence);
        instances.put(instance.id(), attached);
        return evidence;
    }

    @Override
    public synchronized Optional<ApprovalDecisionEvidence> findDecisionEvidence(
            long instanceId,
            int historySequence
    ) {
        return Optional.ofNullable(
                decisionEvidence.get(
                        new EvidenceIdentity(instanceId, historySequence)));
    }

    @Override
    public synchronized List<ApprovalDecisionEvidence>
    findDecisionEvidenceByInstance(long instanceId) {
        return decisionEvidence.entrySet().stream()
                .filter(entry -> entry.getKey().instanceId() == instanceId)
                .sorted(Comparator.comparingInt(
                        entry -> entry.getKey().historySequence()))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Override
    public synchronized ApprovalDelegationRule saveDelegation(
            ApprovalDelegationRule rule
    ) {
        var existing = delegations.get(rule.id());
        if (existing != null
                && (existing.status() == ApprovalDelegationRule.Status.REVOKED
                || rule.status() != ApprovalDelegationRule.Status.REVOKED)) {
            throw new ApprovalDomainException(
                    PERSISTENCE_CONFLICT,
                    "Approval delegation changed concurrently"
            );
        }
        delegations.put(rule.id(), rule);
        return rule;
    }

    @Override
    public synchronized Optional<ApprovalDelegationRule> findDelegation(
            long delegationRuleId
    ) {
        return Optional.ofNullable(delegations.get(delegationRuleId));
    }

    @Override
    public synchronized Optional<ApprovalDelegationRule> findDelegationForUpdate(
            long delegationRuleId
    ) {
        return findDelegation(delegationRuleId);
    }

    @Override
    public synchronized List<ApprovalDelegationRule> findDelegationsByDelegator(
            long delegatorMemberId,
            int offset,
            int limit
    ) {
        return delegations.values().stream()
                .filter(rule -> rule.delegatorMemberId() == delegatorMemberId)
                .sorted(Comparator.comparing(ApprovalDelegationRule::createdAt)
                        .thenComparingLong(ApprovalDelegationRule::id)
                        .reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countDelegationsByDelegator(long delegatorMemberId) {
        return delegations.values().stream()
                .filter(rule -> rule.delegatorMemberId() == delegatorMemberId)
                .count();
    }

    @Override
    public synchronized List<ApprovalDelegationRule> findDelegationConflictsForUpdate(
            long delegatorMemberId,
            long delegateMemberId,
            Instant startsAt,
            Instant endsAt
    ) {
        return delegations.values().stream()
                .filter(rule -> rule.status() != ApprovalDelegationRule.Status.REVOKED)
                .filter(rule -> rule.startsAt().isBefore(endsAt)
                        && startsAt.isBefore(rule.endsAt()))
                .filter(rule ->
                        rule.delegatorMemberId() == delegatorMemberId
                                || rule.delegateMemberId() == delegatorMemberId
                                || rule.delegatorMemberId() == delegateMemberId
                                || rule.delegateMemberId() == delegateMemberId)
                .toList();
    }

    @Override
    public synchronized Optional<ApprovalDelegationRule> findActiveDelegationForUpdate(
            long delegateMemberId,
            long representedMemberId,
            long definitionId,
            Instant effectiveAt
    ) {
        return delegations.values().stream()
                .filter(rule -> rule.delegateMemberId() == delegateMemberId)
                .filter(rule -> rule.delegatorMemberId() == representedMemberId)
                .filter(rule -> rule.isEffectiveAt(effectiveAt, definitionId))
                .findFirst();
    }

    @Override
    public synchronized List<ApprovalTaskAssignment> findApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            Instant effectiveAt,
            int offset,
            int limit
    ) {
        return taskAssignments(actorMemberId, status, effectiveAt)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized long countApprovalTaskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            Instant effectiveAt
    ) {
        return taskAssignments(actorMemberId, status, effectiveAt).count();
    }

    private java.util.stream.Stream<ApprovalInstance> approvalTasks(
            long approverId,
            ApprovalTaskStatus status
    ) {
        return instances.values().stream()
                .filter(instance -> instance.hasApprovalTask(approverId, status))
                .sorted(Comparator.comparing(ApprovalInstance::startedAt)
                        .thenComparingLong(ApprovalInstance::id)
                        .reversed());
    }

    private java.util.stream.Stream<ApprovalInstance> claimableTasks() {
        return instances.values().stream()
                .filter(instance -> instance.status() == ApprovalInstance.Status.PENDING)
                .filter(instance -> instance.claimState() == ApprovalInstance.ClaimState.OPEN)
                .sorted(Comparator.comparing(ApprovalInstance::startedAt)
                        .thenComparingLong(ApprovalInstance::id)
                        .reversed());
    }

    private java.util.stream.Stream<ApprovalTaskAssignment> taskAssignments(
            long actorMemberId,
            ApprovalTaskStatus status,
            Instant effectiveAt
    ) {
        return instances.values().stream()
                .map(instance -> {
                    var authorities =
                            new ArrayList<ApprovalTaskAssignment.RepresentedAuthority>();
                    if (instance.hasApprovalTask(actorMemberId, status)) {
                        authorities.add(
                                new ApprovalTaskAssignment.RepresentedAuthority(
                                        actorMemberId,
                                        null
                                )
                        );
                    }
                    delegations.values().stream()
                            .filter(rule -> rule.delegateMemberId() == actorMemberId)
                            .filter(rule -> rule.isEffectiveAt(
                                    effectiveAt,
                                    instance.definitionId()
                            ))
                            .filter(rule -> instance.hasApprovalTask(
                                    rule.delegatorMemberId(),
                                    status
                            ))
                            .sorted(Comparator.comparingLong(
                                    ApprovalDelegationRule::delegatorMemberId
                            ))
                            .forEach(rule -> authorities.add(
                                    new ApprovalTaskAssignment.RepresentedAuthority(
                                            rule.delegatorMemberId(),
                                            rule.id()
                                    )
                            ));
                    return authorities.isEmpty()
                            ? null
                            : new ApprovalTaskAssignment(
                                    instance,
                                    actorMemberId,
                                    authorities
                            );
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(
                        (ApprovalTaskAssignment value) ->
                                value.instance().startedAt()
                ).thenComparingLong(value -> value.instance().id()).reversed());
    }

    private void requireUniqueTemplateName(
            ApprovalDecisionCommentTemplate template
    ) {
        var duplicate = commentTemplates.values().stream()
                .anyMatch(existing ->
                        existing.id() != template.id()
                                && existing.name().equals(template.name()));
        if (duplicate) {
            throw conflict("Comment template name already exists");
        }
    }

    private static ApprovalDomainException conflict(String message) {
        return new ApprovalDomainException(PERSISTENCE_CONFLICT, message);
    }

    private record EvidenceIdentity(long instanceId, int historySequence) {
    }
}
