package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.domain.WorkActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Authoritative prepare/confirm owner for one Work task or report draft. */
@Component
public class AiWorkDraftAdapter implements AiWorkDraftFacade {
    static final Duration COMMAND_TTL = Duration.ofMinutes(15);

    private final Context contexts;
    private final ExecutionStore store;
    private final WorkAiDraftCommandSealer sealer;
    private final WorkAiDraftCommandCodec codec;
    private final Clock clock;
    private final Duration commandTtl;

    @Autowired
    public AiWorkDraftAdapter(
            WorkAiDraftContextReader contexts,
            JdbcWorkAiDraftExecutionStore store,
            WorkAiDraftCommandSealer sealer,
            Clock clock
    ) {
        this(new Context() {
                    @Override
                    public WorkActor validate(
                            WorkAiDraftContextReader.Access access,
                            TaskDraft task,
                            DailyReportDraft report) {
                        return contexts.validate(access, task, report);
                    }

                    @Override
                    public WorkActor authorize(
                            WorkAiDraftContextReader.Access access) {
                        return contexts.authorize(access);
                    }

                    @Override
                    public void validateFacts(
                            WorkActor actor, Operation operation,
                            TaskDraft task, DailyReportDraft report) {
                        contexts.validateFacts(actor, operation, task, report);
                    }
                }, store::execute, sealer,
                new WorkAiDraftCommandCodec(), clock, COMMAND_TTL);
    }

    AiWorkDraftAdapter(
            Context contexts,
            ExecutionStore store,
            WorkAiDraftCommandSealer sealer,
            WorkAiDraftCommandCodec codec,
            Clock clock,
            Duration commandTtl
    ) {
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
        contexts.validate(access(request), request.task(), request.report());
        var expiresAt = clock.instant().plus(commandTtl)
                .truncatedTo(ChronoUnit.MICROS);
        var command = codec.command(request, expiresAt);
        var plaintext = codec.encode(command);
        var sealed = sealer.seal(plaintext, binding(command));
        return new PreparedDraft(
                new DraftPreview(
                        command.operation(), command.task(), command.report()),
                sealed, expiresAt);
    }

    @Override
    @Transactional
    public DraftReadback execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        var plaintext = sealer.open(
                request.sealedCommand(), binding(request));
        if (!WorkAiDraftCommandSealer.equal(
                WorkAiDraftCommandSealer.sha256(plaintext),
                request.sealedCommand().commandSha256())) {
            throw WorkAiDraftCommandSealer.invalid();
        }
        var command = codec.decode(plaintext);
        var actor = contexts.authorize(access(request));
        assertIdentity(request, command);
        if (!command.expiresAt().isAfter(clock.instant())) {
            throw new BusinessException(
                    "AI_WORK_DRAFT_COMMAND_EXPIRED",
                    "The Work draft owner command expired",
                    HttpStatus.CONFLICT);
        }
        return store.execute(
                command, actor,
                () -> contexts.validateFacts(
                        actor, command.operation(),
                        command.task(), command.report()),
                request.idempotencyKey(), request.requestId(),
                request.traceId());
    }

    private static void assertIdentity(
            ExecuteRequest request,
            WorkAiDraftCommandCodec.Command command
    ) {
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
            throw WorkAiDraftCommandSealer.invalid();
        }
        if (command.authorizationEpoch()
                != request.authorizationEpoch()
                || !command.effectivePermissions().equals(permissions)) {
            throw new BusinessException(
                    "AI_WORK_AUTHORIZATION_STALE",
                    "Work authorization changed before draft confirmation",
                    HttpStatus.CONFLICT);
        }
    }

    private static WorkAiDraftContextReader.Access access(
            PrepareRequest request
    ) {
        return new WorkAiDraftContextReader.Access(
                request.systemId(), request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(),
                request.operation());
    }

    private static WorkAiDraftContextReader.Access access(
            ExecuteRequest request
    ) {
        return new WorkAiDraftContextReader.Access(
                request.systemId(), request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(),
                request.operation());
    }

    private static WorkAiDraftCommandSealer.Binding binding(
            WorkAiDraftCommandCodec.Command command
    ) {
        return new WorkAiDraftCommandSealer.Binding(
                command.accountId(), command.systemId(), command.tenantId(),
                command.memberId(), command.proposalId(), command.sessionId(),
                command.turnId(), command.operation());
    }

    private static WorkAiDraftCommandSealer.Binding binding(
            ExecuteRequest request
    ) {
        return new WorkAiDraftCommandSealer.Binding(
                request.accountId(), request.systemId(), request.tenantId(),
                request.memberId(), request.proposalId(), request.sessionId(),
                request.turnId(), request.operation());
    }

    @FunctionalInterface
    interface ExecutionStore {
        DraftReadback execute(
                WorkAiDraftCommandCodec.Command command,
                WorkActor actor,
                Runnable liveFactCheck,
                String idempotencyKey,
                String requestId,
                String traceId);
    }

    interface Context {
        WorkActor validate(
                WorkAiDraftContextReader.Access access,
                TaskDraft task,
                DailyReportDraft report);

        WorkActor authorize(WorkAiDraftContextReader.Access access);

        void validateFacts(
                WorkActor actor,
                Operation operation,
                TaskDraft task,
                DailyReportDraft report);
    }
}
