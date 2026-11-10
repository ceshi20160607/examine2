package com.unique.unexamine.sys.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.core.common.dto.AiConversationView;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.QueryRequest;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.QueryView;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.AvailableView;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.WriteConfirmRequest;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.WritePreviewRequest;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.WritePreviewView;
import com.unique.unexamine.sys.manage.dto.SystemAiRuntimeDto.WriteResult;
import com.unique.unexamine.sys.manage.service.SystemAiRuntimeService;
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

/** 系统运行态 Agent 入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/ai")
@Tag(name = "系统智能助手")
public class SystemAiRuntimeController {
    private final SystemAiRuntimeService service;

    public SystemAiRuntimeController(SystemAiRuntimeService service) { this.service = service; }

    /** 普通成员查看当前身份可用的 Agent 模块动作。 */
    @GetMapping("/available")
    @Operation(summary = "查询可用智能助手")
    public Result<List<AvailableView>> available(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId, @PathVariable Long systemId) {
        return Result.ok(service.available(token, systemId, tenantId));
    }

    /** 识别查询意图后，以当前登录人的模块权限执行查询。 */
    @PostMapping("/query")
    @Operation(summary = "智能查询模块数据")
    public Result<QueryView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody QueryRequest request) {
        return Result.ok(service.query(token, systemId, tenantId, request));
    }

    /** 只生成字段映射预览，不写入业务记录。 */
    @PostMapping("/write-preview")
    @Operation(summary = "智能录入预览")
    public Result<WritePreviewView> preview(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody WritePreviewRequest request) {
        return Result.ok(service.preview(token, systemId, tenantId, request));
    }

    /** 用户确认后才按当前模块权限和字段校验保存。 */
    @PostMapping("/write-confirm")
    @Operation(summary = "确认智能录入")
    public Result<WriteResult> confirm(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody WriteConfirmRequest request) {
        return Result.ok(service.confirm(token, systemId, tenantId, request));
    }

    /** 原操作者按追踪标识读取原始对话和最终写入结果。 */
    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "查询本人智能对话")
    public Result<AiConversationView> conversation(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable String conversationId) {
        return Result.ok(service.conversation(token, systemId, tenantId, conversationId));
    }
}
