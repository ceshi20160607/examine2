package com.unique.unexamine.sys.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.CreateSystemRequest;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.CreateTenantRequest;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemView;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.TenantModeRequest;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.TenantView;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 系统和租户入口，参数转交系统业务 Service。 */
@RestController
@RequestMapping("/api/systems")
@Tag(name = "业务系统与租户")
public class SystemController {
    private final SystemManageService service;

    /** 注入系统业务服务。 */
    public SystemController(SystemManageService service) {
        this.service = service;
    }

    /** 为当前平台账号创建系统及主租户。 */
    @PostMapping
    @Operation(summary = "创建系统及主租户")
    public Result<SystemView> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody CreateSystemRequest request) {
        return Result.ok(service.create(token, request));
    }

    /** 列出当前账号拥有系统成员身份的系统。 */
    @GetMapping
    @Operation(summary = "我的系统")
    public Result<List<SystemView>> list(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.list(token));
    }

    /** 建立当前系统与租户上下文。 */
    @GetMapping("/{systemId}/context")
    @Operation(summary = "进入系统租户")
    public Result<SystemContext> context(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long systemId, @RequestParam(required = false) Long tenantId) {
        return Result.ok(service.context(token, systemId, tenantId));
    }

    /** 根据租户模式列出当前成员可进入的租户。 */
    @GetMapping("/{systemId}/tenants")
    @Operation(summary = "可进入的租户")
    public Result<List<TenantView>> tenants(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long systemId) {
        return Result.ok(service.tenants(token, systemId));
    }

    /** 系统管理员直接开启或关闭多租户。 */
    @PutMapping("/{systemId}/tenant-mode")
    @Operation(summary = "设置多租户模式")
    public Result<SystemView> tenantMode(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long systemId, @Valid @RequestBody TenantModeRequest request) {
        return Result.ok(service.setTenantMode(token, systemId, request));
    }

    /** 在已开启的多租户模式内创建租户。 */
    @PostMapping("/{systemId}/tenants")
    @Operation(summary = "创建系统租户")
    public Result<TenantView> createTenant(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long systemId, @Valid @RequestBody CreateTenantRequest request) {
        return Result.ok(service.createTenant(token, systemId, request));
    }
}
