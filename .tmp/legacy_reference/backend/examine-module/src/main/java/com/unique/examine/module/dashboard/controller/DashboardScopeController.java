package com.unique.examine.module.dashboard.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.dashboard.api.DashboardMapping;
import com.unique.examine.module.dashboard.api.DashboardRequests;
import com.unique.examine.module.dashboard.api.DashboardViews;
import com.unique.examine.module.dashboard.domain.DashboardActor;
import com.unique.examine.module.dashboard.domain.DashboardException;
import com.unique.examine.module.dashboard.domain.DashboardPlacement;
import com.unique.examine.module.dashboard.runtime.DashboardRuntimeService;
import com.unique.examine.module.dashboard.service.DashboardScopeService;
import com.unique.examine.module.dashboard.service.DashboardService;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}")
public class DashboardScopeController {
    private final DashboardScopeService scopes;
    private final DashboardService dashboards;
    private final DashboardRuntimeService runtime;

    public DashboardScopeController(
            DashboardScopeService scopes,
            DashboardService dashboards,
            DashboardRuntimeService runtime
    ) {
        this.scopes = scopes;
        this.dashboards = dashboards;
        this.runtime = runtime;
    }

    @PostMapping("/admin/dashboard-scopes/{placement}/{scopeKey}")
    public ApiResponse<DashboardViews.Dashboard> createPublic(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @Valid @RequestBody DashboardRequests.ScopedCreate body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var parsed = publicPlacement(placement);
        return ok(DashboardMapping.dashboard(scopes.create(
                adminActor(value, systemId), parsed, scopeKey, body.code(),
                body.name(), body.description()), scopeKey), request);
    }

    @PostMapping("/dashboards/personal/{scopeKey}")
    public ApiResponse<DashboardViews.Dashboard> createPersonal(
            @PathVariable long systemId,
            @PathVariable String scopeKey,
            @Valid @RequestBody DashboardRequests.ScopedCreate body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(DashboardMapping.dashboard(scopes.create(
                memberActor(value, systemId), DashboardPlacement.PERSONAL_HOME,
                scopeKey, body.code(), body.name(), body.description()),
                scopeKey),
                request);
    }

    @GetMapping("/admin/dashboard-scopes")
    public ApiResponse<List<DashboardViews.Dashboard>> publicScopes(
            @PathVariable long systemId,
            @RequestParam(required = false) String placement,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var parsed = placement == null || placement.isBlank()
                ? null : publicPlacement(placement);
        return ok(scopes.publicDashboards(
                        adminActor(value, systemId), parsed).stream()
                .map(entry -> DashboardMapping.dashboard(
                        entry.dashboard(), entry.scopeKey())).toList(),
                request);
    }

    @GetMapping("/admin/dashboard-scopes/{placement}/{scopeKey}")
    public ApiResponse<DashboardViews.Dashboard> publicDetail(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = adminActor(value, systemId);
        return ok(DashboardMapping.dashboard(scopes.detail(
                actor, publicPlacement(placement), scopeKey), scopeKey),
                request);
    }

    @PutMapping("/admin/dashboard-scopes/{placement}/{scopeKey}/draft")
    public ApiResponse<DashboardViews.Dashboard> savePublicDraft(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @Valid @RequestBody DashboardRequests.SaveDraft body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = adminActor(value, systemId);
        var root = scopes.detail(
                actor, publicPlacement(placement), scopeKey);
        return ok(DashboardMapping.dashboard(dashboards.saveDraft(
                actor, root.id(), body.expectedVersion(), body.name(),
                body.description(), DashboardMapping.draft(body)), scopeKey),
                request);
    }

    @PostMapping("/admin/dashboard-scopes/{placement}/{scopeKey}/draft:check")
    public ApiResponse<DashboardViews.CheckResult> checkPublic(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = adminActor(value, systemId);
        var root = scopes.detail(
                actor, publicPlacement(placement), scopeKey);
        return ok(DashboardMapping.check(dashboards.check(
                actor, root.id())), request);
    }

    @PostMapping("/admin/dashboard-scopes/{placement}/{scopeKey}/draft:publish")
    public ApiResponse<DashboardViews.PublishResult> publishPublic(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @Valid @RequestBody DashboardRequests.Publish body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = adminActor(value, systemId);
        var parsed = publicPlacement(placement);
        var root = scopes.detail(actor, parsed, scopeKey);
        var version = dashboards.publish(
                actor, root.id(), body.expectedVersion());
        var revised = scopes.detail(actor, parsed, scopeKey);
        return ok(new DashboardViews.PublishResult(
                DashboardMapping.dashboard(revised, scopeKey),
                DashboardMapping.version(version, revised.activeVersionId())),
                request);
    }

    @GetMapping("/admin/dashboard-scopes/{placement}/{scopeKey}/versions")
    public ApiResponse<List<DashboardViews.Version>> publicVersions(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = adminActor(value, systemId);
        var root = scopes.detail(
                actor, publicPlacement(placement), scopeKey);
        return ok(dashboards.versions(actor, root.id()).stream()
                .map(version -> DashboardMapping.version(
                        version, root.activeVersionId())).toList(), request);
    }

