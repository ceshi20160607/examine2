package com.unique.unexamine.sys.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.bo.SystemLogSearchBO;
import com.unique.unexamine.sys.manage.dto.SystemLogDto.LogView;
import com.unique.unexamine.sys.manage.dto.SystemLogDto.LoginView;
import com.unique.unexamine.sys.manage.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统操作日志查询入口，具体筛选及授权由业务服务处理。 */
@RestController
@RequestMapping("/api/systems/{systemId}/logs")
@Tag(name = "系统操作日志")
public class SystemLogController {
    private final SystemLogService service;

    /** 注入系统日志业务服务。 */
    public SystemLogController(SystemLogService service) {
        this.service = service;
    }

    /** 查询系统管理范围内的操作日志。 */
    @GetMapping
    @Operation(summary = "筛选系统操作日志")
    public Result<Page<LogView>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long systemId, @ModelAttribute SystemLogSearchBO search) {
        return Result.ok(service.queryPageList(token, systemId, search));
    }

    /** 管理员按租户、账号和时间查看显式进入系统的记录。 */
    @GetMapping("/login")
    @Operation(summary = "查询系统登录日志")
    public Result<Page<LoginView>> logins(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long systemId, @ModelAttribute SystemLogSearchBO search) {
        return Result.ok(service.logins(token, systemId, search));
    }
}
