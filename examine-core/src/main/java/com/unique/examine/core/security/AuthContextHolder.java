package com.unique.examine.core.security;

public final class AuthContextHolder {

    private static final ThreadLocal<Long> PLAT_ID = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void setPlatId(Long platId) {
        PLAT_ID.set(platId);
    }

    public static Long getPlatId() {
        return PLAT_ID.get();
    }

    public static void clear() {
        PLAT_ID.remove();
    }
}
