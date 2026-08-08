package com.unique.examine.work.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.unique.examine.core.ai.AiWorkDraftFacade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Strict canonical codec for the complete Work owner command identity. */
final class WorkAiDraftCommandCodec {
    private final ObjectMapper json;

    WorkAiDraftCommandCodec() {
        json = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        json.registerModule(new JavaTimeModule());
        json.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        json.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    Command command(
            AiWorkDraftFacade.PrepareRequest request,
            Instant expiresAt
    ) {
        var permissions = request.effectivePermissions().stream()
                .sorted().toList();
        var payload = new Payload(
                request.proposalId(), request.sessionId(), request.turnId(),
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(), permissions,
                request.operation(), request.task(), request.report(),
                request.policyVersionId(), request.providerId(),
                request.providerVersion(), request.promptVersion(), expiresAt,
                request.requestId(), request.traceId());
        return new Command(
                payload.proposalId(), payload.sessionId(), payload.turnId(),
                payload.accountId(), payload.systemId(), payload.tenantId(),
                payload.memberId(), payload.authorizationEpoch(),
                payload.effectivePermissions(), payload.operation(),
                payload.task(), payload.report(), payload.policyVersionId(),
                payload.providerId(), payload.providerVersion(),
                payload.promptVersion(), payload.expiresAt(),
                payload.prepareRequestId(), payload.prepareTraceId(),
                WorkAiDraftCommandSealer.sha256(write(payload)));
    }

    String encode(Command command) {
        return write(command);
    }

    Command decode(String value) {
        final Command command;
        try {
            command = json.readValue(value, Command.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw WorkAiDraftCommandSealer.invalid();
        }
        var expected = WorkAiDraftCommandSealer.sha256(
                write(command.payload()));
        if (!WorkAiDraftCommandSealer.equal(
                expected, command.payloadHash())) {
            throw WorkAiDraftCommandSealer.invalid();
        }
        return command;
    }

    String writeReadback(AiWorkDraftFacade.DraftReadback value) {
        return write(value);
    }

    AiWorkDraftFacade.DraftReadback readReadback(String value) {
        try {
            return json.readValue(
                    value, AiWorkDraftFacade.DraftReadback.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Stored Work AI draft readback is invalid", failure);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode Work AI draft command", failure);
        }
    }

    record Command(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            List<String> effectivePermissions,
            AiWorkDraftFacade.Operation operation,
            AiWorkDraftFacade.TaskDraft task,
            AiWorkDraftFacade.DailyReportDraft report,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId,
            String payloadHash
    ) {
        Command {
            Objects.requireNonNull(proposalId, "proposalId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(turnId, "turnId");
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0
                    || memberId <= 0 || authorizationEpoch <= 0
                    || providerVersion < 0) {
                throw new IllegalArgumentException("Command identity is invalid");
            }
            effectivePermissions = List.copyOf(
                    Objects.requireNonNull(
                            effectivePermissions, "effectivePermissions"));
            if (!effectivePermissions.equals(
                    effectivePermissions.stream().sorted().distinct().toList())) {
                throw new IllegalArgumentException(
                        "Command permissions are invalid");
            }
            operation = Objects.requireNonNull(operation, "operation");
            if ((operation == AiWorkDraftFacade.Operation.WORK_TASK_DRAFT)
                    != (task != null)
                    || (operation == AiWorkDraftFacade.Operation
                    .WORK_DAILY_REPORT_DRAFT) != (report != null)) {
                throw new IllegalArgumentException("Command payload is invalid");
            }
            Objects.requireNonNull(policyVersionId, "policyVersionId");
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(promptVersion, "promptVersion");
            Objects.requireNonNull(expiresAt, "expiresAt");
            Objects.requireNonNull(prepareRequestId, "prepareRequestId");
            Objects.requireNonNull(prepareTraceId, "prepareTraceId");
            if (payloadHash == null
                    || !payloadHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("payloadHash is invalid");
            }
        }

        Payload payload() {
            return new Payload(
                    proposalId, sessionId, turnId, accountId, systemId,
                    tenantId, memberId, authorizationEpoch,
                    effectivePermissions, operation, task, report,
                    policyVersionId, providerId, providerVersion,
                    promptVersion, expiresAt, prepareRequestId,
                    prepareTraceId);
        }
    }

    private record Payload(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            List<String> effectivePermissions,
            AiWorkDraftFacade.Operation operation,
            AiWorkDraftFacade.TaskDraft task,
            AiWorkDraftFacade.DailyReportDraft report,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId
    ) { }
}
