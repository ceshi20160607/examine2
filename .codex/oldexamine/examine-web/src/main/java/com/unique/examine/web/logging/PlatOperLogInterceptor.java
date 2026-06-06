package com.unique.examine.web.logging;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.plat.entity.po.PlatOperLog;
import com.unique.examine.plat.service.IPlatOperLogService;
import com.unique.examine.web.security.RequestContextFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Component
public class PlatOperLogInterceptor implements HandlerInterceptor {

    @Autowired
    private IPlatOperLogService operLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("_oper_log_start", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            return;
        }
        if (!(handler instanceof HandlerMethod)) {
            return;
        }

        String uri = request.getRequestURI();
        String moduleCode = resolveModuleCode(uri);
        String actionCode = request.getMethod() + " " + uri;

        PlatOperLog log = new PlatOperLog();
        log.setPlatAccountId(platId);
        log.setOperTime(LocalDateTime.now());
        log.setModuleCode(moduleCode);
        log.setActionCode(actionCode);
        log.setIp(resolveClientIp(request));
        Object rid = request.getAttribute(RequestContextFilter.ATTR_REQUEST_ID);
        log.setRequestId(rid == null ? null : String.valueOf(rid));

        log.setCreateUserId(platId);
        log.setUpdateUserId(platId);

        operLogService.save(log);
    }

    private static String resolveModuleCode(String uri) {
        // e.g. /v1/platform/auth/login -> platform
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("v1".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }
}

