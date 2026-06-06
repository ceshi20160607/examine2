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
 * 模块字段定义、校验、唯一、关联、子表和自动编号配置。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_field")
@Schema(name = "Field", description = "模块字段定义、校验、唯一、关联、子表和自动编号配置。")
public class Field implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "字段 ID。")
    @TableId(value = "field_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "字段名称。")
    private String name;

    @Schema(description = "字段编码，同模块唯一。")
    private String code;

    @Schema(description = "字段类型。")
    private String fieldType;

    @Schema(description = "是否必填。")
    private Byte requiredFlag;

    @Schema(description = "是否字段级唯一。")
    private Byte uniqueFlag;

    @Schema(description = "是否生成查询索引。")
    private Byte indexFlag;

    @Schema(description = "默认值。")
    private String defaultValueJson;

    @Schema(description = "字典类型引用，由 DBA-002 字典表提供。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictTypeId;

    @Schema(description = "关联模块、展示字段和数据范围配置。")
    private String relationConfigJson;

    @Schema(description = "子表列定义。")
    private String subTableConfigJson;

    @Schema(description = "自动编号规则。")
    private String serialConfigJson;

    @Schema(description = "长度、范围、格式等校验。")
    private String validationJson;

    @Schema(description = "前端展示和格式化配置。")
    private String displayConfigJson;

    @Schema(description = "字段状态。")
    private String fieldStatus;

    @Schema(description = "字段排序。")
    private Integer sortOrder;

    @Schema(description = "乐观锁版本。")
    private Integer version;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "更新成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
