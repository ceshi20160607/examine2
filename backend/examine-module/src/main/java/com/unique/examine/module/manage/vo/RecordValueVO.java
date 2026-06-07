package com.unique.examine.module.manage.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行记录字段值回显。
 */
@Data
@Builder
@Schema(description = "运行记录字段值回显")
public class RecordValueVO {

    @Schema(description = "字段 ID")
    private String fieldId;

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "字段类型")
    private String fieldType;

    @Schema(description = "字段值")
    private JsonNode value;

    @Schema(description = "展示值")
    private JsonNode displayValue;
}
