package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统成员信息。
 */
@Data
@Builder
@Schema(description = "系统成员信息")
public class MemberVO {

    @Schema(description = "成员 ID")
    private String memberId;

    @Schema(description = "所属系统 ID")
    private String systemId;

    @Schema(description = "平台账号 ID")
    private String accountId;

    @Schema(description = "平台账号登录名")
    private String loginName;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "成员编码")
    private String memberCode;

    @Schema(description = "默认租户 ID")
    private String defaultTenantId;

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "成员状态")
    private String status;

    @Schema(description = "是否系统超级管理员")
    private Boolean superAdmin;

    @Schema(description = "可访问租户 ID")
    private List<String> tenantIds;

    @Schema(description = "部门 ID")
    private List<String> deptIds;

    @Schema(description = "角色 ID")
    private List<String> roleIds;
}
