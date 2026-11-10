package com.unique.unexamine.web.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.DataSourceView;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.SaveConfigRequest;
import com.unique.unexamine.dashboard.manage.dto.DataSourceManageDto.SaveRequest;
import com.unique.unexamine.dashboard.manage.service.DataSourceManageService;
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

/** 数据源配置入口；数据源运行必须经过发布快照。 */
@RestController
@RequestMapping("/api/systems/{systemId}/data-sources")
@Tag(name = "仪表盘数据源")
public class DataSourceManageController {
    private final DataSourceManageService service;

    /** 注入数据源配置服务。 */
    public DataSourceManageController(DataSourceManageService service) {
        this.service = service;
    }

    /** 列出当前系统租户可维护的数据源。 */
    @GetMapping
    @Operation(summary = "查询数据源列表")
    public Result<List<DataSourceView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.list(token, systemId, tenantId));
    }

    /** 查询数据源及其配置。 */
    @GetMapping("/{dataSourceId}")
    @Operation(summary = "查询数据源配置")
    public Result<DataSourceView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dataSourceId) {
        return Result.ok(service.query(token, systemId, tenantId, dataSourceId));
    }

    /** 保存数据源草稿。 */
    @PutMapping
    @Operation(summary = "保存数据源")
    public Result<DataSourceView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 保存数据源查询配置草稿。 */
    @PutMapping("/{dataSourceId}/config")
    @Operation(summary = "保存数据源查询配置")
    public Result<DataSourceView> saveConfig(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dataSourceId,
            @Valid @RequestBody SaveConfigRequest request) {
        return Result.ok(service.saveConfig(token, systemId, tenantId, dataSourceId, request));
    }

    /** 发布数据源配置快照。 */
    @PostMapping("/{dataSourceId}/publish")
    @Operation(summary = "发布数据源")
    public Result<DataSourceView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long dataSourceId) {
        return Result.ok(service.publish(token, systemId, tenantId, dataSourceId));
    }
}
