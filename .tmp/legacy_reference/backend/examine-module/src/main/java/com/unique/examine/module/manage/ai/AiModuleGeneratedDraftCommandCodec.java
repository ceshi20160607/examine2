package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Strict canonical codec for a module-generated draft owner command. */
final class AiModuleGeneratedDraftCommandCodec {
    private final ObjectMapper json;

    AiModuleGeneratedDraftCommandCodec() {
        json = new ObjectMapper(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build());
        var time = new SimpleModule();
        time.addSerializer(Instant.class, new InstantSerializer());
        time.addDeserializer(Instant.class, new InstantDeserializer());
        json.registerModule(time);
        json.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        json.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    Command command(
            AiModuleGeneratedDraftFacade.PrepareRequest request,
            Instant expiresAt) {
        var permissions = request.effectivePermissions().stream()
                .sorted().toList();
        var payload = new Payload(
                request.proposalId(), request.sessionId(), request.turnId(),
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(), permissions,
                request.operation(), request.report(), request.printTemplate(),
                request.policyVersionId(), request.providerId(),
                request.providerVersion(), request.promptVersion(), expiresAt,
                request.requestId(), request.traceId());
        return new Command(
                payload.proposalId(), payload.sessionId(), payload.turnId(),
                payload.accountId(), payload.systemId(), payload.tenantId(),
                payload.memberId(), payload.authorizationEpoch(),
                payload.effectivePermissions(), payload.operation(),
                payload.report(), payload.printTemplate(),
                payload.policyVersionId(), payload.providerId(),
                payload.providerVersion(), payload.promptVersion(),
                payload.expiresAt(), payload.prepareRequestId(),
                payload.prepareTraceId(),
                AiModuleGeneratedDraftCommandSealer.sha256(write(payload)));
    }

    String encode(Command command) {
        return write(command);
    }

    Command decode(String value) {
        final Command command;
        try {
            command = json.readValue(value, Command.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw AiModuleGeneratedDraftCommandSealer.invalid();
        }
        var expected = AiModuleGeneratedDraftCommandSealer.sha256(
                write(command.payload()));
        if (!AiModuleGeneratedDraftCommandSealer.equal(
                expected, command.payloadHash())) {
            throw AiModuleGeneratedDraftCommandSealer.invalid();
        }
        return command;
    }

    String writeReadback(AiModuleGeneratedDraftFacade.DraftReadback value) {
        return write(value);
    }

    AiModuleGeneratedDraftFacade.DraftReadback readReadback(String value) {
        try {
            return json.readValue(value,
                    AiModuleGeneratedDraftFacade.DraftReadback.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Stored module-generated draft readback is invalid", failure);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode module-generated draft command", failure);
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
            AiModuleGeneratedDraftFacade.Operation operation,
            AiModuleGeneratedDraftFacade.ReportDraft report,
            AiModuleGeneratedDraftFacade.PrintTemplateDraft printTemplate,
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
            effectivePermissions = List.copyOf(Objects.requireNonNull(
                    effectivePermissions, "effectivePermissions"));
            if (!effectivePermissions.equals(effectivePermissions.stream()
                    .sorted().distinct().toList())) {
                throw new IllegalArgumentException(
                        "Command permissions are invalid");
            }
            operation = Objects.requireNonNull(operation, "operation");
            if ((operation == AiModuleGeneratedDraftFacade.Operation
                    .CONFIG_REPORT_DRAFT) != (report != null)
                    || (operation == AiModuleGeneratedDraftFacade.Operation
                    .CONFIG_PRINT_TEMPLATE_DRAFT) != (printTemplate != null)) {
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
                    effectivePermissions, operation, report, printTemplate,
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
            AiModuleGeneratedDraftFacade.Operation operation,
            AiModuleGeneratedDraftFacade.ReportDraft report,
            AiModuleGeneratedDraftFacade.PrintTemplateDraft printTemplate,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String prepareRequestId,
            String prepareTraceId
    ) { }

    private static final class InstantSerializer
            extends StdScalarSerializer<Instant> {
        private InstantSerializer() {
            super(Instant.class);
        }

        @Override
        public void serialize(
                Instant value,
                JsonGenerator generator,
                SerializerProvider provider) throws IOException {
            generator.writeString(value.toString());
        }
    }

    private static final class InstantDeserializer
            extends StdScalarDeserializer<Instant> {
        private InstantDeserializer() {
            super(Instant.class);
        }

        @Override
        public Instant deserialize(
                JsonParser parser,
                DeserializationContext context) throws IOException {
            try {
                return Instant.parse(parser.getValueAsString());
            } catch (RuntimeException failure) {
                throw context.weirdStringException(
                        parser.getValueAsString(), Instant.class,
                        "Instant must use ISO-8601 UTC format");
            }
        }
    }
}
