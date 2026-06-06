package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 账号保存入参。
 */
@Data
@Schema(description = "账号保存入参")
public class PlatformAccountSaveBO {

    @Schema(description = "登录账号，全局唯一")
    private String username;

    @Schema(description = "显示名称")
    private String displayName;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "密码明文，MVP 中保存为占位摘要，生产需替换为加密摘要")
    private String password;
}
