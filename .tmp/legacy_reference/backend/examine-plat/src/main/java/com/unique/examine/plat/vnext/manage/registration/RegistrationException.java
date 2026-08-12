package com.unique.examine.plat.vnext.manage.registration;

import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.List;

final class RegistrationException extends BusinessException {
    RegistrationException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    RegistrationException(String message, HttpStatus status, List<ApiError> errors) {
        super("REGISTER_INVALID", message, status, errors);
    }
}
