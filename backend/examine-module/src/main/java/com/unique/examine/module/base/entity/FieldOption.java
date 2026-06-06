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
 * 单选、多选、标签等字段静态选项。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_field_option")
@Schema(name = "FieldOption", description = "单选、多选、标签等字段静态选项。")
public class FieldOption implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "选项 ID。")
    @TableId(value = "option_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long optionId;

    @Schema(description = "字段 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "选项编码。")
    private String code;

    @Schema(description = "展示文本。")
    private String label;

    @Schema(description = "选项值。")
    private String value;

    @Schema(description = "颜色标识。")
    private String color;

    @Schema(description = "是否启用。")
    private Byte enabledFlag;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
