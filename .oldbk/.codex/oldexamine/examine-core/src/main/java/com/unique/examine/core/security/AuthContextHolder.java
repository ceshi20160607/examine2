package com.unique.examine.core.security;

public final class AuthContextHolder {

    private static final ThreadLocal<Long> PLAT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<Long> SYSTEM_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void setPlatId(Long platId) {
        PLAT_ID.set(platId);
    }

    public static Long getPlatId() {
        return PLAT_ID.get();
    }

    public static void setUsername(String username) {
        USERNAME.set(username);
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    public static void clearUsername() {
        USERNAME.remove();
    }

    public static void setSystemId(Long systemId) {
        SYSTEM_ID.set(systemId);
    }

    public static Long getSystemIdOrDefault() {
        Long v = SYSTEM_ID.get();
        return v != null ? v : 0L;
    }

    public static void clearSystemId() {
        SYSTEM_ID.remove();
    }

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantIdOrDefault() {
        Long v = TENANT_ID.get();
        return v != null ? v : 0L;
    }

    public static void clearTenantId() {
        TENANT_ID.remove();
    }

    public static void clear() {
        PLAT_ID.remove();
        USERNAME.remove();
        SYSTEM_ID.remove();
        TENANT_ID.remove();
        ModuleAuthContextHolder.clear();
    }
}
