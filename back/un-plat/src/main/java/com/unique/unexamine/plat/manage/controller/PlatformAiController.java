package com.unique.unexamine.plat.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.PlatformAiDto.ConfigSearch;
import com.unique.unexamine.plat.manage.dto.PlatformAiDto.ConfigView;
import com.unique.unexamine.plat.manage.dto.PlatformAiDto.SaveRequest;
import com.unique.unexamine.plat.manage.service.PlatformAiConfigService;
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

/** 平台模型授权配置入口。 */
@RestController
@RequestMapping("/api/platform/ai-configs")
@Tag(name = "平台智能能力配置")
public class PlatformAiController {
    private final PlatformAiConfigService service;

    public PlatformAiController(PlatformAiConfigService service) { this.service = service; }

    /** 管理员查询模型授权配置。 */
    @GetMapping
    @Operation(summary = "查询平台智能能力配置")
    public Result<Page<ConfigView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @ModelAttribute ConfigSearch search) {
        return Result.ok(service.list(token, search));
    }

    /** 管理员保存待发布的模型配置。 */
    @PutMapping
    @Operation(summary = "保存平台智能能力草稿")
    public Result<ConfigView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, request));
    }

    /** 发布模型能力和授权快照。 */
    @PostMapping("/{configId}/publish")
    @Operation(summary = "发布平台智能能力")
    public Result<ConfigView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long configId) {
        return Result.ok(service.publish(token, configId));
    }

    /** 停用模型授权。 */
    @PostMapping("/{configId}/disable")
    @Operation(summary = "停用平台智能能力")
    public Result<ConfigView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long configId) {
        return Result.ok(service.disable(token, configId));
    }
}
