package com.unique.examine.module.datasource.config;

import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.service.DataSourcePublicationPreflight;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.datasource.service.JdbcTableDataSourcePublicationPreflight;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DataSourceConfiguration {
    @Bean
    public DataSourceService dataSourceService(
            DataSourceRepository repository,
            DataSourceModuleCatalog moduleCatalog,
            ObjectProvider<DataSourcePublicationPreflight> preflights,
            ObjectProvider<JdbcTableDataSourcePublicationPreflight>
                    jdbcPreflights
    ) {
        var preflight = preflights.getIfUnique();
        var jdbcPreflight = jdbcPreflights.getIfUnique();
        if (jdbcPreflight == null) {
            return preflight == null
                    ? new DataSourceService(
                    repository, moduleCatalog, Clock.systemUTC())
                    : new DataSourceService(
                    repository, moduleCatalog, Clock.systemUTC(), preflight);
        }
        if (preflight == null) {
            preflight = (actor, draft) -> {
                throw new DataSourceException(
                        "DATA_SOURCE_PUBLICATION_PREFLIGHT_UNAVAILABLE",
                        "HTTP data source publication preflight is unavailable");
            };
        }
        return new DataSourceService(
                repository, moduleCatalog, Clock.systemUTC(), preflight,
                jdbcPreflight);
    }
}
