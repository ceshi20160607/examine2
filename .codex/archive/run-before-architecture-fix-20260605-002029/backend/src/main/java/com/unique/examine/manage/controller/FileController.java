package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.FileManageService;
import com.unique.examine.manage.vo.*;
import com.unique.examine.manage.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/files")
public class FileController {
    private final FileManageService fileManageService;

    @PostMapping
    public ApiResponse<SimpleVO> createFile(@Valid @RequestBody FileObjectSaveBO bo) { return ApiResponse.ok(fileManageService.createFile(bo)); }
    @GetMapping
    public ApiResponse<PageResult<SimpleVO>> files(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) String status) { return ApiResponse.ok(fileManageService.files(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId), status)); }
    @PostMapping("/relations")
    public ApiResponse<SimpleVO> link(@Valid @RequestBody FileRelationSaveBO bo) { return ApiResponse.ok(fileManageService.link(bo)); }
    @DeleteMapping("/{fileId}")
    public ApiResponse<Boolean> delete(@PathVariable Long fileId) { fileManageService.delete(fileId); return ApiResponse.ok(Boolean.TRUE); }
    @PostMapping("/tasks")
    public ApiResponse<SimpleVO> createTask(@Valid @RequestBody ImportExportTaskSaveBO bo) { return ApiResponse.ok(fileManageService.createImportExportTask(bo)); }
    @GetMapping("/tasks")
    public ApiResponse<PageResult<SimpleVO>> tasks(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) Long moduleId, @RequestParam(required = false) String taskType) { return ApiResponse.ok(fileManageService.importExportTasks(pageNo, pageSize, resolveSystemId(systemId), resolveTenantId(tenantId), moduleId, taskType)); }

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
