package com.unique.unexamine.system.manage;

import java.util.List;
import java.util.Map;

public record TenantModeMigrationPreflight(
        String fromMode,
        String toMode,
        boolean allowed,
        List<String> blockers,
        Map<String, Long> impact,
        List<String> migrationSteps) {
}
