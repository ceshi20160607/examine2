package com.unique.examine.web.controller;

import com.unique.examine.core.security.ModuleAuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "自建系统态-module权限")
@RestController
@RequestMapping("/v1/system/auth")
public class SystemModuleAuthController {

    @Operation(summary = "当前会话在本系统内的 module 权限键（所有者返回通配符 *；成员经角色-权限解析；与 Redis 缓存一致）")
    @GetMapping("/permissions")
    public ApiResult<Map<String, Object>> modulePermissions() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("permKeys", ModuleAuthContextHolder.getPermKeys().stream().sorted().collect(Collectors.toList()));
        m.put("ownerWildcard", ModuleAuthContextHolder.isOwnerWildcard());
        return ApiResult.ok(m);
    }
}
