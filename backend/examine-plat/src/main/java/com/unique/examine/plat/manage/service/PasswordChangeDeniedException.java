package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

final class PasswordChangeDeniedException extends BusinessException {
    PasswordChangeDeniedException() {
        super(
                "PASSWORD_CURRENT_INVALID",
                "Current password is invalid",
                HttpStatus.BAD_REQUEST
        );
    }
}
