package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 字段配置回显。
 */
@Data
@Builder
@Schema(description = "字段配置回显")
public class FieldVO {

    @Schema(description = "字段 ID")
    private String fieldId;

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "字段名称")
    private String name;

    @Schema(description = "字段编码")
    private String code;

    @Schema(description = "字段类型")
    private String fieldType;

    @Schema(description = "是否必填")
    private Boolean required;

    @Schema(description = "是否唯一")
    private Boolean unique;

    @Schema(description = "是否生成索引")
    private Boolean indexed;

    @Schema(description = "默认值 JSON")
    private String defaultValue;

    @Schema(description = "字典类型 ID")
    private String dictTypeId;

    @Schema(description = "关联配置 JSON")
    private String relationConfig;

    @Schema(description = "子表配置 JSON")
    private String subTableConfig;

    @Schema(description = "自动编号配置 JSON")
    private String serialConfig;

    @Schema(description = "校验配置 JSON")
    private String validation;

    @Schema(description = "展示配置 JSON")
    private String displayConfig;

    @Schema(description = "字段状态")
    private String status;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "字段静态选项")
    private List<FieldOptionVO> options;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
