package com.unique.unexamine.runtimedata.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 模块范围共享使用结构化条件，不开放脚本或任意来源租户参数。 */
public final class TenantRangeShareModels {
    private TenantRangeShareModels() { }

    /** id/version 仅用于修改当前来源已有授权；开始和截止时间按北京时间解释。 */
    public record Grant(Long id, @NotBlank @Size(max = 200) String name,
                        @NotNull Long targetTenantId, List<String> allowedActions,
                        @NotNull JsonNode condition, LocalDateTime startTime,
                        LocalDateTime expireTime, Integer version) { }

    /** 条件目录仅含已发布且来源操作者可读取的非敏感字段与安全选项。 */
    public record ConditionField(String code, String name, String fieldType, String configJson) { }

    /** 来源管理视图；目标列表不返回条件和授予者的数据权限快照。 */
    public record View(Long id, String name, Long sourceTenantId, String sourceTenantName,
                       Long targetTenantId, String targetTenantName, String moduleCode,
                       List<String> allowedActions, JsonNode condition, String status,
                       LocalDateTime startTime, LocalDateTime expireTime, LocalDateTime grantTime,
                       LocalDateTime revokeTime, Long grantedByMemberId, Integer version) { }
}
