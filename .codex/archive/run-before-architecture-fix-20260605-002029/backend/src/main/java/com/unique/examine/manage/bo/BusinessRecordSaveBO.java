package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "业务记录保存入参")
public class BusinessRecordSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long appId;
    @NotNull private Long moduleId;
    @Schema(description = "记录编号，空时后端通过序号表生成") private String recordNo;
    @Schema(description = "记录状态：DRAFT/SUBMITTED") private String recordStatus;
    @Schema(description = "应用版本ID") private Long appVersionId;
    @Schema(description = "配置快照JSON") private String configSnapshot;
    @Schema(description = "字段值列表") private java.util.List<com.unique.examine.manage.dto.FieldValueDTO> values;
}

