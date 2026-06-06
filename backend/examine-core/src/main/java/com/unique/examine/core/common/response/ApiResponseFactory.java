package com.unique.examine.core.common.response;

import java.util.List;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.context.TraceIdGenerator;
import com.unique.examine.core.error.ErrorCode;

/**
 * API 响应创建工具。
 */
public final class ApiResponseFactory {

    private ApiResponseFactory() {
    }

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @return API 响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success(data, metaFromContext());
    }

    /**
     * 构建错误响应。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     * @param errors 错误明细
     * @return API 响应
     */
    public static ApiResponse<Object> failure(ErrorCode errorCode, String message, List<ApiErrorDetail> errors) {
        return ApiResponse.failure(errorCode, message, metaFromContext(), errors);
    }

    /**
     * 从当前请求上下文构建响应元数据。
     *
     * @return 响应元数据
     */
    public static ApiResponseMeta metaFromContext() {
        RequestContext context = RequestContextHolder.get();
        if (context == null) {
            String requestId = TraceIdGenerator.newRequestId();
            String traceId = TraceIdGenerator.newTraceId();
            return ApiResponseMeta.builder().requestId(requestId).traceId(traceId).build();
        }
        return ApiResponseMeta.builder()
                .requestId(context.getRequestId())
                .traceId(context.getTraceId())
                .path(context.getPath())
                .method(context.getMethod())
                .idempotencyKey(context.getIdempotencyKey())
                .requestHash(context.getRequestHash())
                .build();
    }
}
