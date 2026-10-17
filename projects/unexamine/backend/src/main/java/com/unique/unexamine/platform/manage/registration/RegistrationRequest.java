package com.unique.unexamine.platform.manage.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 统一平台账号注册；系统名称为空时只创建平台账号，后续可申请或接受系统授权。 */
public record RegistrationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String displayName,
        @Email @Size(max = 255) String email,
        @Size(max = 200) String systemName,
        @Size(max = 50) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,49}") String systemCode) {
}
