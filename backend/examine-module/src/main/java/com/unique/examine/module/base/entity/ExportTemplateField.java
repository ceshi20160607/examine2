package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * 导出模板字段列、顺序和脱敏配置。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_export_template_field")
@Schema(name = "ExportTemplateField", description = "导出模板字段列、顺序和脱敏配置。")
public class ExportTemplateField implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模板字段 ID。")
    @TableId(value = "template_field_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateFieldId;

    @Schema(description = "模板 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    @Schema(description = "字段 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "字段编码快照。")
    private String fieldCode;

    @Schema(description = "导出表头。")
    private String headerName;

    @Schema(description = "列顺序。")
    private Integer columnOrder;

    @Schema(description = "是否要求明文导出权限。")
    private Byte plainRequiredFlag;

    @Schema(description = "脱敏策略编码。")
    private String maskStrategy;

    @Schema(description = "日期、金额、字典展示格式。")
    private String formatJson;

    @Schema(description = "是否启用。")
    private Byte enabledFlag;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
