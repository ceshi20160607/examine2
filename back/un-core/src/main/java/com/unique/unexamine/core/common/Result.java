package com.unique.unexamine.core.common;

import com.unique.unexamine.core.enums.SystemCodeEnum;

/** JSON 接口统一返回值，业务响应码与 HTTP 状态各负其责。 */
public record Result<T>(int code, String msg, T data) {
    /** 带业务数据的成功结果。 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(SystemCodeEnum.SYSTEM_OK.getCode(), SystemCodeEnum.SYSTEM_OK.getMsg(), data);
    }

    /** 无业务数据的成功结果。 */
    public static Result<Void> ok() {
        return ok(null);
    }

    /** 根据公共或业务响应码返回失败。 */
    public static <T> Result<T> error(ResultBaseCode code) {
        return new Result<>(code.getCode(), code.getMsg(), null);
    }

    /** 保留稳定错误码并补充本次错误原因。 */
    public static <T> Result<T> error(ResultBaseCode code, String message) {
        return new Result<>(code.getCode(), message, null);
    }

    /** 兼容明确的 HTTP 失败状态及其原因。 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 判断业务结果是否成功。 */
    public boolean hasSuccess() {
        return code == SystemCodeEnum.SYSTEM_OK.getCode();
    }
}
