package com.unique.unexamine.plat.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 平台运维备份只返回元数据，不公开服务端文件路径。 */
public final class PlatformBackupDto {
    private PlatformBackupDto() {}

    public record View(@Schema(description="备份主键") Long id,
                       @Schema(description="平台或系统作用域") String scopeType,
                       @Schema(description="系统备份所属系统") Long systemId,
                       @Schema(description="备份类型") String backupKind,
                       @Schema(description="创建中、可用或失败") String status,
                       @Schema(description="SQL 文件大小") Long sizeBytes,
                       @Schema(description="SQL 文件 SHA-256") String sha256,
                       @Schema(description="失败原因摘要") String errorText,
                       @Schema(description="发起人账号主键") Long createAccountId,
                       @Schema(description="发起时间") LocalDateTime createTime) {}

}
