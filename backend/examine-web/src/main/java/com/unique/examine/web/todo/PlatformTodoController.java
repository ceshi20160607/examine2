package com.unique.examine.web.todo;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.task.PlatformTaskLifecycleService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/platform/todos")
public class PlatformTodoController {
    private final PlatformTaskLifecycleService tasks;
    private final PlatformTodoStore store;

    public PlatformTodoController(
            PlatformTaskLifecycleService tasks, PlatformTodoStore store) {
        this.tasks = tasks;
        this.store = store;
    }

    @GetMapping
    public ApiResponse<PlatformTodoApiModels.Page> query(
            @RequestParam(defaultValue = "OPEN")
            PlatformTodoApiModels.StateFilter state,
            @RequestParam(defaultValue = "ALL")
            PlatformTodoApiModels.TypeFilter type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        var caller = SessionGuard.requirePlatform(session, "platform.task.read");
        return success(store.page(caller.accountId(), state, type, page, size), request);
    }

    /** Compatibility seam for direct controller contract tests. */
    public ApiResponse<PlatformTodoApiModels.Page> page(
            PlatformTodoApiModels.StateFilter state, int page, int size,
            Object session, HttpServletRequest request) {
        var caller = SessionGuard.requirePlatform(session, "platform.task.read");
        return success(store.page(caller.accountId(), state,
                PlatformTodoApiModels.TypeFilter.ALL, page, size), request);
    }

    @GetMapping("/counts")
    public ApiResponse<PlatformTodoApiModels.Counts> counts(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        var caller = SessionGuard.requirePlatform(session, "platform.task.read");
        return success(store.counts(caller.accountId()), request);
    }

    @GetMapping("/{todoId}")
    public ApiResponse<PlatformTodoApiModels.TodoView> detail(
            @PathVariable long todoId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        var caller = SessionGuard.requirePlatform(session, "platform.task.read");
        return success(store.find(caller.accountId(), todoId)
                .orElseThrow(PlatformTodoController::notFound), request);
    }

    @PostMapping("/{todoId}:action")
    @Transactional
    public ApiResponse<PlatformTodoApiModels.ActionResult> action(
            @PathVariable long todoId,
            @RequestBody PlatformTodoApiModels.ActionBody body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        var caller = SessionGuard.requirePlatform(session, "platform.task.manage");
        requireAction(body, idempotencyKey);
        var replay = store.replay(caller.accountId(), idempotencyKey).orElse(null);
        if (replay != null) {
            if (replay.taskId() != todoId || replay.action() != body.action()
                    || replay.requestVersion() != body.version()) {
                throw new BusinessException(
                        "PLATFORM_TODO_IDEMPOTENCY_CONFLICT",
                        "Platform Todo idempotency key belongs to another action",
                        HttpStatus.CONFLICT);
            }
            return success(new PlatformTodoApiModels.ActionResult(
                    replay.result(), true), request);
        }
        var taskId = Long.toString(todoId);
        switch (body.action()) {
            case COMPLETE -> tasks.complete(caller, taskId, body.version());
            case REOPEN -> tasks.reopen(caller, taskId, body.version());
            case CANCEL -> tasks.cancel(caller, taskId, body.version());
        }
        var result = store.find(caller.accountId(), todoId)
                .orElseThrow(PlatformTodoController::notFound);
        store.saveReplay(caller.accountId(), idempotencyKey, todoId,
                body.action(), body.version(), result, Instant.now());
        return success(new PlatformTodoApiModels.ActionResult(result, false), request);
    }

    private static void requireAction(
            PlatformTodoApiModels.ActionBody body, String idempotencyKey) {
        if (body == null || body.action() == null || body.version() < 0
                || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 128
                || !idempotencyKey.matches(
                "^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$")) {
            throw new BusinessException(
                    "PLATFORM_TODO_ACTION_INVALID",
                    "Action, version and Idempotency-Key are required",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(
                "PLATFORM_TODO_NOT_FOUND", "Platform Todo was not found",
                HttpStatus.NOT_FOUND);
    }

    private static <T> ApiResponse<T> success(
            T data, HttpServletRequest request) {
        return ApiResponse.success(data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
