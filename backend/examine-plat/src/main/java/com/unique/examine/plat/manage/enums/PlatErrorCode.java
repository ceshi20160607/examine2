package com.unique.examine.plat.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 平台中心错误码。
 */
@Getter
@RequiredArgsConstructor
public enum PlatErrorCode implements ErrorCode {

    /** 平台账号登录名重复。 */
    ACCOUNT_DUPLICATED("PLAT_ACCOUNT_DUPLICATED", "平台账号已存在", HttpStatus.CONFLICT, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.PLAT;
    }
}
