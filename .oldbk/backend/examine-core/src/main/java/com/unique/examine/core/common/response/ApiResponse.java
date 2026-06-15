package com.unique.examine.core.common.response;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.error.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 统一响应模型。
 *
 * @param <T> data 数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "API 统一响应")
public class ApiResponse<T> {

    @Schema(description = "请求级追踪 ID")
    private String requestId;

    @Schema(description = "链路追踪 ID")
    private String traceId;

    @Schema(description = "响应时间，ISO-8601，Asia/Shanghai")
    private OffsetDateTime timestamp;

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "模块化错误码或成功码")
    private String code;

    @Schema(description = "稳定提示信息")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    @Schema(description = "响应元数据")
    private ApiResponseMeta meta;

    @Schema(description = "错误明细")
    @Builder.Default
    private List<ApiErrorDetail> errors = new ArrayList<>();

    /**
     * 生成成功响应。
     *
     * @param data 响应数据
     * @param meta 响应元数据
     * @return API 响应
     * @param <T> 响应数据类型
     */
    public static <T> ApiResponse<T> success(T data, ApiResponseMeta meta) {
        return ApiResponse.<T>builder()
                .requestId(meta.getRequestId())
                .traceId(meta.getTraceId())
                .timestamp(ResponseClock.now())
                .success(true)
                .code(CommonErrorCode.OK.getCode())
                .message(CommonErrorCode.OK.getMessage())
                .data(data)
                .meta(meta)
                .errors(List.of())
                .build();
    }

    /**
     * 生成失败响应。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     * @param meta 响应元数据
     * @param errors 错误明细
     * @return API 响应
     */
    public static ApiResponse<Object> failure(ErrorCode errorCode, String message, ApiResponseMeta meta,
            List<ApiErrorDetail> errors) {
        return ApiResponse.builder()
                .requestId(meta.getRequestId())
                .traceId(meta.getTraceId())
                .timestamp(ResponseClock.now())
                .success(false)
                .code(errorCode.getCode())
                .message(message)
                .data(null)
                .meta(meta)
                .errors(errors == null ? List.of() : errors)
                .build();
    }
}
