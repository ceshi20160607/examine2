package com.unique.examine.plat.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台配置返回对象。
 */
@Data
@Builder
@Schema(description = "平台配置返回对象")
public class PlatformConfigVO {

    @Schema(description = "配置 key")
    private String configKey;

    @Schema(description = "配置名称")
    private String configName;

    @Schema(description = "配置值，敏感配置返回脱敏值")
    private String value;

    @Schema(description = "是否敏感配置")
    private Boolean sensitive;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "备注")
    private String remark;
}
