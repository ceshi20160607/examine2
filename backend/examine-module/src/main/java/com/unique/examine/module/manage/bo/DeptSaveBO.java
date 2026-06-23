package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 部门保存入参。
 */
@Data
@Schema(description = "部门保存入参")
public class DeptSaveBO {

    @Schema(description = "租户 ID，0 表示系统级共享部门")
    private String tenantId;

    @Schema(description = "父部门 ID，0 表示根部门")
    private String parentId;

    @NotBlank(message = "部门编码不能为空")
    @Schema(description = "部门编码")
    private String code;

    @NotBlank(message = "部门名称不能为空")
    @Schema(description = "部门名称")
    private String name;

    @Schema(description = "部门状态：ENABLED、DISABLED")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;
}
