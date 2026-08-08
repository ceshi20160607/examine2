package com.unique.examine.module.dashboard.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.module.dashboard.api.DashboardViews;
import com.unique.examine.module.dashboard.runtime.DashboardRuntimeService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/dashboards")
public class DashboardRuntimeController {
    private final DashboardRuntimeService service;

    public DashboardRuntimeController(DashboardRuntimeService service) {
        this.service = service;
    }

    @GetMapping("/system-home")
    public ApiResponse<DashboardViews.RuntimeDashboard> systemHome(
            @PathVariable long systemId,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.systemHome(
                RuntimeSession.require(value, systemId)), request);
    }

    @GetMapping("/{code}")
    public ApiResponse<DashboardViews.RuntimeDashboard> byCode(
            @PathVariable long systemId,
            @PathVariable String code,
            @RequestAttribute(
                    value = RequestSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.byCode(
                RuntimeSession.require(value, systemId), code), request);
    }

    private static <T> ApiResponse<T> ok(
            T data,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                data,
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.REQUEST_ID)),
                String.valueOf(request.getAttribute(
                        WebRequestAttributes.TRACE_ID)));
    }
}
