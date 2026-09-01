package com.unique.unexamine.shared.manage.web;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class DomainException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public DomainException(String code, String message, HttpStatus status) {
        this(code, message, status, Map.of());
    }

    public DomainException(String code, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, Object> details() {
        return details;
    }
}
