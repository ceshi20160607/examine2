package com.unique.examine.module.datasource.external.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Deployment-owned transport policy for read-only MySQL. */
@ConfigurationProperties(prefix = "examine.module.datasource.jdbc")
public record JdbcTableDataSourceProperties(
        List<String> allowedTargets,
        TlsMode tlsMode
) {
    public JdbcTableDataSourceProperties {
        allowedTargets = allowedTargets == null
                ? List.of()
                : allowedTargets.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
        tlsMode = tlsMode == null ? TlsMode.VERIFY_IDENTITY : tlsMode;
    }

    /** Deliberately excludes Connector/J's downgrade-prone PREFERRED mode. */
    public enum TlsMode {
        VERIFY_IDENTITY,
        VERIFY_CA,
        REQUIRED,
        DISABLED
    }
}
