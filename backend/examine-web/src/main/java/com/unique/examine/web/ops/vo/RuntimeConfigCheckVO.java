package com.unique.examine.web.ops.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行配置检查结果。
 */
@Data
@Builder
@Schema(description = "运行配置检查结果")
public class RuntimeConfigCheckVO {

    @Schema(description = "配置键")
    private String configKey;

    @Schema(description = "组件")
    private String component;

    @Schema(description = "PASS、WARN、FAIL")
    private String status;

    @Schema(description = "检查消息")
    private String message;

    @Schema(description = "修复建议")
    private String suggestion;

    @Schema(description = "检查时间")
    private LocalDateTime checkedAt;
}
