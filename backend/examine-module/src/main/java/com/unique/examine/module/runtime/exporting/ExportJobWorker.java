package com.unique.examine.module.runtime.exporting;

import com.unique.examine.core.job.DurableJobFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ExportJobWorker {
    private final DurableJobFacade jobs;
    private final ExportJobExecutor executor;

    public ExportJobWorker(DurableJobFacade jobs, ExportJobExecutor executor) {
        this.jobs = jobs;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${examine.jobs.export.poll-delay-ms:250}")
    public void poll() {
        jobs.claim(ExportService.JOB_TYPE, Duration.ofSeconds(60)).ifPresent(job -> {
            try {
                executor.execute(job);
            } catch (RuntimeException exception) {
                var failed = jobs.fail(job.id(), job.version(), safeMessage(exception), Duration.ofSeconds(1));
                if ("FAILED".equals(failed.status())) executor.markTerminalFailure(job, exception);
            }
        });
    }

    private static String safeMessage(RuntimeException failure) {
        return failure instanceof com.unique.examine.core.error.BusinessException
                ? failure.getMessage() : "Export task failed safely";
    }
}
