package com.unique.examine.manage.controller;

import com.unique.examine.manage.bo.GlobalConfigSaveBO;
import com.unique.examine.manage.service.OpsManageService;
import com.unique.examine.manage.vo.*;
import com.unique.examine.manage.vo.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops")
public class OpsController {
    private final OpsManageService opsManageService;

    @GetMapping("/health")
    public ApiResponse<HealthVO> health() { return ApiResponse.ok(opsManageService.health()); }
    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<SimpleVO>> auditLogs(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId, @RequestParam(required = false) String actionType) { return ApiResponse.ok(opsManageService.auditLogs(pageNo, pageSize, systemId, tenantId, actionType)); }
    @GetMapping("/configs")
    public ApiResponse<PageResult<SimpleVO>> configs(@RequestParam(defaultValue = "1") long pageNo, @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) Long systemId, @RequestParam(required = false) Long tenantId) { return ApiResponse.ok(opsManageService.configs(pageNo, pageSize, systemId, tenantId)); }
    @PostMapping("/configs")
    public ApiResponse<SimpleVO> saveConfig(@Valid @RequestBody GlobalConfigSaveBO bo) { return ApiResponse.ok(opsManageService.saveConfig(bo)); }
}
