package com.unique.unexamine.sys.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.dto.SystemAiDto.ConfigSearch;
import com.unique.unexamine.sys.manage.dto.SystemAiDto.ConfigView;
import com.unique.unexamine.sys.manage.dto.SystemAiDto.SaveRequest;
import com.unique.unexamine.sys.manage.service.SystemAiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统租户智能能力授权和发布入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/ai-configs")
@Tag(name = "系统智能能力配置")
public class SystemAiController {
    private final SystemAiConfigService service;

    public SystemAiController(SystemAiConfigService service) { this.service = service; }

    /** 管理员查看本租户配置。 */
    @GetMapping
    @Operation(summary = "查询系统智能能力配置")
    public Result<Page<ConfigView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @ModelAttribute ConfigSearch search) {
        return Result.ok(service.list(token, systemId, tenantId, search));
    }

    /** 管理员保存策略草稿。 */
    @PutMapping
    @Operation(summary = "保存系统智能能力草稿")
    public Result<ConfigView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 发布前核对平台授权和本租户模块字段边界。 */
    @PostMapping("/{configId}/publish")
    @Operation(summary = "发布系统智能能力")
    public Result<ConfigView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @PathVariable Long configId) {
        return Result.ok(service.publish(token, systemId, tenantId, configId));
    }

    /** 停用本租户智能能力。 */
    @PostMapping("/{configId}/disable")
    @Operation(summary = "停用系统智能能力")
    public Result<ConfigView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @PathVariable Long configId) {
        return Result.ok(service.disable(token, systemId, tenantId, configId));
    }
}
