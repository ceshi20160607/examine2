package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 模块按钮、行操作、详情操作和导出入口动作。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_action")
@Schema(name = "Action", description = "模块按钮、行操作、详情操作和导出入口动作。")
public class Action implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "动作 ID。")
    @TableId(value = "action_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actionId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "动作编码，如 RECORD_CREATE、RECORD_EXPORT。")
    private String actionCode;

    @Schema(description = "动作名称。")
    private String actionName;

    @Schema(description = "BUTTON、ROW、DETAIL、EXPORT、FLOW。")
    private String actionType;

    @Schema(description = "是否危险操作。")
    private Byte dangerFlag;

    @Schema(description = "是否需要确认。")
    private Byte confirmRequired;

    @Schema(description = "是否启用。")
    private Byte enabledFlag;

    @Schema(description = "前端按钮、状态规则和权限提示配置。")
    private String configJson;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
