package com.unique.examine.core.api;

import java.util.List;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        String traceId,
        List<ApiError> errors
) {
    public static <T> ApiResponse<T> success(T data, String requestId, String traceId) {
        return new ApiResponse<>("OK", "", data, requestId, traceId, List.of());
    }

    public static ApiResponse<Void> failure(
            String code,
            String message,
            String requestId,
            String traceId,
            List<ApiError> errors
    ) {
        return new ApiResponse<>(code, message, null, requestId, traceId, List.copyOf(errors));
    }

    public static <T> ApiResponse<T> failure(
            String code,
            String message,
            T data,
            String requestId,
            String traceId,
            List<ApiError> errors
    ) {
        return new ApiResponse<>(code, message, data, requestId, traceId, List.copyOf(errors));
    }
}
