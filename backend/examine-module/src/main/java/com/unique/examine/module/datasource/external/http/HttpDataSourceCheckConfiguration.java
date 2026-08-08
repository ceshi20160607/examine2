package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.service.DataSourceService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the deny-by-default HTTP data-source egress properties. */
@Configuration
@EnableConfigurationProperties(HttpDataSourceCheckProperties.class)
public class HttpDataSourceCheckConfiguration {
    @Bean
    HttpJsonDataSourceProbe httpJsonDataSourceProbe(
            OutboundHttpTransport transport,
            SecretResolverFacade secrets,
            ObjectMapper json,
            HttpDataSourceCheckProperties properties
    ) {
        return new HttpJsonDataSourceProbe(
                transport, secrets, json, properties);
    }

    @Bean
    HttpJsonDataSourceConnectionChecker httpJsonDataSourceConnectionChecker(
            DataSourceService dataSources,
            HttpJsonDataSourceProbe probe
    ) {
        return new HttpJsonDataSourceConnectionChecker(dataSources, probe);
    }

    @Bean
    HttpJsonDataSourceSchemaDiscoverer httpJsonDataSourceSchemaDiscoverer(
            DataSourceService dataSources,
            HttpJsonDataSourceProbe probe
    ) {
        return new HttpJsonDataSourceSchemaDiscoverer(dataSources, probe);
    }

    @Bean
    HttpJsonDataSourceDraftRowsPreviewer
    httpJsonDataSourceDraftRowsPreviewer(
            DataSourceService dataSources,
            HttpJsonDataSourceProbe probe
    ) {
        return new HttpJsonDataSourceDraftRowsPreviewer(dataSources, probe);
    }

    @Bean
    HttpJsonDataSourcePublicationPreflight
    httpJsonDataSourcePublicationPreflight(
            HttpJsonDataSourceProbe probe
    ) {
        return new HttpJsonDataSourcePublicationPreflight(probe);
    }

    @Bean
    HttpJsonPublishedDataSourceRowsReader
    httpJsonPublishedDataSourceRowsReader(
            HttpJsonDataSourceProbe probe
    ) {
        return new HttpJsonPublishedDataSourceRowsReader(probe);
    }
}
