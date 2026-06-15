package com.unique.examine.web.ops.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 健康检查结果。
 */
@Data
@Builder
@Schema(description = "健康检查结果")
public class HealthCheckResultVO {

    @Schema(description = "总体状态")
    private String status;

    @Schema(description = "requestId")
    private String requestId;

    @Schema(description = "检查项")
    private List<OpsComponentStatusVO> checks;
}
