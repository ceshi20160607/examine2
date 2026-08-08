package com.unique.examine.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.repository.JdbcPlatformAiRepository;
import com.unique.examine.ai.repository.PlatformAiRepository;
import com.unique.examine.ai.provider.OpenAiCompatiblePlatformProviderClient;
import com.unique.examine.ai.service.PlatformAiPolicyService;
import com.unique.examine.ai.service.PlatformAiProviderService;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.id.IdService;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.time.Clock;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAiSpringProxyContractTest {
    @Test
    void transactionalPlatformRepositoryAndServicesAreProxyable() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            assertThat(AopUtils.isAopProxy(
                    context.getBean(JdbcPlatformAiRepository.class))).isTrue();
            assertThat(AopUtils.isAopProxy(
                    context.getBean(PlatformAiProviderService.class))).isTrue();
            assertThat(AopUtils.isAopProxy(
                    context.getBean(PlatformAiPolicyService.class))).isTrue();
        }
    }

    @Test
    void platformHttpProviderHasAnUnambiguousSpringConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(PlatformSecretResolverFacade.class,
                    () -> request -> java.util.Optional.empty());
            context.register(OpenAiCompatiblePlatformProviderClient.class);
            context.refresh();

            assertThat(context.getBean(
                    OpenAiCompatiblePlatformProviderClient.class)).isNotNull();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override protected Object doGetTransaction() { return new Object(); }
                @Override protected void doBegin(
                        Object transaction, TransactionDefinition definition) { }
                @Override protected void doCommit(DefaultTransactionStatus status) { }
                @Override protected void doRollback(DefaultTransactionStatus status) { }
            };
        }

        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:unused");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean IdService idService() { return new IdService(); }
        @Bean
        JdbcPlatformAiRepository jdbcPlatformAiRepository(
                JdbcTemplate jdbc, ObjectMapper json) {
            return new JdbcPlatformAiRepository(jdbc, json);
        }
        @Bean
        PlatformAiProviderService platformAiProviderService(
                PlatformAiRepository repository, IdService ids, Clock clock) {
            return new PlatformAiProviderService(repository, ids, clock);
        }
        @Bean
        PlatformAiPolicyService platformAiPolicyService(
                PlatformAiRepository repository, IdService ids,
                Clock clock, ObjectMapper json) {
            return new PlatformAiPolicyService(repository, ids, clock, json);
        }
    }
}
