package com.unique.examine.work.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotencyFacade;
import com.unique.examine.core.api.IdempotencyRecord;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.api.OutboxEvent;
import com.unique.examine.core.api.OutboxFacade;
import com.unique.examine.core.api.WorkTaskCreationFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.service.WorkTaskService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** Authoritative non-AI adapter for idempotent cross-domain Work task creation. */
@Component
public class WorkTaskCreationAdapter implements WorkTaskCreationFacade {
    private static final String IDEMPOTENCY_SCOPE = "WORK_TASK_CREATE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final WorkTaskService tasks;
    private final IdempotencyFacade idempotency;
    private final OperationAuditFacade audits;
    private final OutboxFacade outbox;
    private final ObjectMapper json;

    public WorkTaskCreationAdapter(
            WorkTaskService tasks,
            IdempotencyFacade idempotency,
            OperationAuditFacade audits,
            OutboxFacade outbox,
            ObjectMapper json) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    @Transactional
    public CreatedTask create(Command command) {
        Objects.requireNonNull(command, "command");
        try {
            requireCreatePermission(command);
            var hash = requestHash(command);
            var existing = idempotency.find(
                    IDEMPOTENCY_SCOPE, scopeKey(command), command.idempotencyKey());
            if (existing.isPresent()) {
                var replay = replay(existing.get(), hash);
                audits.recordSuccess(successAudit(command, replay, "WORK_TASK_CREATE_REPLAY"));
                return replay;
            }
            var id = begin(command, hash);
            var task = tasks.create(
                    actor(command), command.title(), command.assigneeMemberId(),
                    command.projectId(), command.description(), command.dueAt());
            var result = view(task, false);
            outbox.enqueue(new OutboxEvent(
                    "WORK_TASK_CREATED", 1,
                    new AggregateRef("WORK_TASK", Long.toString(task.id())),
                    new OutboxEvent.Context(task.systemId(), task.tenantId()),
                    command.idempotencyKey(), result, command.traceId()));
            audits.recordSuccess(successAudit(command, result, "WORK_TASK_CREATE"));
            idempotency.complete(id, 201, "CREATED", write(result));
            return result;
        } catch (WorkDomainException failure) {
            recordFailure(command, failure);
            throw ownerFailure(failure);
        } catch (RuntimeException failure) {
            recordFailure(command, failure);
            throw failure;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TaskState state(StateQuery query) {
        Objects.requireNonNull(query, "query");
        try {
            var task = tasks.get(new WorkActor(
                    query.systemId(), query.tenantId(), query.memberId(),
                    query.effectivePermissions()), query.taskId());
            return new TaskState(task.id(), task.version(), task.status().name());
        } catch (WorkDomainException failure) {
            throw ownerFailure(failure);
        }
    }

    private long begin(Command command, String hash) {
        try {
            return idempotency.begin(
                    IDEMPOTENCY_SCOPE, scopeKey(command), command.idempotencyKey(),
                    hash, IDEMPOTENCY_TTL);
        } catch (DataIntegrityViolationException conflict) {
            throw new BusinessException(
                    "WORK_TASK_REQUEST_IN_PROGRESS",
                    "The Work task creation request is already being processed",
                    HttpStatus.CONFLICT);
        }
    }

    private CreatedTask replay(IdempotencyRecord record, String hash) {
        if (!hash.equals(record.requestHash())) {
            throw new BusinessException(
                    "WORK_TASK_IDEMPOTENCY_CONFLICT",
                    "The Work task idempotency key was reused for different input",
                    HttpStatus.CONFLICT);
        }
        if (!"COMPLETED".equals(record.status()) || record.responseBody() == null) {
            throw new BusinessException(
                    "WORK_TASK_REQUEST_IN_PROGRESS",
                    "The Work task creation request is still being processed",
                    HttpStatus.CONFLICT);
        }
        try {
            return json.readValue(record.responseBody(), CreatedTask.class).asReplay();
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException(
                    "Cannot read the idempotent Work task result", invalid);
        }
    }

    private void recordFailure(Command command, RuntimeException failure) {
        try {
            var code = failure instanceof BusinessException business
                    ? business.code()
                    : failure instanceof WorkDomainException work
                    ? work.code() : failure.getClass().getSimpleName();
            audits.recordFailed(OperationAudit.failed(
                    new OperationAudit.Actor(command.accountId(), "USER"),
                    context(command), command.source(), "WORK_TASK_CREATE",
                    null, null, new OperationAudit.Failure(bounded(code)),
                    command.requestId(), command.traceId()));
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
        }
    }

    private static OperationAudit successAudit(
            Command command, CreatedTask result, String action) {
        return OperationAudit.success(
                new OperationAudit.Actor(command.accountId(), "USER"),
                context(command),
                new AggregateRef("WORK_TASK", Long.toString(result.taskId())),
                action, command.source(), result,
                command.requestId(), command.traceId());
    }

    private static OperationAudit.Context context(Command command) {
        return new OperationAudit.Context(
                ContextType.SYSTEM, command.systemId(), command.tenantId());
    }

    private static WorkActor actor(Command command) {
        return new WorkActor(
                command.systemId(), command.tenantId(),
                command.creatorMemberId(), command.effectivePermissions());
    }

    private static void requireCreatePermission(Command command) {
        if (!command.effectivePermissions().contains(WorkTaskService.CREATE)) {
            throw new BusinessException(
                    "WORK_TASK_FORBIDDEN",
                    "Missing permission: " + WorkTaskService.CREATE,
                    HttpStatus.FORBIDDEN);
        }
    }

    private static BusinessException ownerFailure(WorkDomainException failure) {
        var status = switch (failure.code()) {
            case "WORK_TASK_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "WORK_TASK_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "WORK_TASK_VERSION_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return new BusinessException(failure.code(), failure.getMessage(), status);
    }

    private static CreatedTask view(WorkTask task, boolean replay) {
        return new CreatedTask(
                task.id(), task.version(), task.systemId(), task.tenantId(),
                task.creatorMemberId(), task.assigneeMemberId(), task.title(),
                task.description(), task.projectId(), task.dueAt(),
                task.status().name(), task.createdAt(), replay);
    }

    private String requestHash(Command command) {
        return sha256(write(new Fingerprint(
                command.systemId(), command.tenantId(), command.creatorMemberId(),
                command.assigneeMemberId(), command.title(), command.description(),
                command.projectId(), command.dueAt(), command.source().type(),
                command.source().id())));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException(
                    "Work task creation payload must be JSON serializable", invalid);
        }
    }

    private static String scopeKey(Command command) {
        return command.systemId() + ":" + command.tenantId() + ":"
                + command.source().type() + ":" + command.source().id();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static String bounded(String value) {
        var normalized = value == null || value.isBlank()
                ? "WORK_TASK_CREATE_FAILED" : value;
        return normalized.length() <= 64
                ? normalized : normalized.substring(0, 64);
    }

    private record Fingerprint(
            long systemId,
            long tenantId,
            long creatorMemberId,
            long assigneeMemberId,
            String title,
            String description,
            Long projectId,
            java.time.Instant dueAt,
            String sourceType,
            String sourceId) {
    }
}
