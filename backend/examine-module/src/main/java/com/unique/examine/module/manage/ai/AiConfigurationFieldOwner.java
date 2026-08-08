package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/** Authoritative prepare/confirm owner for one scalar draft field. */
@Component
public class AiConfigurationFieldOwner
        implements AiConfigurationFieldFacade {
    static final Duration COMMAND_TTL = Duration.ofMinutes(15);

    private final ContextResolver contexts;
    private final MutationStore store;
    private final AiConfigurationFieldCommandSealer sealer;
    private final AiConfigurationFieldCommandCodec codec;
    private final Clock clock;
    private final Duration commandTtl;

    @Autowired
    public AiConfigurationFieldOwner(
            AiConfigurationFieldContextReader contexts,
            AiConfigurationFieldMutationStore store,
            AiConfigurationFieldCommandSealer sealer,
            ObjectMapper json) {
        this(contexts::resolve, store::execute, sealer,
                new AiConfigurationFieldCommandCodec(json),
                Clock.systemUTC(), COMMAND_TTL);
    }

    AiConfigurationFieldOwner(
            ContextResolver contexts,
            MutationStore store,
            AiConfigurationFieldCommandSealer sealer,
            AiConfigurationFieldCommandCodec codec,
            Clock clock,
            Duration commandTtl) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.store = Objects.requireNonNull(store, "store");
        this.sealer = Objects.requireNonNull(sealer, "sealer");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commandTtl = Objects.requireNonNull(commandTtl, "commandTtl");
        if (commandTtl.isZero() || commandTtl.isNegative()
                || commandTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("commandTtl is invalid");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PreparedField prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        var field = normalize(request.field());
        var live = contexts.resolve(access(request, field.fieldCode()));
        if (live.fieldCodeExists()) {
            throw fieldConflict();
        }
        if (live.draftRevision() == Long.MAX_VALUE) {
            throw stale();
        }
        var normalized = normalized(request, field);
        var expiresAt = clock.instant().plus(commandTtl);
        var command = codec.command(normalized, live, expiresAt);
        var plaintext = codec.encode(command);
        var sealed = sealer.seal(plaintext, binding(command));
        return new PreparedField(
                new FieldPreview(
                        Long.toString(live.configRootId()),
                        Long.toString(live.moduleId()), live.moduleCode(),
                        live.draftRevision(), live.draftRevision() + 1, field),
                expiresAt, sealed);
    }

    @Override
    @Transactional
    public FieldReadback execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        var binding = binding(request);
        var plaintext = sealer.open(request.sealedCommand(), binding);
        if (!AiConfigurationFieldCommandSealer.equal(
                AiConfigurationFieldCommandSealer.sha256(plaintext),
                request.sealedCommand().commandSha256())) {
            throw AiConfigurationFieldCommandCodec.invalid();
        }
        var command = codec.decode(plaintext);
        assertIdentity(request, command);
        if (!command.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(
                    "AI_CONFIG_FIELD_COMMAND_EXPIRED",
                    "The configuration field command expired",
                    HttpStatus.CONFLICT);
        }
        var currentAccess = access(request, command.field().fieldCode());
        var live = contexts.resolve(currentAccess);
        if (live.configRootId() != command.configRootId()
                || live.moduleId() != command.moduleId()
                || !live.moduleCode().equals(command.moduleCode())) {
            throw stale();
        }
        return store.execute(
                command, currentAccess, request.idempotencyKey(),
                request.requestId(), request.traceId());
    }

    private static void assertIdentity(
            ExecuteRequest request,
            AiConfigurationFieldCommandCodec.Command command) {
        if (!command.proposalId().equals(request.proposalId())
                || command.accountId() != request.accountId()
                || command.systemId() != request.systemId()
                || command.tenantId() != request.tenantId()
                || command.memberId() != request.memberId()
                || command.configRootId() != Long.parseLong(
                request.configRootId())
                || command.moduleId() != Long.parseLong(request.moduleId())
                || !command.moduleCode().equals(request.moduleCode())
                || command.expectedDraftRevision()
                != request.expectedDraftRevision()) {
            throw AiConfigurationFieldCommandCodec.invalid();
        }
    }

    private static FieldDraft normalize(FieldDraft value) {
        var name = Normalizer.normalize(
                value.fieldName(), Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ");
        var settings = value.settings();
        if (value.fieldType() == FieldType.INTEGER) {
            settings = new ScalarSettings(
                    null, null, null, null,
                    settings.minimum(), settings.maximum(),
                    settings.precision() == null ? 38 : settings.precision(),
                    0, null, null);
        } else if (value.fieldType() == FieldType.DECIMAL) {
            settings = new ScalarSettings(
                    null, null, null, null,
                    settings.minimum(), settings.maximum(),
                    settings.precision() == null ? 18 : settings.precision(),
                    settings.scale() == null ? 2 : settings.scale(),
                    null, null);
        }
        return new FieldDraft(
                value.fieldCode(), name, value.fieldType(),
                value.required(), settings);
    }

    private static PrepareRequest normalized(
            PrepareRequest value, FieldDraft field) {
        return new PrepareRequest(
                value.proposalId(), value.accountId(), value.systemId(),
                value.tenantId(), value.memberId(), value.authorizationEpoch(),
                value.effectivePermissions(), value.moduleCode(), field,
                value.policyVersionId(), value.providerId(),
                value.providerVersion(), value.promptVersion(),
                value.requestId(), value.traceId());
    }

    private static AiConfigurationFieldContextReader.Access access(
            PrepareRequest request, String fieldCode) {
        return new AiConfigurationFieldContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.moduleCode(), fieldCode);
    }

    private static AiConfigurationFieldContextReader.Access access(
            ExecuteRequest request, String fieldCode) {
        return new AiConfigurationFieldContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.moduleCode(), fieldCode);
    }

    private static AiConfigurationFieldCommandSealer.Binding binding(
            AiConfigurationFieldCommandCodec.Command command) {
        return new AiConfigurationFieldCommandSealer.Binding(
                command.accountId(), command.systemId(), command.tenantId(),
                command.memberId(), command.proposalId(), command.configRootId(),
                command.moduleId(), command.moduleCode(),
                command.expectedDraftRevision());
    }

    private static AiConfigurationFieldCommandSealer.Binding binding(
            ExecuteRequest request) {
        return new AiConfigurationFieldCommandSealer.Binding(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.proposalId(),
                Long.parseLong(request.configRootId()),
                Long.parseLong(request.moduleId()), request.moduleCode(),
                request.expectedDraftRevision());
    }

    private static BusinessException fieldConflict() {
        return new BusinessException(
                "AI_CONFIG_FIELD_CODE_CONFLICT",
                "The configuration field code already exists",
                HttpStatus.CONFLICT);
    }

    private static BusinessException stale() {
        return new BusinessException(
                "AI_CONFIG_DRAFT_STALE",
                "The configuration draft changed before confirmation",
                HttpStatus.CONFLICT);
    }

    @FunctionalInterface
    interface ContextResolver {
        AiConfigurationFieldContextReader.Snapshot resolve(
                AiConfigurationFieldContextReader.Access access);
    }

    @FunctionalInterface
    interface MutationStore {
        FieldReadback execute(
                AiConfigurationFieldCommandCodec.Command command,
                AiConfigurationFieldContextReader.Access currentAccess,
                String idempotencyKey,
                String requestId,
                String traceId);
    }
}
