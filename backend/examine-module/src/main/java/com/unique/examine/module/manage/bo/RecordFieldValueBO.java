package com.unique.examine.module.manage.bo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 运行记录字段值入参。
 */
@Data
@Schema(description = "运行记录字段值入参")
public class RecordFieldValueBO {

    @NotBlank(message = "字段编码不能为空")
    @Schema(description = "字段编码，对应模块字段 code")
    private String fieldCode;

    @Schema(description = "字段值")
    private JsonNode value;

    @Schema(description = "前端展示值快照，可为空，由后端兜底")
    private JsonNode displayValue;
}
