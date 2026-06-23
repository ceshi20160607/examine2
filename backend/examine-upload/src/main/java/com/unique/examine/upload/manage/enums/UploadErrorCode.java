package com.unique.examine.upload.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 上传文件错误码。
 */
@Getter
@RequiredArgsConstructor
public enum UploadErrorCode implements ErrorCode {

    /** 文件大小超过限制。 */
    SIZE_EXCEEDED("UPLOAD_SIZE_EXCEEDED", "文件大小超过限制", HttpStatus.BAD_REQUEST, false),

    /** 文件类型不允许上传。 */
    TYPE_FORBIDDEN("UPLOAD_TYPE_FORBIDDEN", "文件类型不允许上传", HttpStatus.BAD_REQUEST, false),

    /** 文件存储不可用。 */
    STORAGE_UNAVAILABLE("UPLOAD_STORAGE_UNAVAILABLE", "文件存储不可用", HttpStatus.INTERNAL_SERVER_ERROR, true),

    /** 文件不存在。 */
    FILE_NOT_FOUND("UPLOAD_FILE_NOT_FOUND", "文件不存在", HttpStatus.NOT_FOUND, false),

    /** 文件已删除或已过期。 */
    FILE_DELETED("UPLOAD_FILE_DELETED", "文件已删除或已过期", HttpStatus.CONFLICT, false),

    /** 文件引用权限不足。 */
    REF_PERMISSION_DENIED("UPLOAD_REF_PERMISSION_DENIED", "文件引用权限不足", HttpStatus.FORBIDDEN, false),

    /** 已引用文件不能直接删除。 */
    FILE_REFERENCED("UPLOAD_FILE_REFERENCED", "已引用文件不能直接删除", HttpStatus.CONFLICT, false),

    /** 文件不可预览。 */
    FILE_NOT_PREVIEWABLE("UPLOAD_FILE_NOT_PREVIEWABLE", "文件不可预览", HttpStatus.BAD_REQUEST, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.UPLOAD;
    }
}
