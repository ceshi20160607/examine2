package com.unique.unexamine.plat.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 平台消息模板配置、站内投递和审计展示模型。 */
public final class PlatformMessageTemplateDto {
    private PlatformMessageTemplateDto() {}

    /** 平台管理员保存模板草稿；变量与占位符须一致。 */
    public record SaveRequest(Long id, @NotBlank String templateCode, @NotBlank String titleTemplate,
            @NotBlank String contentTemplate, @NotNull List<String> variableKeys,
            @NotBlank String channel, String targetType) {}

    /** 按已发布模板向有效平台账号发送。 */
    public record SendRequest(@NotBlank String templateCode, @NotNull Long recipientAccountId,
            @NotNull Map<String, String> variables, Long targetId, String dedupeKey) {}

    /** 管理列表按模板状态筛选。 */
    public static class TemplateSearch extends SearchBO {
        @Schema(description = "DRAFT、PUBLISHED 或 DISABLED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 平台模板配置不包含任何凭据。 */
    public record TemplateView(Long id, String templateCode, String titleTemplate,
            String contentTemplate, List<String> variableKeys, String channel, String targetType,
            String status, LocalDateTime createTime, LocalDateTime updateTime) {}

    /** 一次站内投递的结果记录。 */
    public record DeliveryView(Long id, Long templateId, Long recipientAccountId,
            String dedupeKey, String channel, String status, Long messageId,
            int attemptCount, String errorText, LocalDateTime createTime) {}
}
