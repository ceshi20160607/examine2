package com.unique.examine.module.kpi.config;

import com.unique.examine.core.api.IdempotentMemberMessageFacade;
import com.unique.examine.module.kpi.adapter.KpiInboxReminderNotifier;
import com.unique.examine.module.kpi.port.KpiRepository;
import com.unique.examine.module.kpi.port.KpiReminderNotifier;
import com.unique.examine.module.kpi.port.KpiReminderRecipientDirectory;
import com.unique.examine.module.kpi.port.KpiSourceCatalog;
import com.unique.examine.module.kpi.port.KpiStatisticsExecutor;
import com.unique.examine.module.kpi.port.KpiSubjectDirectory;
import com.unique.examine.module.kpi.service.KpiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class KpiConfiguration {
    @Bean
    public KpiService kpiService(
            KpiRepository repository,
            KpiSourceCatalog sources,
            KpiSubjectDirectory subjects,
            KpiStatisticsExecutor statistics,
            KpiReminderRecipientDirectory reminderRecipients,
            KpiReminderNotifier reminders
    ) {
        return new KpiService(
                repository, sources, subjects, statistics,
                reminderRecipients, reminders,
                Clock.systemUTC());
    }

    @Bean
    public KpiReminderNotifier kpiReminderNotifier(
            IdempotentMemberMessageFacade messages
    ) {
        return new KpiInboxReminderNotifier(messages);
    }
}
