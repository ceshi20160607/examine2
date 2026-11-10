package com.unique.unexamine.plat.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 平台公告的保存、筛选和展示模型。 */
public final class PlatformNoticeDto {
    private PlatformNoticeDto() {}

    /** 管理员保存公告草稿。 */
    public record SaveRequest(Long id, @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 15000) String content,
            @Schema(description = "accountIds、departmentIds、roleIds；空表示全部有效平台账号") JsonNode audience,
            LocalDateTime expireTime) {}

    /** 公告分页条件。 */
    public static class NoticeSearch extends SearchBO {
        @Schema(description = "管理端筛选状态：DRAFT、PUBLISHED、DISABLED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 公告展示数据。 */
    public record NoticeView(Long id, String title, String content, JsonNode audience,
            String status, LocalDateTime publishTime, LocalDateTime expireTime, LocalDateTime createTime) {}
}
