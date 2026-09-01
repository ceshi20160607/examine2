package com.unique.unexamine.dataexchange.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobModels;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobService;
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
@RequestMapping("/api/runtime/modules/{moduleCode}/imports")
public class ModuleImportController {
    private final ModuleImportService service;
    private final BackgroundJobService jobs;

    public ModuleImportController(ModuleImportService service, BackgroundJobService jobs) {
        this.service = service;
        this.jobs = jobs;
    }

    @GetMapping("/template")
    public ApiResult<ImportModels.TemplateView> template(
            @PathVariable String moduleCode, HttpServletRequest request) {
        return ApiResult.ok(service.template(AuthenticationContextHolder.require(), moduleCode,
                TraceIdFilter.current(request)));
    }

    @GetMapping
    public ApiResult<List<ImportModels.ImportBatchView>> list(
            @PathVariable String moduleCode, HttpServletRequest request) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), moduleCode,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/preview")
    public ApiResult<ImportModels.ImportBatchView> preview(
            @PathVariable String moduleCode,
            @Valid @RequestBody ImportModels.PreviewRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), moduleCode, input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{batchId}")
    public ApiResult<ImportModels.ImportBatchView> detail(
            @PathVariable String moduleCode, @PathVariable Long batchId, HttpServletRequest request) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), moduleCode, batchId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{batchId}/execute")
    public ApiResult<ImportModels.ImportBatchView> execute(
            @PathVariable String moduleCode,
            @PathVariable Long batchId,
            @Valid @RequestBody ImportModels.ExecuteRequest input,
            HttpServletRequest request) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        String traceId = TraceIdFilter.current(request);
        service.queue(context, moduleCode, batchId, input.version(), traceId);
        try {
            BackgroundJobModels.JobDetail job = jobs.submit(context, new BackgroundJobModels.SubmitJobRequest(
                    ModuleImportBackgroundJobHandler.MODULE_IMPORT, "IMPORT_BATCH", String.valueOf(batchId),
                    service.executionParameters(context, batchId), 1), traceId);
            service.attachJob(context, moduleCode, batchId, job.job().id());
            return ApiResult.ok(service.detail(context, moduleCode, batchId, traceId));
        } catch (RuntimeException exception) {
            service.resetQueue(context, moduleCode, batchId);
            throw exception;
        }
    }

    @PostMapping("/{batchId}/rollback")
    public ApiResult<ImportModels.ImportBatchView> rollback(
            @PathVariable String moduleCode,
            @PathVariable Long batchId,
            @Valid @RequestBody ImportModels.RollbackRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.rollback(AuthenticationContextHolder.require(), moduleCode, batchId, input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{batchId}/errors.csv")
    public ResponseEntity<byte[]> errors(
            @PathVariable String moduleCode, @PathVariable Long batchId, HttpServletRequest request) {
        byte[] content = service.errorCsv(AuthenticationContextHolder.require(), moduleCode, batchId,
                TraceIdFilter.current(request));
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("import-" + batchId + "-errors.csv", StandardCharsets.UTF_8).build().toString())
                .contentLength(content.length).body(content);
    }
}
