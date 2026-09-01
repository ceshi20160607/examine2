package com.unique.unexamine.authentication.manage;

public final class AuthenticationContextHolder {
    private static final ThreadLocal<AuthenticatedContext> CURRENT = new ThreadLocal<>();

    private AuthenticationContextHolder() {
    }

    public static void set(AuthenticatedContext context) {
        CURRENT.set(context);
    }

    public static AuthenticatedContext require() {
        AuthenticatedContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No authenticated context on current request");
        }
        return context;
    }

    public static AuthenticatedContext currentOrNull() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
