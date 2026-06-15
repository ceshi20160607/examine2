package com.unique.examine.module.manage.bo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字段保存入参。
 */
@Data
@Schema(description = "字段保存入参")
public class FieldSaveBO {

    @NotBlank(message = "字段名称不能为空")
    @Schema(description = "字段名称")
    private String name;

    @NotBlank(message = "字段编码不能为空")
    @Schema(description = "字段编码，同模块唯一")
    private String code;

    @NotBlank(message = "字段类型不能为空")
    @Schema(description = "字段类型")
    private String fieldType;

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

    @Schema(description = "字段状态：ENABLED、DISABLED、DELETED")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;
}
