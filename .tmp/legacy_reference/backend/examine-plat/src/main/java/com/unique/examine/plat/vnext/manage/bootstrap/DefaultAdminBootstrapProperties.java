package com.unique.examine.plat.vnext.manage.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "examine.foundation.vnext.bootstrap")
public record DefaultAdminBootstrapProperties(
        String username,
        String password,
        String displayName
) {
    String requiredUsername() {
        return requireText(username, "username").trim();
    }

    String requiredPassword() {
        return requireText(password, "password");
    }

    String effectiveDisplayName() {
        return displayName == null || displayName.isBlank() ? requiredUsername() : displayName.trim();
    }

    @Override
    public String toString() {
        return "DefaultAdminBootstrapProperties[username=" + username
                + ", password=<redacted>, displayName=" + displayName + "]";
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "FOUNDATION_BOOTSTRAP_CONFIGURATION_MISSING: examine.foundation.vnext.bootstrap."
                            + property + " must be configured"
            );
        }
        return value;
    }
}
