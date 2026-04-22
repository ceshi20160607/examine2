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
 * temp_ver-节点（node；关系表版）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_temp_ver_node")
@Schema(name = "FlowTempVerNode对象", description = "temp_ver-节点（node；关系表版）")
public class FlowTempVerNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "版本节点ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_flow_temp_ver.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tempVerId;

    @Schema(description = "节点标识（同版本内唯一；原 graph_json.nodes[].id）")
    private String nodeKey;

    @Schema(description = "父节点标识：仅用于 group 容器分组（子节点指向 group.node_key）；subflow 不使用该字段表达嵌套")
    private String parentNodeKey;

    @Schema(description = "节点类型：start|approve|condition|cc|end|action_http|action_push|group|subflow|custom|...（可扩展）")
    private String nodeType;

    @Schema(description = "节点名称（可选）")
    private String nodeName;

    @Schema(description = "编辑器排序（不参与执行语义）")
    private Integer sortNo;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

    @Schema(description = "节点配置（可选；subflow 建议包含 sub_def_id/sub_def_code + 入参/出参映射；group 可包含展示信息）")
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
