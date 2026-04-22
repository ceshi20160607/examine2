package com.unique.examine.flow.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * <p>
 * 动作日志（log_action；原 action_log）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_log_action")
@Schema(name = "FlowLogAction对象", description = "动作日志（log_action；原 action_log）")
public class FlowLogAction implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "动作日志ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "record ID（un_flow_record.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "任务ID（可为空，如系统动作）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    @Schema(description = "节点 node_key（可选）")
    private String nodeKey;

    @Schema(description = "动作：start|approve|reject|transfer|add_sign|withdraw|terminate|cc")
    private String action;

    @Schema(description = "执行人 platId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long actorPlatId;

    @Schema(description = "审批意见/备注")
    private String commentText;

    @Schema(description = "附件引用（JSON：file_id 列表，使用 upload 模块）")
    private String attachmentJson;

    @Schema(description = "扩展信息（JSON）")
    private String extraJson;

    @Schema(description = "动作时间")
    private LocalDateTime actionTime;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
