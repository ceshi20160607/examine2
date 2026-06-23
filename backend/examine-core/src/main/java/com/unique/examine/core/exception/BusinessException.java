package com.unique.examine.core.exception;

import java.util.List;

import com.unique.examine.core.common.response.ApiErrorDetail;
import com.unique.examine.core.error.ErrorCode;

/**
 * 业务异常基类。
 */
public class BusinessException extends ExamineException {

    /**
     * 创建业务异常。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    /**
     * 创建业务异常。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建业务异常。
     *
     * @param errorCode 错误码
     * @param message 错误提示
     * @param errors 错误明细
     */
    public BusinessException(ErrorCode errorCode, String message, List<ApiErrorDetail> errors) {
        super(errorCode, message, errors);
    }
}
