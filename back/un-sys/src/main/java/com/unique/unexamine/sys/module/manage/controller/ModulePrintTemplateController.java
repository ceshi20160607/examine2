package com.unique.unexamine.sys.module.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintTemplateDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintTemplateDto.TemplateSearch;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintTemplateDto.TemplateView;
import com.unique.unexamine.sys.module.manage.service.ModulePrintTemplateService;
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

/** 模块打印模板的管理入口，打印执行仍走 PRINT 菜单动作。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/print-templates")
@Tag(name = "模块打印模板")
public class ModulePrintTemplateController {
    private final ModulePrintTemplateService service;

    public ModulePrintTemplateController(ModulePrintTemplateService service) { this.service = service; }

    /** 管理员分页查看草稿与历史版本。 */
    @GetMapping
    @Operation(summary = "查询打印模板")
    public Result<Page<TemplateView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @ModelAttribute TemplateSearch search) {
        return Result.ok(service.list(token, systemId, tenantId, moduleId, search));
    }

    /** 保存草稿模板版本。 */
    @PutMapping
    @Operation(summary = "保存打印模板草稿")
    public Result<TemplateView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, moduleId, request));
    }

    /** 发布后打印动作使用此版本。 */
    @PostMapping("/{templateId}/publish")
    @Operation(summary = "发布打印模板")
    public Result<TemplateView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long templateId) {
        return Result.ok(service.publish(token, systemId, tenantId, moduleId, templateId));
    }

    /** 停用版本后打印动作不回退历史版本。 */
    @PostMapping("/{templateId}/disable")
    @Operation(summary = "停用打印模板")
    public Result<TemplateView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long templateId) {
        return Result.ok(service.disable(token, systemId, tenantId, moduleId, templateId));
    }
}
