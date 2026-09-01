package com.unique.unexamine.analytics.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/admin/report-metadata")
    public ApiResult<ReportModels.Metadata> reportMetadata(HttpServletRequest request) {
        return ApiResult.ok(service.reportMetadata(AuthenticationContextHolder.require(),
                TraceIdFilter.current(request)));
    }

    @GetMapping("/admin/data-sources/{sourceId}/report-preview")
    public ApiResult<ReportModels.Preview> previewReportSource(
            @PathVariable Long sourceId, HttpServletRequest request) {
        return ApiResult.ok(service.previewReportSource(AuthenticationContextHolder.require(), sourceId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/report/data-sources/{sourceId}")
    public ApiResult<ReportModels.Result> executeReportSource(
            @PathVariable Long sourceId, HttpServletRequest request) {
        return ApiResult.ok(service.executeReportSource(AuthenticationContextHolder.require(), sourceId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/runtime")
    public ApiResult<DashboardModels.RuntimeDashboard> runtime(HttpServletRequest request) {
        return ApiResult.ok(service.runtime(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @GetMapping("/admin")
    public ApiResult<DashboardModels.AdminOverview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/admin/data-sources")
    public ApiResult<DashboardModels.DataSourceView> createSource(
            @Valid @RequestBody DashboardModels.DataSourceRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.createSource(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/admin/data-sources/{sourceId}")
    public ApiResult<DashboardModels.DataSourceView> saveSource(
            @PathVariable Long sourceId, @Valid @RequestBody DashboardModels.DataSourceRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveSource(AuthenticationContextHolder.require(), sourceId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/data-sources/{sourceId}/publish")
    public ApiResult<DashboardModels.DataSourceView> publishSource(
            @PathVariable Long sourceId, @Valid @RequestBody DashboardModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publishSource(AuthenticationContextHolder.require(), sourceId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/dashboards")
    public ApiResult<DashboardModels.DashboardView> createDashboard(
            @Valid @RequestBody DashboardModels.DashboardRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.createDashboard(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/admin/dashboards/{dashboardId}")
    public ApiResult<DashboardModels.DashboardView> saveDashboard(
            @PathVariable Long dashboardId, @Valid @RequestBody DashboardModels.DashboardRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveDashboard(AuthenticationContextHolder.require(), dashboardId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/admin/dashboards/{dashboardId}/preview")
    public ApiResult<DashboardModels.Preview> preview(@PathVariable Long dashboardId,
                                                       HttpServletRequest request) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), dashboardId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/dashboards/{dashboardId}/publish")
    public ApiResult<DashboardModels.PublishResult> publishDashboard(
            @PathVariable Long dashboardId, @Valid @RequestBody DashboardModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publishDashboard(AuthenticationContextHolder.require(), dashboardId, body,
                TraceIdFilter.current(request)));
    }
}
