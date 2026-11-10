package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** 单条业务记录的团队授权请求和回显。 */
public final class ModuleTeamDto {
    private ModuleTeamDto() {}

    /** 加入或调整成员时显式选择查看或编辑。 */
    public record SaveRequest(
            @NotNull @Schema(description = "当前系统租户的有效账号主键") Long accountId,
            @NotBlank @Schema(description = "团队权限：VIEW 查看，EDIT 编辑并查看") String accessLevel) {}

    /** 团队关系回显不包含系统角色权限。 */
    public record View(
            @Schema(description = "团队关系主键") Long id,
            @Schema(description = "业务记录主键") Long recordId,
            @Schema(description = "团队成员账号主键") Long accountId,
            @Schema(description = "VIEW 或 EDIT") String accessLevel,
            @Schema(description = "关系状态") String status,
            @Schema(description = "最近修改时间") LocalDateTime updateTime) {}
}
