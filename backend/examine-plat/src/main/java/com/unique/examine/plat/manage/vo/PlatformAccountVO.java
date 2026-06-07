package com.unique.examine.plat.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台账号返回对象。
 */
@Data
@Builder
@Schema(description = "平台账号返回对象")
public class PlatformAccountVO {

    @Schema(description = "账号 ID")
    private String accountId;

    @Schema(description = "登录名")
    private String loginName;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "账号状态")
    private String status;

    @Schema(description = "平台角色 ID 集合")
    private List<String> roleIds;
}
