package com.unique.examine.module.runtime.printing;

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
import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/records/{recordId}")
public class PrintController {
    private final PrintService service;

    public PrintController(PrintService service) { this.service = service; }

    @GetMapping("/print-templates")
    public ApiResponse<List<PrintViews.RuntimeTemplate>> templates(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long recordId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.templates(RuntimeSession.require(value, systemId), moduleCode, recordId), request);
    }

    @PostMapping("/print-preview")
    public ApiResponse<PrintViews.Preview> preview(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long recordId,
            @RequestBody PrintViews.PreviewRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.preview(RuntimeSession.require(value, systemId), moduleCode, recordId, body), request);
    }

    @PostMapping("/prints")
    public ResponseEntity<ApiResponse<PrintViews.Task>> create(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long recordId,
            @RequestBody PrintViews.CreatePrintRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        var result = service.create(RuntimeSession.require(value, systemId), moduleCode, recordId, body,
                idempotencyKey, requestId(request), traceId(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ok(result, request));
    }

    @GetMapping("/prints")
    public ApiResponse<PrintViews.TaskPage> list(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long recordId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.list(RuntimeSession.require(value, systemId), moduleCode, recordId, page, size), request);
    }

    @GetMapping("/prints/{printId}")
    public ApiResponse<PrintViews.Task> get(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long recordId,
            @PathVariable long printId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.get(RuntimeSession.require(value, systemId), moduleCode, recordId, printId), request);
    }

    @GetMapping("/prints/{printId}/result.pdf")
    public ResponseEntity<byte[]> result(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long recordId,
            @PathVariable long printId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value) {
        var result = service.result(RuntimeSession.require(value, systemId), moduleCode, recordId, printId);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
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
