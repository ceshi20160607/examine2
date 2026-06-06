package com.unique.examine.module.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 模块字段保存入参。
 */
@Data
@Schema(description = "模块字段保存入参")
public class ModuleFieldSaveBO {

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "字段名称")
    private String fieldName;

    @Schema(description = "字段类型：TEXT、NUMBER、DECIMAL、DATE、DATETIME、SELECT、MULTI_SELECT、USER、DEPT、FILE")
    private String fieldType;

    @Schema(description = "是否必填：0-否，1-是")
    private Byte requiredFlag;

    @Schema(description = "是否唯一：0-否，1-是")
    private Byte uniqueFlag;

    @Schema(description = "列表是否可见：0-否，1-是")
    private Byte listVisible;

    @Schema(description = "是否可搜索：0-否，1-是")
    private Byte searchable;

    @Schema(description = "是否可编辑：0-否，1-是")
    private Byte editable;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "校验规则 JSON")
    private String validationJson;

    @Schema(description = "排序号")
    private Integer sortOrder;

    @Schema(description = "字段选项列表")
    private List<ModuleFieldOptionDTO> options;
}
