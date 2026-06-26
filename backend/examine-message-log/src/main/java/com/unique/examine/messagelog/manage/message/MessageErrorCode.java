package com.unique.examine.messagelog.manage.message;

import com.unique.examine.core.error.ErrorCode;

/**
 * Message domain error codes.
 */
public enum MessageErrorCode implements ErrorCode {

    MESSAGE_REQUIRED_FIELD("MESSAGE_REQUIRED_FIELD", "消息必填字段缺失"),
    MESSAGE_TARGET_FORBIDDEN("MESSAGE_TARGET_FORBIDDEN", "消息目标不允许跨层直跳"),
    MESSAGE_CONTEXT_REQUIRED("MESSAGE_CONTEXT_REQUIRED", "系统消息需要系统成员上下文");

    private final String code;
    private final String message;

    MessageErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
