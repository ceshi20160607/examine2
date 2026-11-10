package com.unique.unexamine.sys.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.DeliveryView;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.SaveRequest;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.SendRequest;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.TemplateSearch;
import com.unique.unexamine.sys.manage.dto.SystemMessageTemplateDto.TemplateView;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;
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

/** 系统管理员配置消息模板并查看站内投递结果。 */
@RestController
@RequestMapping("/api/systems/{systemId}/message-templates")
@Tag(name = "系统消息模板")
public class SystemMessageTemplateController {
    private final SystemMessageTemplateService service;

    public SystemMessageTemplateController(SystemMessageTemplateService service) { this.service = service; }

    /** 查看当前租户模板配置。 */
    @GetMapping
    @Operation(summary = "查询系统消息模板")
    public Result<Page<TemplateView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @ModelAttribute TemplateSearch search) {
        return Result.ok(service.list(token, systemId, tenantId, search));
    }

    /** 保存草稿，变量和跳转目标配置在发布后生效。 */
    @PutMapping
    @Operation(summary = "保存系统消息模板草稿")
    public Result<TemplateView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 发布已保存模板。 */
    @PostMapping("/{templateId}/publish")
    @Operation(summary = "发布系统消息模板")
    public Result<TemplateView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long templateId) {
        return Result.ok(service.publish(token, systemId, tenantId, templateId));
    }

    /** 停用模板，历史投递仍可查询。 */
    @PostMapping("/{templateId}/disable")
    @Operation(summary = "停用系统消息模板")
    public Result<TemplateView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long templateId) {
        return Result.ok(service.disable(token, systemId, tenantId, templateId));
    }

    /** 按已发布模板发送一条系统站内消息。 */
    @PostMapping("/send")
    @Operation(summary = "按模板发送系统消息")
    public Result<DeliveryView> send(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SendRequest request) {
        return Result.ok(service.send(token, systemId, tenantId, request));
    }

    /** 查看模板对应的投递记录。 */
    @GetMapping("/{templateId}/deliveries")
    @Operation(summary = "查询系统消息投递记录")
    public Result<Page<DeliveryView>> deliveries(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long templateId, @ModelAttribute TemplateSearch search) {
        return Result.ok(service.deliveries(token, systemId, tenantId, templateId, search));
    }

    /** 人工重试当前租户失败或中断的消息投递。 */
    @PostMapping("/{templateId}/deliveries/{deliveryId}/retry")
    @Operation(summary = "重试系统消息投递")
    public Result<DeliveryView> retry(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long templateId, @PathVariable Long deliveryId) {
        return Result.ok(service.retry(token, systemId, tenantId, templateId, deliveryId));
    }
}
