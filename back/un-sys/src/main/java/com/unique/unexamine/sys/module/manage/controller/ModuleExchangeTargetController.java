package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.service.ModuleExchangeTargetService;
import com.unique.unexamine.sys.module.manage.service.ModuleExchangeTargetService.TargetView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 消息跳转数据交换结果时，先按当前账号重新核对作业读取权限。 */
@RestController
@RequestMapping("/api/systems/{systemId}/exchange-jobs")
@Tag(name = "数据交换消息跳转")
public class ModuleExchangeTargetController {
    private final ModuleExchangeTargetService service;

    public ModuleExchangeTargetController(ModuleExchangeTargetService service) { this.service=service; }

    /** 用作业编号解析模块与结果类型，不绕过原导入导出接口授权。 */
    @GetMapping("/{jobId}/target")
    @Operation(summary = "解析导入导出消息的结果目标")
    public Result<TargetView> target(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long jobId) {
        return Result.ok(service.resolve(token,systemId,tenantId,jobId));
    }
}
