package com.unique.examine.flow.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.extension.FlowExtensionRepository;
import com.unique.examine.flow.extension.JdbcFlowExtensionRepository;
import com.unique.examine.flow.interaction.FlowInteractionServiceFactory;
import com.unique.examine.flow.interaction.JdbcFlowInteractionServiceFactory;
import com.unique.examine.flow.interaction.jdbc.JdbcFlowInteractionRepositoryFactory;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import com.unique.examine.flow.metrics.DefaultFlowMetricsFacade;
import com.unique.examine.flow.metrics.FlowMetricsFacade;
import com.unique.examine.flow.metrics.FlowMetricsRepository;
import com.unique.examine.flow.metrics.JdbcFlowMetricsRepository;
import com.unique.examine.flow.service.FlowRequestServiceFactory;
import com.unique.examine.flow.service.JdbcFlowRequestServiceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

@Configuration
public class FlowPersistenceConfiguration {
    @Bean
    public FlowExtensionRepository flowExtensionRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper
    ) {
        return new JdbcFlowExtensionRepository(jdbc, objectMapper);
    }

    @Bean
    public JdbcApprovalRepositoryFactory jdbcApprovalRepositoryFactory(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        return new JdbcApprovalRepositoryFactory(jdbc, transactionManager);
    }

    @Bean
    public FlowMetricsRepository flowMetricsRepository(JdbcTemplate jdbc) {
        return new JdbcFlowMetricsRepository(jdbc);
    }

    @Bean
    public FlowMetricsFacade flowMetricsFacade(FlowMetricsRepository repository) {
        return new DefaultFlowMetricsFacade(repository);
    }

    @Bean
    public FlowRequestServiceFactory flowRequestServiceFactory(
            JdbcApprovalRepositoryFactory repositories,
            IdService ids
    ) {
        return new JdbcFlowRequestServiceFactory(repositories, ids, Clock.systemUTC());
    }

    @Bean
    public JdbcFlowInteractionRepositoryFactory jdbcFlowInteractionRepositoryFactory(
            JdbcTemplate jdbc
    ) {
        return new JdbcFlowInteractionRepositoryFactory(jdbc);
    }

    @Bean
    public FlowInteractionServiceFactory flowInteractionServiceFactory(
            FlowRequestServiceFactory workflows,
            JdbcFlowInteractionRepositoryFactory repositories,
            IdService ids
    ) {
        return new JdbcFlowInteractionServiceFactory(
                workflows,
                repositories,
                ids,
                Clock.systemUTC()
        );
    }
}
