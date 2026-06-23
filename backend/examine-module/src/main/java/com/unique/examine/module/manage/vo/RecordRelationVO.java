package com.unique.examine.module.manage.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行记录关联关系。
 */
@Data
@Builder
@Schema(description = "运行记录关联关系")
public class RecordRelationVO {

    @Schema(description = "关系 ID")
    private String relationId;

    @Schema(description = "关联字段 ID")
    private String fieldId;

    @Schema(description = "目标模块 ID")
    private String targetModuleId;

    @Schema(description = "目标记录 ID")
    private String targetRecordId;

    @Schema(description = "关系类型")
    private String relationType;

    @Schema(description = "展示快照")
    private JsonNode displaySnapshot;
}
