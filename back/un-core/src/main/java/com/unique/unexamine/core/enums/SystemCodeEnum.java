package com.unique.unexamine.core.enums;

import com.unique.unexamine.core.common.ResultBaseCode;

/** 全工程共用的基础响应码；具体业务可实现 ResultBaseCode 扩展。 */
public enum SystemCodeEnum implements ResultBaseCode {
    SYSTEM_OK(0, "成功"),
    SYSTEM_NO_VALID(400, "请求参数无效"),
    SYSTEM_NOT_LOGIN(401, "请先登录"),
    SYSTEM_NO_AUTH(403, "无权操作"),
    SYSTEM_NO_FOUND(404, "资源不存在"),
    SYSTEM_CONFLICT(409, "数据冲突"),
    SYSTEM_ERROR(500, "服务处理失败");

    private final int code;
    private final String msg;

    SystemCodeEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
