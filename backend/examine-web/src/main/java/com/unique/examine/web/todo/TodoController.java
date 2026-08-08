package com.unique.examine.web.todo;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.todo.domain.TodoActor;
import com.unique.examine.todo.domain.TodoQuery;
import com.unique.examine.todo.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/v1/systems/{systemId}")
public final class TodoController {
    private final TodoService todos;

    public TodoController(TodoService todos) {
        if (todos == null) {
            throw new IllegalArgumentException("Todo service is required");
        }
        this.todos = todos;
    }

    @PostMapping("/todos:refresh")
    public ApiResponse<TodoApiModels.Refresh> refresh(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        var result = todos.refresh(actor(session, systemId, request));
        return success(TodoApiModels.Refresh.from(result), request);
    }

    @GetMapping("/todos")
    public ApiResponse<TodoApiModels.Page> page(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "ALL")
            TodoQuery.CategoryFilter category,
            @RequestParam(defaultValue = "OPEN")
            TodoQuery.StateFilter state,
            @RequestParam(defaultValue = "ALL")
            TodoQuery.TimeFilter time,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        var day = LocalDate.now(ZoneOffset.UTC);
        var todayStart = day.atStartOfDay().toInstant(ZoneOffset.UTC);
        var tomorrowStart = day.plusDays(1)
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        var result = todos.page(
                actor(session, systemId, request),
                new TodoQuery(
                        category, state, time, page, size,
                        todayStart, tomorrowStart));
        return success(TodoApiModels.Page.from(result), request);
    }

    @GetMapping("/todos/counts")
    public ApiResponse<TodoApiModels.Counts> counts(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(
                TodoApiModels.Counts.from(todos.counts(
                        actor(session, systemId, request))),
                request);
    }

    @GetMapping("/todos/{todoId}")
    public ApiResponse<TodoApiModels.Detail> detail(
            @PathVariable long systemId,
            @PathVariable long todoId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        return success(
                TodoApiModels.Detail.from(todos.detail(
                        actor(session, systemId, request), todoId)),
                request);
    }

    @PostMapping("/todos/{todoId}:action")
    public ApiResponse<TodoApiModels.ActionResult> action(
            @PathVariable long systemId,
            @PathVariable long todoId,
            @RequestBody TodoApiModels.ActionBody body,
            @RequestHeader(
                    name = "Idempotency-Key",
                    required = false) String idempotencyKey,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request
    ) {
        var result = todos.action(
                actor(session, systemId, request), todoId,
                body == null ? 0 : body.version(),
                body == null ? null : body.action(),
                body == null ? null : body.comment(),
                body == null ? null : body.reason(),
                idempotencyKey);
        return success(TodoApiModels.ActionResult.from(result), request);
    }

    private static TodoActor actor(
            Object value,
            long systemId,
            HttpServletRequest request
    ) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED", "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != systemId
                || session.tenantId() == null
                || session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system member context does not match the request",
                    HttpStatus.FORBIDDEN);
        }
        var requestId = attribute(request, WebRequestAttributes.REQUEST_ID);
        if (requestId.isBlank()) {
            requestId = request.getRequestId();
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = "todo-request";
        }
        var traceId = attribute(request, WebRequestAttributes.TRACE_ID);
        if (traceId.isBlank()) {
            traceId = requestId;
        }
        return new TodoActor(
                session.accountId(), systemId, session.tenantId(), session.memberId(),
                session.permissions(), requestId, traceId);
    }

    private static <T> ApiResponse<T> success(
            T data,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(
            HttpServletRequest request,
            String name
    ) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
