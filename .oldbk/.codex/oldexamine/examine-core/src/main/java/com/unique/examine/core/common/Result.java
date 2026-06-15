package com.unique.examine.core.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 兼容生成器代码的轻量返回结构（仅用于编译与基础使用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}

