package com.unique.examine.plat.manage.tenant;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.tenant.TenantModels.TenantSaveRequest;
import com.unique.examine.plat.manage.tenant.TenantModels.TenantVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant API controller.
 */
@RestController
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/api/v1/systems/{systemId}/tenants")
    public ApiResponse<List<TenantVO>> list(@PathVariable String systemId) {
        return ApiResponse.success(tenantService.list(systemId));
    }

    @PostMapping("/api/v1/systems/{systemId}/tenants")
    public ApiResponse<TenantVO> create(@PathVariable String systemId, @RequestBody TenantSaveRequest request) {
        return ApiResponse.success(tenantService.create(systemId, request));
    }

    @PatchMapping("/api/v1/systems/{systemId}/tenants/{tenantId}")
    public ApiResponse<TenantVO> update(@PathVariable String systemId, @PathVariable String tenantId,
                                        @RequestBody TenantSaveRequest request) {
        return ApiResponse.success(tenantService.update(systemId, tenantId, request));
    }
}
