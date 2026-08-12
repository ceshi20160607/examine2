package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Authoritative prepare/confirm owner for report and print configuration drafts. */
@Component
public class AiModuleGeneratedDraftOwner
        implements AiModuleGeneratedDraftFacade {
    static final Duration COMMAND_TTL = Duration.ofMinutes(15);

    private final Context contexts;
    private final ExecutionStore store;
    private final AiModuleGeneratedDraftCommandSealer sealer;
    private final AiModuleGeneratedDraftCommandCodec codec;
    private final Clock clock;
    private final Duration commandTtl;

    @Autowired
    public AiModuleGeneratedDraftOwner(
            AiModuleGeneratedDraftContextReader contexts,
            JdbcModuleAiGeneratedDraftExecutionStore store,
            AiModuleGeneratedDraftCommandSealer sealer) {
        this(new Context() {
                    @Override
                    public AiModuleGeneratedDraftContextReader.OwnerContext validate(
                            AiModuleGeneratedDraftContextReader.Access access,
                            ReportDraft report,
                            PrintTemplateDraft printTemplate) {
                        return contexts.validate(access, report, printTemplate);
                    }

                    @Override
                    public AiModuleGeneratedDraftContextReader.OwnerContext authorize(
                            AiModuleGeneratedDraftContextReader.Access access) {
                        return contexts.authorize(access);
                    }

                    @Override
                    public void validateFacts(
                            AiModuleGeneratedDraftContextReader.OwnerContext context,
                            Operation operation,
                            ReportDraft report,
                            PrintTemplateDraft printTemplate) {
                        contexts.validateFacts(
                                context, operation, report, printTemplate);
                    }
                }, store::execute, sealer,
                new AiModuleGeneratedDraftCommandCodec(),
                Clock.systemUTC(), COMMAND_TTL);
    }

    AiModuleGeneratedDraftOwner(
            Context contexts,
            ExecutionStore store,
            AiModuleGeneratedDraftCommandSealer sealer,
            AiModuleGeneratedDraftCommandCodec codec,
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
    public PreparedDraft prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        contexts.validate(
                access(request), request.report(), request.printTemplate());
        var expiresAt = clock.instant().plus(commandTtl)
                .truncatedTo(ChronoUnit.MICROS);
        var command = codec.command(request, expiresAt);
        var plaintext = codec.encode(command);
        var sealed = sealer.seal(plaintext, binding(command));
        return new PreparedDraft(
                new DraftPreview(
                        command.operation(), command.report(),
                        command.printTemplate()),
                sealed, expiresAt);
    }

    @Override
    @Transactional
    public DraftReadback execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        var plaintext = sealer.open(
                request.sealedCommand(), binding(request));
        if (!AiModuleGeneratedDraftCommandSealer.equal(
                AiModuleGeneratedDraftCommandSealer.sha256(plaintext),
                request.sealedCommand().commandSha256())) {
            throw AiModuleGeneratedDraftCommandSealer.invalid();
        }
        var command = codec.decode(plaintext);
        var context = contexts.authorize(access(request));
        assertIdentity(request, command);
        if (!command.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(
                    "AI_MODULE_DRAFT_COMMAND_EXPIRED",
                    "The module-generated draft owner command expired",
                    HttpStatus.CONFLICT);
        }
        return store.execute(
                command, context,
                () -> contexts.validateFacts(
                        context, command.operation(), command.report(),
                        command.printTemplate()),
                request.idempotencyKey(), request.requestId(),
                request.traceId());
    }

    private static void assertIdentity(
            ExecuteRequest request,
            AiModuleGeneratedDraftCommandCodec.Command command) {
        var permissions = request.effectivePermissions().stream()
                .sorted().toList();
        if (!command.proposalId().equals(request.proposalId())
                || !command.sessionId().equals(request.sessionId())
                || !command.turnId().equals(request.turnId())
                || command.accountId() != request.accountId()
                || command.systemId() != request.systemId()
                || command.tenantId() != request.tenantId()
                || command.memberId() != request.memberId()
                || command.operation() != request.operation()) {
            throw AiModuleGeneratedDraftCommandSealer.invalid();
        }
        if (command.authorizationEpoch() != request.authorizationEpoch()
                || !command.effectivePermissions().equals(permissions)) {
            throw new BusinessException(
                    "AI_MODULE_DRAFT_AUTHORIZATION_STALE",
                    "Module configuration authorization changed",
                    HttpStatus.CONFLICT);
        }
    }

    private static AiModuleGeneratedDraftContextReader.Access access(
            PrepareRequest request) {
        return new AiModuleGeneratedDraftContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.operation());
    }

    private static AiModuleGeneratedDraftContextReader.Access access(
            ExecuteRequest request) {
        return new AiModuleGeneratedDraftContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.operation());
    }

    private static AiModuleGeneratedDraftCommandSealer.Binding binding(
            AiModuleGeneratedDraftCommandCodec.Command command) {
        return new AiModuleGeneratedDraftCommandSealer.Binding(
                command.accountId(), command.systemId(), command.tenantId(),
                command.memberId(), command.proposalId(), command.sessionId(),
                command.turnId(), command.operation());
    }

    private static AiModuleGeneratedDraftCommandSealer.Binding binding(
            ExecuteRequest request) {
        return new AiModuleGeneratedDraftCommandSealer.Binding(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.proposalId(), request.sessionId(),
                request.turnId(), request.operation());
    }

    @FunctionalInterface
    interface ExecutionStore {
        DraftReadback execute(
                AiModuleGeneratedDraftCommandCodec.Command command,
                AiModuleGeneratedDraftContextReader.OwnerContext context,
                Runnable liveFactCheck,
                String idempotencyKey,
                String requestId,
                String traceId);
    }

    interface Context {
        AiModuleGeneratedDraftContextReader.OwnerContext validate(
                AiModuleGeneratedDraftContextReader.Access access,
                ReportDraft report,
                PrintTemplateDraft printTemplate);

        AiModuleGeneratedDraftContextReader.OwnerContext authorize(
                AiModuleGeneratedDraftContextReader.Access access);

        void validateFacts(
                AiModuleGeneratedDraftContextReader.OwnerContext context,
                Operation operation,
                ReportDraft report,
                PrintTemplateDraft printTemplate);
    }
}
