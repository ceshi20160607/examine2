package com.unique.examine.core.security;

/**
 * 请求级安全上下文。
 */
public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> HOLDER = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    /**
     * 设置当前上下文。
     *
     * @param context 当前上下文
     */
    public static void set(AuthContext context) {
        HOLDER.set(context);
    }

    /**
     * 获取当前上下文。
     *
     * @return 当前上下文
     */
    public static AuthContext get() {
        return HOLDER.get();
    }

    /**
     * 清理当前上下文。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
