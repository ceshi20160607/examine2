package com.unique.unexamine.sys.flow.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 模块动作、业务事件与 Flow 的绑定接口模型。 */
public final class FlowBindingDto {
    private FlowBindingDto() {}

    /** 保存后立即用于后续命中的绑定；flowVersionId 由服务端取当前发布版本。 */
    public record SaveBindingRequest(
            @Schema(description = "已有绑定主键；新增时为空") Long id,
            @NotNull @Schema(description = "Flow 定义主键") Long flowId,
            @Schema(description = "触发业务模块主键；为空表示作用域级事件") Long moduleId,
            @Schema(description = "触发动作菜单主键") Long actionMenuId,
            @NotBlank @Size(max = 40) @Schema(description = "记录新建、编辑或动作事件编码") String eventCode,
            @Size(max = 30) @Schema(description = "先执行后触发、先审批后执行或只启动流程") String executionMode,
            @Schema(description = "同一事件的匹配顺序") Integer priorityNo,
            @Schema(description = "命中后是否不再匹配后续绑定") Boolean exclusiveFlag,
            @Schema(description = "业务字段条件") JsonNode condition) {}

    /** 当前租户的 Flow 绑定。 */
    public record BindingView(Long id, String scopeType, Long systemId, Long tenantId,
                              Long moduleId, Long actionMenuId, String eventCode, Long flowId,
                              Long flowVersionId, String executionMode, Integer priorityNo,
                              Boolean exclusiveFlag, JsonNode condition, String status,
                              Integer publishedVersion, LocalDateTime updateTime) {}
}
