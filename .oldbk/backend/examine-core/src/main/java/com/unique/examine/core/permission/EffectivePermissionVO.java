package com.unique.examine.core.permission;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 当前请求主体的有效权限。
 */
@Data
@Builder
@Schema(description = "当前请求主体的有效权限")
public class EffectivePermissionVO {

    @Schema(description = "系统 ID")
    private String systemId;

    @Schema(description = "租户 ID")
    private String tenantId;

    @Schema(description = "成员 ID")
    private String memberId;

    @Schema(description = "角色 ID 集合")
    private Set<String> roles;

    @Schema(description = "可见菜单编码集合")
    private Set<String> menus;

    @Schema(description = "允许操作编码集合")
    private Set<String> operations;

    @Schema(description = "允许访问的 OpenAPI scope 编码集合")
    private Set<String> openapiScopes;

    @Schema(description = "字段权限，key 为字段编码")
    private Map<String, FieldPermissionVO> fieldPermissions;

    @Schema(description = "数据范围规则")
    private List<DataScopeRuleVO> dataScopes;

    @Schema(description = "权限版本")
    private String version;

    /**
     * 创建空权限快照。
     *
     * @return 空权限快照
     */
    public static EffectivePermissionVO empty() {
        return EffectivePermissionVO.builder()
                .roles(Set.of())
                .menus(Set.of())
                .operations(Set.of())
                .openapiScopes(Set.of())
                .fieldPermissions(Map.of())
                .dataScopes(List.of())
                .build();
    }
}
