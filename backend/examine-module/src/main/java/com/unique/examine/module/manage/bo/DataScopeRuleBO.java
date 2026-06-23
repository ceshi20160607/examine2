package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 角色数据范围保存入参。
 */
@Data
@Schema(description = "角色数据范围保存入参")
public class DataScopeRuleBO {

    @Schema(description = "租户 ID，0 表示系统级")
    private String tenantId;

    @NotBlank(message = "资源类型不能为空")
    @Schema(description = "资源类型：SYSTEM、MODULE、FLOW、EXPORT、OPENAPI")
    private String resourceType;

    @Schema(description = "资源 ID，0 表示该类型全局")
    private String resourceId;

    @NotBlank(message = "数据范围不能为空")
    @Schema(description = "数据范围：SELF、DEPT、DEPT_TREE、ALL、CUSTOM")
    private String scopeType;

    @Schema(description = "部门范围")
    private List<String> deptIds;

    @Schema(description = "成员范围")
    private List<String> memberIds;

    @Schema(description = "结构化自定义条件")
    private String customConditions;

    @Schema(description = "多角色合并规则")
    private String minVisibleRule;
}
