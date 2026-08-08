package com.unique.examine.module.runtime.importing;

import com.unique.examine.core.job.DurableJobFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ImportJobWorker {
    private final DurableJobFacade jobs;
    private final ImportJobExecutor executor;

    public ImportJobWorker(DurableJobFacade jobs, ImportJobExecutor executor) {
        this.jobs = jobs;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${examine.jobs.import.poll-delay-ms:250}")
    public void poll() {
        for (var type : List.of(ImportService.PREVIEW_JOB, ImportService.COMMIT_JOB, ImportService.ROLLBACK_JOB)) {
            jobs.claim(type, Duration.ofSeconds(30)).ifPresent(job -> {
                try {
                    executor.execute(job);
                } catch (RuntimeException exception) {
                    var failed = jobs.fail(job.id(), job.version(), exception.getMessage(), Duration.ofSeconds(1));
                    if ("FAILED".equals(failed.status())) executor.markTerminalFailure(job, exception);
                }
            });
        }
    }
}
