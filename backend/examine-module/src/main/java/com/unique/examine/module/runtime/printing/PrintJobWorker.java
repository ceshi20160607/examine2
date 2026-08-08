package com.unique.examine.module.runtime.printing;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.job.DurableJobFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PrintJobWorker {
    private final DurableJobFacade jobs;
    private final PrintJobExecutor executor;

    public PrintJobWorker(DurableJobFacade jobs, PrintJobExecutor executor) {
        this.jobs = jobs;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${examine.jobs.print.poll-delay-ms:250}")
    public void poll() {
        jobs.claim(PrintService.JOB_TYPE, Duration.ofSeconds(60)).ifPresent(job -> {
            try {
                executor.execute(job);
            } catch (RuntimeException exception) {
                var failed = jobs.fail(job.id(), job.version(), safeMessage(exception), Duration.ofSeconds(1));
                if ("FAILED".equals(failed.status())) executor.markTerminalFailure(job, exception);
            }
        });
    }

    private static String safeMessage(RuntimeException failure) {
        return failure instanceof BusinessException ? failure.getMessage() : "Print task failed safely";
    }
}
