package com.unique.examine.module.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.api.ConfigRecoveryViews;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigRecoveryService;
import com.unique.examine.module.manage.service.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/config-recovery")
public class ConfigRecoveryController {
    private final ConfigRecoveryService service;

    public ConfigRecoveryController(ConfigRecoveryService service) {
        this.service = service;
    }

    @PostMapping("/module-config/versions/{versionId}/{versionNumber}:restore")
    public ApiResponse<ConfigRecoveryViews.RestoreResult> restoreModuleConfig(
            @PathVariable long systemId,
            @PathVariable long versionId,
            @PathVariable int versionNumber,
            @Valid @RequestBody ConfigRequests.RestoreConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.restoreModuleConfig(session(value, systemId),
                versionId, versionNumber, body, key, context(request)),
                request);
    }

    @PostMapping("/data-sources/{resourceId}/versions/{versionNumber}:restore")
    public ApiResponse<ConfigRecoveryViews.RestoreResult> restoreDataSource(
            @PathVariable long systemId,
            @PathVariable long resourceId,
            @PathVariable int versionNumber,
            @Valid @RequestBody ConfigRequests.RestoreConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.restoreDataSource(session(value, systemId),
                resourceId, versionNumber, body, key, context(request)),
                request);
    }

    @PostMapping("/dashboards/{resourceId}/versions/{versionNumber}:restore")
    public ApiResponse<ConfigRecoveryViews.RestoreResult> restoreDashboard(
            @PathVariable long systemId,
            @PathVariable long resourceId,
            @PathVariable int versionNumber,
            @Valid @RequestBody ConfigRequests.RestoreConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.restoreDashboard(session(value, systemId),
                resourceId, versionNumber, body, key, context(request)),
                request);
    }

    @PostMapping("/kpis/{resourceId}/versions/{versionNumber}:restore")
    public ApiResponse<ConfigRecoveryViews.RestoreResult> restoreKpi(
            @PathVariable long systemId,
            @PathVariable long resourceId,
            @PathVariable int versionNumber,
            @Valid @RequestBody ConfigRequests.RestoreConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.restoreKpi(session(value, systemId), resourceId,
                versionNumber, body, key, context(request)), request);
    }

    @PostMapping("/reports/{resourceId}/versions/{versionNumber}:restore")
    public ApiResponse<ConfigRecoveryViews.RestoreResult> restoreReport(
            @PathVariable long systemId,
            @PathVariable long resourceId,
            @PathVariable int versionNumber,
            @Valid @RequestBody ConfigRequests.RestoreConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE,
                    required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.restoreReport(session(value, systemId), resourceId,
                versionNumber, body, key, context(request)), request);
    }

    private static ConfigSession session(Object value, long systemId) {
        return ConfigSession.require(value, systemId);
    }

    private static RequestContext context(HttpServletRequest request) {
        return new RequestContext(attribute(request,
                WebRequestAttributes.REQUEST_ID), attribute(request,
                WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(
            HttpServletRequest request,
            String name
    ) {
        return String.valueOf(request.getAttribute(name));
    }

    private static <T> ApiResponse<T> ok(
            T data,
            HttpServletRequest request
    ) {
        return ApiResponse.success(data,
                attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }
}
