package com.unique.unexamine.authentication.manage;

import jakarta.validation.constraints.NotBlank;

/** 目录账号凭据只用于本次 LDAPS Bind，不进入身份源配置和审计明细。 */
public record DirectoryLoginRequest(@NotBlank String username, @NotBlank String password) {}
