package com.unique.examine.module.datasource.external.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Deployment-owned egress allowlist for HTTP JSON data-source checks. */
@ConfigurationProperties(prefix = "examine.module.datasource.http")
public record HttpDataSourceCheckProperties(List<String> allowedHosts) {
    public HttpDataSourceCheckProperties {
        allowedHosts = allowedHosts == null
                ? List.of()
                : allowedHosts.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .toList();
    }
}
