package com.unique.examine.web.config;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts exceptions to the frozen API response shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        LOGGER.warn("Business exception: code={}, message={}", exception.getErrorCode().code(),
                exception.getMessage());
        ApiResponse<Void> body = ApiResponse.failure(
                exception.getErrorCode().code(),
                exception.getMessage(),
                exception.getErrorFields(),
                exception.getDisabledReason()
        );
        return ResponseEntity.status(statusFor(exception)).body(body);
    }

    private HttpStatus statusFor(BusinessException exception) {
        if (CommonErrorCode.AUTH_UNAUTHORIZED.code().equals(exception.getErrorCode().code())) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (CommonErrorCode.PERMISSION_DENIED.code().equals(exception.getErrorCode().code())) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        ApiResponse<Void> body = ApiResponse.failure(
                CommonErrorCode.OPS_INTERNAL_ERROR.code(),
                CommonErrorCode.OPS_INTERNAL_ERROR.message(),
                null,
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
