package com.unique.unexamine.operations.manage;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component("operationsReadinessHealthIndicator")
public class OperationsReadinessHealthIndicator implements HealthIndicator {
    private final StartupPreflightChecks checks;

    public OperationsReadinessHealthIndicator(StartupPreflightChecks checks) {
        this.checks = checks;
    }

    @Override
    public Health health() {
        List<StartupPreflightChecks.Check> results = checks.inspect();
        Map<String, Object> details = new LinkedHashMap<>();
        results.forEach(item -> details.put(item.code(), Map.of(
                "status", item.status(), "message", item.message(), "checked", true)));
        boolean ready = results.stream().filter(StartupPreflightChecks.Check::critical)
                .allMatch(StartupPreflightChecks.Check::passed);
        return (ready ? Health.up() : Health.down()).withDetail("ready", ready).withDetails(details).build();
    }
}
