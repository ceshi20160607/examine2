package com.unique.examine.web;

import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

final class FoundationStartupDiagnostics {
    private static final List<RequiredProperty> REQUIRED = List.of(
            new RequiredProperty("spring.datasource.url", "EXAMINE_VNEXT_DB_URL"),
            new RequiredProperty("spring.datasource.username", "EXAMINE_VNEXT_DB_USERNAME"),
            new RequiredProperty("spring.datasource.password", "EXAMINE_VNEXT_DB_PASSWORD"),
            new RequiredProperty("spring.data.redis.host", "EXAMINE_VNEXT_REDIS_HOST"),
            new RequiredProperty("spring.flyway.locations", "EXAMINE_VNEXT_FLYWAY_LOCATIONS")
    );

    private FoundationStartupDiagnostics() {
    }

    static void validateConfiguration(Environment environment) {
        if (!Boolean.parseBoolean(read(environment, "examine.foundation.vnext.enabled"))) {
            return;
        }
        if (!Boolean.parseBoolean(read(environment, "spring.flyway.enabled"))) {
            throw new IllegalStateException(
                    "FOUNDATION_FLYWAY_DISABLED: spring.flyway.enabled must be true for the VNext foundation"
            );
        }
        var missing = new ArrayList<String>();
        for (var property : REQUIRED) {
            var value = read(environment, property.name());
            if (value == null || value.isBlank() || value.contains("${")) {
                missing.add(property.name() + " (set " + property.environmentVariable() + ")");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "FOUNDATION_CONFIGURATION_MISSING: " + String.join(", ", missing)
            );
        }
    }

    private static String read(Environment environment, String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    private record RequiredProperty(String name, String environmentVariable) {
    }
}
