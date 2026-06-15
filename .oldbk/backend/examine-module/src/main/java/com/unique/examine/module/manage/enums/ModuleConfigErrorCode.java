package com.unique.examine.module.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 应用、模块、字段和页面配置错误码。
 */
@Getter
@RequiredArgsConstructor
public enum ModuleConfigErrorCode implements ErrorCode {

    /** 应用编码重复。 */
    APP_CODE_DUPLICATED("MODULE_APP_CODE_DUPLICATED", "应用编码重复", HttpStatus.CONFLICT, false),

    /** 应用不存在。 */
    APP_NOT_FOUND("MODULE_APP_NOT_FOUND", "应用不存在", HttpStatus.NOT_FOUND, false),

    /** 应用状态非法。 */
    APP_STATUS_INVALID("MODULE_APP_STATUS_INVALID", "应用状态不合法", HttpStatus.CONFLICT, false),

    /** 模块编码重复。 */
    MODULE_CODE_DUPLICATED("MODULE_CODE_DUPLICATED", "模块编码重复", HttpStatus.CONFLICT, false),

    /** 模块不存在。 */
    MODULE_NOT_FOUND("MODULE_NOT_FOUND", "模块不存在", HttpStatus.NOT_FOUND, false),

    /** 模块状态非法或版本冲突。 */
    MODULE_STATUS_INVALID("MODULE_STATUS_INVALID", "模块状态不合法或版本冲突", HttpStatus.CONFLICT, false),

    /** 模块发布检查失败。 */
    MODULE_PUBLISH_CHECK_FAILED("MODULE_PUBLISH_CHECK_FAILED", "模块发布检查失败", HttpStatus.CONFLICT, false),

    /** 字段编码重复。 */
    FIELD_CODE_DUPLICATED("FIELD_CODE_DUPLICATED", "字段编码重复", HttpStatus.CONFLICT, false),

    /** 字段不存在。 */
    FIELD_NOT_FOUND("FIELD_NOT_FOUND", "字段不存在", HttpStatus.NOT_FOUND, false),

    /** 字段类型不支持。 */
    FIELD_TYPE_UNSUPPORTED("FIELD_TYPE_UNSUPPORTED", "字段类型不支持", HttpStatus.BAD_REQUEST, false),

    /** 字段删除存在历史数据。 */
    FIELD_DELETE_HAS_DATA("FIELD_DELETE_HAS_DATA", "字段存在历史数据，不能物理删除", HttpStatus.CONFLICT, false),

    /** 字段关联配置非法。 */
    FIELD_RELATION_INVALID("FIELD_RELATION_INVALID", "字段关联配置不合法", HttpStatus.BAD_REQUEST, false),

    /** 自动编号规则非法。 */
    FIELD_SERIAL_RULE_INVALID("FIELD_SERIAL_RULE_INVALID", "自动编号规则不合法", HttpStatus.BAD_REQUEST, false),

    /** 页面引用了不存在或已删除的字段。 */
    MODULE_PAGE_FIELD_MISSING("MODULE_PAGE_FIELD_MISSING", "页面引用了不存在的字段", HttpStatus.BAD_REQUEST, false),

    /** 运行菜单编码重复。 */
    MODULE_MENU_CODE_DUPLICATED("MODULE_MENU_CODE_DUPLICATED", "运行菜单编码重复", HttpStatus.CONFLICT, false),

    /** 配置版本冲突。 */
    MODULE_CONFIG_VERSION_CONFLICT("MODULE_CONFIG_VERSION_CONFLICT", "配置版本冲突", HttpStatus.CONFLICT, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.MODULE;
    }
}
