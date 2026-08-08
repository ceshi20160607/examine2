package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.service.SessionService;
import com.unique.examine.plat.manage.vo.AuthResultVo;
import com.unique.examine.plat.manage.vo.SystemSummaryVo;
import com.unique.examine.plat.manage.vo.TenantSummaryVo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/context")
public class ContextController {
    private final SessionService sessionService;
    private final SessionCookieSupport cookieSupport;

    public ContextController(SessionService sessionService, SessionCookieSupport cookieSupport) {
        this.sessionService = sessionService;
        this.cookieSupport = cookieSupport;
    }

    @GetMapping("/systems")
    public ApiResponse<List<SystemSummaryVo>> systems(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.require(sessionValue);
        return ApiResponse.success(
                sessionService.listSystems(session.accountId()),
                ControllerSupport.requestId(request),
                ControllerSupport.traceId(request)
        );
    }

    @GetMapping("/tenants")
    public ApiResponse<List<TenantSummaryVo>> tenants(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.require(sessionValue);
        return ApiResponse.success(
                sessionService.listTenants(session),
                ControllerSupport.requestId(request),
                ControllerSupport.traceId(request)
        );
    }

    @PostMapping("/platform:switch")
    public ApiResponse<AuthResultVo> platform(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = SessionGuard.require(sessionValue);
        var client = ControllerSupport.client(request);
        var issued = sessionService.switchPlatform(session, client);
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.result(), client.requestId(), client.traceId());
    }

    @PostMapping("/systems/{systemId}:switch")
    public ApiResponse<AuthResultVo> system(
            @PathVariable long systemId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = SessionGuard.require(sessionValue);
        var client = ControllerSupport.client(request);
        var issued = sessionService.switchSystem(session, systemId, client);
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.result(), client.requestId(), client.traceId());
    }

    @PostMapping("/tenants/{tenantId}:switch")
    public ApiResponse<AuthResultVo> tenant(
            @PathVariable long tenantId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var session = SessionGuard.require(sessionValue);
        var client = ControllerSupport.client(request);
        var issued = sessionService.switchTenant(session, tenantId, client);
        cookieSupport.write(response, issued);
        return ApiResponse.success(issued.result(), client.requestId(), client.traceId());
    }
}
