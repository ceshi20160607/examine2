package com.unique.unexamine.shared.manage.web;

import com.unique.unexamine.shared.manage.web.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiResult<Map<String, Object>>> domain(DomainException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResult<Map<String, Object>>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求内容不符合要求", request, fields);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResult<Map<String, Object>>> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "唯一编码已被使用，或关联数据不属于当前范围", request, null);
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fields) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("traceId", TraceIdFilter.current(request));
        if (fields != null && !fields.isEmpty()) {
            detail.put("fields", fields);
        }
        return ResponseEntity.status(status).body(new ApiResult<>(code, message, detail));
    }
}
