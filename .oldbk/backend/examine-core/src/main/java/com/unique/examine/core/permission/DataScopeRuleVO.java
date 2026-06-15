package com.unique.examine.core.permission;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 数据范围规则。
 */
@Data
@Builder
@Schema(description = "数据范围规则")
public class DataScopeRuleVO {

    @Schema(description = "资源类型：SYSTEM、MODULE、FLOW、EXPORT、OPENAPI")
    private String resourceType;

    @Schema(description = "资源 ID，0 表示资源类型全局")
    private String resourceId;

    @Schema(description = "数据范围：SELF、DEPT、DEPT_TREE、ALL、CUSTOM")
    private String scopeType;

    @Schema(description = "允许访问的部门 ID")
    private Set<String> deptIds;

    @Schema(description = "允许访问的成员 ID")
    private Set<String> memberIds;

    @Schema(description = "自定义条件 JSON")
    private String customConditions;

    @Schema(description = "多角色合并规则")
    private String minVisibleRule;
}
