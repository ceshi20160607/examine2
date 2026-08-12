package com.unique.examine.plat.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Authoritative owner for prepare/confirm of a personal platform task. */
@Component
public class PlatformTaskOwner implements PlatformTaskFacade {
    static final Duration COMMAND_TTL = Duration.ofMinutes(15);
    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "platform.ai.agent.use", "platform.task.create");

    private final PlatformTaskAccess access;
    private final PlatformTaskStore tasks;
    private final PlatformTaskCommandSealer sealer;
    private final PlatformTaskCommandCodec codec;
    private final IdService ids;
    private final Clock clock;
    private final Duration commandTtl;

    @Autowired
    public PlatformTaskOwner(
            LivePlatformTaskAccess access,
            JdbcPlatformTaskStore tasks,
            PlatformTaskCommandSealer sealer,
            ObjectMapper json,
            IdService ids) {
        this(access, tasks, sealer, new PlatformTaskCommandCodec(json),
                ids, Clock.systemUTC(), COMMAND_TTL);
    }

    PlatformTaskOwner(
            PlatformTaskAccess access,
            PlatformTaskStore tasks,
            PlatformTaskCommandSealer sealer,
            PlatformTaskCommandCodec codec,
            IdService ids,
            Clock clock,
            Duration commandTtl) {
        this.access = Objects.requireNonNull(access, "access");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.sealer = Objects.requireNonNull(sealer, "sealer");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commandTtl = Objects.requireNonNull(commandTtl, "commandTtl");
        if (commandTtl.isNegative() || commandTtl.isZero()
                || commandTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("commandTtl is invalid");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PreparedTask prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        authorize(request.accountId(), request.authorizationEpoch());
        var now = clock.instant();
        var expiresAt = now.plus(commandTtl);
        var draft = normalize(request.draft(), now);
        var encoded = codec.encode(
                request.proposalId(), request.accountId(),
                request.authorizationEpoch(), draft, expiresAt);
        var sealed = sealer.seal(
                encoded.plaintext(), encoded.payloadHash(),
                binding(request.accountId(), request.proposalId()));
        return new PreparedTask(
                new TaskPreview(
                        request.accountId(), draft.title(), draft.description(),
                        draft.dueAt(), draft.priority(), Status.OPEN, Source.AGENT),
                expiresAt, sealed);
    }

    @Override
    @Transactional
    public TaskView execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        authorize(request.accountId(), request.authorizationEpoch());
        var opened = codec.decode(sealer.open(
                request.sealedCommand(),
                binding(request.accountId(), request.proposalId())));
        if (!opened.proposalId().equals(request.proposalId())
                || opened.accountId() != request.accountId()
                || opened.authorizationEpoch() != request.authorizationEpoch()
                || !PlatformTaskCommandSealer.equal(
                opened.payloadHash(), request.sealedCommand().commandSha256())) {
            throw PlatformTaskCommandCodec.invalid();
        }
        var now = clock.instant();
        if (!opened.expiresAt().isAfter(now)) {
            throw new BusinessException(
                    "PLATFORM_TASK_COMMAND_EXPIRED",
                    "The platform task owner command expired",
                    HttpStatus.CONFLICT);
        }
        if (opened.dueAt() != null && !opened.dueAt().isAfter(now)) {
            throw invalidDraft("dueAt must remain in the future");
        }
        var candidate = new PlatformTask(
                ids.nextId(), request.accountId(), opened.title(),
                opened.description(), opened.dueAt(), opened.priority(),
                Status.OPEN, Source.AGENT, request.authorizationEpoch(),
                opened.payloadHash(), request.idempotencyKey(),
                request.requestId(), request.traceId(), now, request.accountId());
        var stored = tasks.createOrReplay(candidate);
        if (!PlatformTaskCommandSealer.equal(
                stored.payloadHash(), candidate.payloadHash())) {
            throw new BusinessException(
                    "PLATFORM_TASK_IDEMPOTENCY_CONFLICT",
                    "The platform task idempotency key was already used",
                    HttpStatus.CONFLICT);
        }
        return view(stored);
    }

    private void authorize(long accountId, long expectedEpoch) {
        var live = access.current(accountId);
        if (live.epoch() != expectedEpoch) {
            throw new BusinessException(
                    "PLATFORM_TASK_AUTHORIZATION_STALE",
                    "Platform authorization changed before the task operation",
                    HttpStatus.CONFLICT);
        }
        if (!live.permissions().containsAll(REQUIRED_PERMISSIONS)) {
            throw new BusinessException(
                    "PLATFORM_TASK_PERMISSION_DENIED",
                    "Platform Agent use and task creation are required",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static TaskDraft normalize(TaskDraft value, Instant now) {
        var title = normalized(value.title(), 200, false);
        var description = normalized(value.description(), 2_000, true);
        if (value.dueAt() != null && !value.dueAt().isAfter(now)) {
            throw invalidDraft("dueAt must be in the future");
        }
        return new TaskDraft(
                title, description, value.dueAt(), value.priority());
    }

    private static String normalized(
            String value, int maximum, boolean nullable) {
        if (nullable && value == null) return null;
        if (value == null || value.isBlank()) throw invalidDraft("text is required");
        value = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip().replaceAll("\\s+", " ");
        if (value.isBlank() || value.codePointCount(0, value.length()) > maximum) {
            throw invalidDraft("text is too long");
        }
        return value;
    }

    private static PlatformTaskCommandSealer.Binding binding(
            long accountId, String proposalId) {
        return new PlatformTaskCommandSealer.Binding(accountId, proposalId);
    }

    private static TaskView view(PlatformTask value) {
        return new TaskView(
                Long.toString(value.id()), value.accountId(), value.title(),
                value.description(), value.dueAt(), value.priority(),
                value.status(), value.source(), value.authorizationEpoch(),
                value.payloadHash(), value.requestId(), value.traceId(),
                value.createdAt(), value.createdBy());
    }

    private static BusinessException invalidDraft(String message) {
        return new BusinessException(
                "PLATFORM_TASK_DRAFT_INVALID", message,
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
