package com.unique.unexamine.runtimedata.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 记录详情中的评论请求与分页结果；父评论只接受同一条业务记录内的主键。 */
public final class RuntimeRecordCommentModels {
    private RuntimeRecordCommentModels() {
    }

    /** 新增评论或回复；正文长度限制在数据库 TEXT 容量以内。 */
    public record CreateRequest(@NotBlank @Size(max = 10000) String body, Long parentCommentId) {
    }

    /** 评论作者使用系统成员身份，避免把客户端提交的作者信息当成可信数据。 */
    public record View(Long id, Long recordId, Long parentCommentId, String body,
                       Long createdByMemberId, String createdByName, LocalDateTime createdTime) {
    }

    /** 详情页按发表顺序逐页读取评论和回复。 */
    public record Page(List<View> comments, long total, int page, int pageSize) {
    }
}
