package com.unique.examine.module.report.exporting;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public final class ReportExportJobWorker {
    private final DurableJobFacade jobs;
    private final ReportExportJobExecutor executor;

    public ReportExportJobWorker(
            DurableJobFacade jobs,
            ReportExportJobExecutor executor
    ) {
        this.jobs = jobs;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString =
            "${examine.jobs.report-export.poll-delay-ms:250}")
    public void poll() {
        jobs.claim(ReportExportService.JOB_TYPE, Duration.ofSeconds(60))
                .ifPresent(job -> {
                    try {
                        executor.execute(job);
                    } catch (RuntimeException failure) {
                        var failed = jobs.fail(job.id(), job.version(),
                                safeMessage(failure), Duration.ofSeconds(1));
                        if ("FAILED".equals(failed.status())) {
                            executor.markTerminalFailure(job, failure);
                        }
                    }
                });
    }

    private static String safeMessage(RuntimeException failure) {
        return failure instanceof BusinessException
                ? ReportExportJobExecutor.safeMessage(failure.getMessage())
                : "Report export failed safely";
    }
}
