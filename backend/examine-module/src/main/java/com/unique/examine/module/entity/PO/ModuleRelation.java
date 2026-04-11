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
 * 模型关系（元数据）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_relation")
@Schema(name = "ModuleRelation对象", description = "模型关系（元数据）")
public class ModuleRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关系ID")
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

    @Schema(description = "源模型ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long srcModelId;

    @Schema(description = "目标模型ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dstModelId;

    @Schema(description = "关系类型：1-1|1-n|n-n")
    private String relType;

    @Schema(description = "关系配置（字段映射/约束/级联等）")
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
