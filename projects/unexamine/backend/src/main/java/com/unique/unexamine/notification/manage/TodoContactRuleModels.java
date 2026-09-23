package com.unique.unexamine.notification.manage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 今日联系规则只暴露命名配置，不向页面暴露模块快照或 JSON。 */
public final class TodoContactRuleModels {
    private TodoContactRuleModels() { }

    public record RuleView(
            Long id,
            String name,
            String moduleCode,
            String moduleName,
            String contactFieldCode,
            String contactFieldName,
            String contactFieldType,
            Integer leadDays,
            String priority,
            String status,
            Long createdByAccountId,
            LocalDateTime createTime,
            LocalDateTime updateTime,
            Integer version) { }

    public record SaveRuleRequest(
            Integer expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String moduleCode,
            @NotBlank @Size(max = 100) String contactFieldCode,
            @NotNull @Min(0) @Max(365) Integer leadDays,
            @NotBlank @Size(max = 32) String priority) { }

    public record ChangeStatusRequest(@NotNull Integer expectedVersion) { }
}
