package com.unique.examine.app.manage.enums;

import lombok.Getter;

/**
 * 应用与 OpenAPI 管理错误码。
 */
@Getter
public enum AppManageErrorCode {

    PARAM_REQUIRED("APP_PARAM_REQUIRED", "应用管理参数缺失"),
    DATA_NOT_FOUND("APP_DATA_NOT_FOUND", "应用或 OpenAPI 数据不存在"),
    STATUS_INVALID("APP_STATUS_INVALID", "应用或 OpenAPI 状态不允许当前操作");

    private final String code;
    private final String message;

    AppManageErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
