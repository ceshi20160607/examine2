package com.unique.unexamine.plat.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 平台配置和站内消息的 HTTP 模型；不暴露平台实体。 */
public final class PlatformManageDto {
    private PlatformManageDto() {}

    /** 平台配置保存请求；配置键保存后保持稳定。 */
    public record ConfigRequest(
            @Schema(description = "已有配置主键") Long id,
            @NotBlank @Size(max = 100) @Schema(description = "配置键") String configKey,
            @NotBlank @Size(max = 20000) @Schema(description = "配置内容") String configValue,
            @NotBlank @Size(max = 20) @Schema(description = "ACTIVE 或 DISABLED") String status) {}

    /** 平台配置对外展示模型。 */
    public record ConfigView(Long id, String configKey, String configValue, String status,
                             Long updateAccountId, LocalDateTime updateTime) {}

    /** 平台配置查询条件。 */
    public static class ConfigSearch extends SearchBO {
        @Schema(description = "配置状态") private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 当前账号收到的站内消息。 */
    public record MessageView(Long id, Long recipientAccountId, String title, String content,
                              String targetType, Long targetId, String status,
                              LocalDateTime readTime, LocalDateTime createTime) {}

    /** 当前平台账号的未读站内消息数量。 */
    public record UnreadSummary(long unreadCount) {}

    /** 当前账号消息查询条件；分页沿用 core 的 SearchBO。 */
    public static class MessageSearch extends SearchBO {
        @Schema(description = "消息状态：UNREAD、READ 或 ARCHIVED")
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
