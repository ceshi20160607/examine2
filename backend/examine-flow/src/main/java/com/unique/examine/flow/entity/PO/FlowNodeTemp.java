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
 * 节点模板（node_temp；可复用）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-21
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_node_temp")
@Schema(name = "FlowNodeTemp对象", description = "节点模板（node_temp；可复用）")
public class FlowNodeTemp implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "节点模板ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "模板编码（同 system 内唯一）")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "节点类型：start|approve|cc|condition|parallel|join|end|custom")
    private String nodeType;

    @Schema(description = "模板配置（参与人规则/表单权限/策略等）")
    private String configJson;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

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
