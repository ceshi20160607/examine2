package com.unique.examine.flow.api;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicies;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicy;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicy;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy;
import com.unique.examine.flow.domain.ApprovalQuorumRule;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.ApprovalGateway;
import com.unique.examine.flow.domain.ApprovalInclusiveGateway;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.PeriodicSchedule;
import com.unique.examine.flow.domain.RecordStatusMapping;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.APPROVER_SEQUENCE_INVALID;

public final class FlowHttpErrors {
    private static final Set<String> TRIGGER_EVENTS = Set.of(
            "RECORD_ACTIVATED",
            "RECORD_CREATED",
            "RECORD_UPDATED",
            "RECORD_DELETED",
            "RECORD_STATUS_CHANGED",
            "IMPORT_COMPLETED",
            "PERIODIC"
    );

    private FlowHttpErrors() {
    }

    public static <T> T execute(Supplier<T> action) {
        try {
            return action.get();
        } catch (ApprovalDomainException exception) {
            throw translate(exception);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("FLOW_REQUEST_INVALID", exception.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public static long id(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    "FLOW_REQUEST_INVALID",
                    field + " must be a positive integer string",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    public static List<Long> approverIds(String approverId, List<String> approverIds) {
        var hasLegacy = approverId != null;
        var hasSequence = approverIds != null;
        if (hasLegacy == hasSequence) {
            throw invalidApprovers(
                    "Exactly one of approverId or approverIds must be provided"
            );
        }
        var values = hasLegacy ? List.of(approverId) : approverIds;
        if (values.isEmpty() || values.size() > 10) {
            throw invalidApprovers("Approver sequence must contain 1 to 10 member ids");
        }
        try {
            return values.stream().map(value -> {
                var parsed = Long.parseLong(value);
                if (parsed <= 0) {
                    throw new NumberFormatException();
                }
                return parsed;
            }).toList();
        } catch (RuntimeException exception) {
            throw invalidApprovers("Approver sequence must contain positive integer strings");
        }
    }

    public static TriggerBinding triggerBinding(FlowRequests.TriggerBinding value) {
        return triggerBinding(value, 1L);
    }

    public static TriggerBinding triggerBinding(
            FlowRequests.TriggerBinding value,
            long requesterMemberId
    ) {
        if (value == null) {
            return null;
        }
        if (value.event() == null
                || !TRIGGER_EVENTS.contains(value.event())
                || value.priority() == null
                || value.priority() < -1000
                || value.priority() > 1000
                || value.exclusive() == null
                || (value.conditions() != null && value.conditions().size() > 10)) {
            throw invalidTriggerBinding();
        }
        try {
            if ("PERIODIC".equals(value.event())) {
                if (value.moduleCode() != null
                        || value.priority() != 0
                        || !value.exclusive()
                        || (value.conditions() != null && !value.conditions().isEmpty())
                        || value.startAt() == null
                        || value.intervalMinutes() == null) {
                    throw invalidTriggerBinding();
                }
                return TriggerBinding.periodic(new PeriodicSchedule(
                        Instant.parse(value.startAt()),
                        value.intervalMinutes(),
                        requesterMemberId
                ));
            }
            if (value.moduleCode() == null
                    || !value.moduleCode().matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                    || value.startAt() != null
                    || value.intervalMinutes() != null) {
                throw invalidTriggerBinding();
            }
            var conditions = value.conditions() == null
                    ? List.<TriggerCondition>of()
                    : value.conditions().stream().map(FlowHttpErrors::triggerCondition).toList();
            return new TriggerBinding(
                    value.moduleCode(),
                    TriggerBinding.Event.valueOf(value.event()),
                    value.priority(),
                    value.exclusive(),
                    conditions
            );
        } catch (RuntimeException exception) {
            throw invalidTriggerBinding();
        }
    }

    private static TriggerCondition triggerCondition(FlowRequests.TriggerCondition value) {
        if (value == null
                || value.fieldCode() == null
                || !value.fieldCode().matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")
                || value.operator() == null) {
            throw invalidTriggerBinding();
        }
        final TriggerCondition.Operator operator;
        try {
            operator = TriggerCondition.Operator.valueOf(value.operator());
        } catch (IllegalArgumentException exception) {
            throw invalidTriggerBinding();
        }
        var hasNonNullValue = value.value() != null && !value.value().isNull();
        var hasAnyValue = value.value() != null;
        if ((operator.requiresValue() && !hasNonNullValue)
                || (!operator.requiresValue() && hasAnyValue)) {
            throw invalidTriggerBinding();
        }
        return new TriggerCondition(
                value.fieldCode(),
                operator,
                hasNonNullValue ? value.value().toString() : null
        );
    }

    public static ApprovalGateway gateway(FlowRequests.Gateway value) {
        return gateway(value, source -> {
            throw invalidApprovers("Dynamic approver source requires directory resolution");
        });
    }

    public static ApprovalGateway gateway(
            FlowRequests.Gateway value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (value == null) {
            return null;
        }
        if (value.branches() == null) {
            throw invalidGateway();
        }
        try {
            return new ApprovalGateway(value.branches().stream()
                    .map(branch -> gatewayBranch(branch, dynamicMembers))
                    .toList());
        } catch (ApprovalDomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidGateway();
        }
    }

    private static ApprovalGateway.Branch gatewayBranch(
            FlowRequests.GatewayBranch value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (value == null || value.defaultBranch() == null) {
            throw invalidGateway();
        }
        var conditions = value.conditions() == null
                ? List.<TriggerCondition>of()
                : value.conditions().stream().map(FlowHttpErrors::triggerCondition).toList();
        var stages = approvalStages(value.approvalStages());
        var approverIds = branchApproverIds(
                value.approverIds(), value.approverSource(), stages, dynamicMembers);
        return new ApprovalGateway.Branch(
                value.code(),
                value.name(),
                value.defaultBranch(),
                conditions,
                approverIds,
                branchApprovalMode(value.approvalMode(), stages),
                stages
        );
    }

    public static ApprovalParallelGateway parallelGateway(
            FlowRequests.ParallelGateway value
    ) {
        return parallelGateway(value, source -> {
            throw invalidApprovers("Dynamic approver source requires directory resolution");
        });
    }

    public static ApprovalParallelGateway parallelGateway(
            FlowRequests.ParallelGateway value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (value == null) {
            return null;
        }
        if (value.branches() == null) {
            throw invalidGateway();
        }
        try {
            return new ApprovalParallelGateway(value.branches().stream()
                    .map(branch -> parallelBranch(branch, dynamicMembers))
                    .toList());
        } catch (ApprovalDomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidGateway();
        }
    }

    private static ApprovalParallelGateway.Branch parallelBranch(
            FlowRequests.ParallelBranch value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (value == null) {
            throw invalidGateway();
        }
        var stages = approvalStages(value.approvalStages());
        return new ApprovalParallelGateway.Branch(
                value.code(),
                value.name(),
                branchApproverIds(
                        value.approverIds(), value.approverSource(), stages,
                        dynamicMembers),
                branchApprovalMode(value.approvalMode(), stages),
                stages
        );
    }

    public static ApprovalInclusiveGateway inclusiveGateway(
            FlowRequests.InclusiveGateway value
    ) {
        return inclusiveGateway(value, source -> {
            throw invalidApprovers("Dynamic approver source requires directory resolution");
        });
    }

    public static ApprovalInclusiveGateway inclusiveGateway(
            FlowRequests.InclusiveGateway value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (value == null) {
            return null;
        }
        if (value.branches() == null) {
            throw invalidGateway();
        }
        try {
            return new ApprovalInclusiveGateway(value.branches().stream()
                    .map(branch -> inclusiveBranch(branch, dynamicMembers))
                    .toList());
        } catch (ApprovalDomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidGateway();
        }
    }

    private static ApprovalInclusiveGateway.Branch inclusiveBranch(
            FlowRequests.InclusiveBranch value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (value == null || value.defaultBranch() == null) {
            throw invalidGateway();
        }
        var conditions = value.conditions() == null
                ? List.<TriggerCondition>of()
                : value.conditions().stream().map(FlowHttpErrors::triggerCondition).toList();
        var stages = approvalStages(value.approvalStages());
        return new ApprovalInclusiveGateway.Branch(
                value.code(),
                value.name(),
                value.defaultBranch(),
                conditions,
                branchApproverIds(
                        value.approverIds(), value.approverSource(), stages,
                        dynamicMembers),
                branchApprovalMode(value.approvalMode(), stages),
                stages
        );
    }

    private static List<Long> branchApproverIds(
            List<String> legacyApproverIds,
            FlowRequests.ApproverSource legacySource,
            List<ApprovalStage> stages,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        if (stages == null) {
            return routeApproverIds(
                    null, legacyApproverIds, legacySource, dynamicMembers);
        }
        var first = stages.getFirst();
        if (legacyApproverIds != null
                && !approverIds(null, legacyApproverIds)
                        .equals(first.approverIds())) {
            throw invalidBranchStageMirror(
                    "Legacy branch approverIds must exactly mirror approvalStages[0]");
        }
        if (legacySource != null
                && !approverSource(legacySource).equals(first.approverSource())) {
            throw invalidBranchStageMirror(
                    "Legacy branch approverSource must exactly mirror approvalStages[0]");
        }
        return first.approverSource().dynamic()
                ? List.copyOf(dynamicMembers.apply(first.approverSource()))
                : first.approverIds();
    }

    private static ApprovalMode branchApprovalMode(
            String legacyMode,
            List<ApprovalStage> stages
    ) {
        if (stages == null) {
            return approvalMode(legacyMode);
        }
        var first = stages.getFirst();
        if (legacyMode != null
                && approvalMode(legacyMode) != first.approvalMode()) {
            throw invalidBranchStageMirror(
                    "Legacy branch approvalMode must exactly mirror approvalStages[0]");
        }
        return first.approvalMode();
    }

    public static ApprovalMode approvalMode(String value) {
        if (value == null || value.isBlank()) {
            return ApprovalMode.SEQUENTIAL;
        }
        try {
            return ApprovalMode.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "FLOW_APPROVAL_MODE_INVALID",
                    "Approval mode must be SEQUENTIAL, ANY, ALL or QUORUM",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    public static List<Long> routeApproverIds(
            String approverId,
            List<String> approverIds,
            FlowRequests.ApproverSource value,
            Function<ApprovalApproverSource, List<Long>> dynamicMembers
    ) {
        var source = approverSource(value);
        if (!source.dynamic()) {
            return approverIds(approverId, approverIds);
        }
        return List.copyOf(dynamicMembers.apply(source));
    }

    public static ApprovalApproverSource approverSource(FlowRequests.ApproverSource value) {
        if (value == null || value.kind() == null || value.kind().isBlank()) {
            return ApprovalApproverSource.fixed();
        }
        try {
            var kind = ApprovalApproverSource.Kind.valueOf(
                    value.kind().strip().toUpperCase(java.util.Locale.ROOT));
            if (kind.requiresSourceId()
                    && (value.sourceId() == null || value.sourceId().isBlank())) {
                throw new IllegalArgumentException(
                        kind + " approver source requires sourceId");
            }
            var sourceId = !kind.requiresSourceId()
                    ? null
                    : id(value.sourceId(), "approverSource.sourceId");
            if (!kind.requiresSourceId()
                    && value.sourceId() != null
                    && !value.sourceId().isBlank()) {
                throw new IllegalArgumentException(
                        kind + " approver source must not define sourceId");
            }
            var moduleCode = value.moduleCode();
            if (kind.requiresModuleCode()) {
                if (moduleCode == null || moduleCode.isBlank()) {
                    throw new IllegalArgumentException(
                            kind + " approver source requires moduleCode");
                }
                moduleCode = moduleCode.strip();
            } else if (moduleCode != null) {
                throw new IllegalArgumentException(
                        kind + " approver source must not define moduleCode");
            }
            return new ApprovalApproverSource(kind, sourceId, moduleCode);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_INVALID",
                    "Approver source must be FIXED, ROLE, DEPARTMENT, DEPARTMENT_LEADER, "
                            + "REQUESTER, REQUESTER_MANAGER, "
                            + "REQUESTER_DEPARTMENT_LEADER, RECORD_MEMBER_FIELD "
                            + "or PREVIOUS_HANDLER "
                            + "with the required "
                            + "sourceId/moduleCode shape",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    public static List<ApprovalStage> approvalStages(
            List<FlowRequests.ApprovalStage> values
    ) {
        if (values == null) {
            return null;
        }
        try {
            return values.stream().map(value -> {
                if (value == null) {
                    throw new IllegalArgumentException("Approval stage is required");
                }
                var source = approverSource(value.approverSource());
                var fixedMembers = source.kind() == ApprovalApproverSource.Kind.FIXED
                        ? approverIds(null, value.approverIds())
                        : value.approverIds() == null
                                ? List.<Long>of()
                                : value.approverIds().stream().map(memberId ->
                                        id(memberId, "approvalStages.approverIds")).toList();
                return new ApprovalStage(
                        value.code(),
                        value.name(),
                        fixedMembers,
                        approvalMode(value.approvalMode()),
                        source,
                        routeQuorumRule(value.approvalMode(), value.quorumRule()),
                        deadlinePolicy(value.deadlinePolicy()),
                        value.decisionCommentPolicy() == null
                                ? ApprovalDecisionCommentPolicy.defaults()
                                : decisionCommentPolicy(value.decisionCommentPolicy()),
                        decisionEvidencePolicy(value.decisionEvidencePolicy())
                );
            }).toList();
        } catch (BusinessException | ApprovalDomainException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidApprovers("Approval stages are invalid");
        }
    }

    public static ApprovalApproverSources approverSources(
            FlowRequests.ApproverSource route,
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        var branches = new LinkedHashMap<String, ApprovalApproverSource>();
        if (gateway != null && gateway.branches() != null) {
            gateway.branches().forEach(branch ->
                    branches.put(
                            branch.code(),
                            branchApproverSource(
                                    branch.approverSource(),
                                    branch.approvalStages())));
        } else if (parallelGateway != null && parallelGateway.branches() != null) {
            parallelGateway.branches().forEach(branch ->
                    branches.put(
                            branch.code(),
                            branchApproverSource(
                                    branch.approverSource(),
                                    branch.approvalStages())));
        } else if (inclusiveGateway != null && inclusiveGateway.branches() != null) {
            inclusiveGateway.branches().forEach(branch ->
                    branches.put(
                            branch.code(),
                            branchApproverSource(
                                    branch.approverSource(),
                                    branch.approvalStages())));
        }
        return new ApprovalApproverSources(approverSource(route), branches);
    }

    private static ApprovalApproverSource branchApproverSource(
            FlowRequests.ApproverSource legacySource,
            List<FlowRequests.ApprovalStage> stageRequests
    ) {
        var stages = approvalStages(stageRequests);
        if (stages == null) {
            return approverSource(legacySource);
        }
        var first = stages.getFirst();
        if (legacySource != null
                && !approverSource(legacySource).equals(first.approverSource())) {
            throw invalidBranchStageMirror(
                    "Legacy branch approverSource must exactly mirror approvalStages[0]");
        }
        return first.approverSource();
    }

    public static ApprovalQuorumRules quorumRules(
            String routeMode,
            FlowRequests.QuorumRule routeRule,
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        var primary = routeQuorumRule(routeMode, routeRule);
        var branches = new LinkedHashMap<String, ApprovalQuorumRule>();
        if (gateway != null && gateway.branches() != null) {
            gateway.branches().forEach(branch ->
                    putQuorumRule(
                            branches,
                            branch.code(),
                            branch.approvalMode(),
                            branch.quorumRule(),
                            branch.approvalStages()
                    ));
        } else if (parallelGateway != null && parallelGateway.branches() != null) {
            parallelGateway.branches().forEach(branch ->
                    putQuorumRule(
                            branches,
                            branch.code(),
                            branch.approvalMode(),
                            branch.quorumRule(),
                            branch.approvalStages()
                    ));
        } else if (inclusiveGateway != null && inclusiveGateway.branches() != null) {
            inclusiveGateway.branches().forEach(branch ->
                    putQuorumRule(
                            branches,
                            branch.code(),
                            branch.approvalMode(),
                            branch.quorumRule(),
                            branch.approvalStages()
                    ));
        }
        return new ApprovalQuorumRules(primary, branches);
    }

    private static void putQuorumRule(
            Map<String, ApprovalQuorumRule> target,
            String code,
            String mode,
            FlowRequests.QuorumRule value,
            List<FlowRequests.ApprovalStage> stageRequests
    ) {
        var stages = approvalStages(stageRequests);
        var rule = stages == null
                ? routeQuorumRule(mode, value)
                : stages.getFirst().quorumRule();
        if (stages != null && (mode != null || value != null)
                && !Objects.equals(routeQuorumRule(
                        mode == null
                                ? stages.getFirst().approvalMode().name()
                                : mode,
                        value), rule)) {
            throw invalidBranchStageMirror(
                    "Legacy branch quorum fields must exactly mirror approvalStages[0]");
        }
        if (rule != null) {
            target.put(code, rule);
        }
    }

    private static ApprovalQuorumRule routeQuorumRule(
            String modeValue,
            FlowRequests.QuorumRule value
    ) {
        var mode = approvalMode(modeValue);
        if ((mode == ApprovalMode.QUORUM) != (value != null)) {
            throw invalidQuorumRule();
        }
        if (value == null) {
            return null;
        }
        try {
            if (value.type() == null || value.value() == null) {
                throw new IllegalArgumentException("Quorum rule fields are required");
            }
            return new ApprovalQuorumRule(
                    ApprovalQuorumRule.Type.valueOf(
                            value.type().strip().toUpperCase(java.util.Locale.ROOT)
                    ),
                    value.value()
            );
        } catch (RuntimeException exception) {
            throw invalidQuorumRule();
        }
    }

    private static BusinessException invalidQuorumRule() {
        return new BusinessException(
                "FLOW_QUORUM_RULE_INVALID",
                "QUORUM requires COUNT > 0 or PERCENTAGE within 1..100; other modes must omit the rule",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    public static ApprovalDeadlinePolicies deadlinePolicies(
            FlowRequests.DeadlinePolicy routePolicy,
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        var branches = new LinkedHashMap<String, ApprovalDeadlinePolicy>();
        if (gateway != null && gateway.branches() != null) {
            gateway.branches().forEach(branch ->
                    putDeadlinePolicy(
                            branches, branch.code(), branch.deadlinePolicy(),
                            branch.approvalStages()));
        } else if (parallelGateway != null && parallelGateway.branches() != null) {
            parallelGateway.branches().forEach(branch ->
                    putDeadlinePolicy(
                            branches, branch.code(), branch.deadlinePolicy(),
                            branch.approvalStages()));
        } else if (inclusiveGateway != null && inclusiveGateway.branches() != null) {
            inclusiveGateway.branches().forEach(branch ->
                    putDeadlinePolicy(
                            branches, branch.code(), branch.deadlinePolicy(),
                            branch.approvalStages()));
        }
        try {
            return new ApprovalDeadlinePolicies(deadlinePolicy(routePolicy), branches);
        } catch (RuntimeException exception) {
            throw invalidDeadlinePolicy();
        }
    }

    private static void putDeadlinePolicy(
            Map<String, ApprovalDeadlinePolicy> target,
            String code,
            FlowRequests.DeadlinePolicy value,
            List<FlowRequests.ApprovalStage> stageRequests
    ) {
        var stages = approvalStages(stageRequests);
        var policy = stages == null
                ? deadlinePolicy(value)
                : stages.getFirst().deadlinePolicy();
        if (stages != null
                && value != null
                && !Objects.equals(deadlinePolicy(value), policy)) {
            throw invalidBranchStageMirror(
                    "Legacy branch deadlinePolicy must exactly mirror approvalStages[0]");
        }
        if (policy != null) {
            target.put(code, policy);
        }
    }

    private static ApprovalDeadlinePolicy deadlinePolicy(
            FlowRequests.DeadlinePolicy value
    ) {
        if (value == null) {
            return null;
        }
        try {
            if (value.timeoutMinutes() == null || value.timeoutAction() == null) {
                throw new IllegalArgumentException("Deadline policy fields are required");
            }
            return new ApprovalDeadlinePolicy(
                    value.timeoutMinutes(),
                    value.remindBeforeMinutes(),
                    ApprovalDeadlinePolicy.TimeoutAction.valueOf(
                            value.timeoutAction().strip().toUpperCase(java.util.Locale.ROOT)
                    )
            );
        } catch (RuntimeException exception) {
            throw invalidDeadlinePolicy();
        }
    }

    private static BusinessException invalidDeadlinePolicy() {
        return new BusinessException(
                "FLOW_DEADLINE_POLICY_INVALID",
                "Deadline timeout must be 1..525600 minutes; reminder must be earlier; action must be NONE, AUTO_APPROVE or AUTO_REJECT",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    public static ApprovalDecisionCommentPolicies decisionCommentPolicies(
            FlowRequests.DecisionCommentPolicy routePolicy,
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        var branches = new LinkedHashMap<String, ApprovalDecisionCommentPolicy>();
        if (gateway != null && gateway.branches() != null) {
            gateway.branches().forEach(branch ->
                    putDecisionCommentPolicy(
                            branches, branch.code(), branch.decisionCommentPolicy(),
                            branch.approvalStages()));
        } else if (parallelGateway != null && parallelGateway.branches() != null) {
            parallelGateway.branches().forEach(branch ->
                    putDecisionCommentPolicy(
                            branches, branch.code(), branch.decisionCommentPolicy(),
                            branch.approvalStages()));
        } else if (inclusiveGateway != null && inclusiveGateway.branches() != null) {
            inclusiveGateway.branches().forEach(branch ->
                    putDecisionCommentPolicy(
                            branches, branch.code(), branch.decisionCommentPolicy(),
                            branch.approvalStages()));
        }
        try {
            var primaryStages = primaryBranchStages(
                    gateway, parallelGateway, inclusiveGateway);
            var primaryPolicy = routePolicy == null && primaryStages != null
                    ? approvalStages(primaryStages).getFirst()
                            .decisionCommentPolicy()
                    : decisionCommentPolicy(routePolicy);
            var result = new ApprovalDecisionCommentPolicies(
                    primaryPolicy,
                    branches
            );
            var mirroredCode = mirroredDecisionCommentBranch(
                    gateway, parallelGateway, inclusiveGateway);
            if (mirroredCode != null
                    && !Objects.equals(result.primary(), result.branch(mirroredCode))) {
                throw new IllegalArgumentException(
                        "Route decision comment policy must mirror the primary branch");
            }
            return result;
        } catch (RuntimeException exception) {
            throw invalidDecisionCommentPolicy();
        }
    }

    private static List<FlowRequests.ApprovalStage> primaryBranchStages(
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        if (gateway != null && gateway.branches() != null) {
            for (var branch : gateway.branches()) {
                if (Boolean.TRUE.equals(branch.defaultBranch())) {
                    return branch.approvalStages();
                }
            }
            return null;
        }
        if (parallelGateway != null
                && parallelGateway.branches() != null
                && !parallelGateway.branches().isEmpty()) {
            return parallelGateway.branches().getFirst().approvalStages();
        }
        if (inclusiveGateway != null
                && inclusiveGateway.branches() != null
                && !inclusiveGateway.branches().isEmpty()) {
            return inclusiveGateway.branches().getFirst().approvalStages();
        }
        return null;
    }

    private static String mirroredDecisionCommentBranch(
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        if (gateway != null && gateway.branches() != null) {
            return gateway.branches().stream()
                    .filter(branch -> Boolean.TRUE.equals(branch.defaultBranch()))
                    .map(FlowRequests.GatewayBranch::code)
                    .findFirst()
                    .orElse(null);
        }
        if (parallelGateway != null
                && parallelGateway.branches() != null
                && !parallelGateway.branches().isEmpty()) {
            return parallelGateway.branches().getFirst().code();
        }
        if (inclusiveGateway != null
                && inclusiveGateway.branches() != null
                && !inclusiveGateway.branches().isEmpty()) {
            return inclusiveGateway.branches().getFirst().code();
        }
        return null;
    }

    private static void putDecisionCommentPolicy(
            Map<String, ApprovalDecisionCommentPolicy> target,
            String code,
            FlowRequests.DecisionCommentPolicy value,
            List<FlowRequests.ApprovalStage> stageRequests
    ) {
        var stages = approvalStages(stageRequests);
        var policy = stages == null
                ? decisionCommentPolicy(value)
                : stages.getFirst().decisionCommentPolicy();
        if (stages != null
                && value != null
                && !decisionCommentPolicy(value).equals(policy)) {
            throw invalidBranchStageMirror(
                    "Legacy branch decisionCommentPolicy must exactly mirror "
                            + "approvalStages[0]");
        }
        if (policy != null) {
            target.put(code, policy);
        }
    }

    private static BusinessException invalidBranchStageMirror(String message) {
        return new BusinessException(
                "FLOW_APPROVAL_STAGE_MIRROR_INVALID",
                message,
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static ApprovalDecisionCommentPolicy decisionCommentPolicy(
            FlowRequests.DecisionCommentPolicy value
    ) {
        if (value == null) {
            return null;
        }
        try {
            if (value.approveRequired() == null
                    || value.rejectRequired() == null
                    || value.minimumLength() == null) {
                throw new IllegalArgumentException(
                        "Decision comment policy fields are required");
            }
            return new ApprovalDecisionCommentPolicy(
                    value.approveRequired(),
                    value.rejectRequired(),
                    value.minimumLength()
            );
        } catch (RuntimeException exception) {
            throw invalidDecisionCommentPolicy();
        }
    }

    public static ApprovalDecisionEvidencePolicy decisionEvidencePolicy(
            FlowRequests.DecisionEvidencePolicy value
    ) {
        if (value == null) {
            return null;
        }
        try {
            if (value.minimumAttachments() == null
                    || value.maximumAttachments() == null
                    || value.signatureMode() == null
                    || value.signatureMode().isBlank()) {
                throw new IllegalArgumentException(
                        "Decision evidence policy fields are required");
            }
            Set<ApprovalDecisionEvidencePolicy.MimeFamily> families =
                    value.allowedMimeFamilies() == null
                            ? null
                            : value.allowedMimeFamilies().stream()
                                    .map(family ->
                                            ApprovalDecisionEvidencePolicy
                                                    .MimeFamily.valueOf(
                                                            family.strip()
                                                                    .toUpperCase(
                                                                            java.util.Locale
                                                                                    .ROOT)))
                                    .collect(java.util.stream.Collectors
                                            .toUnmodifiableSet());
            return new ApprovalDecisionEvidencePolicy(
                    value.minimumAttachments(),
                    value.maximumAttachments(),
                    families,
                    ApprovalDecisionEvidencePolicy.SignatureMode.valueOf(
                            value.signatureMode().strip().toUpperCase(
                                    java.util.Locale.ROOT))
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    "FLOW_EVIDENCE_POLICY_INVALID",
                    "Decision evidence policy requires attachment bounds 0..5, "
                            + "optional MIME families IMAGE/PDF/DOCUMENT/ARCHIVE/OTHER "
                            + "and signatureMode NONE/OPTIONAL/REQUIRED",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    public static ApprovalDecisionEvidencePolicies decisionEvidencePolicies(
            FlowRequests.DecisionEvidencePolicy routePolicy,
            FlowRequests.Gateway gateway,
            FlowRequests.ParallelGateway parallelGateway,
            FlowRequests.InclusiveGateway inclusiveGateway
    ) {
        var branches = new LinkedHashMap<String, ApprovalDecisionEvidencePolicy>();
        if (gateway != null && gateway.branches() != null) {
            gateway.branches().forEach(branch ->
                    putDecisionEvidencePolicy(
                            branches, branch.code(),
                            branch.decisionEvidencePolicy(),
                            branch.approvalStages()));
        } else if (parallelGateway != null
                && parallelGateway.branches() != null) {
            parallelGateway.branches().forEach(branch ->
                    putDecisionEvidencePolicy(
                            branches, branch.code(),
                            branch.decisionEvidencePolicy(),
                            branch.approvalStages()));
        } else if (inclusiveGateway != null
                && inclusiveGateway.branches() != null) {
            inclusiveGateway.branches().forEach(branch ->
                    putDecisionEvidencePolicy(
                            branches, branch.code(),
                            branch.decisionEvidencePolicy(),
                            branch.approvalStages()));
        }
        try {
            var primaryStages = primaryBranchStages(
                    gateway, parallelGateway, inclusiveGateway);
            var primaryPolicy = routePolicy == null && primaryStages != null
                    ? approvalStages(primaryStages).getFirst()
                            .decisionEvidencePolicy()
                    : decisionEvidencePolicy(routePolicy);
            var result = new ApprovalDecisionEvidencePolicies(
                    primaryPolicy, branches);
            var mirroredCode = mirroredDecisionCommentBranch(
                    gateway, parallelGateway, inclusiveGateway);
            if (mirroredCode != null
                    && !Objects.equals(
                            result.primary(), result.branch(mirroredCode))) {
                throw new IllegalArgumentException(
                        "Route decision evidence policy must mirror "
                                + "the primary branch");
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidDecisionEvidencePolicy();
        }
    }

    private static void putDecisionEvidencePolicy(
            Map<String, ApprovalDecisionEvidencePolicy> target,
            String code,
            FlowRequests.DecisionEvidencePolicy value,
            List<FlowRequests.ApprovalStage> stageRequests
    ) {
        var stages = approvalStages(stageRequests);
        var policy = stages == null
                ? decisionEvidencePolicy(value)
                : stages.getFirst().decisionEvidencePolicy();
        if (stages != null
                && value != null
                && !decisionEvidencePolicy(value).equals(policy)) {
            throw invalidBranchStageMirror(
                    "Legacy branch decisionEvidencePolicy must exactly mirror "
                            + "approvalStages[0]");
        }
        if (policy != null) {
            target.put(code, policy);
        }
    }

    private static BusinessException invalidDecisionEvidencePolicy() {
        return new BusinessException(
                "FLOW_EVIDENCE_POLICY_INVALID",
                "Decision evidence policy requires attachment bounds 0..5, "
                        + "optional MIME families "
                        + "IMAGE/PDF/DOCUMENT/ARCHIVE/OTHER and signatureMode "
                        + "NONE/OPTIONAL/REQUIRED",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static BusinessException invalidDecisionCommentPolicy() {
        return new BusinessException(
                "FLOW_DECISION_COMMENT_POLICY_INVALID",
                "Decision comment policy requires approveRequired, rejectRequired and minimumLength within 1..500",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    public static RecordStatusMapping recordStatusMapping(
            FlowRequests.RecordStatusMapping value
    ) {
        if (value == null) {
            return null;
        }
        try {
            return new RecordStatusMapping(
                    value.fieldCode(),
                    value.approvedValue(),
                    value.rejectedValue(),
                    value.withdrawnValue(),
                    value.terminatedValue()
            );
        } catch (RuntimeException exception) {
            throw invalidRecordStatusMapping();
        }
    }

    public static DefinitionConfiguration definitionConfiguration(
            FlowRequests.TriggerBinding triggerValue,
            FlowRequests.RecordStatusMapping mappingValue
    ) {
        return definitionConfiguration(triggerValue, mappingValue, 1L);
    }

    public static DefinitionConfiguration definitionConfiguration(
            FlowRequests.TriggerBinding triggerValue,
            FlowRequests.RecordStatusMapping mappingValue,
            long requesterMemberId
    ) {
        var triggerBinding = triggerBinding(triggerValue, requesterMemberId);
        var recordStatusMapping = recordStatusMapping(mappingValue);
        if (recordStatusMapping != null
                && triggerBinding != null
                && triggerBinding.event() != TriggerBinding.Event.RECORD_ACTIVATED) {
            throw invalidRecordStatusMapping();
        }
        return new DefinitionConfiguration(triggerBinding, recordStatusMapping);
    }

    private static BusinessException translate(ApprovalDomainException exception) {
        return switch (exception.code()) {
            case DRAFT_NOT_FOUND, VERSION_NOT_FOUND, INSTANCE_NOT_FOUND,
                    COMPLETION_EXECUTION_NOT_FOUND ->
                    new BusinessException("FLOW_" + exception.code(), exception.getMessage(), HttpStatus.NOT_FOUND);
            case APPROVER_FORBIDDEN ->
                    new BusinessException("FLOW_APPROVER_FORBIDDEN", exception.getMessage(), HttpStatus.FORBIDDEN);
            case REQUESTER_FORBIDDEN ->
                    new BusinessException("FLOW_REQUESTER_FORBIDDEN", exception.getMessage(), HttpStatus.FORBIDDEN);
            case APPROVER_SEQUENCE_INVALID ->
                    new BusinessException(
                            "FLOW_APPROVER_SEQUENCE_INVALID",
                            exception.getMessage(),
                            HttpStatus.BAD_REQUEST
                    );
            case GATEWAY_INVALID ->
                    new BusinessException(
                            "FLOW_GATEWAY_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case APPROVAL_STAGES_GATEWAY_UNSUPPORTED ->
                    new BusinessException(
                            "FLOW_APPROVAL_STAGES_GATEWAY_UNSUPPORTED",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case REJECTION_REASON_REQUIRED ->
                    new BusinessException("FLOW_REJECTION_REASON_REQUIRED", exception.getMessage(), HttpStatus.BAD_REQUEST);
            case APPROVAL_COMMENT_REQUIRED ->
                    new BusinessException(
                            "FLOW_APPROVAL_COMMENT_REQUIRED",
                            exception.getMessage(),
                            HttpStatus.BAD_REQUEST
                    );
            case EVIDENCE_INVALID ->
                    new BusinessException(
                            "FLOW_EVIDENCE_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case EVIDENCE_NOT_FOUND ->
                    new BusinessException(
                            "FLOW_EVIDENCE_NOT_FOUND",
                            exception.getMessage(),
                            HttpStatus.NOT_FOUND
                    );
            case COMMENT_TEMPLATE_INVALID ->
                    new BusinessException(
                            "FLOW_COMMENT_TEMPLATE_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case COMMENT_TEMPLATE_NOT_FOUND ->
                    new BusinessException(
                            "FLOW_COMMENT_TEMPLATE_NOT_FOUND",
                            exception.getMessage(),
                            HttpStatus.NOT_FOUND
                    );
            case DELEGATION_RULE_INVALID ->
                    new BusinessException(
                            "FLOW_DELEGATION_RULE_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case DELEGATION_FORBIDDEN ->
                    new BusinessException(
                            "FLOW_DELEGATION_FORBIDDEN",
                            exception.getMessage(),
                            HttpStatus.FORBIDDEN
                    );
            case DELEGATION_CONFLICT ->
                    new BusinessException(
                            "FLOW_DELEGATION_CONFLICT",
                            exception.getMessage(),
                            HttpStatus.CONFLICT
                    );
            case DELEGATION_NOT_FOUND ->
                    new BusinessException(
                            "FLOW_DELEGATION_NOT_FOUND",
                            exception.getMessage(),
                            HttpStatus.NOT_FOUND
                    );
            case DELEGATION_INACTIVE ->
                    new BusinessException(
                            "FLOW_DELEGATION_INACTIVE",
                            exception.getMessage(),
                            HttpStatus.FORBIDDEN
                    );
            case WITHDRAW_REASON_REQUIRED ->
                    new BusinessException(
                            "FLOW_WITHDRAW_REASON_REQUIRED",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case TERMINATE_REASON_REQUIRED ->
                    new BusinessException(
                            "FLOW_TERMINATE_REASON_REQUIRED",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case URGE_MESSAGE_INVALID ->
                    new BusinessException(
                            "FLOW_URGE_MESSAGE_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case COMMENT_BODY_REQUIRED ->
                    new BusinessException(
                            "FLOW_COMMENT_BODY_REQUIRED",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case ASSIGNMENT_REQUEST_INVALID ->
                    new BusinessException(
                            "FLOW_ASSIGNMENT_REQUEST_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case RETURN_REQUEST_INVALID ->
                    new BusinessException(
                            "FLOW_RETURN_REQUEST_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case CANCEL_CLAIM_REQUEST_INVALID ->
                    new BusinessException(
                            "FLOW_CANCEL_CLAIM_REQUEST_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case CLAIM_REQUEST_INVALID ->
                    new BusinessException(
                            "FLOW_CLAIM_REQUEST_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case REDUCE_SIGN_REQUEST_INVALID ->
                    new BusinessException(
                            "FLOW_REDUCE_SIGN_REQUEST_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case COPY_TARGET_INVALID ->
                    new BusinessException(
                            "FLOW_COPY_TARGET_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case COPY_MESSAGE_INVALID ->
                    new BusinessException(
                            "FLOW_COPY_MESSAGE_INVALID",
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case COPY_ALREADY_EXISTS ->
                    new BusinessException(
                            "FLOW_COPY_ALREADY_EXISTS",
                            exception.getMessage(),
                            HttpStatus.CONFLICT
                    );
            case COMPLETION_STEP_INVALID, COMPLETION_EXECUTION_INVALID ->
                    new BusinessException(
                            "FLOW_" + exception.code(),
                            exception.getMessage(),
                            HttpStatus.UNPROCESSABLE_ENTITY
                    );
            case COMPLETION_LEASE_INVALID, COMPLETION_STATE_CONFLICT ->
                    new BusinessException(
                            "FLOW_" + exception.code(),
                            exception.getMessage(),
                            HttpStatus.CONFLICT
                    );
            case SUBFLOW_TARGET_INVALID, SUBFLOW_CYCLE,
                    SUBFLOW_DEPTH_EXCEEDED ->
                    new BusinessException(
                            "FLOW_" + exception.code(),
                            exception.getMessage(),
                            HttpStatus.BAD_REQUEST
                    );
            case SUBFLOW_RUN_CONFLICT ->
                    new BusinessException(
                            "FLOW_SUBFLOW_RUN_CONFLICT",
                            exception.getMessage(),
                            HttpStatus.CONFLICT
                    );
            case INSTANCE_STATE_INVALID ->
                    new BusinessException("FLOW_INSTANCE_STATE_INVALID", exception.getMessage(), HttpStatus.CONFLICT);
            case VERSION_ALREADY_EXISTS, PERSISTENCE_CONFLICT ->
                    new BusinessException("FLOW_CONFLICT", exception.getMessage(), HttpStatus.CONFLICT);
        };
    }

    private static ApprovalDomainException invalidApprovers(String message) {
        return new ApprovalDomainException(APPROVER_SEQUENCE_INVALID, message);
    }

    private static BusinessException invalidTriggerBinding() {
        return new BusinessException(
                "FLOW_TRIGGER_BINDING_INVALID",
                "triggerBinding and its all-of conditions are invalid",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static BusinessException invalidRecordStatusMapping() {
        return new BusinessException(
                "FLOW_RECORD_STATUS_MAPPING_INVALID",
                "recordStatusMapping must contain a canonical STATUS field code and four positive option ids, "
                        + "and automatic mappings are supported only for RECORD_ACTIVATED",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    private static BusinessException invalidGateway() {
        return new BusinessException(
                "FLOW_GATEWAY_INVALID",
                "gateway must contain 1 to 5 ordered conditional branches and one final default branch",
                HttpStatus.UNPROCESSABLE_ENTITY
        );
    }

    public record DefinitionConfiguration(
            TriggerBinding triggerBinding,
            RecordStatusMapping recordStatusMapping
    ) {
    }
}
