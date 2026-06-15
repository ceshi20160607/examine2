package com.unique.examine.core.security;

import java.util.Collections;
import java.util.Set;

/**
 * 自建系统内 module 权限键快照（请求线程内；由拦截器填充，与 Redis 缓存配合）。
 * 所有者可用通配符 {@value #OWNER_WILDCARD} 表示全部权限。
 */
public final class ModuleAuthContextHolder {

    public static final String OWNER_WILDCARD = "*";

    private static final ThreadLocal<Set<String>> PERM_KEYS = new ThreadLocal<>();

    private ModuleAuthContextHolder() {}

    public static void setPermKeys(Set<String> keys) {
        PERM_KEYS.set(keys == null ? Set.of() : Set.copyOf(keys));
    }

    /** 未设置时视为空集（未走 /v1/system 链或非系统态）。 */
    public static Set<String> getPermKeys() {
        Set<String> s = PERM_KEYS.get();
        return s != null ? s : Collections.emptySet();
    }

    public static boolean hasModulePerm(String permKey) {
        if (permKey == null || permKey.isBlank()) {
            return false;
        }
        Set<String> keys = PERM_KEYS.get();
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        if (keys.contains(OWNER_WILDCARD)) {
            return true;
        }
        return keys.contains(permKey.trim());
    }

    public static boolean isOwnerWildcard() {
        Set<String> keys = PERM_KEYS.get();
        return keys != null && keys.contains(OWNER_WILDCARD);
    }

    public static void clear() {
        PERM_KEYS.remove();
    }
}
