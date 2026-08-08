package com.unique.examine.plat.manage.audit;

import java.time.Instant;
import java.util.List;

public final class OperationsHealthModels {
    private OperationsHealthModels() {
    }

    public record Summary(
            Instant generatedAt,
            String overallStatus,
            String version,
            String flywayVersion,
            List<Component> components
    ) {
        public Summary {
            components = List.copyOf(components);
        }
    }

    public record Component(
            String code,
            String label,
            String status,
            String summary,
            String hint
    ) {
    }
}
