package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统成员邀请入参。
 */
@Data
@Schema(description = "系统成员邀请入参")
public class MemberInviteBO {

    @Schema(description = "平台账号 ID")
    private String accountId;

    @Schema(description = "平台账号登录名，accountId 为空时使用")
    private String loginName;

    @Schema(description = "账号展示名称；登录名不存在且需要自动创建平台账号时使用")
    private String displayName;

    @Schema(description = "初始密码；登录名不存在且需要自动创建平台账号时必填")
    private String initialPassword;

    @Schema(description = "成员编码，不传则使用平台账号登录名")
    private String memberCode;

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "默认租户 ID")
    private String defaultTenantId;

    @Schema(description = "可访问租户 ID 集合")
    private List<String> tenantIds;

    @Schema(description = "部门 ID 集合")
    private List<String> deptIds;

    @Schema(description = "角色 ID 集合")
    private List<String> roleIds;
}
