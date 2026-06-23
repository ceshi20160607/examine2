package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员重置平台账号密码入参。
 */
@Data
@Schema(description = "管理员重置平台账号密码入参")
public class PlatformAccountResetPasswordBO {

    @Schema(description = "新密码")
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
