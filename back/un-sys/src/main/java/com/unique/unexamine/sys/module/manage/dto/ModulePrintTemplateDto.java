package com.unique.unexamine.sys.module.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 模块打印模板的版本化配置接口。 */
public final class ModulePrintTemplateDto {
    private ModulePrintTemplateDto() {}

    /** 管理员只修改草稿版本，已发布版本保持历史快照。 */
    public record SaveRequest(Long id,
            @NotBlank @Size(max = 80) String templateCode,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Schema(description = "纸张尺寸、方向和边距") JsonNode paper,
            @NotNull @Schema(description = "明确列出打印字段 fieldKeys") JsonNode body) {}

    /** 模板编码与状态筛选。 */
    public static class TemplateSearch extends SearchBO {
        @Schema(description = "DRAFT、PUBLISHED 或 DISABLED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 模板历史版本。 */
    public record TemplateView(Long id, Long moduleId, Long tenantId, String templateCode,
            String name, Integer versionNo, String status, JsonNode paper, JsonNode body,
            LocalDateTime publishTime, LocalDateTime createTime) {}
}
