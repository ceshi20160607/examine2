package com.unique.examine.module.manage.service;

public record RequestContext(String requestId, String traceId) {
    public RequestContext {
        requestId = required(requestId, "requestId");
        traceId = required(traceId, "traceId");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
