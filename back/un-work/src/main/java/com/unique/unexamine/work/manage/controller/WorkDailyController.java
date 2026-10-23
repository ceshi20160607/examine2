package com.unique.unexamine.work.manage.controller;

import com.unique.unexamine.work.manage.service.WorkDailyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
    public WorkDailyService.DailyView create(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @Valid @RequestBody WorkDailyService.DailyRequest request) {
        return service.create(token,systemId,tenantId,request);
    }

    /** 本人当前日志按日期和状态筛选。 */
    @GetMapping
    @Operation(summary="我的工作日志")
    public WorkDailyService.DailyPage list(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,
            @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) Long projectId,
            @RequestParam(required=false) Long taskId,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size) {
        return service.queryPageList(token,systemId,tenantId,from,to,status,projectId,taskId,page,size);
    }

    /** 以首版 ID 查询当前有效版本。 */
    @GetMapping("/{rootId}")
    @Operation(summary="工作日志详情")
    public WorkDailyService.DailyView detail(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return service.queryById(token,systemId,tenantId,rootId);
    }

    /** 本人编辑当前草稿。 */
    @PutMapping("/{rootId}")
    @Operation(summary="编辑工作日志草稿")
    public WorkDailyService.DailyView edit(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId,
            @Valid @RequestBody WorkDailyService.DailyRequest request) {
        return service.edit(token,systemId,tenantId,rootId,request);
    }

    /** 本人明确提交草稿，形成新版本。 */
    @PostMapping("/{rootId}/submit")
    @Operation(summary="提交工作日志")
    public WorkDailyService.DailyView submit(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return service.submit(token,systemId,tenantId,rootId);
    }

    /** 本人撤回已提交版本，旧版本仍可追溯。 */
    @PostMapping("/{rootId}/withdraw")
    @Operation(summary="撤回工作日志")
    public WorkDailyService.DailyView withdraw(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return service.withdraw(token,systemId,tenantId,rootId);
    }

    /** 用户在撤回后主动恢复可编辑草稿。 */
    @PostMapping("/{rootId}/reopen")
    @Operation(summary="恢复工作日志草稿")
    public WorkDailyService.DailyView reopen(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return service.reopen(token,systemId,tenantId,rootId);
    }

    /** 本人查看提交、撤回及恢复记录。 */
    @GetMapping("/{rootId}/versions")
    @Operation(summary="工作日志版本历史")
    public List<WorkDailyService.DailyView> versions(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @RequestParam(required=false) Long systemId,@PathVariable Long rootId) {
        return service.versions(token,systemId,tenantId,rootId);
    }
}
