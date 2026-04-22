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
 * 流程实例记录（record；原 instance）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_record")
@Schema(name = "FlowRecord对象", description = "流程实例记录（record；原 instance）")
public class FlowRecord implements Serializable {

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

    @Schema(description = "根实例ID（顶层实例为自身；子流程实例指向最顶层实例，便于整单查询）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long rootRecordId;

    @Schema(description = "父实例ID（subflow 子流程运行态；顶层实例为空）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentRecordId;

    @Schema(description = "父 record 中触发 subflow 的节点 node_key（仅 subflow 子 record 使用）")
    private String parentNodeKey;

    @Schema(description = "父实例中触发 subflow 的任务ID（可选，便于回溯）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentTaskId;

    @Schema(description = "流程模板ID（un_flow_temp.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tempId;

    @Schema(description = "流程模板版本号（发布时的版本）")
    private Integer tempVerNo;

    @Schema(description = "运行时流程图快照（模板→实例复制；改模板不影响已发起实例）")
    private String graphJson;

    @Schema(description = "运行时表单快照（可选）")
    private String formJson;

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

    @Schema(description = "当前节点 node_key（可选）")
    private String currentNodeKey;

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
