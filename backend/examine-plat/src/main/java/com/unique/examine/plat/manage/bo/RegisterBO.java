package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 平台账号注册入参。
 */
@Data
@Schema(description = "平台账号注册入参")
public class RegisterBO {

    @Schema(description = "登录名，全局唯一")
    @NotBlank(message = "登录名不能为空")
    @Size(max = 64, message = "登录名长度不能超过 64")
    private String loginName;

    @Schema(description = "登录密码")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度必须为 8 至 128")
    private String password;

    @Schema(description = "展示名称")
    @Size(max = 64, message = "展示名称长度不能超过 64")
    private String displayName;

    @Schema(description = "手机号")
    @Size(max = 32, message = "手机号长度不能超过 32")
    private String mobile;

    @Schema(description = "邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;
}
