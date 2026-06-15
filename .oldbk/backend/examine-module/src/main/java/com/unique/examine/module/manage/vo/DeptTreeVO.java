package com.unique.examine.module.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 部门树节点。
 */
@Data
@Builder
@Schema(description = "部门树节点")
public class DeptTreeVO {

    @Schema(description = "部门 ID")
    private String deptId;

    @Schema(description = "所属系统 ID")
    private String systemId;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "父部门 ID")
    private String parentId;

    @Schema(description = "部门编码")
    private String code;

    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "部门状态")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "层级")
    private Integer depthLevel;

    @Schema(description = "部门路径")
    private String depthPath;

    @Schema(description = "子部门")
    private List<DeptTreeVO> children;
}
