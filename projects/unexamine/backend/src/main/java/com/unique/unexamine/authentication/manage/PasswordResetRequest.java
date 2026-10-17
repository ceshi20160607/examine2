package com.unique.unexamine.authentication.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 找回密码时输入用户名或绑定邮箱；响应不披露账号是否存在。 */
public record PasswordResetRequest(@NotBlank @Size(max = 255) String account) {
}
