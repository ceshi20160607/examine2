package com.unique.unexamine.examine.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.DefinitionView;
import com.unique.unexamine.examine.manage.dto.ExamineDefinitionDto.SaveDefinitionRequest;
import com.unique.unexamine.examine.manage.service.PlatformExamineDefinitionService;
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

/** 平台内审批定义入口；向外提供审批服务时由应用授权控制。 */
@RestController
@RequestMapping("/api/platform/examines")
@Tag(name = "平台审批定义")
public class PlatformExamineDefinitionController {
    private final PlatformExamineDefinitionService service;

    public PlatformExamineDefinitionController(PlatformExamineDefinitionService service) {
        this.service = service;
    }

    /** 查询平台审批定义。 */
    @GetMapping
    @Operation(summary = "查询平台审批")
    public Result<List<DefinitionView>> list(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.list(token));
    }

    /** 查询平台审批详情。 */
    @GetMapping("/{examineId}")
    @Operation(summary = "查询平台审批详情")
    public Result<DefinitionView> query(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long examineId) {
        return Result.ok(service.query(token, examineId));
    }

    /** 保存并立即启用平台审批快照。 */
    @PutMapping
    @Operation(summary = "保存平台审批")
    public Result<DefinitionView> save(@RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody SaveDefinitionRequest request) {
        return Result.ok(service.save(token, request));
    }

    /** 停用平台审批定义。 */
    @PostMapping("/{examineId}/disable")
    @Operation(summary = "停用平台审批")
    public Result<DefinitionView> disable(@RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long examineId) {
        return Result.ok(service.disable(token, examineId));
    }
}
