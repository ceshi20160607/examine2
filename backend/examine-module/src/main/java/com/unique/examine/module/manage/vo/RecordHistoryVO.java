package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 运行记录历史。
 */
@Data
@Builder
@Schema(description = "运行记录历史")
public class RecordHistoryVO {

    @Schema(description = "历史 ID")
    private String historyId;

    @Schema(description = "记录版本")
    private Integer recordVersion;

    @Schema(description = "操作类型")
    private String operationType;

    @Schema(description = "变更前状态")
    private String beforeStatus;

    @Schema(description = "变更后状态")
    private String afterStatus;

    @Schema(description = "变更字段")
    private JsonNode changedFields;

    @Schema(description = "变更前快照")
    private JsonNode beforeSnapshot;

    @Schema(description = "变更后快照")
    private JsonNode afterSnapshot;

    @Schema(description = "请求 ID")
    private String requestId;

    @Schema(description = "操作成员 ID")
    private String operatorMemberId;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
