package com.unique.examine.plat.manage.enums;

import lombok.Getter;

/**
 * 平台管理错误码。
 */
@Getter
public enum PlatManageErrorCode {

    PARAM_REQUIRED("PLAT_PARAM_REQUIRED", "平台管理参数缺失"),
    DATA_NOT_FOUND("PLAT_DATA_NOT_FOUND", "平台管理数据不存在"),
    STATUS_INVALID("PLAT_STATUS_INVALID", "平台状态值不合法"),
    PERMISSION_TYPE_INVALID("PLAT_PERMISSION_TYPE_INVALID", "平台权限类型不合法"),
    LOGIN_FAILED("PLAT_LOGIN_FAILED", "账号或状态不正确");

    private final String code;
    private final String message;

    PlatManageErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
