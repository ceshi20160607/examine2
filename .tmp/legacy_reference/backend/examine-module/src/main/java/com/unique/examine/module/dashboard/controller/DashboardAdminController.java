package com.unique.examine.module.dashboard.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.dashboard.api.DashboardMapping;
import com.unique.examine.module.dashboard.api.DashboardRequests;
import com.unique.examine.module.dashboard.api.DashboardViews;
import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardDraft;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.domain.SystemDashboard;
import com.unique.examine.module.dashboard.service.DashboardService;
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
@RequestMapping("/api/v1/systems/{systemId}/admin/dashboards")
public class DashboardAdminController {
    private final DashboardService service;

    public DashboardAdminController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DashboardViews.Dashboard>> list(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(service.list(actor).stream()
                .filter(root -> root.placement()
                        == DashboardPlacement.SYSTEM_HOME)
                .map(DashboardMapping::dashboard).toList(), request);
    }

    @PostMapping
    public ApiResponse<DashboardViews.Dashboard> create(
            @PathVariable long systemId,
            @Valid @RequestBody DashboardRequests.Create body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var placement = DashboardMapping.placement(body.placement());
        if (placement != DashboardPlacement.SYSTEM_HOME) {
            throw scopedOnly();
        }
        return ok(DashboardMapping.dashboard(service.create(
                actor, body.code(), placement,
                body.name(), body.description(), DashboardDraft.empty())),
                request);
    }

    @GetMapping("/{dashboardId}")
    public ApiResponse<DashboardViews.Dashboard> detail(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        return ok(DashboardMapping.dashboard(system(actor, dashboardId)),
                request);
    }

    @PutMapping("/{dashboardId}/draft")
    public ApiResponse<DashboardViews.Dashboard> saveDraft(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @Valid @RequestBody DashboardRequests.SaveDraft body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        system(actor, dashboardId);
        return ok(DashboardMapping.dashboard(service.saveDraft(
                actor, dashboardId, body.expectedVersion(), body.name(),
                body.description(), DashboardMapping.draft(body))), request);
    }

    @PostMapping("/{dashboardId}/draft:check")
    public ApiResponse<DashboardViews.CheckResult> check(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        system(actor, dashboardId);
        return ok(DashboardMapping.check(
                service.check(actor, dashboardId)), request);
    }

    @PostMapping("/{dashboardId}/draft:publish")
    public ApiResponse<DashboardViews.PublishResult> publish(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @Valid @RequestBody DashboardRequests.Publish body,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        system(actor, dashboardId);
        var version = service.publish(
                actor, dashboardId, body.expectedVersion());
        var dashboard = system(actor, dashboardId);
        return ok(new DashboardViews.PublishResult(
                DashboardMapping.dashboard(dashboard),
                DashboardMapping.version(
                        version, dashboard.activeVersionId())), request);
    }

    @GetMapping("/{dashboardId}/versions")
    public ApiResponse<List<DashboardViews.Version>> versions(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var dashboard = system(actor, dashboardId);
        return ok(service.versions(actor, dashboardId).stream()
                .map(version -> DashboardMapping.version(
                        version, dashboard.activeVersionId()))
                .toList(), request);
    }

    @GetMapping("/{dashboardId}/versions/{versionNumber}")
    public ApiResponse<DashboardViews.Version> version(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @PathVariable int versionNumber,
            @RequestAttribute(
                    value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = actor(value, systemId);
        var dashboard = system(actor, dashboardId);
        return ok(DashboardMapping.version(service.version(
                        actor, dashboardId, versionNumber),
                dashboard.activeVersionId()), request);
    }

    private static DashboardActor actor(Object value, long systemId) {
        var session = ConfigSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DashboardException(
                    "DASHBOARD_TENANT_REQUIRED",
                    "Select an active tenant before managing dashboards");
        }
        return new DashboardActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private SystemDashboard system(
            DashboardActor actor,
            long dashboardId
    ) {
        var root = service.detail(actor, dashboardId);
        if (root.placement() != DashboardPlacement.SYSTEM_HOME) {
            throw new DashboardException(
                    "DASHBOARD_NOT_FOUND", "Dashboard does not exist");
        }
        return root;
    }

    private static DashboardException scopedOnly() {
        return new DashboardException(
                "DASHBOARD_SCOPE_REQUIRED",
                "Application, module, and personal dashboards use scoped endpoints");
    }

    private static <T> ApiResponse<T> ok(
            T data,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
