package com.unique.unexamine.sys.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.dto.SystemMessageDto.MessageSearch;
import com.unique.unexamine.sys.manage.dto.SystemMessageDto.MessageView;
import com.unique.unexamine.sys.manage.dto.SystemMessageDto.UnreadSummary;
import com.unique.unexamine.sys.manage.service.SystemMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统消息发送与当前成员收件箱。 */
@RestController
@RequestMapping("/api/systems/{systemId}/messages")
@Tag(name = "系统消息")
public class SystemMessageController {
    private final SystemMessageService service;

    public SystemMessageController(SystemMessageService service) { this.service = service; }

    /** 当前成员分页查看本人在当前租户的消息。 */
    @GetMapping
    @Operation(summary = "查询我的系统消息")
    public Result<Page<MessageView>> mine(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @ModelAttribute MessageSearch search) {
        return Result.ok(service.mine(token, systemId, tenantId, search));
    }

    /** 当前成员读取顶部消息未读数量。 */
    @GetMapping("/unread")
    @Operation(summary = "查询系统消息未读数量")
    public Result<UnreadSummary> unread(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId) {
        return Result.ok(service.unread(token, systemId, tenantId));
    }

    /** 一次性标记当前租户的本人未读消息。 */
    @PostMapping("/read-all")
    @Operation(summary = "全部标记系统消息已读")
    public Result<UnreadSummary> readAll(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId) {
        return Result.ok(service.readAll(token, systemId, tenantId));
    }

    /** 当前成员标记自己的消息已读。 */
    @PostMapping("/{messageId}/read")
    @Operation(summary = "标记系统消息已读")
    public Result<MessageView> read(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @PathVariable Long messageId) {
        return Result.ok(service.read(token, systemId, tenantId, messageId));
    }

    /** 当前成员归档自己的消息。 */
    @DeleteMapping("/{messageId}")
    @Operation(summary = "归档系统消息")
    public Result<Void> archive(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @PathVariable Long messageId) {
        service.archive(token, systemId, tenantId, messageId);
        return Result.ok();
    }
}
