package com.unique.unexamine.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.KpiView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.ResultView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.RuntimeView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.SaveRequest;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.TargetRequest;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.TargetView;
import com.unique.unexamine.dashboard.manage.service.KpiManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** KPI 配置、目标和运行计算接口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/kpis")
@Tag(name = "KPI")
public class KpiController {
    private final KpiManageService service;

    /** 注入 KPI 业务服务。 */
    public KpiController(KpiManageService service) {
        this.service = service;
    }

    /** 列出当前系统租户的 KPI 配置。 */
    @GetMapping
    @Operation(summary = "查询 KPI 列表")
    public Result<List<KpiView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.list(token, systemId, tenantId));
    }

    /** 查询 KPI 配置。 */
    @GetMapping("/{kpiId}")
    @Operation(summary = "查询 KPI")
    public Result<KpiView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long kpiId) {
        return Result.ok(service.query(token, systemId, tenantId, kpiId));
    }

    /** 查询全租户和各目标的 KPI 周期计算历史。 */
    @GetMapping("/{kpiId}/results")
    @Operation(summary = "查询 KPI 计算历史")
    public Result<Page<ResultView>> results(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long kpiId,
            @RequestParam(required = false) String periodKey, @ModelAttribute SearchBO search) {
        return Result.ok(service.results(token, systemId, tenantId, kpiId, periodKey, search));
    }

    /** 保存 KPI 草稿。 */
    @PutMapping
    @Operation(summary = "保存 KPI")
    public Result<KpiView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 发布 KPI 计算口径。 */
    @PostMapping("/{kpiId}/publish")
    @Operation(summary = "发布 KPI")
    public Result<KpiView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long kpiId) {
        return Result.ok(service.publish(token, systemId, tenantId, kpiId));
    }

    /** 按周期查看当前租户 KPI 的目标、最近实际值和达成率。 */
    @GetMapping("/{kpiId}/targets")
    @Operation(summary = "查询 KPI 目标")
    public Result<Page<TargetView>> targets(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long kpiId,
            @RequestParam(required = false) String periodKey, @ModelAttribute SearchBO search) {
        return Result.ok(service.targets(token, systemId, tenantId, kpiId, periodKey, search));
    }

    /** 保存 KPI 周期目标。 */
    @PutMapping("/{kpiId}/targets")
    @Operation(summary = "保存 KPI 目标")
    public Result<TargetView> target(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long kpiId,
            @Valid @RequestBody TargetRequest request) {
        return Result.ok(service.saveTarget(token, systemId, tenantId, kpiId, request));
    }

    /** 计算指定周期并更新有效目标与结果历史。 */
    @PostMapping("/{kpiId}/calculate")
    @Operation(summary = "计算 KPI")
    public Result<RuntimeView> calculate(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long kpiId,
            @RequestParam String periodKey) {
        return Result.ok(service.calculate(token, systemId, tenantId, kpiId, periodKey));
    }
}
