package com.unique.examine.module.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 导出错误码。
 */
@Getter
@RequiredArgsConstructor
public enum ExportErrorCode implements ErrorCode {

    /** 导出模板不存在。 */
    TEMPLATE_NOT_FOUND("EXPORT_TEMPLATE_NOT_FOUND", "导出模板不存在", HttpStatus.NOT_FOUND, false),

    /** 导出模板字段无效。 */
    TEMPLATE_FIELD_INVALID("EXPORT_TEMPLATE_FIELD_INVALID", "导出模板字段无效", HttpStatus.BAD_REQUEST, false),

    /** 无导出权限。 */
    PERMISSION_DENIED("EXPORT_PERMISSION_DENIED", "无导出权限", HttpStatus.FORBIDDEN, false),

    /** 导出任务不存在。 */
    JOB_NOT_FOUND("EXPORT_JOB_NOT_FOUND", "导出任务不存在", HttpStatus.NOT_FOUND, false),

    /** 导出任务状态冲突。 */
    JOB_STATUS_CONFLICT("EXPORT_JOB_STATUS_CONFLICT", "导出任务状态冲突", HttpStatus.CONFLICT, false),

    /** 导出文件生成失败。 */
    FILE_GENERATE_FAILED("EXPORT_FILE_GENERATE_FAILED", "导出文件生成失败", HttpStatus.INTERNAL_SERVER_ERROR, true),

    /** 导出文件存储失败。 */
    STORAGE_FAILED("EXPORT_STORAGE_FAILED", "导出文件存储失败", HttpStatus.INTERNAL_SERVER_ERROR, true),

    /** 导入能力暂未实现。 */
    IMPORT_NOT_IMPLEMENTED("IMPORT_NOT_IMPLEMENTED", "导入能力暂未实现", HttpStatus.NOT_IMPLEMENTED, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.EXPORT;
    }
}
