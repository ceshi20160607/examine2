package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字段选项保存入参")
public class FieldOptionSaveBO {
    @NotNull private Long fieldId;
    @NotBlank private String optionLabel;
    @NotBlank private String optionValue;
    @Schema(description = "排序号") private Integer sortOrder;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

