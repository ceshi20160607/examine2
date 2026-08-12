package com.unique.examine.plat.work;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/work")
public class PlatformWorkController {
    private final PlatformWorkService work;

    public PlatformWorkController(PlatformWorkService work) { this.work = work; }

    @GetMapping("/overview")
    public ApiResponse<PlatformWorkApi.Overview> overview(@RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.overview(SessionGuard.require(value)), request);
    }
    @GetMapping("/projects")
    public ApiResponse<List<PlatformWorkApi.ProjectView>> projects(@RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.projects(SessionGuard.require(value)), request);
    }
    @PostMapping("/projects")
    public ApiResponse<PlatformWorkApi.ProjectView> createProject(@RequestBody PlatformWorkApi.ProjectInput body, @RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.createProject(SessionGuard.require(value), body), request);
    }
    @GetMapping("/tasks")
    public ApiResponse<PlatformWorkApi.TaskPage> tasks(@RequestParam(required=false) String kind, @RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="50") int size, @RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.tasks(SessionGuard.require(value), kind, status, page, size), request);
    }
    @PostMapping("/tasks")
    public ApiResponse<PlatformWorkApi.TaskView> createTask(@RequestBody PlatformWorkApi.TaskInput body, @RequestHeader("Idempotency-Key") String key, @RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.createTask(SessionGuard.require(value), body, key, attribute(request, WebRequestAttributes.REQUEST_ID), attribute(request, WebRequestAttributes.TRACE_ID)), request);
    }
    @GetMapping("/reports")
    public ApiResponse<List<PlatformWorkApi.ReportView>> reports(@RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.reports(SessionGuard.require(value)), request);
    }
    @PostMapping("/reports")
    public ApiResponse<PlatformWorkApi.ReportView> saveReport(@RequestBody PlatformWorkApi.ReportInput body, @RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.saveReport(SessionGuard.require(value), body), request);
    }
    @PostMapping("/reports/{reportId}:submit")
    public ApiResponse<PlatformWorkApi.ReportView> submitReport(@PathVariable String reportId, @RequestBody VersionBody body, @RequestAttribute(value=AuthenticatedSession.REQUEST_ATTRIBUTE, required=false) Object value, HttpServletRequest request) {
        return success(work.submitReport(SessionGuard.require(value), reportId, body.version()), request);
    }

    public record VersionBody(long version) { }
    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) { return ApiResponse.success(data, attribute(request, WebRequestAttributes.REQUEST_ID), attribute(request, WebRequestAttributes.TRACE_ID)); }
    private static String attribute(HttpServletRequest request, String name) { var value=request.getAttribute(name); return value==null?"":String.valueOf(value); }
}
