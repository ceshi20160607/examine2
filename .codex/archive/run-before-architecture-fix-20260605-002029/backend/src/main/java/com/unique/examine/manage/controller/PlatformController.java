package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.service.PlatformManageService;
import com.unique.examine.manage.vo.*;
import com.unique.examine.manage.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final PlatformManageService platformManageService;

    @GetMapping("/tenants")
    public ApiResponse<PageResult<SimpleVO>> tenants(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String keyword) { return ApiResponse.ok(platformManageService.listTenants(pageNo, pageSize, keyword)); }

    @PostMapping("/tenants")
    public ApiResponse<SimpleVO> createTenant(@Valid @RequestBody TenantSaveBO bo) { return ApiResponse.ok(platformManageService.createTenant(bo)); }

    @GetMapping("/systems")
    public ApiResponse<PageResult<SimpleVO>> systems(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) String keyword) { return ApiResponse.ok(platformManageService.listSystems(pageNo, pageSize, tenantId, keyword)); }

    @PostMapping("/systems")
    public ApiResponse<SimpleVO> createSystem(@Valid @RequestBody SystemSaveBO bo) { return ApiResponse.ok(platformManageService.createSystem(bo)); }

    @PatchMapping("/systems/{systemId}/status")
    public ApiResponse<SimpleVO> updateSystemStatus(@PathVariable Long systemId, @Valid @RequestBody StatusUpdateBO bo) { return ApiResponse.ok(platformManageService.updateSystemStatus(systemId, bo)); }

    @PostMapping("/context/enter-system")
    public ApiResponse<AuthTokenVO> enterSystem(@Valid @RequestBody ContextEnterBO bo) { return ApiResponse.ok(platformManageService.enterSystem(bo)); }
}
