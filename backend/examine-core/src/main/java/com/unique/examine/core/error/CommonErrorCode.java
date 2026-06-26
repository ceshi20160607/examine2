package com.unique.examine.core.error;

/**
 * Common cross-module error codes.
 */
public enum CommonErrorCode implements ErrorCode {

    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "请先登录"),
    PERMISSION_DENIED("PERMISSION_DENIED", "没有权限执行该操作"),
    FIELD_VALIDATION_FAILED("FIELD_VALIDATION_FAILED", "字段校验失败"),
    TASK_STATE_CONFLICT("TASK_STATE_CONFLICT", "后台任务状态不允许该操作"),
    OPS_INTERNAL_ERROR("OPS_INTERNAL_ERROR", "系统内部异常");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}

