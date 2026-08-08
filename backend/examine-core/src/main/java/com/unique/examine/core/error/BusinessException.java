package com.unique.examine.core.error;

import com.unique.examine.core.api.ApiError;
import org.springframework.http.HttpStatus;

import java.util.List;

public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final List<ApiError> errors;
    private final Object data;

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, List.of(), null);
    }

    public BusinessException(String code, String message, HttpStatus status, List<ApiError> errors) {
        this(code, message, status, errors, null);
    }

    public BusinessException(
            String code,
            String message,
            HttpStatus status,
            List<ApiError> errors,
            Object data
    ) {
        super(message);
        this.code = code;
        this.status = status;
        this.errors = List.copyOf(errors);
        this.data = data;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public List<ApiError> errors() {
        return errors;
    }

    public Object data() {
        return data;
    }
}
