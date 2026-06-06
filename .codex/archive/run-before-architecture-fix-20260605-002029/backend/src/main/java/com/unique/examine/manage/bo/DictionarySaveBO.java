package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字典保存入参")
public class DictionarySaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotBlank private String dictCode;
    @NotBlank private String dictName;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

