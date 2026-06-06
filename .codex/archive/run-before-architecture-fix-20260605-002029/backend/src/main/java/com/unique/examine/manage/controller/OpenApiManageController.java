package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.OpenApiManageService;
import com.unique.examine.manage.vo.*;
import com.unique.examine.manage.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/openapi")
public class OpenApiManageController {
    private final OpenApiManageService openApiManageService;

    @GetMapping("/clients")
    public ApiResponse<PageResult<SimpleVO>> clients(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId) { return ApiResponse.ok(openApiManageService.clients(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId))); }
    @PostMapping("/clients")
    public ApiResponse<SimpleVO> saveClient(@Valid @RequestBody OpenApiClientSaveBO bo) { return ApiResponse.ok(openApiManageService.saveClient(bo)); }
    @PostMapping("/credentials")
    public ApiResponse<CredentialVO> createCredential(@Valid @RequestBody OpenApiCredentialCreateBO bo) { return ApiResponse.ok(openApiManageService.createCredential(bo)); }
    @PostMapping("/scopes")
    public ApiResponse<SimpleVO> saveScope(@Valid @RequestBody OpenApiScopeSaveBO bo) { return ApiResponse.ok(openApiManageService.saveScope(bo)); }
    @PostMapping("/ip-whitelist")
    public ApiResponse<SimpleVO> saveIp(@Valid @RequestBody OpenApiIpSaveBO bo) { return ApiResponse.ok(openApiManageService.saveIp(bo)); }

    private Long resolveSystemId(Long systemId) {
        Long resolved = systemId == null ? SecurityContext.currentUser().getSystemId() : systemId;
        if (resolved == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少系统上下文"); }
        return resolved;
    }

    private Long resolveTenantId(Long tenantId) {
        Long resolved = tenantId == null ? SecurityContext.currentUser().getTenantId() : tenantId;
        if (resolved == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少租户上下文"); }
        return resolved;
    }
}
