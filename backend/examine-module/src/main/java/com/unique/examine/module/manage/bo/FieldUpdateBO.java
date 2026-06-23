package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字段更新入参。
 */
@Data
@Schema(description = "字段更新入参")
public class FieldUpdateBO {

    @Schema(description = "字段名称")
    private String name;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "是否唯一")
    private Boolean unique;

    @Schema(description = "是否生成索引")
    private Boolean indexed;

    @Schema(description = "默认值 JSON")
    private Object defaultValue;

    @Schema(description = "字典类型 ID")
    private String dictTypeId;

    @Schema(description = "静态选项")
    private List<FieldOptionBO> options;

    @Schema(description = "关联配置")
    private Object relationConfig;

    @Schema(description = "子表配置")
    private Object subTableConfig;

    @Schema(description = "自动编号配置")
    private Object serialConfig;

    @Schema(description = "校验配置")
    private Object validation;

    @Schema(description = "展示配置")
    private Object displayConfig;

    @Schema(description = "排序")
    private Integer sortOrder;

    @NotNull(message = "版本号不能为空")
    @Schema(description = "乐观锁版本")
    private Integer version;
}
