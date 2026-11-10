package com.unique.unexamine.plat.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.DeliveryView;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.SaveRequest;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.SendRequest;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.TemplateSearch;
import com.unique.unexamine.plat.manage.dto.PlatformMessageTemplateDto.TemplateView;
import com.unique.unexamine.plat.manage.service.PlatformMessageTemplateService;
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

/** 平台消息模板配置和投递结果入口。 */
@RestController
@RequestMapping("/api/platform/message-templates")
@Tag(name = "平台消息模板")
public class PlatformMessageTemplateController {
    private final PlatformMessageTemplateService service;

    public PlatformMessageTemplateController(PlatformMessageTemplateService service) { this.service = service; }

    /** 管理员分页查看模板。 */
    @GetMapping
    @Operation(summary = "查询平台消息模板")
    public Result<Page<TemplateView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @ModelAttribute TemplateSearch search) {
        return Result.ok(service.list(token, search));
    }

    /** 保存平台消息模板草稿。 */
    @PutMapping
    @Operation(summary = "保存平台消息模板草稿")
    public Result<TemplateView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, request));
    }

    /** 发布模板后可按变量发送。 */
    @PostMapping("/{templateId}/publish")
    @Operation(summary = "发布平台消息模板")
    public Result<TemplateView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long templateId) {
        return Result.ok(service.publish(token, templateId));
    }

    /** 停用模板，历史消息仍保留。 */
    @PostMapping("/{templateId}/disable")
    @Operation(summary = "停用平台消息模板")
    public Result<TemplateView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long templateId) {
        return Result.ok(service.disable(token, templateId));
    }

    /** 管理员按模板发送平台站内消息。 */
    @PostMapping("/send")
    @Operation(summary = "按模板发送平台消息")
    public Result<DeliveryView> send(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SendRequest request) {
        return Result.ok(service.send(token, request));
    }

    /** 管理员查看模板投递记录。 */
    @GetMapping("/{templateId}/deliveries")
    @Operation(summary = "查询平台消息投递记录")
    public Result<Page<DeliveryView>> deliveries(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long templateId, @ModelAttribute TemplateSearch search) {
        return Result.ok(service.deliveries(token, templateId, search));
    }

    /** 人工重试平台失败或中断的消息投递。 */
    @PostMapping("/{templateId}/deliveries/{deliveryId}/retry")
    @Operation(summary = "重试平台消息投递")
    public Result<DeliveryView> retry(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long templateId, @PathVariable Long deliveryId) {
        return Result.ok(service.retry(token, templateId, deliveryId));
    }
}
