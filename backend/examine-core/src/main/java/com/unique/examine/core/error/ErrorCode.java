package com.unique.examine.core.error;

import org.springframework.http.HttpStatus;

/**
 * 模块化错误码接口。
 */
public interface ErrorCode {

    /**
     * 错误码命名空间。
     *
     * @return 命名空间
     */
    ErrorCodeNamespace getNamespace();

    /**
     * 错误码编码。
     *
     * @return 错误码
     */
    String getCode();

    /**
     * 默认错误提示。
     *
     * @return 错误提示
     */
    String getMessage();

    /**
     * HTTP 状态。
     *
     * @return HTTP 状态
     */
    HttpStatus getHttpStatus();

    /**
     * 是否建议重试。
     *
     * @return true 表示可重试
     */
    boolean isRetryable();
}
