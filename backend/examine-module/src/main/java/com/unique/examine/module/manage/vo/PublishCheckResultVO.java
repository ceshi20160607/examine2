package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 模块发布检查结果。
 */
@Data
@Builder
@Schema(description = "模块发布检查结果")
public class PublishCheckResultVO {

    @Schema(description = "请求 ID")
    private String requestId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "是否通过")
    private Boolean passed;

    @Schema(description = "下一版本号")
    private Integer nextVersionNo;

    @Schema(description = "检查问题列表")
    private List<CheckIssueVO> issues;

    @Schema(description = "检查时间")
    private LocalDateTime checkedAt;
}
