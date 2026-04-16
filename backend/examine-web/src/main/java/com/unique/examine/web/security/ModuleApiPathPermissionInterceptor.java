package com.unique.examine.web.security;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.security.ModuleAuthContextHolder;
import com.unique.examine.web.service.ModuleRuntimeApiPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 进接口前按 {@code un_module_menu.api_pattern + perm_key} 校验菜单/功能权限（依赖 {@link ModuleAuthContextInterceptor} 已填充权限集合）。
 * 未建表或未种子时无绑定行，不做额外拦截；与业务数据/字段权限分离。
 */
@Component
public class ModuleApiPathPermissionInterceptor implements HandlerInterceptor {

    private static final String MODULE_API_PREFIX = "/v1/system/module";
    private static final String AUTH_PREFIX = "/v1/system/auth/";

    @Autowired
    private ModuleRuntimeApiPermissionService moduleRuntimeApiPermissionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith(MODULE_API_PREFIX)) {
            return true;
        }
        if (uri.startsWith(AUTH_PREFIX)) {
            return true;
        }
        long sid = AuthContextHolder.getSystemIdOrDefault();
        long tid = AuthContextHolder.getTenantIdOrDefault();
        Optional<String> required = moduleRuntimeApiPermissionService.resolveRequiredMenuPermKey(uri, sid, tid);
        if (required.isEmpty()) {
            return true;
        }
        if (!ModuleAuthContextHolder.hasModulePerm(required.get())) {
            return writeForbidden(response, "缺少菜单/接口权限: " + required.get());
        }
        return true;
    }

    private static boolean writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"code\":403,\"message\":\"" + escaped + "\"}");
        return false;
    }
}
