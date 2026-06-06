package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字段保存入参")
public class FieldSaveBO {
    @NotNull private Long systemId;
    @NotNull private Long tenantId;
    @NotNull private Long moduleId;
    @NotBlank private String fieldCode;
    @NotBlank private String fieldName;
    @NotBlank private String fieldType;
    @Schema(description = "是否必填：0-否，1-是") private Integer requiredFlag;
    @Schema(description = "是否唯一：0-否，1-是") private Integer uniqueFlag;
    @Schema(description = "默认值") private String defaultValue;
    @Schema(description = "枚举或字典来源") private String enumSource;
    @Schema(description = "校验规则JSON") private String validateRule;
    @Schema(description = "排序号") private Integer sortOrder;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

