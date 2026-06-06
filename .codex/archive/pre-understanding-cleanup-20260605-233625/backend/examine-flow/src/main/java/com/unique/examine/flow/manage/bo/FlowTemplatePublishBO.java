package com.unique.examine.flow.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 流程模板发布入参。
 */
@Data
@Schema(description = "流程模板发布入参")
public class FlowTemplatePublishBO {

    @Schema(description = "模板 ID")
    private Long templateId;

    @Schema(description = "发布版本号，由前端或配置端指定，数据库唯一约束兜底")
    private Integer versionNo;

    @Schema(description = "流程图 JSON 快照")
    private String graphJson;
}
