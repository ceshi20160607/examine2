package com.unique.examine.web.controller;

import com.unique.examine.core.entity.PlatTenant;
import com.unique.examine.core.service.PlatTenantService;
import com.unique.examine.web.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "租户")
@RestController
@RequestMapping("/api/v1/platform/tenants")
public class PlatTenantController {

    private final PlatTenantService platTenantService;

    public PlatTenantController(PlatTenantService platTenantService) {
        this.platTenantService = platTenantService;
    }

    @Operation(summary = "按系统列出租户")
    @GetMapping
    public ApiResult<List<PlatTenant>> list(@RequestParam Long systemId) {
        return ApiResult.ok(platTenantService.listBySystem(systemId));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public ApiResult<PlatTenant> get(@PathVariable Long id) {
        return ApiResult.ok(platTenantService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public ApiResult<PlatTenant> create(@RequestBody Map<String, Object> body) {
        Long systemId = ((Number) body.get("systemId")).longValue();
        String name = (String) body.get("name");
        return ApiResult.ok(platTenantService.create(systemId, name));
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody PlatTenant body) {
        body.setId(id);
        platTenantService.updateTenant(body);
        return ApiResult.ok();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        platTenantService.removeTenant(id);
        return ApiResult.ok();
    }
}
