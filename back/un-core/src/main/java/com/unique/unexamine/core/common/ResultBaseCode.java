package com.unique.unexamine.core.common;

/** 公共和业务响应码共同遵循的契约。 */
public interface ResultBaseCode {
    int getCode();
    String getMsg();
}
