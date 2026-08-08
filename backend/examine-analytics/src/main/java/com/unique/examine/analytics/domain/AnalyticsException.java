package com.unique.examine.analytics.domain;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

public final class AnalyticsException extends BusinessException {
    public AnalyticsException(String code, String message) {
        super(code, message, code.endsWith("_INVALID")
                ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.BAD_REQUEST);
    }
}
