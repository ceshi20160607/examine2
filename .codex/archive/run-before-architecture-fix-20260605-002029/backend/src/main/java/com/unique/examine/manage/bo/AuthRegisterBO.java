package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "注册入参")
public class AuthRegisterBO {
    @NotBlank
    @Schema(description = "登录账号")
    private String account;
    @NotBlank
    @Schema(description = "姓名")
    private String realName;
    @Schema(description = "手机号")
    private String mobile;
    @Schema(description = "邮箱")
    private String email;
    @NotBlank
    @Schema(description = "登录密码")
    private String password;
}

