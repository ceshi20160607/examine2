package com.unique.examine.core.ai;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Work-owner boundary for one explicitly confirmed task or personal report draft.
 * Prepare is read-only; execute accepts only the authenticated owner command.
 */
public interface AiWorkDraftFacade {

    PreparedDraft prepare(PrepareRequest request);

    DraftReadback execute(ExecuteRequest request);

    enum Operation {
        WORK_TASK_DRAFT,
        WORK_DAILY_REPORT_DRAFT
    }

    record TaskDraft(
            String title,
            String description,
            String assigneeMemberId,
            String projectId,
            Instant dueAt
    ) {
        public TaskDraft {
            title = text(title, "title", 500, false);
            description = text(description, "description", 2_000, true);
            assigneeMemberId = positiveDecimal(
                    assigneeMemberId, "assigneeMemberId");
            if (projectId != null) {
                projectId = positiveDecimal(projectId, "projectId");
            }
        }
    }

    record DailyReportDraft(
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers
    ) {
        public DailyReportDraft {
            workDate = Objects.requireNonNull(workDate, "workDate");
            completedWork = text(
                    completedWork, "completedWork", 4_000, false);
            plannedWork = text(plannedWork, "plannedWork", 4_000, false);
            blockers = text(blockers, "blockers", 4_000, true);
        }
    }

    record PrepareRequest(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            Operation operation,
            TaskDraft task,
            DailyReportDraft report,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            proposalId = token(proposalId, "proposalId", 128);
            sessionId = token(sessionId, "sessionId", 128);
            turnId = token(turnId, "turnId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, task, report);
            policyVersionId = positiveDecimal(
                    policyVersionId, "policyVersionId");
            providerId = positiveDecimal(providerId, "providerId");
            nonNegative(providerVersion, "providerVersion");
            promptVersion = token(promptVersion, "promptVersion", 64);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record PreparedDraft(
            DraftPreview preview,
            SealedCommand sealedCommand,
            Instant expiresAt
    ) {
        public PreparedDraft {
            preview = Objects.requireNonNull(preview, "preview");
            sealedCommand = Objects.requireNonNull(
                    sealedCommand, "sealedCommand");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record DraftPreview(
            Operation operation,
            TaskDraft task,
            DailyReportDraft report
    ) {
        public DraftPreview {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, task, report);
        }
    }

    record SealedCommand(
            String ciphertext,
            String encryptionKeyVersion,
            String commandSha256
    ) {
        public SealedCommand {
            ciphertext = opaque(ciphertext, "ciphertext", 131_072);
            encryptionKeyVersion = token(
                    encryptionKeyVersion, "encryptionKeyVersion", 64);
            hash(commandSha256, "commandSha256");
        }
    }

    record ExecuteRequest(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            Operation operation,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            proposalId = token(proposalId, "proposalId", 128);
            sessionId = token(sessionId, "sessionId", 128);
            turnId = token(turnId, "turnId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            operation = Objects.requireNonNull(operation, "operation");
            sealedCommand = Objects.requireNonNull(
                    sealedCommand, "sealedCommand");
            idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record DraftReadback(
            Operation operation,
            TaskReadback task,
            DailyReportReadback report
    ) {
        public DraftReadback {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, task, report);
        }
    }

    record TaskReadback(
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
        public TaskReadback {
            taskId = positiveDecimal(taskId, "taskId");
            positive(version, "version");
            title = text(title, "title", 500, false);
            description = text(description, "description", 2_000, true);
            if (!"OPEN".equals(status)) {
                throw new IllegalArgumentException("status is invalid");
            }
            assigneeMemberId = positiveDecimal(
                    assigneeMemberId, "assigneeMemberId");
            if (projectId != null) {
                projectId = positiveDecimal(projectId, "projectId");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("task timestamps are invalid");
            }
        }
    }

    record DailyReportReadback(
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
        public DailyReportReadback {
            reportId = positiveDecimal(reportId, "reportId");
            positive(version, "version");
            authorMemberId = positiveDecimal(
                    authorMemberId, "authorMemberId");
            workDate = Objects.requireNonNull(workDate, "workDate");
            completedWork = text(
                    completedWork, "completedWork", 4_000, false);
            plannedWork = text(plannedWork, "plannedWork", 4_000, false);
            blockers = text(blockers, "blockers", 4_000, true);
            if (!"DRAFT".equals(status)) {
                throw new IllegalArgumentException("status is invalid");
            }
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("report timestamps are invalid");
            }
        }
    }

    private static void matching(
            Operation operation, Object task, Object report) {
        if ((operation == Operation.WORK_TASK_DRAFT) != (task != null)
                || (operation == Operation.WORK_DAILY_REPORT_DRAFT)
                != (report != null)) {
            throw new IllegalArgumentException(
                    "exactly the matching Work draft is required");
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static String text(
            String value, String name, int maximum, boolean nullable) {
        if (nullable && value == null) return null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String token(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String opaque(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Za-z0-9_-]+$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static void hash(String value, String name) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
