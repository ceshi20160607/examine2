package com.unique.unexamine.shared.manage.web;

public record ApiResult<T>(String code, String message, T data) {
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>("OK", "", data);
    }

    public static ApiResult<Void> failure(String code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
