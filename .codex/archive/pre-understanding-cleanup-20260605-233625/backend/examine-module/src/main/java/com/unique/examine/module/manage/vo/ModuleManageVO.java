package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 动态模块统一出参。
 */
@Data
@Schema(description = "动态模块统一出参")
public class ModuleManageVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "布局或扩展配置 JSON")
    private String configJson;

    @Schema(description = "记录字段值")
    private Map<String, Object> values;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
