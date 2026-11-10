package com.unique.unexamine.sys.manage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.bo.SearchBO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 系统租户公告的配置与展示模型。 */
public final class SystemNoticeDto {
    private SystemNoticeDto() {}

    /** 公告分页条件；管理员可按状态筛选，成员列表只读取已发布公告。 */
    public static class NoticeSearch extends SearchBO {
        @Schema(description = "公告状态：DRAFT、PUBLISHED 或 DISABLED")
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /** 管理员保存公告草稿；可见范围由账号、部门和角色 ID 数组组成。 */
    public record SaveRequest(
            @Schema(description = "已有公告主键，新增时为空") Long id,
            @NotBlank @Size(max = 200) @Schema(description = "公告标题") String title,
            @NotBlank @Size(max = 15000) @Schema(description = "公告正文") String content,
            @Schema(description = "accountIds、departmentIds、roleIds 可见范围；空表示本租户全部成员") JsonNode audience,
            @Schema(description = "公告到期时间") LocalDateTime expireTime) {}

    /** 当前租户有权查看的公告或管理员草稿。 */
    public record NoticeView(Long id, Long systemId, Long tenantId, String title, String content,
                             JsonNode audience, String status, LocalDateTime publishTime,
                             LocalDateTime expireTime, LocalDateTime createTime) {}
}
