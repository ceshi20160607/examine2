package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录入参")
public class AuthLoginBO {
    @NotBlank
    @Schema(description = "登录账号")
    private String account;
    @NotBlank
    @Schema(description = "登录密码")
    private String password;
}

