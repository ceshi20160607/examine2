package com.unique.examine.module.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 模块动作配置回显。
 */
@Data
@Builder
@Schema(description = "模块动作配置回显")
public class ActionConfigVO {

    @Schema(description = "动作 ID")
    private String actionId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "动作类型")
    private String actionType;

    @Schema(description = "是否危险操作")
    private Boolean danger;

    @Schema(description = "是否需要确认")
    private Boolean confirmRequired;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "配置 JSON")
    private String config;

    @Schema(description = "排序")
    private Integer sortOrder;
}
