package com.unique.examine.module.kpi.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.kpi.api.KpiMapping;
import com.unique.examine.module.kpi.api.KpiRequests;
import com.unique.examine.module.kpi.api.KpiViews;
import com.unique.examine.module.kpi.domain.KpiActor;
import com.unique.examine.module.kpi.domain.KpiException;
import com.unique.examine.module.kpi.service.KpiService;
import com.unique.examine.module.manage.security.ConfigSession;
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
@RequestMapping("/api/v1/systems/{systemId}/admin/kpis")
public class KpiAdminController {
    private final KpiService service;

    public KpiAdminController(KpiService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<KpiViews.Definition>> list(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(service.list(actor).stream()
                .map(KpiMapping::definition).toList(), request);
    }

    @PostMapping
    public ApiResponse<KpiViews.Definition> create(
            @PathVariable long systemId,
            @Valid @RequestBody KpiRequests.Create body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(KpiMapping.definition(service.create(
                actor, body.code(), body.name(), body.description(),
                KpiMapping.draft(body))), request);
    }

    @GetMapping("/{kpiId}")
    public ApiResponse<KpiViews.Definition> detail(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(KpiMapping.definition(service.detail(
                actor(value, systemId), kpiId)), request);
    }

    @PutMapping("/{kpiId}/draft")
    public ApiResponse<KpiViews.Definition> saveDraft(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @Valid @RequestBody KpiRequests.SaveDraft body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(KpiMapping.definition(service.saveDraft(
                actor, kpiId, body.expectedVersion(), body.name(),
                body.description(), KpiMapping.draft(body))), request);
    }

    @PostMapping("/{kpiId}/draft:check")
    public ApiResponse<KpiViews.CheckResult> check(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(KpiMapping.check(service.check(
                actor(value, systemId), kpiId)), request);
    }

    @PostMapping("/{kpiId}/draft:publish")
    public ApiResponse<KpiViews.PublishResult> publish(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @Valid @RequestBody KpiRequests.Publish body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var version = service.publish(actor, kpiId, body.expectedVersion());
        var definition = service.detail(actor, kpiId);
        return ok(new KpiViews.PublishResult(
                KpiMapping.definition(definition),
                KpiMapping.version(version, definition.activeVersionId())),
                request);
    }

    @GetMapping("/{kpiId}/versions")
    public ApiResponse<List<KpiViews.Version>> versions(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var definition = service.detail(actor, kpiId);
        return ok(service.versions(actor, kpiId).stream()
                .map(version -> KpiMapping.version(
                        version, definition.activeVersionId()))
                .toList(), request);
    }

    @GetMapping("/{kpiId}/versions/{versionNumber}")
    public ApiResponse<KpiViews.Version> version(
            @PathVariable long systemId,
            @PathVariable long kpiId,
            @PathVariable int versionNumber,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var definition = service.detail(actor, kpiId);
        return ok(KpiMapping.version(service.version(
                        actor, kpiId, versionNumber),
                definition.activeVersionId()), request);
    }

    static KpiActor actor(Object value, long systemId) {
        var session = ConfigSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new KpiException("KPI_TENANT_REQUIRED",
                    "Select an active tenant before managing KPI definitions");
        }
        return new KpiActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
