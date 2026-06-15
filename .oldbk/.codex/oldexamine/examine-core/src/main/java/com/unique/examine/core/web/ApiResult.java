package com.unique.examine.core.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;
    private String requestId;

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(0, "ok", data, null);
    }

    public static ApiResult<Void> ok() {
        return new ApiResult<>(0, "ok", null, null);
    }

    public static <T> ApiResult<T> fail(int code, String message) {
        return new ApiResult<>(code, message, null, null);
    }

    public static <T> ApiResult<T> fail(int code, String message, String requestId) {
        return new ApiResult<>(code, message, null, requestId);
    }
}

