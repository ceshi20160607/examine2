package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("record_value")
@Schema(description = "record_value 表实体")
public class RecordValue {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "system_id")
    @TableField("system_id")
    private Long systemId;

    @Schema(description = "tenant_id")
    @TableField("tenant_id")
    private Long tenantId;

    @Schema(description = "module_id")
    @TableField("module_id")
    private Long moduleId;

    @Schema(description = "record_id")
    @TableField("record_id")
    private Long recordId;

    @Schema(description = "field_id")
    @TableField("field_id")
    private Long fieldId;

    @Schema(description = "string_value")
    @TableField("string_value")
    private String stringValue;

    @Schema(description = "number_value")
    @TableField("number_value")
    private BigDecimal numberValue;

    @Schema(description = "datetime_value")
    @TableField("datetime_value")
    private LocalDateTime datetimeValue;

    @Schema(description = "boolean_value")
    @TableField("boolean_value")
    private Integer booleanValue;

    @Schema(description = "json_value")
    @TableField("json_value")
    private String jsonValue;

    @Schema(description = "is_deleted")
    @TableField("is_deleted")
    private Integer isDeleted;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
