package com.unique.examine.plat.manage.service;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

final class PasswordRecoveryDeniedException extends BusinessException {
    private final Long accountId;

    private PasswordRecoveryDeniedException(
            String code,
            String message,
            HttpStatus status,
            Long accountId
    ) {
        super(code, message, status);
        this.accountId = accountId;
    }

    static PasswordRecoveryDeniedException invalid(Long accountId) {
        return new PasswordRecoveryDeniedException(
                "PASSWORD_RECOVERY_INVALID",
                "Recovery token is invalid or was already used",
                HttpStatus.BAD_REQUEST,
                accountId
        );
    }

    static PasswordRecoveryDeniedException expired(long accountId) {
        return new PasswordRecoveryDeniedException(
                "PASSWORD_RECOVERY_EXPIRED",
                "Recovery token has expired",
                HttpStatus.GONE,
                accountId
        );
    }

    static PasswordRecoveryDeniedException disabled(long accountId) {
        return new PasswordRecoveryDeniedException(
                "PASSWORD_RECOVERY_ACCOUNT_DISABLED",
                "Account is disabled",
                HttpStatus.FORBIDDEN,
                accountId
        );
    }

    static PasswordRecoveryDeniedException unchanged(long accountId) {
        return new PasswordRecoveryDeniedException(
                "PASSWORD_UNCHANGED",
                "New password must differ from the current password",
                HttpStatus.BAD_REQUEST,
                accountId
        );
    }

    Long accountId() {
        return accountId;
    }
}
