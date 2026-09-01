package com.unique.unexamine.shared.manage.web;

public record ApiResult<T>(String code, String message, T data, String requestId) {
    public ApiResult(String code, String message, T data) {
        this(code, message, data, TraceIdFilter.current());
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>("OK", "", data, TraceIdFilter.current());
    }

    public static ApiResult<Void> failure(String code, String message) {
        return new ApiResult<>(code, message, null, TraceIdFilter.current());
    }
}
