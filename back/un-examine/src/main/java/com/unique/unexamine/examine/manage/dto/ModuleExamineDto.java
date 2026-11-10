package com.unique.unexamine.examine.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 模块动作与审批定义的触发绑定接口模型；保存后立即参与后续业务保存链。 */
public final class ModuleExamineDto {
    private ModuleExamineDto() {}

    /** 绑定一个模块事件、动作菜单和已发布审批定义。 */
    public record SaveRequest(
            @Schema(description = "已有绑定主键；新增时为空") Long id,
            @NotNull @Schema(description = "业务模块主键") Long moduleId,
            @NotNull @Schema(description = "已发布审批定义主键") Long examineId,
            @Schema(description = "模块页面审批状态组主键；空表示该审批不占独立状态字段") Long statusGroupId,
            @Schema(description = "手动发起审批的模块动作菜单主键") Long actionMenuId,
            @NotBlank @Size(max = 80) @Schema(description = "CREATE、UPDATE 或自定义业务事件编码") String triggerCode,
            @Schema(description = "按业务字段匹配的审批触发条件") JsonNode condition) {}

    /** 当前租户模块审批绑定及其条件。 */
    public record View(Long id, Long systemId, Long tenantId, Long moduleId, Long examineId, Long statusGroupId,
                       Long actionMenuId, String triggerCode, JsonNode condition, String status,
                       LocalDateTime createTime, LocalDateTime updateTime) {}

    /** 绑定查询可按模块过滤，仍由服务端限制当前系统租户。 */
    public record ListRequest(Long moduleId) {}
}
