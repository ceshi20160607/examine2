package com.unique.examine.module.datasource.external.jdbc;

import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.service.DataSourceService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.DriverManager;

/** Isolated wiring for the read-only MySQL table adapters. */
@Configuration
@EnableConfigurationProperties(JdbcTableDataSourceProperties.class)
public class JdbcTableDataSourceConfiguration {
    @Bean
    @ConditionalOnMissingBean(JdbcTableConnectionOpener.class)
    JdbcTableConnectionOpener jdbcTableConnectionOpener() {
        return DriverManager::getConnection;
    }

    @Bean
    JdbcTableSafeClient jdbcTableSafeClient(
            SecretResolverFacade secrets,
            JdbcTableConnectionOpener opener,
            JdbcTableDataSourceProperties properties
    ) {
        return new JdbcTableSafeClient(secrets, opener, properties);
    }

    @Bean
    JdbcTableDataSourceConnectionChecker
    jdbcTableDataSourceConnectionChecker(
            DataSourceService dataSources,
            JdbcTableSafeClient client
    ) {
        return new JdbcTableDataSourceConnectionChecker(
                dataSources, client);
    }

    @Bean
    JdbcTableDataSourceSchemaDiscoverer
    jdbcTableDataSourceSchemaDiscoverer(
            DataSourceService dataSources,
            JdbcTableSafeClient client
    ) {
        return new JdbcTableDataSourceSchemaDiscoverer(
                dataSources, client);
    }

    @Bean
    JdbcTableDataSourceDraftRowsPreviewer
    jdbcTableDataSourceDraftRowsPreviewer(
            DataSourceService dataSources,
            JdbcTableSafeClient client
    ) {
        return new JdbcTableDataSourceDraftRowsPreviewer(
                dataSources, client);
    }

    @Bean
    JdbcTableDataSourcePublicationPreflightAdapter
    jdbcTableDataSourcePublicationPreflight(
            JdbcTableSafeClient client
    ) {
        return new JdbcTableDataSourcePublicationPreflightAdapter(client);
    }

    @Bean
    JdbcTablePublishedDataSourceRowsReader
    jdbcTablePublishedDataSourceRowsReader(
            JdbcTableSafeClient client
    ) {
        return new JdbcTablePublishedDataSourceRowsReader(client);
    }
}
