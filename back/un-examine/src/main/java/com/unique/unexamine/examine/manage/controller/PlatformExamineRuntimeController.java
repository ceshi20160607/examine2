package com.unique.unexamine.examine.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionRequest;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.StartRequest;
import com.unique.unexamine.examine.manage.service.ExamineRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台审批实例办理入口；向外暴露时仍需应用资源授权。 */
@RestController
@RequestMapping("/api/platform/examine-examples")
@Tag(name = "平台审批运行")
public class PlatformExamineRuntimeController {
    private final ExamineRuntimeService service;

    public PlatformExamineRuntimeController(ExamineRuntimeService service) {
        this.service = service;
    }

    /** 当前平台账号仍待处理的审批实例。 */
    @GetMapping("/pending")
    @Operation(summary = "查询平台审批待办")
    public Result<Page<ExampleView>> pending(@RequestHeader(value = "Authorization", required = false) String token,
                                             @ModelAttribute SearchBO search) {
        return Result.ok(service.pendingPlatform(token, search));
    }

    /** 平台管理员查看平台审批实例。 */
    @GetMapping
    @Operation(summary = "查询平台审批实例")
    public Result<List<ExampleView>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.listPlatform(token));
    }

    /** 平台账号发起已启用的审批定义。 */
    @PostMapping("/start")
    @Operation(summary = "发起平台审批")
    public Result<ExampleView> start(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody StartRequest request) {
        return Result.ok(service.startPlatform(token, request));
    }

    /** 平台管理员查询单个审批实例。 */
    @GetMapping("/{exampleId}")
    @Operation(summary = "查询平台审批实例详情")
    public Result<ExampleView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long exampleId) {
        return Result.ok(service.queryPlatform(token, exampleId));
    }

    /** 平台审批处理人按实例节点办理。 */
    @PostMapping("/{exampleId}/nodes/{nodeId}/actions")
    @Operation(summary = "办理平台审批节点")
    public Result<ActionView> action(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long exampleId, @PathVariable Long nodeId,
            @Valid @RequestBody ActionRequest request) {
        return Result.ok(service.actionPlatform(token, exampleId, nodeId, request));
    }
}
