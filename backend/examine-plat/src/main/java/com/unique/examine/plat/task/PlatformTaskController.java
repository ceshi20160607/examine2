package com.unique.examine.plat.task;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/tasks")
public class PlatformTaskController {
    private final PlatformTaskLifecycleService tasks;

    public PlatformTaskController(PlatformTaskLifecycleService tasks) {
        this.tasks = tasks;
    }

    @GetMapping
    public ApiResponse<PlatformTaskApi.Page> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestAttribute(
                    value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.require(sessionValue);
        return success(tasks.list(session, status, page, size), request);
    }

    @PostMapping("/{taskId}:complete")
    public ApiResponse<PlatformTaskApi.TaskView> complete(
            @PathVariable String taskId,
            @RequestBody PlatformTaskApi.VersionCommand body,
            @RequestAttribute(
                    value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.require(sessionValue);
        return success(tasks.complete(session, taskId, body.version()), request);
    }

    @PostMapping("/{taskId}:reopen")
    public ApiResponse<PlatformTaskApi.TaskView> reopen(
            @PathVariable String taskId,
            @RequestBody PlatformTaskApi.VersionCommand body,
            @RequestAttribute(
                    value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.require(sessionValue);
        return success(tasks.reopen(session, taskId, body.version()), request);
    }

    @PostMapping("/{taskId}:cancel")
    public ApiResponse<PlatformTaskApi.TaskView> cancel(
            @PathVariable String taskId,
            @RequestBody PlatformTaskApi.VersionCommand body,
            @RequestAttribute(
                    value = AuthenticatedSession.REQUEST_ATTRIBUTE,
                    required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.require(sessionValue);
        return success(tasks.cancel(session, taskId, body.version()), request);
    }

    private static <T> ApiResponse<T> success(
            T value, HttpServletRequest request) {
        return ApiResponse.success(
                value,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
