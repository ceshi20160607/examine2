package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 平台系统保存入参。
 */
@Data
@Schema(description = "平台系统保存入参")
public class PlatformSystemSaveBO {

    @Schema(description = "系统编码，全局唯一")
    private String systemCode;

    @Schema(description = "系统名称")
    private String systemName;

    @Schema(description = "系统说明")
    private String description;
}
