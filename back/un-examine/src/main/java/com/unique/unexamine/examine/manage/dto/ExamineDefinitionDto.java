package com.unique.unexamine.examine.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 审批定义管理接口模型；保存即校验并启用当前发布快照。 */
public final class ExamineDefinitionDto {
    private ExamineDefinitionDto() {}

    /** 审批定义及节点线路快照；nodes 和 edges 均由设计器提交。 */
    public record SaveDefinitionRequest(
            @NotBlank @Size(max = 80) @Schema(description = "租户内稳定审批编码") String code,
            @NotBlank @Size(max = 120) @Schema(description = "审批名称") String name,
            @Size(max = 255) @Schema(description = "审批图标") String icon,
            @Size(max = 500) @Schema(description = "审批说明") String description,
            @Schema(description = "审批分组主键") Long groupId,
            @Schema(description = "所属业务模块主键") Long moduleId,
            @Schema(description = "显示顺序") Integer sortNo,
            @NotNull @Schema(description = "包含 nodes、edges、可选 administratorAccountId 和节点 emptyApprover 的审批流程快照") JsonNode snapshot) {}

    /** 当前有效审批定义。 */
    public record DefinitionView(Long id, Long systemId, Long tenantId, Long groupId, Long moduleId,
                                 String code, String name, String icon, String description,
                                 Integer sortNo, String status, Integer publishedVersion,
                                 JsonNode snapshot, LocalDateTime updateTime) {}
}
