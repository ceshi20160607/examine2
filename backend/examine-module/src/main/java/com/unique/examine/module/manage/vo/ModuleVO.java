package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 模块配置回显。
 */
@Data
@Builder
@Schema(description = "模块配置回显")
public class ModuleVO {

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "应用 ID")
    private String appId;

    @Schema(description = "模块名称")
    private String name;

    @Schema(description = "模块编码")
    private String code;

    @Schema(description = "模块描述")
    private String description;

    @Schema(description = "模块状态")
    private String status;

    @Schema(description = "当前发布版本 ID")
    private String publishedVersion;

    @Schema(description = "流程绑定 ID")
    private String flowBindingId;

    @Schema(description = "标题字段 ID")
    private String titleFieldId;

    @Schema(description = "记录编号字段 ID")
    private String recordNoFieldId;

    @Schema(description = "字段数量")
    private Integer fieldCount;

    @Schema(description = "页面配置数量")
    private Integer pageSchemaCount;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
