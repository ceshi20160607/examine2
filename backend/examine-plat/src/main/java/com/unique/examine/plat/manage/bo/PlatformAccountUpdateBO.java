package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 更新平台账号入参。
 */
@Data
@Schema(description = "更新平台账号入参")
public class PlatformAccountUpdateBO {

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;
}
