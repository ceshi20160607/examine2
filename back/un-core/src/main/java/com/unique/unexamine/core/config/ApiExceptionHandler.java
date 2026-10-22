package com.unique.unexamine.core.config;

import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 对业务失败返回可理解的状态和原因，避免把不存在与无权错误伪装成服务异常。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /** 接口失败的稳定展示结构。 */
    public record ApiError(String code, String message) {}

    /** 业务服务明确声明的授权或冲突状态。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(
                String.valueOf(ex.getStatusCode().value()), ex.getReason()));
    }

    /** 参数无效时提示业务原因。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_ARGUMENT", ex.getMessage()));
    }

    /** 主键查询不到记录时返回不存在。 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> missing(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", ex.getMessage()));
    }

    /** 唯一键等数据约束失败属于业务冲突。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("DATA_CONFLICT", "数据已存在或被其他业务引用"));
    }

    /** 请求体字段校验失败时说明第一个字段问题。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数无效" : ex.getBindingResult().getFieldErrors().getFirst().getField() + "无效";
        return ResponseEntity.badRequest().body(new ApiError("INVALID_ARGUMENT", message));
    }
}