    @GetMapping("/admin/dashboard-scopes/{placement}/{scopeKey}/versions/{versionNumber}")
    public ApiResponse<DashboardViews.Version> publicVersion(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @PathVariable int versionNumber,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = adminActor(value, systemId);
        var root = scopes.detail(
                actor, publicPlacement(placement), scopeKey);
        return ok(DashboardMapping.version(dashboards.version(
                actor, root.id(), versionNumber), root.activeVersionId()),
                request);
    }

    @GetMapping("/dashboards/personal")
    public ApiResponse<List<DashboardViews.Dashboard>> personal(
            @PathVariable long systemId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(scopes.personalDashboards(
                        memberActor(value, systemId)).stream()
                .map(entry -> DashboardMapping.dashboard(
                        entry.dashboard(), entry.scopeKey())).toList(),
                request);
    }

    @GetMapping("/dashboards/personal/{dashboardId}")
    public ApiResponse<DashboardViews.Dashboard> personalDetail(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var entry = scopes.ownedScoped(
                memberActor(value, systemId), dashboardId);
        return ok(DashboardMapping.dashboard(
                entry.dashboard(), entry.scopeKey()), request);
    }

    @PutMapping("/dashboards/personal/{dashboardId}/draft")
    public ApiResponse<DashboardViews.Dashboard> savePersonalDraft(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @Valid @RequestBody DashboardRequests.SaveDraft body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = memberActor(value, systemId);
        var entry = scopes.ownedScoped(actor, dashboardId);
        return ok(DashboardMapping.dashboard(dashboards.saveDraft(
                actor, dashboardId, body.expectedVersion(), body.name(),
                body.description(), DashboardMapping.draft(body)),
                entry.scopeKey()), request);
    }

    @PostMapping("/dashboards/personal/{dashboardId}/draft:publish")
    public ApiResponse<DashboardViews.PublishResult> publishPersonal(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @Valid @RequestBody DashboardRequests.Publish body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = memberActor(value, systemId);
        scopes.ownedScoped(actor, dashboardId);
        var version = dashboards.publish(
                actor, dashboardId, body.expectedVersion());
        var entry = scopes.ownedScoped(actor, dashboardId);
        var root = entry.dashboard();
        return ok(new DashboardViews.PublishResult(
                DashboardMapping.dashboard(root, entry.scopeKey()),
                DashboardMapping.version(version, root.activeVersionId())),
                request);
    }

    @PostMapping("/dashboards/personal/{dashboardId}/draft:check")
    public ApiResponse<DashboardViews.CheckResult> checkPersonal(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = memberActor(value, systemId);
        scopes.owned(actor, dashboardId);
        return ok(DashboardMapping.check(
                dashboards.check(actor, dashboardId)), request);
    }

    @GetMapping("/dashboards/personal/{dashboardId}/versions")
    public ApiResponse<List<DashboardViews.Version>> personalVersions(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = memberActor(value, systemId);
        var root = scopes.owned(actor, dashboardId);
        return ok(dashboards.versions(actor, dashboardId).stream()
                .map(version -> DashboardMapping.version(
                        version, root.activeVersionId())).toList(), request);
    }

    @GetMapping("/dashboards/personal/{dashboardId}/versions/{versionNumber}")
    public ApiResponse<DashboardViews.Version> personalVersion(
            @PathVariable long systemId,
            @PathVariable long dashboardId,
            @PathVariable int versionNumber,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var actor = memberActor(value, systemId);
        var root = scopes.owned(actor, dashboardId);
        return ok(DashboardMapping.version(dashboards.version(
                actor, dashboardId, versionNumber), root.activeVersionId()),
                request);
    }

    @GetMapping("/dashboards/scopes/{placement}/{scopeKey}")
    public ApiResponse<DashboardViews.RuntimeDashboard> runtime(
            @PathVariable long systemId,
            @PathVariable String placement,
            @PathVariable String scopeKey,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var session = RuntimeSession.require(value, systemId);
        return ok(runtime.published(session, scopes.active(
                memberActor(session), placement(placement), scopeKey)),
                request);
    }

    private static DashboardActor adminActor(Object value, long systemId) {
        var session = ConfigSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DashboardException("DASHBOARD_TENANT_REQUIRED",
                    "Select an active tenant before managing dashboards");
        }
        return new DashboardActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static DashboardActor memberActor(Object value, long systemId) {
        return memberActor(RuntimeSession.require(value, systemId));
    }

    private static DashboardActor memberActor(RuntimeSession session) {
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new DashboardException("DASHBOARD_TENANT_REQUIRED",
                    "Select an active tenant before reading dashboards");
        }
        return new DashboardActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static DashboardPlacement placement(String value) {
        try {
            return DashboardPlacement.valueOf(value);
        } catch (RuntimeException unsupported) {
            throw invalid("Dashboard scope placement is unsupported");
        }
    }

    private static DashboardPlacement publicPlacement(String value) {
        var placement = placement(value);
        if (placement != DashboardPlacement.APPLICATION_HOME
                && placement != DashboardPlacement.MODULE_HOME) {
            throw invalid(
                    "Public scoped dashboards must use APPLICATION_HOME or MODULE_HOME");
        }
        return placement;
    }

    private static DashboardException invalid(String message) {
        return new DashboardException("DASHBOARD_SCOPE_INVALID", message);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, String.valueOf(request.getAttribute(
                WebRequestAttributes.REQUEST_ID)), String.valueOf(
                request.getAttribute(WebRequestAttributes.TRACE_ID)));
    }
}
