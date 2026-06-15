package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建平台账号入参。
 */
@Data
@Schema(description = "创建平台账号入参")
public class PlatformAccountSaveBO {

    @Schema(description = "登录名，全局唯一")
    @NotBlank(message = "登录名不能为空")
    private String loginName;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "初始密码")
    @NotBlank(message = "初始密码不能为空")
    private String initialPassword;
}
