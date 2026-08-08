package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

public class LoginDeniedException extends BusinessException {
    public LoginDeniedException(String code, String message) {
        super(code, message, HttpStatus.UNAUTHORIZED);
    }
}
