package com.unique.examine.flow.manage.vo;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程发布检查结果。
 */
@Data
@Builder
@Schema(description = "流程发布检查结果")
public class FlowPublishCheckResultVO {

    @Schema(description = "是否通过")
    private Boolean passed;

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "下一版本号")
    private Integer nextVersionNo;

    @Schema(description = "问题列表")
    private List<FlowCheckIssueVO> issues;

    @Schema(description = "请求 ID")
    private String requestId;

    @Schema(description = "检查时间")
    private LocalDateTime checkedAt;
}
