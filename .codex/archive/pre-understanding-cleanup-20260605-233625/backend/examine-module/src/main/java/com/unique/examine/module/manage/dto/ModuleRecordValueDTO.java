package com.unique.examine.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 模块记录字段值 DTO。
 */
@Data
@Schema(description = "模块记录字段值 DTO")
public class ModuleRecordValueDTO {

    @Schema(description = "字段 ID")
    private Long fieldId;

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "字段类型")
    private String fieldType;

    @Schema(description = "字段值")
    private Object value;
}
