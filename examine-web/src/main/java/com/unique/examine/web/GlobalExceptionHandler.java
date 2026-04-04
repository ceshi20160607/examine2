package com.unique.examine.web;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.web.common.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = "参数错误";
        if (e.getBindingResult().getFieldError() != null) {
            msg = e.getBindingResult().getFieldError().getDefaultMessage();
        }
        return ResponseEntity.badRequest().body(ApiResult.fail(400, msg));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BusinessException e) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        int c = e.getCode();
        if (c == 401) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (c == 403) {
            status = HttpStatus.FORBIDDEN;
        } else if (c == 404) {
            status = HttpStatus.NOT_FOUND;
        }
        return ResponseEntity.status(status).body(ApiResult.fail(c, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleAny(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(500, e.getMessage() != null ? e.getMessage() : "服务器错误"));
    }
}
