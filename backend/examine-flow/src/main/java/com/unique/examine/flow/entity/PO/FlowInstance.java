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
 * 流程实例
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_instance")
@Schema(name = "FlowInstance对象", description = "流程实例")
public class FlowInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "流程定义ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defId;

    @Schema(description = "流程版本号（发布时的版本）")
    private Integer defVersionNo;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "业务ID（字符串以兼容多种ID）")
    private String bizId;

    @Schema(description = "实例标题（可选）")
    private String title;

    @Schema(description = "发起人 platId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long starterPlatId;

    @Schema(description = "状态：1=运行中 2=已结束 3=已撤回 4=已终止")
    private Integer status;

    @Schema(description = "当前节点ID（流程图中的节点标识，可选）")
    private String currentNodeId;

    @Schema(description = "发起时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "创建人 platId（一般同 starter_plat_id）")
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
