package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * EAV 字段值 typed columns 和展示快照。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_value")
@Schema(name = "RecordValue", description = "EAV 字段值 typed columns 和展示快照。")
public class RecordValue implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段值 ID。")
    @TableId(value = "value_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long valueId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "字段 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "字段编码快照。")
    private String fieldCode;

    @Schema(description = "字段类型快照。")
    private String fieldType;

    @Schema(description = "主表字段为 ROOT，子表字段为子表行 ID。")
    private String rowKey;

    @Schema(description = "文本值或短展示值。")
    private String valueText;

    @Schema(description = "数值、金额。")
    private BigDecimal valueNumber;

    @Schema(description = "日期时间。")
    private LocalDateTime valueDatetime;

    @Schema(description = "日期。")
    private LocalDate valueDate;

    @Schema(description = "开关值。")
    private Byte valueBool;

    @Schema(description = "多选、附件、图片、关联、子表、地址、标签、JSON 原始值。")
    private String valueJson;

    @Schema(description = "后端补齐的展示值。")
    private String displayValueJson;

    @Schema(description = "字典、成员、部门、文件名、关联标题等历史展示快照。")
    private String valueSnapshotJson;

    @Schema(description = "typed value hash，供唯一和变更比较。")
    private String valueHash;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
