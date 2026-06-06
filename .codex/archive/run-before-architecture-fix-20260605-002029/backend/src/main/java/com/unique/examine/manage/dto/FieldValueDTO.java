package com.unique.examine.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "运行记录字段值")
public class FieldValueDTO {
    @Schema(description = "字段ID")
    private Long fieldId;
    @Schema(description = "字段值，复杂值传JSON字符串")
    private String value;
}

