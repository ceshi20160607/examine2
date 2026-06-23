package com.unique.examine.plat.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 平台中心错误码。
 */
@Getter
@RequiredArgsConstructor
public enum PlatErrorCode implements ErrorCode {

    /** 平台账号登录名重复。 */
    ACCOUNT_DUPLICATED("PLAT_ACCOUNT_DUPLICATED", "平台账号已存在", HttpStatus.CONFLICT, false),

    /** 系统编码重复。 */
    SYSTEM_CODE_DUPLICATED("PLAT_SYSTEM_CODE_DUPLICATED", "系统编码已存在", HttpStatus.CONFLICT, false),

    /** 系统初始化失败。 */
    SYSTEM_INIT_FAILED("PLAT_SYSTEM_INIT_FAILED", "系统初始化失败", HttpStatus.INTERNAL_SERVER_ERROR, true),

    /** 系统不存在。 */
    SYSTEM_NOT_FOUND("PLAT_SYSTEM_NOT_FOUND", "系统不存在", HttpStatus.NOT_FOUND, false),

    /** 系统状态不允许。 */
    SYSTEM_STATUS_INVALID("PLAT_SYSTEM_STATUS_INVALID", "系统状态不允许", HttpStatus.BAD_REQUEST, false),

    /** 平台账号不存在。 */
    ACCOUNT_NOT_FOUND("PLAT_ACCOUNT_NOT_FOUND", "平台账号不存在", HttpStatus.NOT_FOUND, false),

    /** 平台账号状态不允许。 */
    ACCOUNT_STATUS_INVALID("PLAT_ACCOUNT_STATUS_INVALID", "平台账号状态不允许", HttpStatus.BAD_REQUEST, false),

    /** 平台角色不存在。 */
    ROLE_NOT_FOUND("PLAT_ROLE_NOT_FOUND", "平台角色不存在", HttpStatus.NOT_FOUND, false),

    /** 平台角色编码重复。 */
    ROLE_CODE_DUPLICATED("PLAT_ROLE_CODE_DUPLICATED", "平台角色编码已存在", HttpStatus.CONFLICT, false),

    /** 平台角色状态不允许。 */
    ROLE_STATUS_INVALID("PLAT_ROLE_STATUS_INVALID", "平台角色状态不允许", HttpStatus.BAD_REQUEST, false),

    /** 平台配置不存在。 */
    CONFIG_NOT_FOUND("PLAT_CONFIG_NOT_FOUND", "平台配置不存在", HttpStatus.NOT_FOUND, false),

    /** 敏感配置禁止使用占位值覆盖。 */
    CONFIG_SENSITIVE_VALUE_FORBIDDEN("PLAT_CONFIG_SENSITIVE_VALUE_FORBIDDEN", "敏感配置不能使用占位值覆盖", HttpStatus.BAD_REQUEST, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.PLAT;
    }
}
