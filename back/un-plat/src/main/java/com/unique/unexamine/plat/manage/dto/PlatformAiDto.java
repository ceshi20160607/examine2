package com.unique.unexamine.plat.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 平台智能能力配置的 HTTP 契约；密钥只传安全引用。 */
public final class PlatformAiDto {
    private PlatformAiDto() {}

    /** 模型、能力范围、额度与数据策略由配置 JSON 表达。 */
    public record SaveRequest(Long id,
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 80) String providerCode,
            @Schema(description = "环境变量引用 env:变量名，不传密钥明文") String secretRef,
            JsonNode config) {}

    /** 配置状态与编码筛选。 */
    public static class ConfigSearch extends SearchBO {
        @Schema(description = "DRAFT、PUBLISHED 或 DISABLED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 配置展示；不返回密钥值。 */
    public record ConfigView(Long id, String code, String name, String providerCode,
            String secretRef, JsonNode config, String status, Integer publishedVersion,
            LocalDateTime createTime, LocalDateTime updateTime) {}
}
