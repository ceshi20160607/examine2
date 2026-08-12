package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.report.domain.ReportException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/reports/{reportId}/schedules")
public final class ReportScheduleAdminController {
    private final ReportScheduleService service;

    public ReportScheduleAdminController(ReportScheduleService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<ReportScheduleViews.Schedule> create(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestBody ReportScheduleViews.CreateRequest body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.create(session(value, systemId), reportId, body),
                request);
    }

    @GetMapping
    public ApiResponse<ReportScheduleViews.SchedulePage> list(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.list(session(value, systemId), reportId, page, size),
                request);
    }

    @GetMapping("/{scheduleId}")
    public ApiResponse<ReportScheduleViews.Schedule> detail(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @PathVariable long scheduleId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.detail(session(value, systemId), reportId,
                scheduleId), request);
    }

    @PutMapping("/{scheduleId}")
    public ApiResponse<ReportScheduleViews.Schedule> update(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @PathVariable long scheduleId,
            @RequestBody ReportScheduleViews.UpdateRequest body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.update(session(value, systemId), reportId,
                scheduleId, body), request);
    }

    @PostMapping("/{scheduleId}:enable")
    public ApiResponse<ReportScheduleViews.Schedule> enable(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @PathVariable long scheduleId,
            @RequestBody ReportScheduleViews.ToggleRequest body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.setEnabled(session(value, systemId), reportId,
                scheduleId, body, true), request);
    }

    @PostMapping("/{scheduleId}:disable")
    public ApiResponse<ReportScheduleViews.Schedule> disable(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @PathVariable long scheduleId,
            @RequestBody ReportScheduleViews.ToggleRequest body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.setEnabled(session(value, systemId), reportId,
                scheduleId, body, false), request);
    }

    @PostMapping("/next-fire:preview")
    public ApiResponse<ReportScheduleViews.Preview> preview(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestBody ReportScheduleViews.PreviewRequest body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.preview(session(value, systemId), reportId, body),
                request);
    }

    private static ConfigSession session(Object value, long systemId) {
        var session = ConfigSession.require(value, systemId);
        if (session.tenantId() == null || session.tenantId() <= 0) {
            throw new ReportException("REPORT_TENANT_REQUIRED",
                    "Select an active tenant before managing report schedules");
        }
        return session;
    }

    private static <T> ApiResponse<T> ok(
            T value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(value,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
