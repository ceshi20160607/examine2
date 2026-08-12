package com.unique.examine.module.report.config;

import com.unique.examine.module.report.port.ReportRepository;
import com.unique.examine.module.report.port.ReportSourceCatalog;
import com.unique.examine.module.report.service.ReportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ReportConfiguration {
    @Bean
    public ReportService reportService(
            ReportRepository repository,
            ReportSourceCatalog sources
    ) {
        return new ReportService(repository, sources, Clock.systemUTC());
    }
}
