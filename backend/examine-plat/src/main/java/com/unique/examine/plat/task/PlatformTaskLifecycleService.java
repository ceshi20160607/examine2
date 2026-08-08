package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class PlatformTaskLifecycleService {
    static final String READ = "platform.task.read";
    static final String MANAGE = "platform.task.manage";

    private final PlatformTaskLifecycleStore tasks;
    private final Clock clock;

    @Autowired
    public PlatformTaskLifecycleService(JdbcPlatformTaskStore tasks) {
        this(tasks, Clock.systemUTC());
    }

    PlatformTaskLifecycleService(
            PlatformTaskLifecycleStore tasks, Clock clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public PlatformTaskApi.Page list(
            AuthenticatedSession session,
            String statusValue,
            Integer pageValue,
            Integer sizeValue) {
        var caller = SessionGuard.requirePlatform(session, READ);
        var status = status(statusValue);
        var page = page(pageValue);
        var size = size(sizeValue);
        var offset = Math.multiplyExact(page - 1, size);
        var total = tasks.countOwn(caller.accountId(), status);
        var items = total == 0
                ? java.util.List.<PlatformTaskApi.TaskView>of()
                : tasks.findOwnPage(caller.accountId(), status, offset, size);
        return new PlatformTaskApi.Page(items, page, size, total);
    }

    @Transactional
    public PlatformTaskApi.TaskView complete(
            AuthenticatedSession session, String taskId, long version) {
        return transition(session, taskId, version,
                Set.of(PlatformTaskFacade.Status.OPEN),
                PlatformTaskFacade.Status.COMPLETED);
    }

    @Transactional
    public PlatformTaskApi.TaskView reopen(
            AuthenticatedSession session, String taskId, long version) {
        return transition(session, taskId, version,
                Set.of(PlatformTaskFacade.Status.COMPLETED,
                        PlatformTaskFacade.Status.CANCELLED),
                PlatformTaskFacade.Status.OPEN);
    }

    @Transactional
    public PlatformTaskApi.TaskView cancel(
            AuthenticatedSession session, String taskId, long version) {
        return transition(session, taskId, version,
                Set.of(PlatformTaskFacade.Status.OPEN),
                PlatformTaskFacade.Status.CANCELLED);
    }

    private PlatformTaskApi.TaskView transition(
            AuthenticatedSession session,
            String taskIdValue,
            long version,
            Set<PlatformTaskFacade.Status> expectedStatuses,
            PlatformTaskFacade.Status targetStatus) {
        var caller = SessionGuard.requirePlatform(session, MANAGE);
        var taskId = id(taskIdValue);
        if (version < 0) throw invalid("version must not be negative");
        var current = tasks.findOwnById(caller.accountId(), taskId)
                .orElseThrow(PlatformTaskLifecycleService::notFound);
        requireTransition(current, version, expectedStatuses);
        var at = nextTimestamp(current.updatedAt(), clock.instant());
        var updated = tasks.transition(
                caller.accountId(), taskId, version, expectedStatuses,
                targetStatus, at);
        if (updated != 1) {
            if (updated != 0) {
                throw new IllegalStateException(
                        "Platform task transition changed multiple rows");
            }
            var latest = tasks.findOwnById(caller.accountId(), taskId)
                    .orElseThrow(PlatformTaskLifecycleService::notFound);
            requireTransition(latest, version, expectedStatuses);
            throw conflict();
        }
        var result = tasks.findOwnById(caller.accountId(), taskId)
                .orElseThrow(() -> new IllegalStateException(
                        "Updated platform task cannot be read"));
        if (result.version() != version + 1
                || result.status() != targetStatus
                || !result.updatedAt().equals(at)) {
            throw new IllegalStateException(
                    "Platform task transition result is inconsistent");
        }
        return result;
    }

    private static void requireTransition(
            PlatformTaskApi.TaskView current,
            long version,
            Set<PlatformTaskFacade.Status> expectedStatuses) {
        if (current.version() != version) throw conflict();
        if (!expectedStatuses.contains(current.status())) throw invalidState();
    }

    private static Instant nextTimestamp(Instant current, Instant now) {
        var candidate = now.truncatedTo(ChronoUnit.MICROS);
        return candidate.isAfter(current)
                ? candidate
                : current.truncatedTo(ChronoUnit.MICROS)
                        .plus(1, ChronoUnit.MICROS);
    }

    private static PlatformTaskStatusFilter status(String value) {
        if (value == null) return PlatformTaskStatusFilter.ALL;
        try {
            if (!value.equals(value.toUpperCase(Locale.ROOT))) throw invalidStatus();
            return PlatformTaskStatusFilter.valueOf(value);
        } catch (RuntimeException failure) {
            if (failure instanceof BusinessException business) throw business;
            throw invalidStatus();
        }
    }

    private static int page(Integer value) {
        var result = value == null ? 1 : value;
        if (result < 1 || result > 10_000) {
            throw invalid("page must be between 1 and 10000");
        }
        return result;
    }

    private static int size(Integer value) {
        var result = value == null ? 20 : value;
        if (result < 1 || result > 100) {
            throw invalid("size must be between 1 and 100");
        }
        return result;
    }

    private static long id(String value) {
        try {
            PlatformTaskApi.positiveDecimal(value, "taskId");
            return Long.parseLong(value);
        } catch (RuntimeException failure) {
            throw invalid("taskId must be a positive decimal");
        }
    }

    private static BusinessException invalidStatus() {
        return invalid("status must be ALL, OPEN, COMPLETED or CANCELLED");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "PLATFORM_TASK_REQUEST_INVALID", message,
                HttpStatus.BAD_REQUEST);
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "PLATFORM_TASK_NOT_FOUND", "Platform task was not found",
                HttpStatus.NOT_FOUND);
    }

    private static BusinessException conflict() {
        return new BusinessException(
                "PLATFORM_TASK_VERSION_CONFLICT", "Platform task version is stale",
                HttpStatus.CONFLICT);
    }

    private static BusinessException invalidState() {
        return new BusinessException(
                "PLATFORM_TASK_STATE_INVALID",
                "Platform task cannot perform the requested transition",
                HttpStatus.CONFLICT);
    }
}
