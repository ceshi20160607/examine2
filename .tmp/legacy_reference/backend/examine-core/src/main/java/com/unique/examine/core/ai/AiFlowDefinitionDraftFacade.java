package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Flow-owner boundary for one explicitly confirmed unpublished definition draft. */
public interface AiFlowDefinitionDraftFacade {
    PreparedDraft prepare(PrepareRequest request);

    DefinitionReadback execute(ExecuteRequest request);

    enum Operation {
        FLOW_DEFINITION_DRAFT
    }

    record Draft(String name, List<String> approverMemberIds) {
        public Draft {
            name = narrative(name, "name", 200);
            approverMemberIds = List.copyOf(Objects.requireNonNull(
                    approverMemberIds, "approverMemberIds"));
            if (approverMemberIds.isEmpty()
                    || approverMemberIds.size() > 10) {
                throw new IllegalArgumentException(
                        "approverMemberIds must contain 1 to 10 ids");
            }
            approverMemberIds = approverMemberIds.stream()
                    .map(value -> positiveDecimal(
                            value, "approverMemberIds"))
                    .toList();
        }
    }

    record DraftPreview(Operation operation, Draft draft) {
        public DraftPreview {
            operation = Objects.requireNonNull(operation, "operation");
            draft = Objects.requireNonNull(draft, "draft");
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
            Draft draft,
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
            draft = Objects.requireNonNull(draft, "draft");
            policyVersionId = positiveDecimal(
                    policyVersionId, "policyVersionId");
            providerId = positiveDecimal(providerId, "providerId");
            if (providerVersion < 0) {
                throw new IllegalArgumentException(
                        "providerVersion must not be negative");
            }
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

    record SealedCommand(
            String ciphertext,
            String encryptionKeyVersion,
            String commandSha256
    ) {
        public SealedCommand {
            if (ciphertext == null || ciphertext.length() > 131_072
                    || !ciphertext.matches("^[A-Za-z0-9_-]+$")) {
                throw new IllegalArgumentException("ciphertext is invalid");
            }
            encryptionKeyVersion = token(
                    encryptionKeyVersion, "encryptionKeyVersion", 64);
            if (commandSha256 == null
                    || !commandSha256.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("commandSha256 is invalid");
            }
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
            idempotencyKey = token(
                    idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record DefinitionReadback(
            Operation operation,
            String definitionId,
            String name,
            List<String> approverMemberIds,
            int revision,
            Instant updatedAt,
            boolean published
    ) {
        public DefinitionReadback {
            operation = Objects.requireNonNull(operation, "operation");
            definitionId = positiveDecimal(definitionId, "definitionId");
            name = narrative(name, "name", 200);
            approverMemberIds = List.copyOf(Objects.requireNonNull(
                    approverMemberIds, "approverMemberIds"));
            if (approverMemberIds.isEmpty()
                    || approverMemberIds.size() > 10) {
                throw new IllegalArgumentException(
                        "approverMemberIds are invalid");
            }
            approverMemberIds = approverMemberIds.stream()
                    .map(value -> positiveDecimal(
                            value, "approverMemberIds"))
                    .toList();
            if (revision < 0) {
                throw new IllegalArgumentException("revision is invalid");
            }
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (published) {
                throw new IllegalArgumentException(
                        "AI Flow definition readback must be unpublished");
            }
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

    private static String narrative(
            String value, String name, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(Character::isISOControl)) {
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

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
