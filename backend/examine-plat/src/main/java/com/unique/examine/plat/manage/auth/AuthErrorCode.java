package com.unique.examine.plat.manage.auth;

import com.unique.examine.core.error.ErrorCode;

/**
 * Authentication and system bootstrap error codes.
 */
public enum AuthErrorCode implements ErrorCode {

    LOGIN_NAME_REQUIRED("AUTH_LOGIN_NAME_REQUIRED", "登录名不能为空"),
    PASSWORD_REQUIRED("AUTH_PASSWORD_REQUIRED", "密码不能为空"),
    BAD_CREDENTIALS("AUTH_BAD_CREDENTIALS", "账号或密码不正确"),
    ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", "账号已停用"),
    SYSTEM_NOT_FOUND("AUTH_SYSTEM_NOT_FOUND", "系统不存在或已停用"),
    SYSTEM_BINDING_NOT_FOUND("AUTH_SYSTEM_BINDING_NOT_FOUND", "账号没有该系统的访问权限"),
    REGISTER_FIELD_REQUIRED("AUTH_REGISTER_FIELD_REQUIRED", "注册字段不完整"),
    ACCOUNT_CONFLICT("AUTH_ACCOUNT_CONFLICT", "账号、手机号或邮箱已存在"),
    SYSTEM_CODE_CONFLICT("AUTH_SYSTEM_CODE_CONFLICT", "系统编码已存在");

    private final String code;
    private final String message;

    AuthErrorCode(String code, String message) {
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
