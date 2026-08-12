package com.unique.examine.module.runtime.exporting;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/exports")
public class ExportController {
    private final ExportService service;

    public ExportController(ExportService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<ExportViews.Task>> create(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestBody ExportViews.CreateRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        var result = service.create(RuntimeSession.require(value, systemId), moduleCode, body,
                requestId(request), traceId(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ok(result, request));
    }

    @GetMapping
    public ApiResponse<ExportViews.TaskPage> list(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.list(RuntimeSession.require(value, systemId), moduleCode, page, size), request);
    }

    @GetMapping("/{exportId}")
    public ApiResponse<ExportViews.Task> get(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long exportId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.get(RuntimeSession.require(value, systemId), moduleCode, exportId), request);
    }

    @GetMapping("/{exportId}/result.xlsx")
    public ResponseEntity<byte[]> result(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long exportId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value) {
        var result = service.result(RuntimeSession.require(value, systemId), moduleCode, exportId);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(result.content().length);
        return new ResponseEntity<>(result.content(), headers, HttpStatus.OK);
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestId(request), traceId(request));
    }
    private static String requestId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.REQUEST_ID));
    }
    private static String traceId(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(WebRequestAttributes.TRACE_ID));
    }
}
