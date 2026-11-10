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

/** 平台应用配置入口；发布授权前不会对外或对内生效。 */
@RestController
@RequestMapping("/api/apps")
@Tag(name = "平台应用配置")
public class AppManageController {
    private final AppManageService service;

    /** 注入应用管理服务。 */
    public AppManageController(AppManageService service) {
        this.service = service;
    }

    /** 列出平台应用，便于管理员进入具体应用配置。 */
    @GetMapping
    @Operation(summary = "查询平台应用列表")
    public Result<List<AppView>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.listPlatform(token));
    }

    /** 查询平台应用。 */
    @GetMapping("/{appId}")
    @Operation(summary = "查询平台应用")
    public Result<AppView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long appId) {
        return Result.ok(service.queryPlatform(token, appId));
    }

    /** 保存平台应用草稿。 */
    @PutMapping
    @Operation(summary = "保存平台应用")
    public Result<AppView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.savePlatform(token, request));
    }

    /** 发布平台应用授权快照。 */
    @PostMapping("/{appId}/publish")
    @Operation(summary = "发布平台应用")
    public Result<AppView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long appId) {
        return Result.ok(service.publishPlatform(token, appId));
    }

    /** 保存平台应用事件草稿。 */
    @PutMapping("/{appId}/events")
    @Operation(summary = "保存平台应用事件")
    public Result<EventView> saveEvent(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long appId, @Valid @RequestBody EventRequest request) {
        return Result.ok(service.saveEventPlatform(token, appId, request));
    }

    /** 查询平台应用事件配置。 */
    @GetMapping("/{appId}/events")
    @Operation(summary = "查询平台应用事件")
    public Result<List<EventView>> listEvents(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long appId) {
        return Result.ok(service.listEventsPlatform(token, appId));
    }

    /** 发布平台应用事件。 */
    @PostMapping("/{appId}/events/{eventId}/publish")
    @Operation(summary = "发布平台应用事件")
    public Result<EventView> publishEvent(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long appId, @PathVariable Long eventId) {
        return Result.ok(service.publishEventPlatform(token, appId, eventId));
    }
}
