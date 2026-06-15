package com.unique.examine.web.security;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.po.ModuleMember;
import com.unique.examine.module.service.IModuleMemberService;
import com.unique.examine.plat.entity.po.PlatSystem;
import com.unique.examine.plat.service.IPlatSystemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 自建系统态接口：须已「进入系统」；多租户须已选租户；非所有者须存在 {@code un_module_member} 有效行。
 */
@Component
public class SystemContextInterceptor implements HandlerInterceptor {

    @Autowired
    private IPlatSystemService platSystemService;
    @Autowired
    private IModuleMemberService moduleMemberService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/v1/system/")) {
            return true;
        }
        long sid = AuthContextHolder.getSystemIdOrDefault();
        if (sid == 0L) {
            return writeForbidden(response, "请先进入自建系统");
        }
        PlatSystem sys = platSystemService.getById(sid);
        if (sys == null || sys.getStatus() == null || sys.getStatus() != 1) {
            return writeForbidden(response, "系统不存在或已停用");
        }
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            return writeForbidden(response, "未登录");
        }
        long tid = AuthContextHolder.getTenantIdOrDefault();
        if (sys.getMultiTenantEnabled() != null && sys.getMultiTenantEnabled() == 1) {
            if (tid == 0L) {
                return writeForbidden(response, "请先选择租户");
            }
        }

        if (platId.equals(sys.getOwnerPlatAccountId())) {
            return true;
        }
        Long cnt = moduleMemberService.lambdaQuery()
                .eq(ModuleMember::getSystemId, sid)
                .eq(ModuleMember::getTenantId, tid)
                .eq(ModuleMember::getPlatId, platId)
                .eq(ModuleMember::getStatus, 1)
                .count();
        if (cnt == null || cnt == 0L) {
            return writeForbidden(response, "无权限访问该自建系统");
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
