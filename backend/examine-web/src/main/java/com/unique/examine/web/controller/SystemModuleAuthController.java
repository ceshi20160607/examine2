package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.ModuleAuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.manage.ModuleRuntimeApiPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "自建系统态-module权限")
@RestController
@RequestMapping("/v1/system/auth")
public class SystemModuleAuthController {

    @Autowired
    private ModuleRuntimeApiPermissionService moduleRuntimeApiPermissionService;

    @Operation(summary = "当前会话在本系统内的 module 权限键（所有者返回通配符 *；成员经角色-权限解析；与 Redis 缓存一致）")
    @GetMapping("/permissions")
    public ApiResult<Map<String, Object>> modulePermissions() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("permKeys", ModuleAuthContextHolder.getPermKeys().stream().sorted().collect(Collectors.toList()));
        m.put("ownerWildcard", ModuleAuthContextHolder.isOwnerWildcard());
        return ApiResult.ok(m);
    }

    @Operation(summary = "预览：指定 URI 所需 permKey 及当前会话是否允许（用于 RBAC 调试）")
    @GetMapping("/perm-preview")
    public ApiResult<Map<String, Object>> permPreview(@RequestParam("uri") String uri) {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        String requestUri = uri == null ? "" : uri.trim();

        Optional<String> required = moduleRuntimeApiPermissionService.resolveRequiredMenuPermKey(requestUri, systemId, tenantId);
        String requiredKey = required.orElse(null);
        boolean allowed;
        if (requiredKey == null || requiredKey.isBlank()) {
            allowed = true; // 无 api_pattern 命中则放行（与拦截器一致）
        } else {
            allowed = ModuleAuthContextHolder.isOwnerWildcard() || ModuleAuthContextHolder.getPermKeys().contains(requiredKey);
        }
        return ApiResult.ok(Map.of(
                "uri", requestUri,
                "requiredPermKey", requiredKey,
                "ownerWildcard", ModuleAuthContextHolder.isOwnerWildcard(),
                "allowed", allowed
        ));
    }
}
