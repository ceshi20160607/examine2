package com.unique.unexamine.sys.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.dto.SystemNoticeDto.NoticeSearch;
import com.unique.unexamine.sys.manage.dto.SystemNoticeDto.NoticeView;
import com.unique.unexamine.sys.manage.dto.SystemNoticeDto.SaveRequest;
import com.unique.unexamine.sys.manage.service.SystemNoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统公告管理与成员阅读入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/notices")
@Tag(name = "系统公告")
public class SystemNoticeController {
    private final SystemNoticeService service;

    public SystemNoticeController(SystemNoticeService service) { this.service = service; }

    /** 当前租户成员只读取已发布且未过期的公告。 */
    @GetMapping
    @Operation(summary = "查询可见公告")
    public Result<Page<NoticeView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @org.springframework.web.bind.annotation.ModelAttribute NoticeSearch search) {
        return Result.ok(service.list(token, systemId, tenantId, search));
    }

    /** 管理员查看草稿、已发布和停用公告。 */
    @GetMapping("/manage")
    @Operation(summary = "管理公告列表")
    public Result<Page<NoticeView>> manage(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @org.springframework.web.bind.annotation.ModelAttribute NoticeSearch search) {
        return Result.ok(service.manage(token, systemId, tenantId, search));
    }

    /** 管理员保存草稿。 */
    @PutMapping
    @Operation(summary = "保存公告草稿")
    public Result<NoticeView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 管理员发布公告后才对成员可见。 */
    @PostMapping("/{noticeId}/publish")
    @Operation(summary = "发布公告")
    public Result<NoticeView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @PathVariable Long noticeId) {
        return Result.ok(service.publish(token, systemId, tenantId, noticeId));
    }

    /** 管理员停用公告，历史内容仍留存。 */
    @PostMapping("/{noticeId}/disable")
    @Operation(summary = "停用公告")
    public Result<NoticeView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId,
            @PathVariable Long noticeId) {
        return Result.ok(service.disable(token, systemId, tenantId, noticeId));
    }
}
