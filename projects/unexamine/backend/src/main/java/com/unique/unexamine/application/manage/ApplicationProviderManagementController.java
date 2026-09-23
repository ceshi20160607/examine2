package com.unique.unexamine.application.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 在现有应用管理边界内维护外部平台连接，不另设接口开放中心。 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationProviderManagementController {
    private final ApplicationProviderService service;

    public ApplicationProviderManagementController(ApplicationProviderService service) {
        this.service = service;
    }

    @GetMapping("/provider-capabilities")
    public ApiResult<List<ApplicationProviderModels.CapabilityView>> capabilities() {
        return ApiResult.ok(service.capabilities(AuthenticationContextHolder.require()));
    }

    @GetMapping("/{applicationId}/provider-connections")
    public ApiResult<List<ApplicationProviderModels.ConnectionView>> connections(@PathVariable Long applicationId) {
        return ApiResult.ok(service.connections(AuthenticationContextHolder.require(), applicationId));
    }

    @PostMapping("/{applicationId}/provider-connections")
    public ApiResult<ApplicationProviderModels.ConnectionView> save(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationProviderModels.SaveConnectionRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.save(AuthenticationContextHolder.require(), applicationId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/provider-connections/{connectionId}/diagnose")
    public ApiResult<ApplicationProviderModels.DiagnosticResult> diagnose(
            @PathVariable Long applicationId,
            @PathVariable Long connectionId,
            @Valid @RequestBody ApplicationProviderModels.DiagnosticRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.diagnose(AuthenticationContextHolder.require(), applicationId, connectionId,
                body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/provider-connections/{connectionId}/disable")
    public ApiResult<ApplicationProviderModels.ConnectionView> disable(
            @PathVariable Long applicationId, @PathVariable Long connectionId, HttpServletRequest request) {
        return ApiResult.ok(service.disable(AuthenticationContextHolder.require(), applicationId, connectionId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{applicationId}/provider-connections/{connectionId}/events")
    public ApiResult<List<ApplicationProviderModels.EventView>> events(
            @PathVariable Long applicationId, @PathVariable Long connectionId) {
        return ApiResult.ok(service.events(AuthenticationContextHolder.require(), applicationId, connectionId));
    }

    @GetMapping("/{applicationId}/provider-connections/{connectionId}/identity-options")
    public ApiResult<List<ApplicationProviderModels.IdentityOptionView>> identityOptions(
            @PathVariable Long applicationId, @PathVariable Long connectionId) {
        return ApiResult.ok(service.identityOptions(
                AuthenticationContextHolder.require(), applicationId, connectionId));
    }

    @GetMapping("/{applicationId}/provider-connections/{connectionId}/identities")
    public ApiResult<List<ApplicationProviderModels.IdentityView>> identities(
            @PathVariable Long applicationId, @PathVariable Long connectionId) {
        return ApiResult.ok(service.identities(
                AuthenticationContextHolder.require(), applicationId, connectionId));
    }

    @PostMapping("/{applicationId}/provider-connections/{connectionId}/identities")
    public ApiResult<ApplicationProviderModels.IdentityView> saveIdentity(
            @PathVariable Long applicationId,
            @PathVariable Long connectionId,
            @Valid @RequestBody ApplicationProviderModels.SaveIdentityRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveIdentity(AuthenticationContextHolder.require(), applicationId, connectionId,
                body, TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/provider-connections/{connectionId}/identities/{identityId}/disable")
    public ApiResult<ApplicationProviderModels.IdentityView> disableIdentity(
            @PathVariable Long applicationId,
            @PathVariable Long connectionId,
            @PathVariable Long identityId,
            HttpServletRequest request) {
        return ApiResult.ok(service.disableIdentity(AuthenticationContextHolder.require(), applicationId,
                connectionId, identityId, TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/provider-connections/{connectionId}/messages")
    public ApiResult<ApplicationProviderModels.DeliveryView> sendMessage(
            @PathVariable Long applicationId,
            @PathVariable Long connectionId,
            @Valid @RequestBody ApplicationProviderModels.SendMessageRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.sendMessage(AuthenticationContextHolder.require(), applicationId,
                connectionId, body, TraceIdFilter.current(request)));
    }

    @GetMapping("/{applicationId}/provider-connections/{connectionId}/deliveries")
    public ApiResult<List<ApplicationProviderModels.DeliveryView>> deliveries(
            @PathVariable Long applicationId, @PathVariable Long connectionId) {
        return ApiResult.ok(service.deliveries(
                AuthenticationContextHolder.require(), applicationId, connectionId));
    }
}
