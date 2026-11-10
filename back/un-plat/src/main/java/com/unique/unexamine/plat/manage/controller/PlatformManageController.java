package com.unique.unexamine.plat.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.ConfigRequest;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.ConfigSearch;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.ConfigView;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.MessageSearch;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.MessageView;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.UnreadSummary;
import com.unique.unexamine.plat.manage.service.PlatformConfigManageService;
import com.unique.unexamine.plat.manage.service.PlatformMessageManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台公共配置和站内消息入口；接口返回统一 Result。 */
@RestController
@RequestMapping("/api/platform")
@Tag(name = "平台配置与消息")
public class PlatformManageController {
    private final PlatformConfigManageService configs;
    private final PlatformMessageManageService messages;

    /** 注入平台配置与消息业务服务。 */
    public PlatformManageController(PlatformConfigManageService configs, PlatformMessageManageService messages) {
        this.configs = configs;
        this.messages = messages;
    }

    /** 平台管理员分页查询配置。 */
    @GetMapping("/configs")
    @Operation(summary = "查询平台配置")
    public Result<Page<ConfigView>> configs(@RequestHeader(value = "Authorization", required = false) String token,
                                            @org.springframework.web.bind.annotation.ModelAttribute ConfigSearch search) {
        return Result.ok(configs.query(token, search));
    }

    /** 平台管理员保存配置并立即生效。 */
    @PutMapping("/configs")
    @Operation(summary = "保存平台配置")
    public Result<ConfigView> saveConfig(@RequestHeader(value = "Authorization", required = false) String token,
                                         @Valid @RequestBody ConfigRequest request) {
        return Result.ok(configs.save(token, request));
    }

    /** 当前账号的站内消息分页列表。 */
    @GetMapping("/messages")
    @Operation(summary = "查询我的平台消息")
    public Result<Page<MessageView>> messages(@RequestHeader(value = "Authorization", required = false) String token,
                                              @org.springframework.web.bind.annotation.ModelAttribute MessageSearch search) {
        return Result.ok(messages.mine(token, search));
    }

    /** 当前账号顶部消息角标。 */
    @GetMapping("/messages/unread")
    @Operation(summary = "查询平台消息未读数量")
    public Result<UnreadSummary> unreadMessages(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(messages.unread(token));
    }

    /** 批量标记当前账号的未读消息。 */
    @PostMapping("/messages/read-all")
    @Operation(summary = "全部标记平台消息已读")
    public Result<UnreadSummary> readAllMessages(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(messages.readAll(token));
    }

    /** 当前账号标记自己的消息已读。 */
    @PostMapping("/messages/{messageId}/read")
    @Operation(summary = "标记平台消息已读")
    public Result<MessageView> readMessage(@RequestHeader(value = "Authorization", required = false) String token,
                                           @PathVariable Long messageId) {
        return Result.ok(messages.read(token, messageId));
    }

    /** 当前账号归档自己的消息。 */
    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "归档平台消息")
    public Result<Void> archiveMessage(@RequestHeader(value = "Authorization", required = false) String token,
                                       @PathVariable Long messageId) {
        messages.archive(token, messageId);
        return Result.ok();
    }
}
