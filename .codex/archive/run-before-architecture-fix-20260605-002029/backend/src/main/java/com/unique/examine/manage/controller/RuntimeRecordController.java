package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.BusinessRecordSaveBO;
import com.unique.examine.manage.bo.RecordCommentSaveBO;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.security.SecurityContext;
import com.unique.examine.manage.service.RuntimeRecordManageService;
import com.unique.examine.manage.vo.ApiResponse;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.RecordVO;
import com.unique.examine.manage.vo.SimpleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/records")
public class RuntimeRecordController {
    private final RuntimeRecordManageService runtimeRecordManageService;

    @GetMapping
    public ApiResponse<PageResult<RecordVO>> records(@RequestParam(defaultValue = "1") long pageNo,
                                                     @RequestParam(defaultValue = "20") long pageSize,
                                                     @RequestParam(required = false) Long systemId,
                                                     @RequestParam(required = false) Long tenantId,
                                                     @RequestParam(required = false) Long appId,
                                                     @RequestParam Long moduleId,
                                                     @RequestParam(required = false) String recordNo,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String recordStatus) {
        String resolvedStatus = (status == null || status.isBlank()) ? recordStatus : status;
        return ApiResponse.ok(runtimeRecordManageService.records(pageNo, pageSize,
                resolveSystemId(systemId), resolveTenantId(tenantId), appId, moduleId, recordNo, resolvedStatus));
    }

    @PostMapping
    public ApiResponse<RecordVO> create(@Valid @RequestBody BusinessRecordSaveBO bo) {
        return ApiResponse.ok(runtimeRecordManageService.create(bo));
    }

    @PutMapping("/{recordId}")
    public ApiResponse<RecordVO> update(@PathVariable Long recordId, @Valid @RequestBody BusinessRecordSaveBO bo) {
        return ApiResponse.ok(runtimeRecordManageService.update(recordId, bo));
    }

    @GetMapping("/{recordId}")
    public ApiResponse<RecordVO> detail(@PathVariable Long recordId) {
        return ApiResponse.ok(runtimeRecordManageService.detail(recordId));
    }

    @DeleteMapping("/{recordId}")
    public ApiResponse<Boolean> delete(@PathVariable Long recordId) {
        runtimeRecordManageService.delete(recordId);
        return ApiResponse.ok(Boolean.TRUE);
    }

    @PostMapping("/comments")
    public ApiResponse<SimpleVO> comment(@Valid @RequestBody RecordCommentSaveBO bo) {
        return ApiResponse.ok(runtimeRecordManageService.comment(bo));
    }

    private Long resolveSystemId(Long systemId) {
        Long resolved = systemId == null ? SecurityContext.currentUser().getSystemId() : systemId;
        if (resolved == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少系统上下文");
        }
        return resolved;
    }

    private Long resolveTenantId(Long tenantId) {
        Long resolved = tenantId == null ? SecurityContext.currentUser().getTenantId() : tenantId;
        if (resolved == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少租户上下文");
        }
        return resolved;
    }
}
