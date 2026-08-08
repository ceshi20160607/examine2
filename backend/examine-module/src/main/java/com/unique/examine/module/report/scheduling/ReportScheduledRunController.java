package com.unique.examine.module.report.scheduling;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/reports/{reportCode}/scheduled-runs")
public final class ReportScheduledRunController {
    private final ReportScheduledRunService service;

    public ReportScheduledRunController(ReportScheduledRunService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ReportScheduleViews.RunPage> list(
            @PathVariable long systemId,
            @PathVariable String reportCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.list(RuntimeSession.require(value, systemId),
                reportCode, page, size), request);
    }

    @GetMapping("/{occurrenceId}")
    public ApiResponse<ReportScheduleViews.Run> detail(
            @PathVariable long systemId,
            @PathVariable String reportCode,
            @PathVariable long occurrenceId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.detail(RuntimeSession.require(value, systemId),
                reportCode, occurrenceId), request);
    }

    @GetMapping("/{occurrenceId}/result.xlsx")
    public ResponseEntity<byte[]> result(
            @PathVariable long systemId,
            @PathVariable String reportCode,
            @PathVariable long occurrenceId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value
    ) {
        var result = service.result(RuntimeSession.require(value, systemId),
                reportCode, occurrenceId);
        var content = result.content();
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
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
