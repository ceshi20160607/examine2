package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.identity.EnterpriseIdentityService;
import com.unique.examine.plat.identity.IdentityApi;
import com.unique.examine.plat.manage.service.PlatformMutationSupport;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform/admin/identity-providers")
public class IdentityProviderController {
    private final EnterpriseIdentityService identity;

    public IdentityProviderController(EnterpriseIdentityService identity) {
        this.identity = identity;
    }

    @GetMapping
    public ApiResponse<List<IdentityApi.ProviderView>> list(
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(identity.list(), request);
    }

    @PostMapping
    public ApiResponse<IdentityApi.ProviderView> create(
            @RequestBody IdentityApi.ProviderCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(identity.create(session, body, ControllerSupport.client(request)), request);
    }

    @PutMapping("/{providerId}")
    public ApiResponse<IdentityApi.ProviderView> update(
            @PathVariable String providerId,
            @RequestBody IdentityApi.ProviderCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(identity.update(session, PlatformMutationSupport.id(providerId), body,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/{providerId}:preflight")
    public ApiResponse<IdentityApi.PreflightView> preflight(
            @PathVariable String providerId,
            @RequestBody VersionCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(identity.preflight(session, PlatformMutationSupport.id(providerId),
                body.expectedVersion(), ControllerSupport.client(request)), request);
    }

    @PostMapping("/{providerId}:publish")
    public ApiResponse<IdentityApi.ProviderView> publish(
            @PathVariable String providerId,
            @RequestBody VersionCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(identity.publish(session, PlatformMutationSupport.id(providerId),
                body.expectedVersion(), ControllerSupport.client(request)), request);
    }

    @PostMapping("/{providerId}:disable")
    public ApiResponse<IdentityApi.ProviderView> disable(
            @PathVariable String providerId,
            @RequestBody VersionCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(identity.disable(session, PlatformMutationSupport.id(providerId),
                body.expectedVersion(), ControllerSupport.client(request)), request);
    }

    public record VersionCommand(long expectedVersion) { }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(value, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}

