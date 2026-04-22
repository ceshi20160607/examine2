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
 * 列表筛选项配置（可筛字段/默认值/顺序）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_list_filter_field")
@Schema(name = "ModuleListFilterField对象", description = "列表筛选项配置（可筛字段/默认值/顺序）")
public class ModuleListFilterField implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "筛选项ID")
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

    @Schema(description = "un_module_list_filter_tpl.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tplId;

    @Schema(description = "un_module_field.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "操作符：eq|ne|like|in|between|gt|lt 等")
    private String opCode;

    @Schema(description = "默认值（可选）")
    private String defaultValue;

    @Schema(description = "是否必填：1=必填 0=否")
    private Integer requiredFlag;

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
