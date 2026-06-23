package com.unique.examine.web.ops.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * Migration 状态。
 */
@Data
@Builder
@Schema(description = "Migration 状态")
public class MigrationStatusVO {

    @Schema(description = "版本")
    private String version;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "校验值")
    private String checksum;

    @Schema(description = "安装时间")
    private LocalDateTime installedAt;

    @Schema(description = "执行耗时毫秒")
    private Integer executionTimeMs;

    @Schema(description = "失败摘要")
    private String errorMessage;
}
