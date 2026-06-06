package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.WorkflowManageService;
import com.unique.examine.manage.vo.*;
import com.unique.examine.manage.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/workflow")
public class WorkflowController {
    private final WorkflowManageService workflowManageService;

    @GetMapping("/templates")
    public ApiResponse<PageResult<SimpleVO>> templates(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Long moduleId) { return ApiResponse.ok(workflowManageService.templates(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId), moduleId)); }
    @PostMapping("/templates")
    public ApiResponse<SimpleVO> saveTemplate(@Valid @RequestBody WorkflowTemplateSaveBO bo) { return ApiResponse.ok(workflowManageService.saveTemplate(bo)); }
    @PostMapping("/versions")
    public ApiResponse<SimpleVO> saveVersion(@Valid @RequestBody WorkflowVersionSaveBO bo) { return ApiResponse.ok(workflowManageService.saveVersion(bo)); }
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<SimpleVO> publishVersion(@PathVariable Long versionId) { return ApiResponse.ok(workflowManageService.publishVersion(versionId)); }
    @PostMapping("/instances/start")
    public ApiResponse<SimpleVO> start(@Valid @RequestBody WorkflowStartBO bo) { return ApiResponse.ok(workflowManageService.start(bo)); }
    @GetMapping("/tasks")
    public ApiResponse<PageResult<SimpleVO>> tasks(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long assigneeId, @RequestParam(required = false) String status) { return ApiResponse.ok(workflowManageService.tasks(pageNo, pageSize, assigneeId, status)); }
    @PostMapping("/tasks/{taskId}/handle")
    public ApiResponse<SimpleVO> handle(@PathVariable Long taskId, @Valid @RequestBody WorkflowTaskActionBO bo) { return ApiResponse.ok(workflowManageService.handleTask(taskId, bo)); }

    private Long resolveSystemId(Long systemId) {
        Long resolved = systemId == null ? SecurityContext.currentUser().getSystemId() : systemId;
        if (resolved == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少系统上下文"); }
        return resolved;
    }

    private Long resolveTenantId(Long tenantId) {
        Long resolved = tenantId == null ? SecurityContext.currentUser().getTenantId() : tenantId;
        if (resolved == null) { throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少租户上下文"); }
        return resolved;
    }
}
