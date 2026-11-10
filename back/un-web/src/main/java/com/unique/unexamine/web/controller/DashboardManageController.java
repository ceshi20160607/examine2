package com.unique.unexamine.web.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.DashboardView;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.AccessRequest;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.SaveComponentRequest;
import com.unique.unexamine.dashboard.manage.dto.DashboardManageDto.SaveRequest;
import com.unique.unexamine.dashboard.manage.service.DashboardManageService;
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

/** 仪表盘配置入口；保存草稿和发布是两个明确动作。 */
@RestController
@RequestMapping("/api/systems/{systemId}/dashboards")
@Tag(name = "仪表盘配置")
public class DashboardManageController {
    private final DashboardManageService service;

    /** 注入仪表盘配置服务。 */
    public DashboardManageController(DashboardManageService service) {
        this.service = service;
    }

    /** 列出当前账号可进入的仪表盘。 */
    @GetMapping
    @Operation(summary = "查询仪表盘列表")
    public Result<List<DashboardView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.list(token, systemId, tenantId));
    }

    /** 查询当前租户可维护的仪表盘及组件配置。 */
    @GetMapping("/{dashboardId}")
    @Operation(summary = "查询仪表盘配置")
    public Result<DashboardView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dashboardId) {
        return Result.ok(service.query(token, systemId, tenantId, dashboardId));
    }

    /** 保存仪表盘草稿。 */
    @PutMapping
    @Operation(summary = "保存仪表盘草稿")
    public Result<DashboardView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 保存一个仪表盘组件草稿。 */
    @PutMapping("/{dashboardId}/components")
    @Operation(summary = "保存仪表盘组件")
    public Result<DashboardView> saveComponent(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dashboardId,
            @Valid @RequestBody SaveComponentRequest request) {
        return Result.ok(service.saveComponent(token, systemId, tenantId, dashboardId, request));
    }

    /** 保存仪表盘人员、部门和角色访问范围。 */
    @PutMapping("/{dashboardId}/access")
    @Operation(summary = "保存仪表盘访问范围")
    public Result<DashboardView> saveAccess(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dashboardId,
            @Valid @RequestBody AccessRequest request) {
        return Result.ok(service.saveAccess(token, systemId, tenantId, dashboardId, request));
    }

    /** 发布当前布局和组件快照。 */
    @PostMapping("/{dashboardId}/publish")
    @Operation(summary = "发布仪表盘")
    public Result<DashboardView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dashboardId) {
        return Result.ok(service.publish(token, systemId, tenantId, dashboardId));
    }
}
