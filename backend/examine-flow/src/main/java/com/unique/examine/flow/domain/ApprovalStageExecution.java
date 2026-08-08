package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Durable immutable-or-monotonic execution snapshot for one ordered stage.
 */
public record ApprovalStageExecution(
        int stageIndex,
        String code,
        String name,
        Status status,
        List<Long> approverIds,
        ApprovalMode approvalMode,
        int requiredApprovals,
        List<Long> actualHandlerIds,
        Instant startedAt,
        Instant completedAt,
        ApprovalDeadlineState deadline,
        ApprovalDecisionCommentPolicy decisionCommentPolicy,
        Map<Long, Long> handlerActorsByParticipantId,
        ApprovalDecisionEvidencePolicy decisionEvidencePolicy
) {
    public ApprovalStageExecution(
            int stageIndex,
            String code,
            String name,
            Status status,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            List<Long> actualHandlerIds,
            Instant startedAt,
            Instant completedAt,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy,
            Map<Long, Long> handlerActorsByParticipantId
    ) {
        this(
                stageIndex, code, name, status, approverIds, approvalMode,
                requiredApprovals, actualHandlerIds, startedAt, completedAt,
                deadline, decisionCommentPolicy, handlerActorsByParticipantId,
                null
        );
    }

    public ApprovalStageExecution(
            int stageIndex,
            String code,
            String name,
            Status status,
            List<Long> approverIds,
            ApprovalMode approvalMode,
            int requiredApprovals,
            List<Long> actualHandlerIds,
            Instant startedAt,
            Instant completedAt,
            ApprovalDeadlineState deadline,
            ApprovalDecisionCommentPolicy decisionCommentPolicy
    ) {
        this(
                stageIndex, code, name, status, approverIds, approvalMode,
                requiredApprovals, actualHandlerIds, startedAt, completedAt,
                deadline, decisionCommentPolicy, Map.of(), null
        );
    }

    public ApprovalStageExecution {
        if (stageIndex < 0) {
            throw new IllegalArgumentException("Approval stage index must not be negative");
        }
        if (code == null || !code.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Approval stage code is invalid");
        }
        name = ApprovalDefinitionDraft.requireText(name, "Approval stage name");
        Objects.requireNonNull(status, "status");
        approverIds = approverIds == null ? List.of() : List.copyOf(approverIds);
        actualHandlerIds = actualHandlerIds == null
                ? List.of()
                : actualHandlerIds.stream().distinct().toList();
        approvalMode = approvalMode == null ? ApprovalMode.SEQUENTIAL : approvalMode;
        decisionCommentPolicy = decisionCommentPolicy == null
                ? ApprovalDecisionCommentPolicy.defaults()
                : decisionCommentPolicy;
        handlerActorsByParticipantId = handlerActorsByParticipantId == null
                ? Map.of()
                : Map.copyOf(handlerActorsByParticipantId);
        var participantSnapshot = approverIds;
        if (handlerActorsByParticipantId.entrySet().stream().anyMatch(entry ->
                entry.getKey() == null || entry.getKey() <= 0
                        || entry.getValue() == null || entry.getValue() <= 0
                        || !participantSnapshot.contains(entry.getKey()))) {
            throw new IllegalArgumentException(
                    "Approval stage handler-slot snapshot is invalid");
        }
        var orderedHandlers = orderedHandlers(
                approverIds, handlerActorsByParticipantId);
        if (!handlerActorsByParticipantId.isEmpty()
                && !actualHandlerIds.equals(orderedHandlers)) {
            throw new IllegalArgumentException(
                    "Actual handlers must mirror final participant slot order");
        }
        if (status == Status.WAITING) {
            if (startedAt != null || completedAt != null
                    || !actualHandlerIds.isEmpty() || deadline != null) {
                throw new IllegalArgumentException(
                        "Waiting approval stage contains active execution state");
            }
            if (!approverIds.isEmpty()) {
                ApprovalDefinitionDraft.requireApprovers(approverIds);
                ApprovalBranchExecution.requireRuntimeThreshold(
                        approvalMode, requiredApprovals, approverIds.size());
            } else if (requiredApprovals != 0) {
                throw new IllegalArgumentException(
                        "Unresolved waiting stage cannot require approvals");
            }
        } else {
            approverIds = ApprovalDefinitionDraft.requireApprovers(approverIds);
            ApprovalDefinitionDraft.requireApprovalModeApprovers(
                    approvalMode, approverIds);
            ApprovalBranchExecution.requireRuntimeThreshold(
                    approvalMode, requiredApprovals, approverIds.size());
            Objects.requireNonNull(startedAt, "startedAt");
            if (status == Status.ACTIVE) {
                if (completedAt != null) {
                    throw new IllegalArgumentException(
                            "Active approval stage cannot be completed");
                }
            } else {
                Objects.requireNonNull(completedAt, "completedAt");
                if (completedAt.isBefore(startedAt)) {
                    throw new IllegalArgumentException(
                            "Approval stage completion precedes activation");
                }
            }
        }
        if (actualHandlerIds.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException(
                    "Actual approval stage handlers must be positive");
        }
    }

    public static ApprovalStageExecution waiting(int index, ApprovalStage stage) {
        var members = stage.approverSource().kind()
                == ApprovalApproverSource.Kind.FIXED
                ? stage.approverIds()
                : List.<Long>of();
        var required = members.isEmpty() ? 0 : stage.requiredApprovals(members.size());
        return new ApprovalStageExecution(
                index, stage.code(), stage.name(), Status.WAITING,
                members, stage.approvalMode(), required, List.of(),
                null, null, null, stage.decisionCommentPolicy(), Map.of(),
                stage.decisionEvidencePolicy());

    }

    public static ApprovalStageExecution active(
            int index,
            ApprovalStage stage,
            List<Long> members,
            Instant startedAt
    ) {
        return new ApprovalStageExecution(
                index, stage.code(), stage.name(), Status.ACTIVE,
                members, stage.approvalMode(), stage.requiredApprovals(members.size()),
                List.of(), startedAt, null,
                ApprovalDeadlineState.start(stage.deadlinePolicy(), startedAt),
                stage.decisionCommentPolicy(), Map.of(),
                stage.decisionEvidencePolicy());
    }

    public ApprovalStageExecution complete(
            Status terminalStatus,
            List<Long> handlers,
            Instant completedAt
    ) {
        if (status != Status.ACTIVE
                || (terminalStatus != Status.APPROVED
                && terminalStatus != Status.REJECTED)) {
            throw new IllegalStateException("Approval stage cannot be completed");
        }
        return new ApprovalStageExecution(
                stageIndex, code, name, terminalStatus, approverIds,
                approvalMode, requiredApprovals,
                handlers,
                startedAt, completedAt, deadline, decisionCommentPolicy,
                handlerActorsByParticipantId, decisionEvidencePolicy);
    }

    public ApprovalStageExecution recordApproval(
            long representedParticipantId,
            long actorId
    ) {
        if (status != Status.ACTIVE
                || representedParticipantId <= 0
                || actorId <= 0
                || !approverIds.contains(representedParticipantId)) {
            throw new IllegalStateException(
                    "Approval stage cannot record this handler");
        }
        var actors = new LinkedHashMap<>(handlerActorsByParticipantId);
        actors.put(representedParticipantId, actorId);
        return new ApprovalStageExecution(
                stageIndex, code, name, status, approverIds, approvalMode,
                requiredApprovals, orderedHandlers(approverIds, actors),
                startedAt, completedAt, deadline, decisionCommentPolicy, actors,
                decisionEvidencePolicy);
    }

    public ApprovalStageExecution withParticipants(List<Long> participants) {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException(
                    "Only the active approval stage can change participants");
        }
        if (approvalMode != ApprovalMode.SEQUENTIAL) {
            throw new IllegalStateException(
                    "Only a sequential approval stage can change participants");
        }
        participants = ApprovalDefinitionDraft.requireApprovers(participants);
        var actors = new LinkedHashMap<Long, Long>();
        for (var participant : participants) {
            var actor = handlerActorsByParticipantId.get(participant);
            if (actor != null) {
                actors.put(participant, actor);
            }
        }
        return new ApprovalStageExecution(
                stageIndex, code, name, status, participants, approvalMode,
                participants.size(),
                orderedHandlers(participants, actors), startedAt, null,
                deadline, decisionCommentPolicy, actors, decisionEvidencePolicy);
    }

    public ApprovalStageExecution removeHandlersFrom(
            List<Long> finalParticipants,
            int fromIndex
    ) {
        if (status != Status.ACTIVE
                || fromIndex < 0
                || fromIndex >= finalParticipants.size()) {
            throw new IllegalStateException(
                    "Approval stage handler rollback is invalid");
        }
        var actors = new LinkedHashMap<>(handlerActorsByParticipantId);
        for (var index = fromIndex; index < finalParticipants.size(); index++) {
            actors.remove(finalParticipants.get(index));
        }
        return new ApprovalStageExecution(
                stageIndex, code, name, status, finalParticipants, approvalMode,
                requiredApprovals, orderedHandlers(finalParticipants, actors),
                startedAt, null, deadline, decisionCommentPolicy, actors,
                decisionEvidencePolicy);
    }

    private static List<Long> orderedHandlers(
            List<Long> participants,
            Map<Long, Long> actors
    ) {
        return participants.stream()
                .map(actors::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public enum Status {
        WAITING,
        ACTIVE,
        APPROVED,
        REJECTED
    }
}
