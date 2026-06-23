package com.unique.examine.module.manage.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 应用配置回显。
 */
@Data
@Builder
@Schema(description = "应用配置回显")
public class AppVO {

    @Schema(description = "应用 ID")
    private String appId;

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "应用名称")
    private String name;

    @Schema(description = "应用编码")
    private String code;

    @Schema(description = "应用图标")
    private String icon;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "应用状态")
    private String status;

    @Schema(description = "模块数量")
    private Integer moduleCount;

    @Schema(description = "当前应用发布版本 ID")
    private String publishedVersion;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
