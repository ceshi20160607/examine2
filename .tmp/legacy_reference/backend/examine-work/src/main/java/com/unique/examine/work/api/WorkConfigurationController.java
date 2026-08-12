package com.unique.examine.work.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.work.configuration.WorkConfiguration;
import com.unique.examine.work.configuration.WorkConfigurationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/work/configuration")
public final class WorkConfigurationController {
    private final WorkConfigurationService configurations;

    public WorkConfigurationController(WorkConfigurationService configurations) {
        this.configurations = configurations;
    }

    @PostMapping("/drafts")
    public ResponseEntity<ApiResponse<WorkConfigurationApiModels.ConfigurationView>> draft(
            @PathVariable long systemId,
            @RequestBody WorkConfigurationApiModels.DraftBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        var created = configurations.createDraft(
                admin(session, systemId), body.snapshot());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(
                        WorkConfigurationApiModels.ConfigurationView.from(created),
                        request));
    }

    @PostMapping("/drafts/{configurationId}:publish")
    public ApiResponse<WorkConfigurationApiModels.ConfigurationView> publish(
            @PathVariable long systemId,
            @PathVariable long configurationId,
            @RequestBody WorkConfigurationApiModels.PublishBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        return success(WorkConfigurationApiModels.ConfigurationView.from(
                configurations.publish(admin(session, systemId),
                        configurationId, body.version())), request);
    }

    @PostMapping("/drafts/{configurationId}:check")
    public ApiResponse<WorkConfigurationService.PublishCheck> check(
            @PathVariable long systemId,
            @PathVariable long configurationId,
            @RequestBody WorkConfigurationApiModels.PublishBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        return success(configurations.check(admin(session, systemId),
                configurationId, body.version()), request);
    }

    @PostMapping(":rollback")
    public ApiResponse<WorkConfigurationApiModels.ConfigurationView> rollback(
            @PathVariable long systemId,
            @RequestBody WorkConfigurationApiModels.RollbackBody body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        return success(WorkConfigurationApiModels.ConfigurationView.from(
                configurations.rollback(admin(session, systemId),
                        body.targetRevision())), request);
    }

    @GetMapping("/active")
    public ApiResponse<WorkConfigurationApiModels.ConfigurationView> active(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        return success(WorkConfigurationApiModels.ConfigurationView.from(
                configurations.active(admin(session, systemId))), request);
    }

    @GetMapping("/history")
    public ApiResponse<WorkConfigurationApiModels.History> history(
            @PathVariable long systemId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        return success(new WorkConfigurationApiModels.History(
                configurations.history(admin(session, systemId)).stream()
                        .map(WorkConfigurationApiModels.ConfigurationView::from)
                        .toList()), request);
    }

    @GetMapping("/runtime/{objectType}")
    public ApiResponse<WorkConfigurationService.RuntimeView> runtime(
            @PathVariable long systemId,
            @PathVariable WorkConfiguration.ObjectType objectType,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object session,
            HttpServletRequest request) {
        var permission = objectType == WorkConfiguration.ObjectType.DAILY_REPORT
                ? "work.report.access" : "work.task.access";
        return success(configurations.runtimeView(
                WorkRequestSession.require(session, systemId, permission),
                objectType, 0), request);
    }

    private static com.unique.examine.work.domain.WorkActor admin(
            Object session, long systemId) {
        return WorkRequestSession.require(
                session, systemId, WorkConfigurationService.MANAGE);
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
