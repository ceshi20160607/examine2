package com.unique.examine.analytics.config;

import com.unique.examine.analytics.port.FlowAnalyticsSource;
import com.unique.examine.analytics.port.TodoAnalyticsSource;
import com.unique.examine.analytics.port.WorkAnalyticsSource;
import com.unique.examine.analytics.service.OperationsDashboardService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AnalyticsConfiguration {
    @Bean
    OperationsDashboardService operationsDashboardService(
            WorkAnalyticsSource work,
            FlowAnalyticsSource flow,
            TodoAnalyticsSource todo
    ) {
        return new OperationsDashboardService(work, flow, todo, Clock.systemUTC());
    }
}
