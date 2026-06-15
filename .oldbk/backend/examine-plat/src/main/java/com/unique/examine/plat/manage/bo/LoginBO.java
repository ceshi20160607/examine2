package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 平台账号登录入参。
 */
@Data
@Schema(description = "平台账号登录入参")
public class LoginBO {

    @Schema(description = "登录名")
    @NotBlank(message = "登录名不能为空")
    @Size(max = 64, message = "登录名长度不能超过 64")
    private String loginName;

    @Schema(description = "登录密码")
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过 128")
    private String password;

    @Schema(description = "验证码令牌，MVP 暂不启用")
    @Size(max = 128, message = "验证码令牌长度不能超过 128")
    private String captchaToken;
}
