package com.unique.examine.manage.enums;

import lombok.Getter;

@Getter
public enum ErrorCode {
    SUCCESS(0, "成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "数据不存在"),
    CONFLICT(409, "数据冲突"),
    CONFIG_NOT_PUBLISHED(460, "配置未发布"),
    INVALID_STATUS(461, "状态不允许执行该操作"),
    OPENAPI_SIGNATURE_INVALID(470, "OpenAPI签名校验失败"),
    IDEMPOTENCY_CONFLICT(471, "幂等键请求摘要冲突"),
    FILE_STORAGE_NOT_CONFIGURED(480, "文件存储未配置"),
    SYSTEM_ERROR(500, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
