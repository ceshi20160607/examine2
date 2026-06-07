package com.unique.examine.core.web;

import java.util.List;

import com.unique.examine.core.common.response.ApiErrorDetail;
import com.unique.examine.core.common.response.ApiResponse;
import com.unique.examine.core.common.response.ApiResponseFactory;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.exception.ExamineException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常响应处理器。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ExamineException.class)
    public ResponseEntity<ApiResponse<Object>> handleExamineException(ExamineException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ApiResponse<Object> body = ApiResponseFactory.failure(errorCode, exception.getMessage(),
                exception.getErrors());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    /**
     * 处理 Bean Validation 参数异常。
     *
     * @param exception 参数异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ApiErrorDetail.builder()
                        .targetType("FIELD")
                        .fieldCode(fieldError.getField())
                        .reason("VALIDATION_FAILED")
                        .actual(fieldError.getRejectedValue())
                        .retryable(false)
                        .userMessage(fieldError.getDefaultMessage())
                        .build())
                .toList();
        return failure(CommonErrorCode.PARAM_INVALID, CommonErrorCode.PARAM_INVALID.getMessage(), errors);
    }

    /**
     * 处理绑定异常。
     *
     * @param exception 绑定异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException exception) {
        List<ApiErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> ApiErrorDetail.builder()
                        .targetType("FIELD")
                        .fieldCode(fieldError.getField())
                        .reason("BIND_FAILED")
                        .actual(fieldError.getRejectedValue())
                        .retryable(false)
                        .userMessage(fieldError.getDefaultMessage())
                        .build())
                .toList();
        return failure(CommonErrorCode.PARAM_INVALID, CommonErrorCode.PARAM_INVALID.getMessage(), errors);
    }

    /**
     * 处理请求体解析异常。
     *
     * @param exception 请求体解析异常
     * @return 统一错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        ApiErrorDetail error = ApiErrorDetail.builder()
                .targetType("REQUEST")
                .reason("BODY_NOT_READABLE")
                .retryable(false)
                .userMessage(CommonErrorCode.REQUEST_BODY_INVALID.getMessage())
                .build();
        return failure(CommonErrorCode.REQUEST_BODY_INVALID, CommonErrorCode.REQUEST_BODY_INVALID.getMessage(),
                List.of(error));
    }

    /**
     * 处理约束异常。
     *
     * @param exception 约束异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException exception) {
        List<ApiErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(violation -> ApiErrorDetail.builder()
                        .targetType("FIELD")
                        .fieldCode(violation.getPropertyPath().toString())
                        .reason("VALIDATION_FAILED")
                        .actual(violation.getInvalidValue())
                        .retryable(false)
                        .userMessage(violation.getMessage())
                        .build())
                .toList();
        return failure(CommonErrorCode.PARAM_INVALID, CommonErrorCode.PARAM_INVALID.getMessage(), errors);
    }

    /**
     * 处理请求头缺失异常，认证头缺失按未登录返回。
     *
     * @param exception 请求头缺失异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestHeader(MissingRequestHeaderException exception) {
        if ("Authorization".equalsIgnoreCase(exception.getHeaderName())) {
            return failure(CommonErrorCode.UNAUTHORIZED, CommonErrorCode.UNAUTHORIZED.getMessage(), List.of());
        }
        ApiErrorDetail error = ApiErrorDetail.builder()
                .targetType("HEADER")
                .fieldCode(exception.getHeaderName())
                .reason("HEADER_MISSING")
                .retryable(false)
                .userMessage(CommonErrorCode.PARAM_INVALID.getMessage())
                .build();
        return failure(CommonErrorCode.PARAM_INVALID, CommonErrorCode.PARAM_INVALID.getMessage(), List.of(error));
    }

    /**
     * 处理未预期异常。
     *
     * @param exception 原始异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        LOGGER.error("Unhandled backend exception", exception);
        ApiErrorDetail error = ApiErrorDetail.builder()
                .targetType("SYSTEM")
                .reason("INTERNAL_ERROR")
                .retryable(true)
                .userMessage(CommonErrorCode.INTERNAL_ERROR.getMessage())
                .build();
        return failure(CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.getMessage(), List.of(error));
    }

    private static ResponseEntity<ApiResponse<Object>> failure(ErrorCode errorCode, String message,
            List<ApiErrorDetail> errors) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponseFactory.failure(errorCode, message, errors));
    }
}
