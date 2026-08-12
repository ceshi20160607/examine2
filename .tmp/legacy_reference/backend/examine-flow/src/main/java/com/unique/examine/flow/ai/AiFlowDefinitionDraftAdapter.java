package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Confirmation-gated Flow owner adapter for one unpublished definition draft. */
@Component
public class AiFlowDefinitionDraftAdapter
        implements AiFlowDefinitionDraftFacade {
    static final Duration COMMAND_TTL = Duration.ofMinutes(15);
    private final Context contexts;
    private final ExecutionStore store;
    private final FlowAiDefinitionDraftCommandSealer sealer;
    private final FlowAiDefinitionDraftCommandCodec codec;
    private final Clock clock;
    private final Duration commandTtl;

    @Autowired
    public AiFlowDefinitionDraftAdapter(
            FlowAiDefinitionDraftContextReader contexts,
            JdbcFlowAiDefinitionDraftExecutionStore store,
            FlowAiDefinitionDraftCommandSealer sealer,
            Clock clock) {
        this(new Context() {
                    @Override
                    public FlowSession validate(
                            FlowAiDefinitionDraftContextReader.Access access,
                            Draft draft) {
                        return contexts.validate(access, draft);
                    }

                    @Override
                    public FlowSession authorize(
                            FlowAiDefinitionDraftContextReader.Access access) {
                        return contexts.authorize(access);
                    }

                    @Override
                    public void validateFacts(
                            FlowSession session, Draft draft) {
                        contexts.validateFacts(session, draft);
                    }
                }, store::execute, sealer,
                new FlowAiDefinitionDraftCommandCodec(), clock, COMMAND_TTL);
    }

    AiFlowDefinitionDraftAdapter(
            Context contexts,
            ExecutionStore store,
            FlowAiDefinitionDraftCommandSealer sealer,
            FlowAiDefinitionDraftCommandCodec codec,
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
    @Transactional
    public PreparedDraft prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        contexts.validate(access(request), request.draft());
        var expiresAt = clock.instant().plus(commandTtl)
                .truncatedTo(ChronoUnit.MICROS);
        var command = codec.command(request, expiresAt);
        var plaintext = codec.encode(command);
        var sealed = sealer.seal(plaintext, binding(command));
        return new PreparedDraft(
                new DraftPreview(command.operation(), command.draft()),
                sealed, expiresAt);
    }

    @Override
    @Transactional
    public DefinitionReadback execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        var plaintext = sealer.open(request.sealedCommand(), binding(request));
        if (!FlowAiDefinitionDraftCommandSealer.equal(
                FlowAiDefinitionDraftCommandSealer.sha256(plaintext),
                request.sealedCommand().commandSha256())) {
            throw FlowAiDefinitionDraftCommandSealer.invalid();
        }
        var command = codec.decode(plaintext);
        var session = contexts.authorize(access(request));
        assertIdentity(request, command);
        if (!command.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(
                    "AI_FLOW_DRAFT_COMMAND_EXPIRED",
                    "The Flow definition draft command expired",
                    HttpStatus.CONFLICT);
        }
        contexts.validateFacts(session, command.draft());
        return store.execute(
                command, session,
                () -> { },
                request.idempotencyKey(), request.requestId(),
                request.traceId());
    }

    private static void assertIdentity(
            ExecuteRequest request,
            FlowAiDefinitionDraftCommandCodec.Command command) {
        if (!command.proposalId().equals(request.proposalId())
                || !command.sessionId().equals(request.sessionId())
                || !command.turnId().equals(request.turnId())
                || command.accountId() != request.accountId()
                || command.systemId() != request.systemId()
                || command.tenantId() != request.tenantId()
                || command.memberId() != request.memberId()
                || command.operation() != request.operation()) {
            throw FlowAiDefinitionDraftCommandSealer.invalid();
        }
        var permissions = request.effectivePermissions().stream()
                .sorted().toList();
        if (command.authorizationEpoch() != request.authorizationEpoch()
                || !command.effectivePermissions().equals(permissions)) {
            throw new BusinessException(
                    "AI_FLOW_AUTHORIZATION_STALE",
                    "Flow authorization changed before draft confirmation",
                    HttpStatus.CONFLICT);
        }
    }

    private static FlowAiDefinitionDraftContextReader.Access access(
            PrepareRequest request) {
        return new FlowAiDefinitionDraftContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.operation());
    }

    private static FlowAiDefinitionDraftContextReader.Access access(
            ExecuteRequest request) {
        return new FlowAiDefinitionDraftContextReader.Access(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.authorizationEpoch(),
                request.effectivePermissions(), request.operation());
    }

    private static FlowAiDefinitionDraftCommandSealer.Binding binding(
            FlowAiDefinitionDraftCommandCodec.Command command) {
        return new FlowAiDefinitionDraftCommandSealer.Binding(
                command.accountId(), command.systemId(), command.tenantId(),
                command.memberId(), command.proposalId(), command.sessionId(),
                command.turnId(), command.operation());
    }

    private static FlowAiDefinitionDraftCommandSealer.Binding binding(
            ExecuteRequest request) {
        return new FlowAiDefinitionDraftCommandSealer.Binding(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.proposalId(), request.sessionId(),
                request.turnId(), request.operation());
    }

    @FunctionalInterface
    interface ExecutionStore {
        DefinitionReadback execute(
                FlowAiDefinitionDraftCommandCodec.Command command,
                FlowSession session,
                Runnable liveFactCheck,
                String idempotencyKey,
                String requestId,
                String traceId);
    }

    interface Context {
        FlowSession validate(
                FlowAiDefinitionDraftContextReader.Access access,
                Draft draft);

        FlowSession authorize(
                FlowAiDefinitionDraftContextReader.Access access);

        void validateFacts(FlowSession session, Draft draft);
    }
}
