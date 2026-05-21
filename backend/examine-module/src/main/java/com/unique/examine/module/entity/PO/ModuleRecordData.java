package com.unique.examine.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 模型记录数据（EAV：一行一字段，与 un_module_field.field_code 对齐）
 * </p>
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_data")
@Schema(name = "ModuleRecordData对象", description = "模型记录数据（EAV）")
public class ModuleRecordData implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "数据行ID")
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

    @Schema(description = "un_module_model.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(description = "un_module_record.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "字段编码（与元数据 field_code 一致）")
    private String fieldCode;

    @Schema(description = "字段值（字符串存储）")
    private String valueText;

    @Schema(description = "数值/金额/百分比/布尔（0/1）等 typed 列；与 value_text 同步维护")
    @TableField("value_num")
    private BigDecimal valueNum;

    @Schema(description = "日期时间 typed 列；与 value_text 同步维护")
    @TableField("value_dt")
    private LocalDateTime valueDt;

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
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
