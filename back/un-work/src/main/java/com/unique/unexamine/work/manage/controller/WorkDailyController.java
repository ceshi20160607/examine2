package com.unique.unexamine.work.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.work.manage.bo.WorkDailySearchBO;
import com.unique.unexamine.work.manage.dto.WorkDailyDto.DailyRequest;
import com.unique.unexamine.work.manage.dto.WorkDailyDto.DailyView;
import com.unique.unexamine.work.manage.service.WorkDailyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 手动工作日志入口；Controller 不生成日志，也不执行版本规则。 */
@RestController
@RequestMapping("/api/work/dailies")
@Tag(name="每日工作日志")
public class WorkDailyController {
    private final WorkDailyService service;

    /** 注入日志业务服务。 */
    public WorkDailyController(WorkDailyService service) { this.service=service; }

    /** 用户主动建立今天或补写日期的工作日志草稿。 */
    @PostMapping
    @Operation(summary="手动新建工作日志")
    public Result<DailyView> create(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @Valid @RequestBody DailyRequest request) {
        return Result.ok(service.create(token,systemId,tenantId,request));
    }

    /** 本人当前日志按日期和状态筛选。 */
    @GetMapping
    @Operation(summary="我的工作日志")
    public Result<Page<DailyView>> list(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @ModelAttribute WorkDailySearchBO search) {
        return Result.ok(service.queryPageList(token,systemId,tenantId,search));
    }

    /** 以首版 ID 查询当前有效版本。 */
    @GetMapping("/{rootId}")
    @Operation(summary="工作日志详情")
    public Result<DailyView> detail(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return Result.ok(service.queryById(token,systemId,tenantId,rootId));
    }

    /** 本人编辑当前草稿。 */
    @PutMapping("/{rootId}")
    @Operation(summary="编辑工作日志草稿")
    public Result<DailyView> edit(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId,
            @Valid @RequestBody DailyRequest request) {
        return Result.ok(service.edit(token,systemId,tenantId,rootId,request));
    }

    /** 本人明确提交草稿，形成新版本。 */
    @PostMapping("/{rootId}/submit")
    @Operation(summary="提交工作日志")
    public Result<DailyView> submit(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return Result.ok(service.submit(token,systemId,tenantId,rootId));
    }

    /** 本人撤回已提交版本，旧版本仍可追溯。 */
    @PostMapping("/{rootId}/withdraw")
    @Operation(summary="撤回工作日志")
    public Result<DailyView> withdraw(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return Result.ok(service.withdraw(token,systemId,tenantId,rootId));
    }

    /** 用户在撤回后主动恢复可编辑草稿。 */
    @PostMapping("/{rootId}/reopen")
    @Operation(summary="恢复工作日志草稿")
    public Result<DailyView> reopen(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return Result.ok(service.reopen(token,systemId,tenantId,rootId));
    }

    /** 本人查看提交、撤回及恢复记录。 */
    @GetMapping("/{rootId}/versions")
    @Operation(summary="工作日志版本历史")
    public Result<List<DailyView>> versions(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return Result.ok(service.versions(token,systemId,tenantId,rootId));
    }
}
