package com.unique.examine.plat.manage.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 平台系统返回对象。
 */
@Data
@Builder
@Schema(description = "平台系统返回对象")
public class PlatformSystemVO {

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "系统编码")
    private String systemCode;

    @Schema(description = "系统名称")
    private String systemName;

    @Schema(description = "系统描述")
    private String description;

    @Schema(description = "租户模式")
    private String tenantMode;

    @Schema(description = "默认租户 ID")
    private String defaultTenantId;

    @Schema(description = "创建人平台账号 ID")
    private String ownerAccountId;

    @Schema(description = "创建人成员扩展 ID")
    private String ownerMemberId;

    @Schema(description = "系统状态")
    private String status;

    @Schema(description = "初始化对象列表")
    private List<InitializedObjectVO> initializedObjects;
}
