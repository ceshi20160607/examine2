package com.unique.examine.module.manage.vo;

import java.util.List;

import com.unique.examine.core.permission.EffectivePermissionVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 系统上下文信息。
 */
@Data
@Builder
@Schema(description = "系统上下文信息")
public class SystemContextVO {

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "系统编码")
    private String systemCode;

    @Schema(description = "系统名称")
    private String systemName;

    @Schema(description = "系统状态")
    private String status;

    @Schema(description = "租户模式")
    private String tenantMode;

    @Schema(description = "当前租户")
    private TenantVO currentTenant;

    @Schema(description = "当前成员")
    private MemberVO currentMember;

    @Schema(description = "可访问租户")
    private List<TenantVO> tenants;

    @Schema(description = "有效权限")
    private EffectivePermissionVO permissions;
}
