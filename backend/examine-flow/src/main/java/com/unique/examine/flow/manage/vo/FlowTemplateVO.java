package com.unique.examine.flow.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 流程模板回显。
 */
@Data
@Builder
@Schema(description = "流程模板回显")
public class FlowTemplateVO {

    @Schema(description = "模板 ID")
    private String templateId;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "当前发布版本 ID")
    private String currentVersionId;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "版本号")
    private Integer versionNo;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
