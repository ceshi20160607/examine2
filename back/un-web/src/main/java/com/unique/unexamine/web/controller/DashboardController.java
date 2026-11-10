package com.unique.unexamine.web.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.DashboardView;
import com.unique.unexamine.dashboard.manage.service.DashboardRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仪表盘运行入口；管理配置保存与发布后由这里读取已发布快照。 */
@RestController
@RequestMapping("/api/systems/{systemId}/dashboards")
@Tag(name = "仪表盘运行")
public class DashboardController {
    private final DashboardRuntimeService service;

    /** 注入仪表盘运行服务。 */
    public DashboardController(DashboardRuntimeService service) {
        this.service = service;
    }

    /** 返回当前账号在当前租户有权读取的仪表盘数据。 */
    @GetMapping("/{dashboardId}/runtime")
    @Operation(summary = "查询仪表盘运行数据")
    public Result<DashboardView> runtime(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dashboardId) {
        return Result.ok(service.query(token, systemId, tenantId, dashboardId));
    }
}
