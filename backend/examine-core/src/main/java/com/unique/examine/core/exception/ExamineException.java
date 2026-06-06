package com.unique.examine.core.exception;

import java.util.List;

import com.unique.examine.core.common.response.ApiErrorDetail;
import com.unique.examine.core.error.ErrorCode;

import lombok.Getter;

/**
 * unexamine 后端异常基类。
 */
@Getter
public class ExamineException extends RuntimeException {

    private final ErrorCode errorCode;

    private final List<ApiErrorDetail> errors;

    /**
     * 创建异常。
     *
     * @param errorCode 错误码
     */
    public ExamineException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), List.of(), null);
    }

    /**
     * 创建异常。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     */
    public ExamineException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    /**
     * 创建异常。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     * @param errors 错误明细
     */
    public ExamineException(ErrorCode errorCode, String message, List<ApiErrorDetail> errors) {
        this(errorCode, message, errors, null);
    }

    /**
     * 创建异常。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     * @param errors 错误明细
     * @param cause 原始异常
     */
    public ExamineException(ErrorCode errorCode, String message, List<ApiErrorDetail> errors, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
