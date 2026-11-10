package com.unique.unexamine.sys.flow.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Flow 定义管理接口模型；保存流程图即产生新的启用版本。 */
public final class FlowDefinitionDto {
    private FlowDefinitionDto() {}

    /** Flow 元数据与节点线路快照；nodes 和 edges 由设计器提交。 */
    public record SaveFlowRequest(
            @NotBlank @Size(max = 80) @Schema(description = "租户内稳定 Flow 编码") String code,
            @NotBlank @Size(max = 120) @Schema(description = "Flow 名称") String name,
            @Size(max = 500) @Schema(description = "Flow 用途说明") String description,
            @Schema(description = "超时、撤回等 Flow 高级设置") JsonNode settings,
            @NotNull @Schema(description = "包含 nodes 和 edges 的 Flow 发布快照") JsonNode snapshot) {}

    /** Flow 定义及其版本历史。版本历史供已关联实例继续使用原快照。 */
    public record FlowView(Long id, String scopeType, Long systemId, Long tenantId,
                           String code, String name, String description, String status,
                           Integer latestVersion, JsonNode snapshot, LocalDateTime updateTime,
                           List<VersionView> versions) {}

    /** 不可变的 Flow 发布版本摘要。 */
    public record VersionView(Long id, Integer versionNo, String snapshotHash,
                              Long publishAccountId, LocalDateTime publishTime) {}
}
