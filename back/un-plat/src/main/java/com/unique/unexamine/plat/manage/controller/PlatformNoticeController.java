package com.unique.unexamine.plat.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.PlatformNoticeDto.NoticeSearch;
import com.unique.unexamine.plat.manage.dto.PlatformNoticeDto.NoticeView;
import com.unique.unexamine.plat.manage.dto.PlatformNoticeDto.SaveRequest;
import com.unique.unexamine.plat.manage.service.PlatformNoticeService;
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

/** 平台公告配置与账号阅读入口。 */
@RestController
@RequestMapping("/api/platform/notices")
@Tag(name = "平台公告")
public class PlatformNoticeController {
    private final PlatformNoticeService service;

    public PlatformNoticeController(PlatformNoticeService service) { this.service = service; }

    /** 当前账号查看已发布公告。 */
    @GetMapping
    @Operation(summary = "查询可见平台公告")
    public Result<Page<NoticeView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @ModelAttribute NoticeSearch search) {
        return Result.ok(service.list(token, search));
    }

    /** 平台管理员查看公告配置。 */
    @GetMapping("/manage")
    @Operation(summary = "管理平台公告")
    public Result<Page<NoticeView>> manage(@RequestHeader(value = "Authorization", required = false) String token,
            @ModelAttribute NoticeSearch search) {
        return Result.ok(service.manage(token, search));
    }

    /** 平台管理员保存草稿。 */
    @PutMapping
    @Operation(summary = "保存平台公告草稿")
    public Result<NoticeView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, request));
    }

    /** 平台管理员发布公告。 */
    @PostMapping("/{noticeId}/publish")
    @Operation(summary = "发布平台公告")
    public Result<NoticeView> publish(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long noticeId) {
        return Result.ok(service.publish(token, noticeId));
    }

    /** 平台管理员停用公告。 */
    @PostMapping("/{noticeId}/disable")
    @Operation(summary = "停用平台公告")
    public Result<NoticeView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long noticeId) {
        return Result.ok(service.disable(token, noticeId));
    }
}
