package com.unique.examine.core.error;

import org.springframework.http.HttpStatus;

/**
 * 业务模块可复用的错误码实现。
 */
public record ModuleErrorCode(ErrorCodeNamespace namespace, String code, String message, HttpStatus httpStatus,
        boolean retryable) implements ErrorCode {

    /**
     * 创建业务模块错误码。
     *
     * @param namespace 错误码命名空间
     * @param suffix 错误码后缀
     * @param message 默认错误提示
     * @param httpStatus HTTP 状态
     * @param retryable 是否建议重试
     * @return 模块化错误码
     */
    public static ModuleErrorCode of(ErrorCodeNamespace namespace, String suffix, String message,
            HttpStatus httpStatus, boolean retryable) {
        String normalizedSuffix = suffix == null ? "UNKNOWN" : suffix.strip().toUpperCase();
        return new ModuleErrorCode(namespace, namespace.name() + "_" + normalizedSuffix, message, httpStatus,
                retryable);
    }

    @Override
    public ErrorCodeNamespace getNamespace() {
        return namespace;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public boolean isRetryable() {
        return retryable;
    }
}
