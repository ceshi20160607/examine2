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
 * record-节点（node；关系表版）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_record_node")
@Schema(name = "FlowRecordNode对象", description = "record-节点（node；关系表版）")
public class FlowRecordNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "实例节点ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_flow_record.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "节点标识（同实例内唯一）")
    private String nodeKey;

    @Schema(description = "父节点标识：仅用于 group 容器分组（子节点指向 group.node_key）；subflow 不使用该字段表达嵌套")
    private String parentNodeKey;

    @Schema(description = "节点类型（可扩展）")
    private String nodeType;

    @Schema(description = "节点名称（可选）")
    private String nodeName;

    @Schema(description = "编辑器排序（不参与执行语义）")
    private Integer sortNo;

    @Schema(description = "状态：1=有效 2=作废")
    private Integer status;

    @Schema(description = "来源 temp_ver_id（追溯）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceTempVerId;

    @Schema(description = "来源 def_ver_node.id（追溯）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceDefNodeId;

    @Schema(description = "节点配置（可选）")
    private String configJson;

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
