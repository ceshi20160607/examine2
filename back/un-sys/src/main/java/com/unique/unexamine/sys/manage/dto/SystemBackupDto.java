package com.unique.unexamine.sys.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 系统 SQL 备份的后台视图，不暴露服务器文件路径。 */
public final class SystemBackupDto {
    private SystemBackupDto() {}
    public record View(@Schema(description="备份主键") Long id,
                       @Schema(description="创建中、可用或失败") String status,
                       @Schema(description="SQL 文件字节数") Long sizeBytes,
                       @Schema(description="SQL 文件 SHA-256 摘要") String sha256,
                       @Schema(description="失败原因摘要") String errorText,
                       @Schema(description="创建时间") LocalDateTime createTime) {}
}
