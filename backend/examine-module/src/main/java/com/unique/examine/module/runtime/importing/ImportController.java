package com.unique.examine.module.runtime.importing;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleCode}")
public class ImportController {
    private final ImportService service;

    public ImportController(ImportService service) { this.service = service; }

    @GetMapping("/imports/template")
    public ApiResponse<ImportViews.Template> template(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.template(RuntimeSession.require(value, systemId), moduleCode), request);
    }

    @GetMapping("/imports/template.xlsx")
    public ResponseEntity<byte[]> templateXlsx(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value) {
        var content = service.templateXlsx(RuntimeSession.require(value, systemId), moduleCode);
        return workbook(content, moduleCode + "-import-template.xlsx");
    }

    @PostMapping(value = "/imports:preview-xlsx", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportViews.Batch>> previewXlsx(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestPart("file") MultipartFile file,
            @RequestParam String mode,
            @RequestParam(required = false) String matchFieldCode,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) throws IOException {
        var result = service.previewXlsx(RuntimeSession.require(value, systemId), moduleCode, mode,
                matchFieldCode, file.getOriginalFilename(), file.getBytes(), requestId(request), traceId(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ok(result, request));
    }

    @GetMapping("/imports")
    public ApiResponse<ImportViews.BatchPage> list(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.list(RuntimeSession.require(value, systemId), moduleCode, page, size), request);
    }

    @PostMapping("/imports:preview")
    public ResponseEntity<ApiResponse<ImportViews.Batch>> preview(
            @PathVariable long systemId, @PathVariable String moduleCode,
            @RequestBody ImportViews.PreviewRequest body,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        var result = service.preview(RuntimeSession.require(value, systemId), moduleCode, body,
                requestId(request), traceId(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ok(result, request));
    }

    @GetMapping("/imports/{batchId}")
    public ApiResponse<ImportViews.Batch> get(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long batchId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ok(service.get(RuntimeSession.require(value, systemId), moduleCode, batchId), request);
    }

    @PostMapping("/imports/{batchId}:commit")
    public ResponseEntity<ApiResponse<ImportViews.Batch>> commit(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long batchId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ok(service.commit(RuntimeSession.require(value, systemId), moduleCode, batchId), request));
    }

    @PostMapping("/imports/{batchId}:rollback")
    public ResponseEntity<ApiResponse<ImportViews.Batch>> rollback(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long batchId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ok(service.rollback(RuntimeSession.require(value, systemId), moduleCode, batchId), request));
    }

    @GetMapping("/imports/{batchId}/errors.xlsx")
    public ResponseEntity<byte[]> errorsXlsx(
            @PathVariable long systemId, @PathVariable String moduleCode, @PathVariable long batchId,
            @RequestAttribute(value = RequestSession.REQUEST_ATTRIBUTE, required = false) Object value) {
        var content = service.errorsXlsx(RuntimeSession.require(value, systemId), moduleCode, batchId);
        return workbook(content, moduleCode + "-import-" + batchId + "-errors.xlsx");
    }

    private static ResponseEntity<byte[]> workbook(byte[] content, String filename) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
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
