package com.unique.examine.manage.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "全局配置保存入参")
public class GlobalConfigSaveBO {
    @Schema(description = "系统ID，平台级为0") private Long systemId;
    @Schema(description = "租户ID，平台级为0") private Long tenantId;
    @NotBlank private String configKey;
    @Schema(description = "配置值，不保存明文敏感值") private String configValue;
    @Schema(description = "是否仍为敏感占位：0-否，1-是") private Integer secretPlaceholderFlag;
    @Schema(description = "状态：ENABLED/DISABLED") private String status;
}

