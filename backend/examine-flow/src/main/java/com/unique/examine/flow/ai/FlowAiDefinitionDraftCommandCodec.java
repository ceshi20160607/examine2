package com.unique.examine.flow.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Strict canonical codec for the complete Flow owner command snapshot. */
final class FlowAiDefinitionDraftCommandCodec {
    private final ObjectMapper json;

    FlowAiDefinitionDraftCommandCodec() {
        json = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        var time = new SimpleModule();
        time.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(
                    Instant value,
                    com.fasterxml.jackson.core.JsonGenerator target,
                    com.fasterxml.jackson.databind.SerializerProvider ignored)
                    throws java.io.IOException {
                target.writeString(value.toString());
            }
        });
        time.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override
            public Instant deserialize(
                    com.fasterxml.jackson.core.JsonParser source,
                    com.fasterxml.jackson.databind.DeserializationContext ignored)
                    throws java.io.IOException {
                try {
                    return Instant.parse(source.getValueAsString());
                } catch (RuntimeException failure) {
                    throw com.fasterxml.jackson.databind.JsonMappingException
                            .from(source, "Instant is invalid", failure);
                }
            }
        });
        json.registerModule(time);
        json.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        json.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    Command command(
            AiFlowDefinitionDraftFacade.PrepareRequest request,
            Instant expiresAt) {
        var payload = new Payload(
                request.proposalId(), request.sessionId(), request.turnId(),
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions().stream().sorted().toList(),
                request.operation(), request.draft(),
                request.policyVersionId(), request.providerId(),
                request.providerVersion(), request.promptVersion(), expiresAt,
                request.requestId(), request.traceId());
        return new Command(
                payload.proposalId(), payload.sessionId(), payload.turnId(),
                payload.accountId(), payload.systemId(), payload.tenantId(),
                payload.memberId(), payload.authorizationEpoch(),
                payload.effectivePermissions(), payload.operation(),
                payload.draft(), payload.policyVersionId(),
                payload.providerId(), payload.providerVersion(),
                payload.promptVersion(), payload.expiresAt(),
                payload.prepareRequestId(), payload.prepareTraceId(),
                FlowAiDefinitionDraftCommandSealer.sha256(write(payload)));
    }

    String encode(Command command) {
        return write(command);
    }

    Command decode(String value) {
        try {
            var command = json.readValue(value, Command.class);
            var expected = FlowAiDefinitionDraftCommandSealer.sha256(
                    write(command.payload()));
            if (!FlowAiDefinitionDraftCommandSealer.equal(
                    expected, command.payloadHash())) {
                throw FlowAiDefinitionDraftCommandSealer.invalid();
            }
            return command;
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw FlowAiDefinitionDraftCommandSealer.invalid();
        }
    }

    String writeReadback(
            AiFlowDefinitionDraftFacade.DefinitionReadback value) {
        return write(value);
    }

    AiFlowDefinitionDraftFacade.DefinitionReadback readReadback(String value) {
        try {
            return json.readValue(
                    value, AiFlowDefinitionDraftFacade.DefinitionReadback.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Stored Flow AI definition readback is invalid", failure);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode Flow AI definition command", failure);
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
            AiFlowDefinitionDraftFacade.Operation operation,
            AiFlowDefinitionDraftFacade.Draft draft,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId,
            String payloadHash) {
        Command {
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0
                    || memberId <= 0 || authorizationEpoch <= 0
                    || providerVersion < 0) {
                throw new IllegalArgumentException(
                        "Flow command identity is invalid");
            }
            Objects.requireNonNull(proposalId, "proposalId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(turnId, "turnId");
            effectivePermissions = List.copyOf(Objects.requireNonNull(
                    effectivePermissions, "effectivePermissions"));
            if (!effectivePermissions.equals(effectivePermissions.stream()
                    .sorted().distinct().toList())) {
                throw new IllegalArgumentException(
                        "Flow command permissions are invalid");
            }
            operation = Objects.requireNonNull(operation, "operation");
            draft = Objects.requireNonNull(draft, "draft");
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
                    effectivePermissions, operation, draft, policyVersionId,
                    providerId, providerVersion, promptVersion, expiresAt,
                    prepareRequestId, prepareTraceId);
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
            AiFlowDefinitionDraftFacade.Operation operation,
            AiFlowDefinitionDraftFacade.Draft draft,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId) { }
}
