package com.unique.unexamine.sys.manage.dto;

import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ListView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.List;

/** 系统 Agent 查询的输入与结果；业务数据仅由模块权限查询入口返回。 */
public final class SystemAiRuntimeDto {
    private SystemAiRuntimeDto() {}

    /** 用户指定目标模块，避免模型自行选择其他系统或模块。 */
    public record QueryRequest(@NotNull Long configId, @NotNull Long moduleId,
            @NotBlank @Size(max = 1000) String message) {}

    /** 识别结果与查询口径同时返回，便于用户核对。 */
    public record QueryView(String intent, Long systemId, Long tenantId, Long moduleId,
            String scope, String keyword, Integer systemPolicyVersion, Integer platformModelVersion,
            String promptVersion, ListView result) {}

    /** 当前成员可用的已发布 Agent 模块动作，不暴露完整管理策略。 */
    public record AvailableView(Long configId, String name, Long moduleId, List<String> actions,
            Integer systemPolicyVersion, Integer platformModelVersion) {}

    /** 录入先识别，再由用户核对字段映射和置信度。 */
    public record WritePreviewRequest(@NotNull Long configId, @NotNull Long moduleId,
            @NotBlank @Size(max = 1000) String message) {}

    /** 低置信度结果不生成确认凭证。 */
    public record WritePreviewView(String intent, Long systemId, Long tenantId, Long moduleId,
            Map<String, Object> values, Map<String, Double> confidence, boolean ready,
            String confirmationToken, String conversationId,
            Integer systemPolicyVersion, Integer platformModelVersion,
            String promptVersion) {}

    /** 确认凭证绑定预览、当前用户与发布版本，不能直接提交新 payload。 */
    public record WriteConfirmRequest(@NotBlank String confirmationToken) {}

    /** 写入结果可用预览追踪标识关联 Agent 审计和原始对话。 */
    public record WriteResult(Long recordId, Long moduleId, String confirmationToken, String conversationId) {}
}
