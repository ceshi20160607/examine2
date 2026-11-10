package com.unique.unexamine.sys.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 系统消息模板配置、发送和投递记录的接口模型。 */
public final class SystemMessageTemplateDto {
    private SystemMessageTemplateDto() {}

    /** 保存草稿；变量名必须与标题和正文中的占位符完全一致。 */
    public record SaveRequest(Long id, @NotBlank String templateCode, @NotBlank String titleTemplate,
            @NotBlank String contentTemplate, @NotNull List<String> variableKeys,
            @NotBlank String channel, String targetType) {}

    /** 管理员按已发布模板发送给当前租户成员。 */
    public record SendRequest(@NotBlank String templateCode, @NotNull Long recipientMemberId,
            @NotNull Map<String, String> variables, Long targetId, String dedupeKey) {}

    /** 模板列表可按状态和关键字筛选。 */
    public static class TemplateSearch extends SearchBO {
        @Schema(description = "DRAFT、PUBLISHED 或 DISABLED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 管理员查看当前租户的模板定义。 */
    public record TemplateView(Long id, String templateCode, String titleTemplate,
            String contentTemplate, List<String> variableKeys, String channel, String targetType,
            String status, LocalDateTime createTime, LocalDateTime updateTime) {}

    /** 一次投递的消息和审计标识。 */
    public record DeliveryView(Long id, Long templateId, Long recipientMemberId,
            String dedupeKey, String channel, String status, Long messageId,
            int attemptCount, String errorText, LocalDateTime createTime) {}
}
