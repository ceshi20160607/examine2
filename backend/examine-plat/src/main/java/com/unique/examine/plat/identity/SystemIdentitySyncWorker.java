package com.unique.examine.plat.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "examine.identity.sync.worker-enabled", havingValue = "true", matchIfMissing = true)
public class SystemIdentitySyncWorker {
    private final SystemIdentitySyncService service;

    public SystemIdentitySyncWorker(SystemIdentitySyncService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${examine.identity.sync.schedule-poll-ms:30000}")
    public void schedule() {
        service.enqueueDueSchedules();
    }

    @Scheduled(fixedDelayString = "${examine.identity.sync.job-poll-ms:2000}")
    public void execute() {
        for (int i = 0; i < 10 && service.executeNext() == 1; i++) {
            // Bounded drain: another scheduler tick or node continues the durable queue.
        }
    }
}
