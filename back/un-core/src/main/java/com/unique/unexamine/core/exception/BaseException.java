package com.unique.unexamine.core.exception;

import com.unique.unexamine.core.common.ResultBaseCode;

/** 带稳定响应码的业务异常，由统一异常入口转换为 Result。 */
public class BaseException extends RuntimeException implements ResultBaseCode {
    private final int code;

    /** 使用响应码的默认业务说明。 */
    public BaseException(ResultBaseCode code) {
        this(code, code.getMsg());
    }

    /** 使用响应码并补充当前操作的失败说明。 */
    public BaseException(ResultBaseCode code, String message) {
        super(message);
        this.code = code.getCode();
    }

    /** 保留底层原因，供配置解析或并发写入失败在统一入口记录链路。 */
    public BaseException(ResultBaseCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code.getCode();
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return getMessage();
    }
}
