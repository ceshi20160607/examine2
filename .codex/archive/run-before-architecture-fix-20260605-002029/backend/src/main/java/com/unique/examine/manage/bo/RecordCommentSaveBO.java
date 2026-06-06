package com.unique.examine.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "记录评论保存入参")
public class RecordCommentSaveBO {
    @Schema(description = "系统ID；可不传，后端按记录归属补齐")
    private Long systemId;
    @Schema(description = "租户ID；可不传，后端按记录归属补齐")
    private Long tenantId;
    @NotNull
    @Schema(description = "记录ID")
    private Long recordId;
    @Schema(description = "评论内容")
    private String commentText;
    @Schema(description = "前端兼容字段，等同于 commentText")
    private String comment;
}
