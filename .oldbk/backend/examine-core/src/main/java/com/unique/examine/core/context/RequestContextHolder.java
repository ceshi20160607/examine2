package com.unique.examine.core.context;

/**
 * 请求上下文 ThreadLocal 持有器。
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> LOCAL = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    /**
     * 写入当前请求上下文。
     *
     * @param context 请求上下文
     */
    public static void set(RequestContext context) {
        LOCAL.set(context);
    }

    /**
     * 获取当前请求上下文。
     *
     * @return 请求上下文，非 Web 线程可能为空
     */
    public static RequestContext get() {
        return LOCAL.get();
    }

    /**
     * 清理当前线程上下文。
     */
    public static void clear() {
        LOCAL.remove();
    }
}
