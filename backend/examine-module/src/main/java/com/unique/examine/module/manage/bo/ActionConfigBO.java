package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模块动作配置入参。
 */
@Data
@Schema(description = "模块动作配置入参")
public class ActionConfigBO {

    @NotBlank(message = "动作编码不能为空")
    @Schema(description = "动作编码")
    private String actionCode;

    @NotBlank(message = "动作名称不能为空")
    @Schema(description = "动作名称")
    private String actionName;

    @Schema(description = "动作类型：BUTTON、ROW、DETAIL、EXPORT、FLOW")
    private String actionType;

    @Schema(description = "是否危险操作")
    private Boolean danger;

    @Schema(description = "是否需要确认")
    private Boolean confirmRequired;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "前端按钮、状态规则和权限提示配置")
    private Object config;

    @Schema(description = "排序")
    private Integer sortOrder;
}
