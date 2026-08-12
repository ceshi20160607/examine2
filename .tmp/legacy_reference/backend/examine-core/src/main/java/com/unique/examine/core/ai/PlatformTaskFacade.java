package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.Objects;

/**
 * Platform owner boundary for a personal follow-up task prepared by the Agent.
 * Prepare is read-only. Execute accepts only the authenticated opaque command
 * returned by prepare and performs one owner-controlled idempotent write.
 */
public interface PlatformTaskFacade {

    PreparedTask prepare(PrepareRequest request);

    TaskView execute(ExecuteRequest request);

    enum Priority { LOW, NORMAL, HIGH, URGENT }

    enum Status { OPEN, COMPLETED, CANCELLED }

    enum Source { AGENT, WORK }

    record TaskDraft(
            String title,
            String description,
            Instant dueAt,
            Priority priority
    ) {
        public TaskDraft {
            title = text(title, "title", 200, false);
            description = text(description, "description", 2_000, true);
            priority = Objects.requireNonNull(priority, "priority");
        }
    }

    record PrepareRequest(
            String proposalId,
            long accountId,
            long authorizationEpoch,
            TaskDraft draft,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            proposalId = token(proposalId, "proposalId", 128);
            positive(accountId, "accountId");
            positive(authorizationEpoch, "authorizationEpoch");
            draft = Objects.requireNonNull(draft, "draft");
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
        }
    }

    record PreparedTask(
            TaskPreview preview,
            Instant expiresAt,
            SealedCommand sealedCommand
    ) {
        public PreparedTask {
            preview = Objects.requireNonNull(preview, "preview");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        }
    }

    record TaskPreview(
            long accountId,
            String title,
            String description,
            Instant dueAt,
            Priority priority,
            Status status,
            Source source
    ) {
        public TaskPreview {
            positive(accountId, "accountId");
            title = text(title, "title", 200, false);
            description = text(description, "description", 2_000, true);
            priority = Objects.requireNonNull(priority, "priority");
            status = Objects.requireNonNull(status, "status");
            source = Objects.requireNonNull(source, "source");
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
            long accountId,
            long authorizationEpoch,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            proposalId = token(proposalId, "proposalId", 128);
            positive(accountId, "accountId");
            positive(authorizationEpoch, "authorizationEpoch");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
        }
    }

    record TaskView(
            String taskId,
            long accountId,
            String title,
            String description,
            Instant dueAt,
            Priority priority,
            Status status,
            Source source,
            long authorizationEpoch,
            String payloadHash,
            String requestId,
            String traceId,
            Instant createdAt,
            long createdBy
    ) {
        public TaskView {
            taskId = positiveDecimal(taskId, "taskId");
            positive(accountId, "accountId");
            title = text(title, "title", 200, false);
            description = text(description, "description", 2_000, true);
            priority = Objects.requireNonNull(priority, "priority");
            status = Objects.requireNonNull(status, "status");
            source = Objects.requireNonNull(source, "source");
            positive(authorizationEpoch, "authorizationEpoch");
            hash(payloadHash, "payloadHash");
            requestId = token(requestId, "requestId", 128);
            traceId = token(traceId, "traceId", 128);
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            positive(createdBy, "createdBy");
            if (createdBy != accountId) {
                throw new IllegalArgumentException(
                        "createdBy must equal the self-assigned accountId");
            }
        }
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
}
