package com.unique.examine.flow.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程抄送列表项。
 */
@Data
@Builder
@Schema(description = "流程抄送列表项")
public class FlowCcItemVO {

    @Schema(description = "抄送 ID")
    private String ccId;

    @Schema(description = "实例 ID")
    private String instanceId;

    @Schema(description = "模块 ID")
    private String moduleId;

    @Schema(description = "记录 ID")
    private String recordId;

    @Schema(description = "记录标题")
    private String recordTitle;

    @Schema(description = "节点名称")
    private String nodeName;

    @Schema(description = "是否已读")
    private Boolean read;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
