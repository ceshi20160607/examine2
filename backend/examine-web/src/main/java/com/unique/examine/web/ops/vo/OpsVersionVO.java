package com.unique.examine.web.ops.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运维版本信息。
 */
@Data
@Builder
@Schema(description = "运维版本信息")
public class OpsVersionVO {

    @Schema(description = "应用名称")
    private String application;

    @Schema(description = "版本")
    private String version;

    @Schema(description = "构建时间")
    private String buildTime;

    @Schema(description = "requestId")
    private String requestId;
}
