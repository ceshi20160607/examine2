package com.unique.examine.core.context;

import java.util.UUID;

/**
 * Request trace context shared by controllers, services, audit, and async tasks.
 */
public record RequestContext(String requestId, String traceId, String auditLogId, Long accountId) {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    /**
     * Bind context to current thread.
     *
     * @param context request context
     */
    public static void bind(RequestContext context) {
        HOLDER.set(context);
    }

    /**
     * Return current context, creating a fallback context when web filter has not run.
     *
     * @return current request context
     */
    public static RequestContext current() {
        RequestContext context = HOLDER.get();
        if (context == null) {
            return create(null, null);
        }
        return context;
    }

    /**
     * Clear current thread context.
     */
    public static void clear() {
        HOLDER.remove();
    }

    /**
     * Create a context with generated ids when caller did not provide them.
     *
     * @param requestId optional request id
     * @param traceId optional trace id
     * @return request context
     */
    public static RequestContext create(String requestId, String traceId) {
        String resolvedRequestId = hasText(requestId) ? requestId : "req_" + UUID.randomUUID();
        String resolvedTraceId = hasText(traceId) ? traceId : "trc_" + UUID.randomUUID();
        return new RequestContext(resolvedRequestId, resolvedTraceId, null, null);
    }

    /**
     * Return a copy with audit log id.
     *
     * @param auditLogId audit log id
     * @return copied context
     */
    public RequestContext withAuditLogId(String auditLogId) {
        return new RequestContext(requestId, traceId, auditLogId, accountId);
    }

    /**
     * Return a copy with authenticated account id.
     *
     * @param accountId authenticated account id
     * @return copied context
     */
    public RequestContext withAccountId(Long accountId) {
        return new RequestContext(requestId, traceId, auditLogId, accountId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
