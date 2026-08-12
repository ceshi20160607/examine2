package com.unique.examine.work.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.work.configuration.JdbcWorkConfigurationRepository;
import com.unique.examine.work.configuration.WorkConfigurationRepository;
import com.unique.examine.work.configuration.WorkConfigurationService;
import com.unique.examine.work.adapter.jdbc.JdbcWorkMemberDirectory;
import com.unique.examine.work.adapter.jdbc.JdbcWorkDailyReportRepository;
import com.unique.examine.work.adapter.jdbc.JdbcWorkProjectRepository;
import com.unique.examine.work.adapter.jdbc.JdbcWorkTaskRepository;
import com.unique.examine.work.adapter.jdbc.JdbcWorkTaskReminderRepository;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.port.WorkDailyReportRepository;
import com.unique.examine.work.port.WorkProjectRepository;
import com.unique.examine.work.port.WorkTaskRepository;
import com.unique.examine.work.port.WorkTaskReminderNotifier;
import com.unique.examine.work.port.WorkTaskReminderRepository;
import com.unique.examine.work.service.WorkProjectService;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkTaskService;
import com.unique.examine.work.service.WorkTaskReminderClaimWorker;
import com.unique.examine.work.service.WorkMetricsFacade;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class WorkJdbcConfiguration {
    @Bean
    WorkConfigurationRepository workConfigurationRepository(
            JdbcTemplate jdbcTemplate,
            IdService idService,
            ObjectMapper objectMapper
    ) {
        return new JdbcWorkConfigurationRepository(
                jdbcTemplate, idService, objectMapper);
    }

    @Bean
    WorkConfigurationService workConfigurationService(
            WorkConfigurationRepository repository,
            ObjectMapper objectMapper
    ) {
        return new WorkConfigurationService(
                repository, objectMapper, Clock.systemUTC());
    }

    @Bean
    WorkDailyReportRepository workDailyReportRepository(
            JdbcTemplate jdbcTemplate, IdService idService
    ) {
        return new JdbcWorkDailyReportRepository(jdbcTemplate, idService);
    }

    @Bean
    WorkTaskRepository workTaskRepository(JdbcTemplate jdbcTemplate, IdService idService) {
        return new JdbcWorkTaskRepository(jdbcTemplate, idService);
    }

    @Bean
    WorkTaskReminderRepository workTaskReminderRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        return new JdbcWorkTaskReminderRepository(jdbcTemplate, transactionManager);
    }

    @Bean
    WorkMemberDirectory workMemberDirectory(JdbcTemplate jdbcTemplate) {
        return new JdbcWorkMemberDirectory(jdbcTemplate);
    }

    @Bean
    WorkProjectRepository workProjectRepository(
            JdbcTemplate jdbcTemplate,
            IdService idService,
            PlatformTransactionManager transactionManager
    ) {
        return new JdbcWorkProjectRepository(
                jdbcTemplate, idService, transactionManager);
    }

    @Bean
    WorkTaskService workTaskService(
            WorkTaskRepository repository,
            WorkProjectRepository projects,
            WorkTaskReminderRepository reminders,
            WorkMemberDirectory members
    ) {
        return new WorkTaskService(
                repository, projects, reminders, members, Clock.systemUTC());
    }

    @Bean
    WorkMetricsFacade workMetricsFacade(WorkTaskRepository repository) {
        return new WorkMetricsFacade(repository, Clock.systemUTC());
    }

    @Bean
    WorkTaskReminderClaimWorker workTaskReminderClaimWorker(
            WorkTaskReminderRepository reminders,
            WorkTaskRepository tasks,
            WorkMemberDirectory members,
            WorkTaskReminderNotifier notifier
    ) {
        return new WorkTaskReminderClaimWorker(
                reminders, tasks, members, notifier, Clock.systemUTC());
    }

    @Bean
    WorkProjectService workProjectService(
            WorkProjectRepository repository,
            WorkMemberDirectory members
    ) {
        return new WorkProjectService(
                repository, members, Clock.systemUTC());
    }

    @Bean
    WorkDailyReportService workDailyReportService(
            WorkDailyReportRepository repository,
            WorkMemberDirectory members
    ) {
        return new WorkDailyReportService(
                repository, members, Clock.systemUTC());
    }
}
