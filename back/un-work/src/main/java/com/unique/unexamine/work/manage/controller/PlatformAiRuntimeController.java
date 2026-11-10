package com.unique.unexamine.work.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.core.common.dto.AiConversationView;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.AvailableView;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.ConfirmRequest;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.DialogRequest;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.TaskPreviewView;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.TaskQueryView;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.TaskResult;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.LogQueryView;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.QuotaView;
import com.unique.unexamine.work.manage.dto.PlatformAiRuntimeDto.SystemQueryView;
import com.unique.unexamine.work.manage.service.PlatformAiRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台主页面 Agent 的平台任务运行入口。 */
@RestController
@RequestMapping("/api/platform/ai")
@Tag(name = "平台智能助手")
public class PlatformAiRuntimeController {
    private final PlatformAiRuntimeService service;

    public PlatformAiRuntimeController(PlatformAiRuntimeService service) { this.service = service; }

    /** 平台账号查看可用本地模型。 */
    @GetMapping("/available")
    @Operation(summary = "查询可用平台智能模型")
    public Result<List<AvailableView>> available(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.available(token));
    }

    /** 平台管理员读取模型用量。 */
    @GetMapping("/models/{configId}/quota")
    @Operation(summary = "查询平台模型额度")
    public Result<QuotaView> quota(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long configId) {
        return Result.ok(service.quota(token, configId));
    }

    /** 查询结果只取本人可见的平台任务。 */
    @PostMapping("/tasks/query")
    @Operation(summary = "智能查询本人平台任务")
    public Result<TaskQueryView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody DialogRequest request) {
        return Result.ok(service.queryTasks(token, request));
    }

    /** 平台日志仍只允许原日志接口授权的管理员查询。 */
    @PostMapping("/logs/query")
    @Operation(summary = "智能查询平台日志")
    public Result<LogQueryView> logs(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody DialogRequest request) {
        return Result.ok(service.queryLogs(token, request));
    }

    /** 只提示本人可进入的系统，业务查询须在切换后进行。 */
    @PostMapping("/systems/query")
    @Operation(summary = "智能查询可进入系统")
    public Result<SystemQueryView> systems(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody DialogRequest request) {
        return Result.ok(service.querySystems(token, request));
    }

    /** 任务字段先预览，不在识别接口写入。 */
    @PostMapping("/tasks/preview")
    @Operation(summary = "智能创建平台任务预览")
    public Result<TaskPreviewView> preview(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody DialogRequest request) {
        return Result.ok(service.previewTask(token, request));
    }

    /** 用户确认后领取一次性预览并创建任务。 */
    @PostMapping("/tasks/confirm")
    @Operation(summary = "确认智能创建平台任务")
    public Result<TaskResult> confirm(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody ConfirmRequest request) {
        return Result.ok(service.confirmTask(token, request));
    }

    /** 任务写入结果可回查发起人的原始对话与确认信息。 */
    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "查询本人平台智能对话")
    public Result<AiConversationView> conversation(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String conversationId) {
        return Result.ok(service.conversation(token, conversationId));
    }

    /** 平台管理员手动清理已过保留期的对话，每次最多一千条。 */
    @PostMapping("/conversations/purge-expired")
    @Operation(summary = "清理过期智能对话")
    public Result<Integer> purgeExpired(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.purgeExpiredConversations(token));
    }
}
