package com.unique.examine.module.manage.security;

import java.util.Set;

public final class ConfigPermissions {
    public static final String SYSTEM_ADMIN_ACCESS = "system.admin.access";
    public static final String MODULE_CONFIG_MANAGE = "module.config.manage";

    public static final Set<String> REQUIRED_NOW =
            Set.of(SYSTEM_ADMIN_ACCESS, MODULE_CONFIG_MANAGE);
    public static final Set<String> REQUIRED_AFTER_DYNAMIC_REGISTRATION =
            REQUIRED_NOW;

    private ConfigPermissions() {
    }
}
