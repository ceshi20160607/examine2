package com.unique.examine.module.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 运行台记录错误码。
 */
@Getter
@RequiredArgsConstructor
public enum RuntimeRecordErrorCode implements ErrorCode {

    /** 运行记录不存在。 */
    RECORD_NOT_FOUND("MODULE_RECORD_NOT_FOUND", "运行记录不存在", HttpStatus.NOT_FOUND, false),

    /** 运行记录状态或版本冲突。 */
    RECORD_STATUS_CONFLICT("MODULE_RECORD_STATUS_CONFLICT", "运行记录状态或版本冲突", HttpStatus.CONFLICT, false),

    /** 必填字段缺失。 */
    FIELD_REQUIRED_MISSING("FIELD_REQUIRED_MISSING", "必填字段缺失", HttpStatus.BAD_REQUEST, false),

    /** 字段值类型不合法。 */
    FIELD_VALUE_TYPE_INVALID("FIELD_VALUE_TYPE_INVALID", "字段值类型不合法", HttpStatus.BAD_REQUEST, false),

    /** 字段唯一值冲突。 */
    FIELD_UNIQUE_CONFLICT("FIELD_UNIQUE_CONFLICT", "字段唯一值冲突", HttpStatus.CONFLICT, false),

    /** 流程绑定缺失。 */
    FLOW_BINDING_MISSING("FLOW_BINDING_MISSING", "模块未绑定流程", HttpStatus.CONFLICT, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.MODULE;
    }
}
