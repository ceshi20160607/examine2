package com.unique.unexamine.system.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
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

@RestController
@RequestMapping("/api/systems")
public class SystemController {
    private final SystemEntryService entryService;
    private final AccessRequestService accessRequestService;
    private final PlatformSystemCreationService creationService;

    public SystemController(
            SystemEntryService entryService,
            AccessRequestService accessRequestService,
            PlatformSystemCreationService creationService) {
        this.entryService = entryService;
        this.accessRequestService = accessRequestService;
        this.creationService = creationService;
    }

    @GetMapping
    public ApiResult<List<AccessibleSystem>> list() {
        return ApiResult.ok(entryService.listAccessible(AuthenticationContextHolder.require().accountId()));
    }

    @GetMapping("/directory")
    public ApiResult<List<SystemDirectoryItem>> directory() {
        return ApiResult.ok(accessRequestService.directory(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<AccessibleSystem> create(
            @Valid @RequestBody CreateSystemRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(creationService.create(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/access-requests/mine")
    public ApiResult<List<AccessRequestView>> myAccessRequests() {
        return ApiResult.ok(accessRequestService.mine(AuthenticationContextHolder.require()));
    }

    @PostMapping("/{systemId}/access-requests")
    public ApiResult<AccessRequestView> submitAccessRequest(
            @PathVariable Long systemId,
            @Valid @RequestBody SubmitAccessRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(accessRequestService.submit(AuthenticationContextHolder.require(), systemId, input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{systemId}/enter")
    public ApiResult<SystemEntryResult> enter(
            @PathVariable Long systemId,
            @RequestBody(required = false) SwitchContextRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(entryService.enter(AuthenticationContextHolder.require().accountId(), systemId,
                input == null ? null : input.previousSystemId(), input == null ? null : input.previousTenantId(),
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{systemId}/tenants/{tenantId}/enter")
    public ApiResult<SystemEntryResult> enterTenant(
            @PathVariable Long systemId,
            @PathVariable Long tenantId,
            @RequestBody(required = false) SwitchContextRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(entryService.enterTenant(AuthenticationContextHolder.require().accountId(), systemId,
                tenantId, input == null ? null : input.previousSystemId(),
                input == null ? null : input.previousTenantId(), TraceIdFilter.current(request)));
    }
}
