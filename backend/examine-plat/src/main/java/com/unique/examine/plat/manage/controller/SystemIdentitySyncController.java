package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.identity.SystemIdentitySyncApi;
import com.unique.examine.plat.identity.SystemIdentitySyncService;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/identity-sync")
public class SystemIdentitySyncController {
    private static final String PERMISSION = "system.organization.manage";
    private final SystemIdentitySyncService identity;

    public SystemIdentitySyncController(SystemIdentitySyncService identity) {
        this.identity = identity;
    }

    @GetMapping("/providers")
    public ApiResponse<List<SystemIdentitySyncApi.InheritedProvider>> providers(
            @PathVariable long systemId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.providers(systemId), request);
    }

    @GetMapping("/policies")
    public ApiResponse<List<SystemIdentitySyncApi.PolicyView>> policies(
            @PathVariable long systemId,
            @RequestParam(required = false) Long tenantId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.policies(systemId, tenantId), request);
    }

    @PostMapping("/policies")
    public ApiResponse<SystemIdentitySyncApi.PolicyView> savePolicy(
            @PathVariable long systemId,
            @RequestBody SystemIdentitySyncApi.PolicyCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.savePolicy(session, systemId, body, ControllerSupport.client(request)), request);
    }

    @PostMapping("/policies/{policyId}/snapshots:preflight")
    public ApiResponse<SystemIdentitySyncApi.SnapshotView> preflight(
            @PathVariable long systemId, @PathVariable long policyId,
            @RequestBody SystemIdentitySyncApi.PreflightCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.preflight(session, systemId, policyId, body, ControllerSupport.client(request)), request);
    }

    @GetMapping("/policies/{policyId}/snapshots")
    public ApiResponse<List<SystemIdentitySyncApi.SnapshotView>> snapshots(
            @PathVariable long systemId, @PathVariable long policyId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.snapshots(systemId, policyId), request);
    }

    @PostMapping("/policies/{policyId}/snapshots/{snapshotId}:confirm")
    public ApiResponse<SystemIdentitySyncApi.SnapshotView> confirm(
            @PathVariable long systemId, @PathVariable long policyId, @PathVariable long snapshotId,
            @RequestBody SystemIdentitySyncApi.ConfirmCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.confirm(session, systemId, policyId, snapshotId, body,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/policies/{policyId}/snapshots/{snapshotId}:start")
    public ApiResponse<SystemIdentitySyncApi.TaskView> start(
            @PathVariable long systemId, @PathVariable long policyId, @PathVariable long snapshotId,
            @RequestBody SystemIdentitySyncApi.StartCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.start(session, systemId, policyId, snapshotId, body,
                ControllerSupport.client(request)), request);
    }

    @GetMapping("/policies/{policyId}/tasks")
    public ApiResponse<List<SystemIdentitySyncApi.TaskView>> tasks(
            @PathVariable long systemId, @PathVariable long policyId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        SessionGuard.requireSystem(sessionValue, systemId, PERMISSION);
        return success(identity.tasks(systemId, policyId), request);
    }

    private static <T> ApiResponse<T> success(T value, HttpServletRequest request) {
        return ApiResponse.success(value, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
