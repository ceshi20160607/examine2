package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 系统成员更新入参。
 */
@Data
@Schema(description = "系统成员更新入参")
public class MemberUpdateBO {

    @Schema(description = "岗位名称")
    private String postName;

    @Schema(description = "默认租户 ID")
    private String defaultTenantId;

    @Schema(description = "可访问租户 ID 集合")
    private List<String> tenantIds;

    @Schema(description = "部门 ID 集合")
    private List<String> deptIds;
}
