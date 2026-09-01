package com.unique.unexamine.shared.manage.web;

import jakarta.servlet.http.HttpServletRequest;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.notification.manage.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final MessageService messageService;

    public ApiExceptionHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiResult<Map<String, Object>>> domain(DomainException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), request, exception.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResult<Map<String, Object>>> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求内容不符合要求", request,
                Map.of("fields", fields));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResult<Map<String, Object>>> conflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "唯一编码已被使用，或关联数据不属于当前范围", request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResult<Map<String, Object>>> unknown(Exception exception, HttpServletRequest request) {
        String requestId = TraceIdFilter.current(request);
        LOGGER.error("Unhandled API failure requestId={} method={} path={}",
                requestId, request.getMethod(), request.getRequestURI(), exception);
        try {
            messageService.notifyCriticalFailure(AuthenticationContextHolder.currentOrNull(), "INTERNAL_ERROR", requestId,
                    "服务处理失败，请由运维管理员按 requestId 排查");
        } catch (RuntimeException notificationFailure) {
            LOGGER.warn("Critical failure message could not be persisted requestId={} reason={}",
                    requestId, notificationFailure.getClass().getSimpleName());
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "服务处理失败，请使用请求编号联系管理员", request, null);
    }

    private ResponseEntity<ApiResult<Map<String, Object>>> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, ?> fields) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("traceId", TraceIdFilter.current(request));
        if (fields != null && !fields.isEmpty()) {
            detail.putAll(fields);
        }
        return ResponseEntity.status(status).body(new ApiResult<>(code, message, detail, TraceIdFilter.current(request)));
    }
}
