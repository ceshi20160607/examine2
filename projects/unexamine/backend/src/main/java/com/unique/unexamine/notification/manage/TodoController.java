package com.unique.unexamine.notification.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
    private final TodoService service;

    public TodoController(TodoService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<TodoModels.TodoView>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "ALL") String type) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), status, type));
    }

    @PostMapping("/{todoId}/actions")
    public ApiResult<TodoModels.HandleResult> handle(
            @PathVariable Long todoId, @Valid @RequestBody TodoModels.HandleRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.handle(AuthenticationContextHolder.require(), todoId, body,
                TraceIdFilter.current(request)));
    }
}
