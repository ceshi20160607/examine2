package com.unique.examine.ai.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Confirmation-gated proposal for one Work task or personal report draft. */
public record AiWorkProposal(
        long id,
        long accountId,
        long systemId,
        long tenantId,
        long memberId,
        long sessionId,
        long turnId,
        long policyVersionId,
        long providerId,
        long providerVersion,
        long authorizationEpoch,
        String promptVersion,
        Operation operation,
        String planHash,
        State state,
        long revision,
        Preview preview,
        double confidence,
        String clarification,
        SealedCommand sealedCommand,
        Instant expiresAt,
        Long actedBy,
        Result result,
        String resultCode,
        String requestId,
        String traceId,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {
    public static final String REDACTED_TITLE = "[title:redacted]";
    public static final String REDACTED_DESCRIPTION = "[description:redacted]";
    public static final String REDACTED_NARRATIVE = "[narrative:redacted]";
    public static final String REDACTED_NAME = "[name:redacted]";
    public static final String REDACTED_CLARIFICATION = "[clarification:redacted]";

    public AiWorkProposal {
        positive(id, "id"); positive(accountId, "accountId");
        positive(systemId, "systemId"); positive(tenantId, "tenantId");
        positive(memberId, "memberId"); positive(sessionId, "sessionId");
        positive(turnId, "turnId"); positive(policyVersionId, "policyVersionId");
        positive(providerId, "providerId");
        if (providerVersion < 0 || authorizationEpoch <= 0 || revision < 0) {
            invalid("snapshot");
        }
        promptVersion = token(promptVersion, "promptVersion", 64);
        operation = Objects.requireNonNull(operation, "operation");
        hash(planHash, "planHash");
        state = Objects.requireNonNull(state, "state");
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            invalid("confidence");
        }
        if (clarification != null) {
            clarification = text(clarification, "clarification", 500);
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || updatedAt.isBefore(createdAt)) {
            invalid("timestamps");
        }
        if (actedBy != null && actedBy <= 0) invalid("actedBy");
        resultCode = token(resultCode, "resultCode", 64);
        requestId = token(requestId, "requestId", 64);
        traceId = token(traceId, "traceId", 64);

        if (state == State.CLARIFICATION_REQUIRED) {
            if (preview != null || sealedCommand != null || clarification == null
                    || actedBy != null || result != null || finishedAt != null) {
                invalid("clarification state");
            }
        } else {
            preview = Objects.requireNonNull(preview, "preview");
            if (preview.operation() != operation) invalid("preview operation");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            if (clarification != null) invalid("actionable clarification");
        }
        if (state == State.PENDING) {
            if (actedBy != null || result != null || finishedAt != null) {
                invalid("pending state");
            }
        } else if (state == State.EXECUTING) {
            positive(Objects.requireNonNull(actedBy, "actedBy"), "actedBy");
            if (result != null || finishedAt != null) invalid("executing state");
        } else if (state != State.CLARIFICATION_REQUIRED && finishedAt == null) {
            invalid("terminal state");
        }
        if (state == State.SUCCEEDED
                && (result == null || result.operation() != operation)) {
            invalid("success result");
        }
        if (state != State.SUCCEEDED && result != null) {
            invalid("non-success result");
        }
    }

    public enum Operation { WORK_TASK_DRAFT, WORK_DAILY_REPORT_DRAFT }

    public enum State {
        CLARIFICATION_REQUIRED,
        PENDING,
        EXECUTING,
        SUCCEEDED,
        FAILED,
        REJECTED,
        EXPIRED,
        STALE,
        PERMISSION_DENIED
    }

    public record TaskPreview(
            String title,
            String description,
            String assigneeMemberId,
            String assigneeDisplayName,
            String projectId,
            String projectDisplayName,
            Instant dueAt
    ) {
        public TaskPreview {
            title = text(title, "task title", 500);
            if (description != null) {
                description = text(description, "task description", 2_000);
            }
            assigneeMemberId = positiveDecimal(
                    assigneeMemberId, "assigneeMemberId");
            if (assigneeDisplayName != null) {
                assigneeDisplayName = text(
                        assigneeDisplayName, "assigneeDisplayName", 200);
            }
            if (projectId != null) projectId = positiveDecimal(projectId, "projectId");
            if (projectDisplayName != null) {
                projectDisplayName = text(
                        projectDisplayName, "projectDisplayName", 200);
            }
        }
    }

    public record DailyReportPreview(
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers
    ) {
        public DailyReportPreview {
            Objects.requireNonNull(workDate, "workDate");
            completedWork = text(completedWork, "completedWork", 4_000);
            plannedWork = text(plannedWork, "plannedWork", 4_000);
            if (blockers != null) blockers = text(blockers, "blockers", 4_000);
        }
    }

    public record Preview(
            Operation operation,
            TaskPreview task,
            DailyReportPreview report
    ) {
        public Preview {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, task, report);
        }
    }

    public record SealedCommand(
            String ciphertext,
            String keyVersion,
            String commandHash
    ) {
        public SealedCommand {
            ciphertext = text(ciphertext, "ciphertext", 262_144);
            keyVersion = token(keyVersion, "keyVersion", 64);
            hash(commandHash, "commandHash");
        }
    }

    public record TaskResult(
            String taskId,
            long version,
            String title,
            String description,
            String status,
            String assigneeMemberId,
            String projectId,
            Instant dueAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public TaskResult {
            taskId = positiveDecimal(taskId, "taskId");
            positive(version, "task version");
            title = text(title, "task result title", 500);
            if (description != null) {
                description = text(description, "task result description", 2_000);
            }
            if (!"OPEN".equals(status)) invalid("task result status");
            assigneeMemberId = positiveDecimal(
                    assigneeMemberId, "task result assigneeMemberId");
            if (projectId != null) {
                projectId = positiveDecimal(projectId, "task result projectId");
            }
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) invalid("task result timestamps");
        }
    }

    public record DailyReportResult(
            String reportId,
            long version,
            String authorMemberId,
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public DailyReportResult {
            reportId = positiveDecimal(reportId, "reportId");
            positive(version, "report version");
            authorMemberId = positiveDecimal(authorMemberId, "authorMemberId");
            Objects.requireNonNull(workDate, "workDate");
            completedWork = text(completedWork, "result completedWork", 4_000);
            plannedWork = text(plannedWork, "result plannedWork", 4_000);
            if (blockers != null) blockers = text(blockers, "result blockers", 4_000);
            if (!"DRAFT".equals(status)) invalid("report result status");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) invalid("report result timestamps");
        }
    }

    public record Result(
            Operation operation,
            TaskResult task,
            DailyReportResult report
    ) {
        public Result {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, task, report);
        }
    }

    public record Attempt(
            long id,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long sessionId,
            long turnId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long proposalId,
            String action,
            String requestKey,
            String requestHash,
            State status,
            String resultHash,
            String resultCode,
            Instant createdAt,
            Instant finishedAt
    ) {
        public Attempt {
            positive(id, "attempt id"); positive(accountId, "attempt accountId");
            positive(systemId, "attempt systemId"); positive(tenantId, "attempt tenantId");
            positive(memberId, "attempt memberId"); positive(sessionId, "attempt sessionId");
            positive(turnId, "attempt turnId"); positive(policyVersionId, "attempt policy");
            positive(providerId, "attempt provider"); positive(proposalId, "attempt proposal");
            if (providerVersion < 0) invalid("attempt providerVersion");
            action = token(action, "action", 16);
            requestKey = token(requestKey, "requestKey", 128);
            hash(requestHash, "requestHash");
            status = Objects.requireNonNull(status, "status");
            if (resultHash != null) hash(resultHash, "resultHash");
            resultCode = token(resultCode, "resultCode", 64);
            Objects.requireNonNull(createdAt, "createdAt");
            if ((status == State.EXECUTING) == (finishedAt != null)) {
                invalid("attempt finishedAt");
            }
        }
    }

    public record Event(
            long id,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long sessionId,
            long turnId,
            long policyVersionId,
            long providerId,
            long providerVersion,
            long proposalId,
            Long attemptId,
            String eventType,
            State fromState,
            State toState,
            long revision,
            long actorMemberId,
            String requestId,
            String traceId,
            String resultCode,
            String eventHash,
            Instant createdAt
    ) {
        public Event {
            positive(id, "event id"); positive(accountId, "event accountId");
            positive(systemId, "event systemId"); positive(tenantId, "event tenantId");
            positive(memberId, "event memberId"); positive(sessionId, "event sessionId");
            positive(turnId, "event turnId"); positive(policyVersionId, "event policy");
            positive(providerId, "event provider"); positive(proposalId, "event proposal");
            if (providerVersion < 0 || revision < 0) invalid("event version");
            if (attemptId != null && attemptId <= 0) invalid("attemptId");
            eventType = token(eventType, "eventType", 32);
            toState = Objects.requireNonNull(toState, "toState");
            positive(actorMemberId, "actorMemberId");
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
            resultCode = token(resultCode, "resultCode", 64);
            hash(eventHash, "eventHash");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static void matching(Operation operation, Object task, Object report) {
        if ((operation == Operation.WORK_TASK_DRAFT) != (task != null)
                || (operation == Operation.WORK_DAILY_REPORT_DRAFT) != (report != null)) {
            invalid("payload");
        }
    }

    private static String positiveDecimal(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("AI Work proposal " + field
                    + " is invalid", failure);
        }
    }

    private static String token(String value, String field, int maximum) {
        value = text(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) invalid(field);
        return value;
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) invalid(field);
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) invalid(field);
        return value;
    }

    private static void hash(String value, String field) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) invalid(field);
    }

    private static void positive(long value, String field) {
        if (value <= 0) invalid(field);
    }

    private static void invalid(String field) {
        throw new IllegalArgumentException("AI Work proposal " + field + " is invalid");
    }
}
