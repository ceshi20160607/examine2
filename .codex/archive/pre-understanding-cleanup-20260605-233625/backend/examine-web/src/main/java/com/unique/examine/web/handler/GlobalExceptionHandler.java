package com.unique.examine.web.handler;

import com.unique.examine.core.common.ApiResult;
import com.unique.examine.core.exception.BusinessException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResult<Void> handleBusinessException(BusinessException exception) {
        return ApiResult.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理非预期异常。
     *
     * @param exception 非预期异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception exception) {
        return ApiResult.fail("SYSTEM_ERROR", exception.getMessage());
    }
}
