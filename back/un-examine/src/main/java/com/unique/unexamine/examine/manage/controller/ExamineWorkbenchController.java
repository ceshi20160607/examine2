package com.unique.unexamine.examine.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView;
import com.unique.unexamine.examine.manage.service.ExamineRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 系统审批工作台入口；待办查询不依赖某一条业务记录的详情动作。 */
@RestController
@RequestMapping("/api/systems/{systemId}/examine-tasks")
@Tag(name = "审批工作台")
public class ExamineWorkbenchController {
    private final ExamineRuntimeService service;

    public ExamineWorkbenchController(ExamineRuntimeService service) {
        this.service = service;
    }

    /** 当前系统租户中当前账号仍待处理的审批实例。 */
    @GetMapping
    @Operation(summary = "查询审批待办")
    public Result<Page<ExampleView>> pending(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @ModelAttribute SearchBO search) {
        return Result.ok(service.pending(token, systemId, tenantId, search));
    }
}
