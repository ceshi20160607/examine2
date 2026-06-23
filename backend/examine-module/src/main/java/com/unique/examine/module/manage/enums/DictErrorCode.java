package com.unique.examine.module.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 系统字典错误码。
 */
@Getter
@RequiredArgsConstructor
public enum DictErrorCode implements ErrorCode {

    /** 字典类型不存在。 */
    TYPE_NOT_FOUND("DICT_TYPE_NOT_FOUND", "字典类型不存在", HttpStatus.NOT_FOUND, false),

    /** 字典项不存在。 */
    ITEM_NOT_FOUND("DICT_ITEM_NOT_FOUND", "字典项不存在", HttpStatus.NOT_FOUND, false),

    /** 字典类型编码重复。 */
    TYPE_CODE_DUPLICATE("DICT_TYPE_CODE_DUPLICATE", "字典类型编码重复", HttpStatus.CONFLICT, false),

    /** 字典项编码重复。 */
    ITEM_CODE_DUPLICATE("DICT_ITEM_CODE_DUPLICATE", "字典项编码重复", HttpStatus.CONFLICT, false),

    /** 字典项值重复。 */
    ITEM_VALUE_DUPLICATE("DICT_ITEM_VALUE_DUPLICATE", "字典项值重复", HttpStatus.CONFLICT, false),

    /** 父级字典项不存在。 */
    PARENT_NOT_FOUND("DICT_PARENT_NOT_FOUND", "父级字典项不存在", HttpStatus.NOT_FOUND, false),

    /** 父级字典项已停用。 */
    PARENT_DISABLED("DICT_PARENT_DISABLED", "父级字典项已停用", HttpStatus.CONFLICT, false),

    /** 层级深度超过限制。 */
    DEPTH_EXCEEDED("DICT_DEPTH_EXCEEDED", "字典层级超过限制", HttpStatus.BAD_REQUEST, false),

    /** 字典类型已被引用。 */
    TYPE_IN_USE("DICT_TYPE_IN_USE", "字典类型已被引用", HttpStatus.CONFLICT, false),

    /** 字典项已被引用。 */
    ITEM_IN_USE("DICT_ITEM_IN_USE", "字典项已被引用", HttpStatus.CONFLICT, false),

    /** 字典项存在启用子项。 */
    HAS_ENABLED_CHILDREN("DICT_HAS_ENABLED_CHILDREN", "字典项存在启用子项", HttpStatus.CONFLICT, false),

    /** 内置字典不允许当前操作。 */
    BUILTIN_READONLY("DICT_BUILTIN_READONLY", "内置字典不允许当前操作", HttpStatus.FORBIDDEN, false),

    /** 字典作用域非法。 */
    SCOPE_INVALID("DICT_SCOPE_INVALID", "字典作用域不合法", HttpStatus.BAD_REQUEST, false),

    /** 字典状态流转冲突。 */
    STATUS_CONFLICT("DICT_STATUS_CONFLICT", "字典状态不合法或版本冲突", HttpStatus.CONFLICT, false),

    /** 字典缓存刷新失败。 */
    CACHE_REFRESH_FAILED("DICT_CACHE_REFRESH_FAILED", "字典缓存刷新失败", HttpStatus.INTERNAL_SERVER_ERROR, true);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.DICT;
    }
}
