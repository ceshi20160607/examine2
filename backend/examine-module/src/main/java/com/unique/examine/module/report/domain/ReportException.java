package com.unique.examine.module.report.domain;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

public final class ReportException extends BusinessException {
    public ReportException(String code, String message) {
        super(code, message, status(code));
    }

    private static HttpStatus status(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Report error code is required");
        }
        if (code.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.endsWith("_CONFLICT")) {
            return HttpStatus.CONFLICT;
        }
        if (code.endsWith("_UNPUBLISHED")
                || code.endsWith("_UNAVAILABLE")
                || code.endsWith("_STALE")) {
            return HttpStatus.CONFLICT;
        }
        if (code.endsWith("_FORBIDDEN")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.endsWith("_INVALID") || code.endsWith("_BLOCKED")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
