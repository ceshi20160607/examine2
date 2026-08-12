package com.unique.examine.web.work;

import com.unique.examine.work.service.WorkTaskReminderClaimWorker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically dispatches a bounded task-reminder batch. */
@Component
@ConditionalOnProperty(
        prefix = "examine.work.task-reminder.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class WorkTaskReminderScheduler {
    private static final int MAX_BATCH_SIZE = 100;

    private final WorkTaskReminderClaimWorker worker;
    private final int batchSize;

    public WorkTaskReminderScheduler(
            WorkTaskReminderClaimWorker worker,
            @Value("${examine.work.task-reminder.scheduler.batch-size:100}")
            int batchSize
    ) {
        if (worker == null) {
            throw new IllegalArgumentException("Reminder worker is required");
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Reminder scheduler batch size must be between 1 and 100");
        }
        this.worker = worker;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${examine.work.task-reminder.scheduler.initial-delay-ms:1000}",
            fixedDelayString = "${examine.work.task-reminder.scheduler.fixed-delay-ms:5000}")
    public void scheduledRun() {
        runOnce();
    }

    /** Also gives deterministic integration journeys a direct production entrypoint. */
    public int runOnce() {
        return worker.runBatch(batchSize);
    }
}
