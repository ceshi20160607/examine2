package com.unique.examine.web.security;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.ModuleAuthContextHolder;
import com.unique.examine.web.service.ModuleAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 解析当前会话在本系统内的 module 权限键并写入 {@link ModuleAuthContextHolder}；
 * 路径级菜单/接口权限见 {@link ModuleApiPathPermissionInterceptor}（按 {@code un_module_menu} 上 api_pattern）。
 */
@Component
public class ModuleAuthContextInterceptor implements HandlerInterceptor {

    @Autowired
    private ModuleAuthService moduleAuthService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Long platId = AuthContextHolder.getPlatId();
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (platId == null || systemId == 0L) {
            ModuleAuthContextHolder.setPermKeys(Set.of());
            return true;
        }
        Set<String> keys = moduleAuthService.resolveAndCache(systemId, tenantId, platId);
        ModuleAuthContextHolder.setPermKeys(keys);
        return true;
    }
}
