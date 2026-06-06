package com.unique.examine.plat.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 认证会话错误码。
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    /** 登录名或密码错误。 */
    INVALID_CREDENTIAL("AUTH_INVALID_CREDENTIAL", "登录名或密码错误", HttpStatus.UNAUTHORIZED, false),

    /** 平台账号已停用。 */
    ACCOUNT_DISABLED("AUTH_ACCOUNT_DISABLED", "账号已停用", HttpStatus.FORBIDDEN, false),

    /** 平台账号已锁定。 */
    ACCOUNT_LOCKED("AUTH_ACCOUNT_LOCKED", "账号已锁定", HttpStatus.LOCKED, false),

    /** accessToken 已过期或无效。 */
    TOKEN_EXPIRED("AUTH_TOKEN_EXPIRED", "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED, false),

    /** refreshToken 无效。 */
    REFRESH_INVALID("AUTH_REFRESH_INVALID", "刷新凭证无效，请重新登录", HttpStatus.UNAUTHORIZED, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.AUTH;
    }
}
