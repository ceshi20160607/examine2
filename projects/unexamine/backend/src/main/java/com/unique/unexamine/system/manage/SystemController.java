package com.unique.unexamine.system.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/systems")
public class SystemController {
    private final SystemEntryService entryService;

    public SystemController(SystemEntryService entryService) {
        this.entryService = entryService;
    }

    @org.springframework.web.bind.annotation.GetMapping
    public ApiResult<List<AccessibleSystem>> list() {
        return ApiResult.ok(entryService.listAccessible(AuthenticationContextHolder.require().accountId()));
    }

    @PostMapping("/{systemId}/enter")
    public ApiResult<SystemEntryResult> enter(@PathVariable Long systemId, HttpServletRequest request) {
        return ApiResult.ok(entryService.enter(
                AuthenticationContextHolder.require().accountId(), systemId, TraceIdFilter.current(request)));
    }

    @PostMapping("/{systemId}/tenants/{tenantId}/enter")
    public ApiResult<SystemEntryResult> enterTenant(
            @PathVariable Long systemId,
            @PathVariable Long tenantId,
            HttpServletRequest request) {
        return ApiResult.ok(entryService.enterTenant(
                AuthenticationContextHolder.require().accountId(), systemId, tenantId, TraceIdFilter.current(request)));
    }
}
