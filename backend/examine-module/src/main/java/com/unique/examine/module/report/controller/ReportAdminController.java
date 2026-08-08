package com.unique.examine.module.report.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.api.ReportMapping;
import com.unique.examine.module.report.api.ReportRequests;
import com.unique.examine.module.report.api.ReportViews;
import com.unique.examine.module.report.domain.ReportActor;
import com.unique.examine.module.report.domain.ReportException;
import com.unique.examine.module.report.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/reports")
public class ReportAdminController {
    private final ReportService service;

    public ReportAdminController(ReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ReportViews.Definition>> list(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(service.list(actor).stream()
                .map(ReportMapping::definition).toList(), request);
    }

    @PostMapping
    public ApiResponse<ReportViews.Definition> create(
            @PathVariable long systemId,
            @Valid @RequestBody ReportRequests.Create body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(ReportMapping.definition(service.create(
                actor, body.code(), body.name(), body.description(),
                ReportMapping.draft(body))), request);
    }

    @GetMapping("/{reportId}")
    public ApiResponse<ReportViews.Definition> detail(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(ReportMapping.definition(service.detail(
                actor(value, systemId), reportId)), request);
    }

    @PutMapping("/{reportId}/draft")
    public ApiResponse<ReportViews.Definition> saveDraft(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @Valid @RequestBody ReportRequests.SaveDraft body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(ReportMapping.definition(service.saveDraft(
                actor, reportId, body.expectedVersion(), body.name(),
                body.description(), ReportMapping.draft(body))), request);
    }

    @PostMapping("/{reportId}/draft:check")
    public ApiResponse<ReportViews.CheckResult> check(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(ReportMapping.check(service.check(
                actor(value, systemId), reportId)), request);
    }

    @PostMapping("/{reportId}/draft:publish")
    public ApiResponse<ReportViews.PublishResult> publish(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @Valid @RequestBody ReportRequests.Publish body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var version = service.publish(actor, reportId, body.expectedVersion());
        var definition = service.detail(actor, reportId);
        return ok(new ReportViews.PublishResult(
                ReportMapping.definition(definition),
                ReportMapping.version(version, definition.activeVersionId())),
                request);
    }

    @GetMapping("/{reportId}/versions")
    public ApiResponse<List<ReportViews.Version>> versions(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var definition = service.detail(actor, reportId);
        return ok(service.versions(actor, reportId).stream()
                .map(version -> ReportMapping.version(
                        version, definition.activeVersionId()))
                .toList(), request);
    }

    @GetMapping("/{reportId}/versions/{versionNumber}")
    public ApiResponse<ReportViews.Version> version(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @PathVariable int versionNumber,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var definition = service.detail(actor, reportId);
        return ok(ReportMapping.version(service.version(
                        actor, reportId, versionNumber),
                definition.activeVersionId()), request);
    }

    static ReportActor actor(Object value, long systemId) {
        var session = ConfigSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new ReportException("REPORT_TENANT_REQUIRED",
                    "Select an active tenant before managing reports");
        }
        return new ReportActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
