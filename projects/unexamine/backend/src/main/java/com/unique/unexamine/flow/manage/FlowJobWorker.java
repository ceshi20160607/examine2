package com.unique.unexamine.flow.manage;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class FlowJobWorker {
    private final FlowRuntimeService runtimeService;
    private final String workerId = "flow-" + ManagementFactory.getRuntimeMXBean().getName();

    public FlowJobWorker(FlowRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Scheduled(fixedDelayString = "${unexamine.flow.jobs.poll-delay-ms:1000}")
    public void poll() {
        runtimeService.runDueJobs(workerId, 20);
    }
}
