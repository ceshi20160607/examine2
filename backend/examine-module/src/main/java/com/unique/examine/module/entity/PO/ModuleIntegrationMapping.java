package com.unique.examine.module.entity.po;

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
 * 集成字段映射（外参 ↔ 模型字段）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_integration_mapping")
@Schema(name = "ModuleIntegrationMapping对象", description = "集成字段映射（外参 ↔ 模型字段）")
public class ModuleIntegrationMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "映射ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_module_app.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "un_module_integration.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long integrationId;

    @Schema(description = "un_module_model.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(description = "方向：push|pull")
    private String direction;

    @Schema(description = "外部参数名/字段名")
    private String externalParam;

    @Schema(description = "un_module_field.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "是否必填：1=必填 0=否")
    private Integer requiredFlag;

    @Schema(description = "默认值（可选）")
    private String defaultValue;

    @Schema(description = "转换规则（映射、表达式、字典等）")
    private String transformJson;

    @Schema(description = "排序号")
    private Integer sortNo;

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
