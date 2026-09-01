package com.unique.unexamine.print.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.file.manage.FileModels;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/print")
public class PrintController {
    private final PrintService service;

    public PrintController(PrintService service) {
        this.service = service;
    }

    @GetMapping("/admin")
    public ApiResult<PrintModels.AdminOverview> admin(HttpServletRequest request) {
        return ApiResult.ok(service.adminOverview(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/templates")
    public ApiResult<PrintModels.TemplateView> create(
            @Valid @RequestBody PrintModels.SaveTemplateRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body, TraceIdFilter.current(request)));
    }

    @PutMapping("/admin/templates/{templateId}")
    public ApiResult<PrintModels.TemplateView> update(
            @PathVariable Long templateId, @Valid @RequestBody PrintModels.SaveTemplateRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.update(AuthenticationContextHolder.require(), templateId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/templates/{templateId}/preview")
    public ApiResult<PrintModels.Preview> preview(
            @PathVariable Long templateId, @Valid @RequestBody PrintModels.PreviewRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), templateId, body.sampleRecordId(),
                TraceIdFilter.current(request)));
    }

    @PostMapping("/admin/templates/{templateId}/publish")
    public ApiResult<PrintModels.Publication> publish(
            @PathVariable Long templateId, @Valid @RequestBody PrintModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), templateId,
                body.expectedDraftRevision(), TraceIdFilter.current(request)));
    }

    @GetMapping("/runtime/modules/{moduleCode}/templates")
    public ApiResult<List<PrintModels.RuntimeTemplate>> runtimeTemplates(
            @PathVariable String moduleCode, HttpServletRequest request) {
        return ApiResult.ok(service.runtimeTemplates(AuthenticationContextHolder.require(), moduleCode,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/runtime/modules/{moduleCode}/records/{recordId}/templates/{templateId}")
    public ApiResult<PrintModels.PrintJobView> print(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable Long templateId,
            @Valid @RequestBody PrintModels.PrintRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.print(AuthenticationContextHolder.require(), moduleCode, recordId, templateId,
                body.templateVersionId(), TraceIdFilter.current(request)));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResult<PrintModels.PrintJobView> job(@PathVariable Long jobId) {
        return ApiResult.ok(service.job(AuthenticationContextHolder.require(), jobId));
    }

    @GetMapping("/jobs/{jobId}/preview")
    public ResponseEntity<byte[]> previewFile(@PathVariable Long jobId, HttpServletRequest request) {
        return binary(service.binary(AuthenticationContextHolder.require(), jobId, true, TraceIdFilter.current(request)));
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long jobId, HttpServletRequest request) {
        return binary(service.binary(AuthenticationContextHolder.require(), jobId, false, TraceIdFilter.current(request)));
    }

    private ResponseEntity<byte[]> binary(FileModels.BinaryContent content) {
        ContentDisposition disposition = content.inline()
                ? ContentDisposition.inline().filename(content.fileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(content.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.bytes().length).body(content.bytes());
    }
}
