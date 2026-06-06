package com.unique.examine.plat.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台管理统一出参。
 */
@Data
@Schema(description = "平台管理统一出参")
public class PlatformManageVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "资源编码")
    private String code;

    @Schema(description = "资源名称")
    private String name;

    @Schema(description = "资源状态")
    private String status;

    @Schema(description = "租户 ID")
    private Long tenantId;

    @Schema(description = "系统 ID")
    private Long systemId;

    @Schema(description = "应用 ID")
    private Long appId;

    @Schema(description = "模块 ID")
    private Long moduleId;

    @Schema(description = "资源类型")
    private String type;

    @Schema(description = "资源路径")
    private String resourcePath;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色 ID 列表")
    private List<Long> roleIds;

    @Schema(description = "权限 ID 列表")
    private List<Long> permissionIds;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
