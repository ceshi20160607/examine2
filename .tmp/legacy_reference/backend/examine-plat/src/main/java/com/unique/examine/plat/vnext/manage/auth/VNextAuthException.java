package com.unique.examine.plat.vnext.manage.auth;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

final class VNextAuthException extends BusinessException {
    VNextAuthException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
