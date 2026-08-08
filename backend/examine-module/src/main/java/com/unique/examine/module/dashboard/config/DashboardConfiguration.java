package com.unique.examine.module.dashboard.config;

import com.unique.examine.module.dashboard.port.DashboardRepository;
import com.unique.examine.module.dashboard.port.DashboardKpiCatalog;
import com.unique.examine.module.dashboard.port.DashboardSourceCatalog;
import com.unique.examine.module.dashboard.service.DashboardService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DashboardConfiguration {
    @Bean
    public DashboardService dashboardService(
            DashboardRepository repository,
            DashboardSourceCatalog sources,
            DashboardKpiCatalog kpis
    ) {
        return new DashboardService(
                repository, sources, kpis, Clock.systemUTC());
    }
}
