package com.unique.unexamine.sys.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 系统租户对已获平台授权的智能能力配置。 */
public final class SystemAiDto {
    private SystemAiDto() {}

    /** 策略明确限定模块、字段、动作、外发和人工确认。 */
    public record SaveRequest(Long id,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Long platformConfigId,
            @NotNull @Schema(description = "系统 Agent 策略范围") JsonNode policy) {}

    /** 管理端分页条件。 */
    public static class ConfigSearch extends SearchBO {
        @Schema(description = "DRAFT、PUBLISHED 或 DISABLED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 系统 Agent 配置展示模型。 */
    public record ConfigView(Long id, Long systemId, Long tenantId, String code, String name,
            Long platformConfigId, JsonNode policy, String status, Integer publishedVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {}
}
