package com.unique.examine.plat.vnext.manage.systementry;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

final class SystemEntryErrors {
    private SystemEntryErrors() {
    }

    static BusinessException sessionRequired() {
        return error("AUTH_SESSION_REQUIRED", "An authenticated session is required", HttpStatus.UNAUTHORIZED);
    }

    static BusinessException systemNotFound() {
        return error("SYSTEM_NOT_FOUND", "System was not found", HttpStatus.NOT_FOUND);
    }

    static BusinessException memberRequired() {
        return error("SYSTEM_MEMBER_REQUIRED", "An active and complete system membership is required", HttpStatus.FORBIDDEN);
    }

    static BusinessException memberDisabled() {
        return error("SYSTEM_MEMBER_DISABLED", "System membership is disabled", HttpStatus.FORBIDDEN);
    }

    static BusinessException noTenant() {
        return error("SYSTEM_NO_TENANT", "An active default tenant membership is required", HttpStatus.FORBIDDEN);
    }

    static BusinessException systemDisabled() {
        return error("SYSTEM_DISABLED", "System entry is unavailable", HttpStatus.FORBIDDEN);
    }

    private static BusinessException error(String code, String message, HttpStatus status) {
        return new BusinessException(code, message, status);
    }
}
