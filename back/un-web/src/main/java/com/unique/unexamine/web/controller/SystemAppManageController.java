package com.unique.unexamine.web.controller;

import com.unique.unexamine.app.manage.dto.AppManageDto.AppView;
import com.unique.unexamine.app.manage.dto.AppManageDto.EventRequest;
import com.unique.unexamine.app.manage.dto.AppManageDto.EventView;
import com.unique.unexamine.app.manage.dto.AppManageDto.SaveRequest;
import com.unique.unexamine.app.manage.service.AppManageService;
import com.unique.unexamine.core.common.Result;
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
import org.springframework.web.bind.annotation.RestController;

/** 系统租户应用配置入口；系统权限由当前租户管理员上下文校验。 */
@RestController
@RequestMapping("/api/systems/{systemId}/apps")
@Tag(name = "系统应用配置")
public class SystemAppManageController {
    private final AppManageService service;

    /** 注入应用管理服务。 */
    public SystemAppManageController(AppManageService service) {
        this.service = service;
    }

    /** 列出当前系统租户应用。 */
    @GetMapping
    @Operation(summary = "查询系统应用列表")
    public Result<List<AppView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.listSystem(token, systemId, tenantId));
    }

    /** 查询系统租户应用。 */
    @GetMapping("/{appId}")
    @Operation(summary = "查询系统应用")
    public Result<AppView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long appId) {
        return Result.ok(service.querySystem(token, systemId, tenantId, appId));
    }

    /** 保存系统租户应用草稿。 */
    @PutMapping
    @Operation(summary = "保存系统应用")
    public Result<AppView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.saveSystem(token, systemId, tenantId, request));
    }

    /** 发布系统租户应用授权快照。 */
    @PostMapping("/{appId}/publish")
    @Operation(summary = "发布系统应用")
    public Result<AppView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long appId) {
        return Result.ok(service.publishSystem(token, systemId, tenantId, appId));
    }

    /** 保存系统应用事件草稿。 */
    @PutMapping("/{appId}/events")
    @Operation(summary = "保存系统应用事件")
    public Result<EventView> saveEvent(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long appId,
            @Valid @RequestBody EventRequest request) {
        return Result.ok(service.saveEventSystem(token, systemId, tenantId, appId, request));
    }

    /** 查询系统应用事件配置。 */
    @GetMapping("/{appId}/events")
    @Operation(summary = "查询系统应用事件")
    public Result<List<EventView>> listEvents(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long appId) {
        return Result.ok(service.listEventsSystem(token, systemId, tenantId, appId));
    }

    /** 发布系统应用事件。 */
    @PostMapping("/{appId}/events/{eventId}/publish")
    @Operation(summary = "发布系统应用事件")
    public Result<EventView> publishEvent(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long appId, @PathVariable Long eventId) {
        return Result.ok(service.publishEventSystem(token, systemId, tenantId, appId, eventId));
    }
}
