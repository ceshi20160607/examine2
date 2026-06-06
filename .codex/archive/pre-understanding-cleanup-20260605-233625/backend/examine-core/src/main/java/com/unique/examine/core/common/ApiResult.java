package com.unique.examine.core.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回结构。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一接口返回结构")
public class ApiResult<T> {

    @Schema(description = "业务错误码")
    private String code;

    @Schema(description = "响应消息")
    private String message;

    @Schema(description = "业务数据")
    private T data;

    /**
     * 创建成功响应。
     *
     * @param data 业务数据
     * @param <T> 业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>("SUCCESS", "success", data);
    }

    /**
     * 创建失败响应。
     *
     * @param code 错误码
     * @param message 错误消息
     * @param <T> 业务数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(String code, String message) {
        return new ApiResult<>(code, message, null);
    }
}
