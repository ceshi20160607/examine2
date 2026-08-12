package com.unique.examine.module.runtime.printing;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.module.runtime.notification.JobResultNotifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PrintJobExecutor {
    private final PrintRepository repository;
    private final PrintRenderer renderer;
    private final DurableJobFacade jobs;
    private final OperationAuditFacade audit;
    private final JobResultNotifier notifier;

    public PrintJobExecutor(PrintRepository repository, PrintRenderer renderer, DurableJobFacade jobs,
                            OperationAuditFacade audit, JobResultNotifier notifier) {
        this.repository = repository;
        this.renderer = renderer;
        this.jobs = jobs;
        this.audit = audit;
        this.notifier = notifier;
    }

    @Transactional
    public void execute(DurableJobFacade.JobRecord job) {
        if (!PrintService.JOB_TYPE.equals(job.jobType())) {
            throw new IllegalArgumentException("Unsupported print job " + job.jobType());
        }
        var task = repository.requireTask(printId(job));
        repository.startTask(task.id());
        var content = renderer.pdf(repository.snapshot(task.snapshotJson()));
        if (content.length < 8 || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F') {
            throw new IllegalStateException("Generated print result is not a PDF");
        }
        var filename = task.moduleCode() + "-" + task.recordId() + "-print-" + task.id() + ".pdf";
        repository.completeTask(task.id(), filename, content);
        jobs.succeed(job.id(), job.version(), Map.of("printId", Long.toString(task.id()), "bytes", content.length));
        audit.recordSuccess(OperationAudit.success(
                new OperationAudit.Actor(task.accountId(), "SYSTEM"),
                new OperationAudit.Context(ContextType.SYSTEM, task.systemId(), task.tenantId()),
                new AggregateRef("MODULE_PRINT_TASK", Long.toString(task.id())), "MODULE_RECORD_PDF_PRINTED",
                null, Map.of("status", "SUCCEEDED", "recordId", Long.toString(task.recordId()),
                        "templateVersionId", Long.toString(task.templateVersionId()), "bytes", content.length),
                task.requestId(), task.traceId()));
        notifier.printSucceeded(task);
    }

    @Transactional
    public void markTerminalFailure(DurableJobFacade.JobRecord job, RuntimeException failure) {
        var task = repository.requireTask(printId(job));
        var code = failure instanceof BusinessException business ? business.code() : "PRINT_FAILED";
        var message = failure instanceof BusinessException ? failure.getMessage() : "Print task failed safely";
        repository.failTask(task.id(), code, message);
        notifier.printFailed(task, message);
    }

    private static long printId(DurableJobFacade.JobRecord job) {
        try { return Long.parseLong(String.valueOf(job.input().get("printId"))); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Missing job input printId", exception); }
    }
}
