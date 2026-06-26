package com.unique.examine.core.context;

/**
 * Helper for reading the authenticated account id without coupling feature modules to platform services.
 */
public final class CurrentRequestHeaders {

    public static final String ACCOUNT_ID_HEADER = "X-Account-Id";

    private CurrentRequestHeaders() {
    }

    /**
     * Resolve current account id from request context.
     *
     * @return account id, or null when the request is anonymous
     */
    public static Long currentAccountIdOrNull() {
        return RequestContext.current().accountId();
    }
}
