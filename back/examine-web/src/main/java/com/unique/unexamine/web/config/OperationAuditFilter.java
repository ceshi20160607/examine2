package com.unique.unexamine.web.config;

import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 保存已登录用户的写操作结果，不记录请求体、口令或文件内容。 */
@Component
public class OperationAuditFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(OperationAuditFilter.class);
    private static final Pattern TENANT_PATH = Pattern.compile("^/api/tenants/(\\d+)(?:/|$)");
    private final IUnPlatLogOptService platformLogs;
    private final IUnSysLogOptService systemLogs;
    private final IUnSysMemberService members;

    public OperationAuditFilter(IUnPlatLogOptService platformLogs, IUnSysLogOptService systemLogs,
                                IUnSysMemberService members) {
        this.platformLogs = platformLogs;
        this.systemLogs = systemLogs;
        this.members = members;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || !("POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method))
                || path.startsWith("/api/platform/session") || path.startsWith("/api/system/session");
    }

    /** 请求完成后记录实际 HTTP 成败；审计插入故障另记服务日志，不覆盖业务响应。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            HttpSession session = request.getSession(false);
            if (session != null) {
                boolean success = !failed && response.getStatus() < 400;
                try {
                    save(request, session, success);
                } catch (RuntimeException exception) {
                    LOG.error("业务操作审计写入失败：{} {}", request.getMethod(), request.getRequestURI(), exception);
                }
            }
        }
    }

    private void save(HttpServletRequest request, HttpSession session, boolean success) {
        String path = request.getRequestURI();
        String action = trim(request.getMethod() + " " + path, 100);
        boolean platformRoute = path.startsWith("/api/platform/")
                || path.startsWith("/api/common/messages/platform");
        boolean systemRoute = path.startsWith("/api/system/") || path.startsWith("/api/tenants/")
                || path.startsWith("/api/common/messages/system");
        // 公共资源和应用可属于平台或系统；没有显式路径时按当前会话身份记录。
        if (platformRoute || !systemRoute && session.getAttribute("platformAccountId") instanceof Long
                && !(session.getAttribute("systemMemberId") instanceof Long)) {
            Object account = session.getAttribute("platformAccountId");
            if (account instanceof Long accountId) {
                UnPlatLogOpt log = new UnPlatLogOpt();
                log.setAccountId(accountId);
                log.setActionCode(action);
                log.setRequestPath(trim(path, 255));
                log.setSuccess(success ? (byte) 1 : (byte) 0);
                log.setIpAddress(trim(request.getRemoteAddr(), 45));
                platformLogs.save(log);
            }
            return;
        }
        Object member = session.getAttribute("systemMemberId");
        if (!(member instanceof Long memberId)) {
            return;
        }
        UnSysMember current = members.getById(memberId);
        if (current == null) {
            return;
        }
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(current.getSystemId());
        log.setTenantId(tenantId(request));
        log.setMemberId(memberId);
        log.setActionCode(action);
        log.setRequestPath(trim(path, 255));
        log.setSuccess(success ? (byte) 1 : (byte) 0);
        log.setIpAddress(trim(request.getRemoteAddr(), 45));
        systemLogs.save(log);
    }

    private Long tenantId(HttpServletRequest request) {
        Matcher matcher = TENANT_PATH.matcher(request.getRequestURI());
        String value = matcher.find() ? matcher.group(1) : request.getParameter("tenantId");
        if (value == null || !value.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String trim(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
