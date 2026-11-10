package com.unique.unexamine.sys.manage.dto;

import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 系统消息按当前系统租户和成员身份交互。 */
public final class SystemMessageDto {
    private SystemMessageDto() {}

    /** 当前成员按状态与标题内容筛选消息。 */
    public static class MessageSearch extends SearchBO {
        @Schema(description = "UNREAD、READ 或 ARCHIVED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 消息不包含其他租户的成员资料。 */
    public record MessageView(Long id, Long systemId, Long tenantId, Long recipientMemberId,
            String title, String content, String targetType, Long targetId, String status,
            LocalDateTime readTime, LocalDateTime createTime) {}

    /** 当前成员在当前系统租户的未读消息数量。 */
    public record UnreadSummary(long unreadCount) {}
}
