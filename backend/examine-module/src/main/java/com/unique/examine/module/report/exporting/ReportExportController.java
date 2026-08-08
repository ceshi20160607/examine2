package com.unique.examine.module.report.exporting;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/reports/{reportCode}/exports")
public final class ReportExportController {
    private final ReportExportService service;

    public ReportExportController(ReportExportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReportExportViews.Task>> start(
            @PathVariable long systemId,
            @PathVariable String reportCode,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey,
            @RequestBody(required = false) ReportExportViews.StartRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        var result = service.start(RuntimeSession.require(value, systemId),
                reportCode, body, idempotencyKey, requestId(request),
                traceId(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ok(result, request));
    }

    @GetMapping
    public ApiResponse<ReportExportViews.TaskPage> list(
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

    @GetMapping("/{exportId}")
    public ApiResponse<ReportExportViews.Task> get(
            @PathVariable long systemId,
            @PathVariable String reportCode,
            @PathVariable long exportId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.get(RuntimeSession.require(value, systemId),
                reportCode, exportId), request);
    }

    @GetMapping("/{exportId}/result.xlsx")
    public ResponseEntity<byte[]> result(
            @PathVariable long systemId,
            @PathVariable String reportCode,
            @PathVariable long exportId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value
    ) {
        var result = service.result(RuntimeSession.require(value, systemId),
                reportCode, exportId);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(result.content().length);
        return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);
    }

    private static <T> ApiResponse<T> ok(
            T value,
            HttpServletRequest request
    ) {
        return ApiResponse.success(value, requestId(request), traceId(request));
    }

    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(
                WebRequestAttributes.REQUEST_ID));
    }

    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(
                WebRequestAttributes.TRACE_ID));
    }
}
