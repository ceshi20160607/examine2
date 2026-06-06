package com.unique.examine.module.manage.enums;

import lombok.Getter;

/**
 * 动态模块错误码。
 */
@Getter
public enum ModuleManageErrorCode {

    PARAM_REQUIRED("MODULE_PARAM_REQUIRED", "模块管理参数缺失"),
    DATA_NOT_FOUND("MODULE_DATA_NOT_FOUND", "模块数据不存在"),
    FIELD_INVALID("MODULE_FIELD_INVALID", "字段配置或字段值不合法"),
    RECORD_NO_DUPLICATE("MODULE_RECORD_NO_DUPLICATE", "记录编号已存在"),
    STATUS_INVALID("MODULE_STATUS_INVALID", "模块状态不合法");

    private final String code;
    private final String message;

    ModuleManageErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
