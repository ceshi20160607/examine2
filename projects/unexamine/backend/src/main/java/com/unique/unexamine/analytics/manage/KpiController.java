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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class KpiController {
    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    @GetMapping("/admin/kpis")
    public ApiResult<KpiModels.Overview> adminOverview() {
        return ApiResult.ok(service.adminOverview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/admin/kpis")
    public ApiResult<KpiModels.KpiView> create(
            @Valid @RequestBody KpiModels.KpiRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/admin/kpis/{kpiId}")
    public ApiResult<KpiModels.KpiView> update(
            @PathVariable Long kpiId, @Valid @RequestBody KpiModels.KpiRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.update(AuthenticationContextHolder.require(), kpiId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/admin/kpis/{kpiId}/preview")
    public ApiResult<KpiModels.Preview> preview(
            @PathVariable Long kpiId, @RequestParam String periodKey, HttpServletRequest request) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), kpiId, periodKey,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/kpis/{kpiId}/calculate")
    public ApiResult<KpiModels.ResultView> calculate(
            @PathVariable Long kpiId, @Valid @RequestBody KpiModels.CalculationRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.calculate(AuthenticationContextHolder.require(), kpiId, body.periodKey(),
                TraceIdFilter.current(request)));
    }

    @GetMapping("/kpis")
    public ApiResult<KpiModels.Overview> runtime(HttpServletRequest request) {
        return ApiResult.ok(service.runtimeOverview(AuthenticationContextHolder.require(),
                TraceIdFilter.current(request)));
    }

    @GetMapping("/kpis/{kpiId}")
    public ApiResult<KpiModels.KpiView> runtimeOne(
            @PathVariable Long kpiId, HttpServletRequest request) {
        return ApiResult.ok(service.runtimeOne(AuthenticationContextHolder.require(), kpiId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/kpis/reminders/{reminderId}/acknowledge")
    public ApiResult<KpiModels.ReminderView> acknowledge(
            @PathVariable Long reminderId, HttpServletRequest request) {
        return ApiResult.ok(service.acknowledge(AuthenticationContextHolder.require(), reminderId,
                TraceIdFilter.current(request)));
    }
}
