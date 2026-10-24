package com.unique.unexamine.sys.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** SystemManage 的请求与返回模型。 */
public final class SystemManageDto {
    private SystemManageDto() {}

    /** 创建系统所需的用户可见资料。 */
    public record CreateSystemRequest(@NotBlank @Size(max = 80) String code,
                                      @NotBlank @Size(max = 100) String name,
                                      @Size(max = 500) String description) {}

    /** 多租户模式配置，false 只暂停非主租户访问。 */
    public record TenantModeRequest(@NotNull Boolean enabled) {}

    /** 创建租户所需的基本资料。 */
    public record CreateTenantRequest(@NotBlank @Size(max = 80) String code,
                                      @NotBlank @Size(max = 100) String name) {}

    /** 系统列表与创建结果。 */
    public record SystemView(Long id, String code, String name, Long rootTenantId,
                             boolean multiTenantEnabled, boolean owner) {}

    /** 可进入的租户展示信息。 */
    public record TenantView(Long id, String code, String name, boolean root) {}

    /** 进入系统后重建的系统、租户和成员上下文。 */
    public record SystemContext(Long systemId, Long tenantId, Long accountId, Long memberId,
                                Long tenantMemberId, boolean owner, boolean tenantAdmin) {}
}
