package com.unique.examine.collab.recordteam;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class RecordTeamConfiguration {
    @Bean
    @ConditionalOnMissingBean(RecordTeamRepository.class)
    public RecordTeamRepository recordTeamRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        return new JdbcRecordTeamRepository(jdbcTemplate, transactionManager);
    }

    @Bean
    @ConditionalOnMissingBean(RecordTeamAuthorizationPolicy.class)
    public RecordTeamAuthorizationPolicy recordTeamAuthorizationPolicy() {
        return new CapabilityRecordTeamAuthorizationPolicy();
    }

    @Bean
    @ConditionalOnMissingBean(RecordTeamService.class)
    public RecordTeamService recordTeamService(
            RecordTeamRepository repository,
            RecordTeamAuthorizationPolicy authorizationPolicy
    ) {
        return new RecordTeamService(repository, authorizationPolicy);
    }
}
