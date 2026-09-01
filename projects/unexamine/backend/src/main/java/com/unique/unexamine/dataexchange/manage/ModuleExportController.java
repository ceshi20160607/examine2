package com.unique.unexamine.dataexchange.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobModels;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/runtime/modules/{moduleCode}/exports")
public class ModuleExportController {
    private final ModuleExportService service;
    private final BackgroundJobService jobs;

    public ModuleExportController(ModuleExportService service, BackgroundJobService jobs) {
        this.service = service;
        this.jobs = jobs;
    }

    @PostMapping("/estimate")
    public ApiResult<ExportModels.EstimateView> estimate(
            @PathVariable String moduleCode, @Valid @RequestBody ExportModels.ExportQuery input,
            HttpServletRequest request) {
        return ApiResult.ok(service.estimate(AuthenticationContextHolder.require(), moduleCode, input,
                TraceIdFilter.current(request)));
    }

    @GetMapping
    public ApiResult<List<ExportModels.ExportBatchView>> list(
            @PathVariable String moduleCode, HttpServletRequest request) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), moduleCode,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{batchId}")
    public ApiResult<ExportModels.ExportBatchView> detail(
            @PathVariable String moduleCode, @PathVariable Long batchId, HttpServletRequest request) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), moduleCode, batchId,
                TraceIdFilter.current(request)));
    }

    @PostMapping
    public ApiResult<ExportModels.ExportBatchView> submit(
            @PathVariable String moduleCode, @Valid @RequestBody ExportModels.ExportQuery input,
            HttpServletRequest request) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        String traceId = TraceIdFilter.current(request);
        ExportModels.ExportBatchView batch = service.queue(context, moduleCode, input, traceId);
        try {
            BackgroundJobModels.JobDetail job = jobs.submit(context, new BackgroundJobModels.SubmitJobRequest(
                    ModuleExportBackgroundJobHandler.MODULE_EXPORT, "EXPORT_BATCH", String.valueOf(batch.id()),
                    service.executionParameters(context, batch.id()), 1), traceId);
            service.attachJob(context, moduleCode, batch.id(), job.job().id());
            return ApiResult.ok(service.detail(context, moduleCode, batch.id(), traceId));
        } catch (RuntimeException exception) {
            service.failQueue(context, moduleCode, batch.id(), exception.getMessage());
            throw exception;
        }
    }

    @GetMapping("/{batchId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable String moduleCode, @PathVariable Long batchId, HttpServletRequest request) {
        FileModels.BinaryContent content = service.download(AuthenticationContextHolder.require(), moduleCode, batchId,
                TraceIdFilter.current(request));
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(content.contentType()); }
        catch (RuntimeException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentLength(content.bytes().length).body(content.bytes());
    }
}
