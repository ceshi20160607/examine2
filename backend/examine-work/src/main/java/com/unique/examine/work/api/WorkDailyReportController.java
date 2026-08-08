package com.unique.examine.work.api;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.configuration.WorkConfiguration;
import com.unique.examine.work.configuration.WorkConfigurationService;
import com.unique.examine.work.service.WorkDailyReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/work/reports")
public class WorkDailyReportController {
    private final WorkDailyReportService service;
    private final WorkConfigurationService configurations;

    public WorkDailyReportController(WorkDailyReportService service) {
        this(service, null);
    }

    @Autowired
    public WorkDailyReportController(
            WorkDailyReportService service,
            WorkConfigurationService configurations) {
        this.service = service;
        this.configurations = configurations;
    }

    @GetMapping
    public ApiResponse<WorkDailyReportApiModels.ReportPage> list(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "SELF") WorkDailyReportQuery.Scope scope,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "ALL") WorkDailyReportQuery.StatusFilter status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var result = service.page(
                actor(session, systemId), scope, memberId,
                dateFrom, dateTo, status, page, size);
        var actor = actor(session, systemId);
        return success(new WorkDailyReportApiModels.ReportPage(
                result.items().stream().map(report -> view(actor, report)).toList(),
                result.page(), result.size(), result.total()), request);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<WorkDailyReportApiModels.ReportView>> create(
            @PathVariable long systemId,
            @RequestBody WorkDailyReportApiModels.CreateReport body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = actor(session, systemId);
        var prepared = configurations == null ? null
                : configurations.prepareCreate(actor,
                WorkConfiguration.ObjectType.DAILY_REPORT, body.customFields());
        var report = service.create(
                actor, body.workDate(),
                body.completedWork(), body.plannedWork(), body.blockers());
        if (configurations != null) {
            configurations.saveValues(actor,
                    WorkConfiguration.ObjectType.DAILY_REPORT,
                    report.id(), prepared);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(success(
                        view(actor, report),
                        request));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<WorkDailyReportApiModels.ReportView> get(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = actor(session, systemId);
        var report = service.get(actor, reportId);
        return success(view(actor, report), request);
    }

    @PutMapping("/{reportId}")
    @Transactional
    public ApiResponse<WorkDailyReportApiModels.ReportView> update(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestBody WorkDailyReportApiModels.UpdateReport body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var actor = actor(session, systemId);
        var prepared = configurations == null ? null
                : configurations.prepareUpdate(actor,
                WorkConfiguration.ObjectType.DAILY_REPORT,
                reportId, body.customFields());
        var report = service.update(
                actor, reportId,
                body.completedWork(), body.plannedWork(), body.blockers(),
                body.version());
        if (configurations != null) {
            configurations.saveValues(actor,
                    WorkConfiguration.ObjectType.DAILY_REPORT,
                    report.id(), prepared);
        }
        return success(view(actor, report), request);
    }

    @PostMapping("/{reportId}:submit")
    public ApiResponse<WorkDailyReportApiModels.ReportView> submit(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestBody WorkDailyReportApiModels.ReportVersion body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var report = service.submit(
                actor(session, systemId), reportId, body.version());
        return success(view(actor(session, systemId), report), request);
    }

    @PostMapping("/{reportId}:reopen")
    public ApiResponse<WorkDailyReportApiModels.ReportView> reopen(
            @PathVariable long systemId,
            @PathVariable long reportId,
            @RequestBody WorkDailyReportApiModels.ReportVersion body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var report = service.reopen(
                actor(session, systemId), reportId, body.version());
        return success(view(actor(session, systemId), report), request);
    }

    @GetMapping("/summary")
    public ApiResponse<WorkDailyReportApiModels.SummaryView> summary(
            @PathVariable long systemId,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) Long memberId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object session,
            HttpServletRequest request
    ) {
        var result = service.summary(
                actor(session, systemId), endDate, memberId);
        return success(WorkDailyReportApiModels.SummaryView.from(result), request);
    }

    private static com.unique.examine.work.domain.WorkActor actor(
            Object session, long systemId
    ) {
        return WorkRequestSession.require(
                session, systemId, WorkDailyReportService.ACCESS);
    }

    private WorkDailyReportApiModels.ReportView view(
            com.unique.examine.work.domain.WorkActor actor,
            com.unique.examine.work.domain.WorkDailyReport report) {
        return WorkDailyReportApiModels.ReportView.from(report,
                configurations == null ? null : configurations.runtimeView(
                        actor, WorkConfiguration.ObjectType.DAILY_REPORT,
                        report.id()));
    }

    private static <T> ApiResponse<T> success(
            T data, HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(
            HttpServletRequest request, String name
    ) {
        var value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }
}
