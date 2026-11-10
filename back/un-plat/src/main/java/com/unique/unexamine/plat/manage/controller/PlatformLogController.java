package com.unique.unexamine.plat.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.bo.PlatformLogSearchBO;
import com.unique.unexamine.plat.manage.dto.PlatformLogDto.LogView;
import com.unique.unexamine.plat.manage.service.PlatformLogManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台操作日志查询入口；接口只负责接收筛选条件和返回统一 Result。 */
@RestController
@RequestMapping("/api/platform/logs")
@Tag(name = "平台操作日志")
public class PlatformLogController {
    private final PlatformLogManageService service;

    public PlatformLogController(PlatformLogManageService service) {
        this.service = service;
    }

    /** 查询平台管理范围内的操作日志。 */
    @GetMapping
    @Operation(summary = "筛选平台操作日志")
    public Result<Page<LogView>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @ModelAttribute PlatformLogSearchBO search) {
        return Result.ok(service.queryPageList(token, search));
    }
}
