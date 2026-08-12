package com.unique.examine.plat.manage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "examine.bootstrap.root")
public record BootstrapProperties(
        String username,
        String password,
        String displayName
) {
    public boolean enabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }

    public String effectiveDisplayName() {
        return displayName == null || displayName.isBlank() ? username : displayName.trim();
    }
}
