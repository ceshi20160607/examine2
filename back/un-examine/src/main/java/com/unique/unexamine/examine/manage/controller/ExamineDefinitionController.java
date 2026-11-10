package com.unique.unexamine.examine.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.DefinitionView;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.SaveDefinitionRequest;
import com.unique.unexamine.examine.manage.service.ExamineDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 审批设计器管理入口；保存即启用新快照，运行实例继续使用发起时快照。 */
@RestController
@RequestMapping("/api/systems/{systemId}/examines")
@Tag(name = "审批定义管理")
public class ExamineDefinitionController {
    private final ExamineDefinitionService service;

    public ExamineDefinitionController(ExamineDefinitionService service) {
        this.service = service;
    }

    /** 查询当前系统租户的审批定义。 */
    @GetMapping
    @Operation(summary = "查询审批定义")
    public Result<List<DefinitionView>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.list(token, systemId, tenantId));
    }

    /** 查询审批定义和当前发布快照。 */
    @GetMapping("/{examineId}")
    @Operation(summary = "查询审批定义详情")
    public Result<DefinitionView> query(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long examineId) {
        return Result.ok(service.query(token, systemId, tenantId, examineId));
    }

    /** 校验并立即启用新的审批发布快照。 */
    @PutMapping
    @Operation(summary = "保存并启用审批定义")
    public Result<DefinitionView> save(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody SaveDefinitionRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, request));
    }

    /** 停用审批定义；历史审批实例不受影响。 */
    @PostMapping("/{examineId}/disable")
    @Operation(summary = "停用审批定义")
    public Result<DefinitionView> disable(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long examineId) {
        return Result.ok(service.disable(token, systemId, tenantId, examineId));
    }
}
