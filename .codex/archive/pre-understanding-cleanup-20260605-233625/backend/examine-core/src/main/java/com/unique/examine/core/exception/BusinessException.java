package com.unique.examine.core.exception;

import lombok.Getter;

/**
 * 业务异常。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    /**
     * 创建业务异常。
     *
     * @param code 错误码
     * @param message 错误消息
     */
    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }
}
