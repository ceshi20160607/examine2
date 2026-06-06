package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("module_field")
@Schema(description = "module_field 表实体")
public class ModuleField {

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

    @Schema(description = "field_code")
    @TableField("field_code")
    private String fieldCode;

    @Schema(description = "field_name")
    @TableField("field_name")
    private String fieldName;

    @Schema(description = "field_type")
    @TableField("field_type")
    private String fieldType;

    @Schema(description = "required_flag")
    @TableField("required_flag")
    private Integer requiredFlag;

    @Schema(description = "unique_flag")
    @TableField("unique_flag")
    private Integer uniqueFlag;

    @Schema(description = "default_value")
    @TableField("default_value")
    private String defaultValue;

    @Schema(description = "enum_source")
    @TableField("enum_source")
    private String enumSource;

    @Schema(description = "validate_rule")
    @TableField("validate_rule")
    private String validateRule;

    @Schema(description = "sort_order")
    @TableField("sort_order")
    private Integer sortOrder;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
