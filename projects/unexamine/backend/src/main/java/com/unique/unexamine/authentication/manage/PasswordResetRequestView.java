package com.unique.unexamine.authentication.manage;

/** 投递方式只反映当前环境配置，不反映输入的账号是否存在。 */
public record PasswordResetRequestView(String deliveryMode) {
}
