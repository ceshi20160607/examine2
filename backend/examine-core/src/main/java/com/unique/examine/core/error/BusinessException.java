package com.unique.examine.core.error;

import com.unique.examine.core.api.ErrorField;
import java.util.List;

/**
 * Business exception carrying an API error code and optional frontend hints.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ErrorField> errorFields;
    private final String disabledReason;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), List.of(), null);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<ErrorField> errorFields,
                             String disabledReason) {
        super(message);
        this.errorCode = errorCode;
        this.errorFields = errorFields == null ? List.of() : List.copyOf(errorFields);
        this.disabledReason = disabledReason;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ErrorField> getErrorFields() {
        return errorFields;
    }

    public String getDisabledReason() {
        return disabledReason;
    }
}

