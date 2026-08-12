package com.unique.examine.web.work;

import com.unique.examine.work.adapter.memory.InMemoryWorkTaskReminderRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.service.WorkTaskReminderClaimWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTaskReminderSchedulerTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of())
                    .withUserConfiguration(
                            WorkerConfiguration.class,
                            WorkTaskReminderScheduler.class);

    @Test
    void enabledByDefaultAndRunsTheProductionWorkerEntryPoint() {
        context.run(result -> {
            assertThat(result).hasSingleBean(WorkTaskReminderScheduler.class);
            assertThat(result.getBean(WorkTaskReminderScheduler.class).runOnce())
                    .isZero();
        });
    }

    @Test
    void mayBeDisabledForDeterministicManualExecution() {
        context.withPropertyValues(
                        "examine.work.task-reminder.scheduler.enabled=false")
                .run(result -> assertThat(result)
                        .doesNotHaveBean(WorkTaskReminderScheduler.class));
    }

    @Test
    void rejectsBatchSizeAboveTheRepositoryClaimLimit() {
        context.withPropertyValues(
                        "examine.work.task-reminder.scheduler.batch-size=101")
                .run(result -> {
                    assertThat(result).hasFailed();
                    assertThat(result.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasRootCauseMessage(
                                    "Reminder scheduler batch size must be between 1 and 100");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class WorkerConfiguration {
        @Bean
        WorkTaskReminderClaimWorker worker() {
            return new WorkTaskReminderClaimWorker(
                    new InMemoryWorkTaskReminderRepository(),
                    new InMemoryWorkTaskRepository(),
                    (systemId, tenantId, memberId) -> true,
                    ignored -> {
                    },
                    Clock.systemUTC());
        }
    }
}
