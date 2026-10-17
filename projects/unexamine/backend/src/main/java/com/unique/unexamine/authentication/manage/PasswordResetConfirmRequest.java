package com.unique.unexamine.authentication.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 一次性令牌只能从找回链接取得，不能作为账号密码重复使用。 */
public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 12, max = 200) String newPassword) {
}
