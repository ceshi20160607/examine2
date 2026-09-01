package com.unique.unexamine.operations.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/operations/continuity")
public class OperationsContinuityController {
    private final OperationsContinuityService service;

    public OperationsContinuityController(OperationsContinuityService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "BACKUP", actionCode = "READ")
    public ApiResult<OperationsContinuityModels.Overview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/backups")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "BACKUP", actionCode = "MANAGE")
    public ApiResult<OperationsContinuityModels.BackupView> createBackup(
            @Valid @RequestBody OperationsContinuityModels.CreateBackupRequest input, HttpServletRequest request) {
        return ApiResult.ok(service.createBackup(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/backups/{backupId}/manifest")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "BACKUP", actionCode = "DOWNLOAD")
    public ApiResult<OperationsContinuityModels.BackupManifest> manifest(
            @PathVariable Long backupId, HttpServletRequest request) {
        return ApiResult.ok(service.manifest(AuthenticationContextHolder.require(), backupId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/backups/{backupId}/restore-drills")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "RESTORE", actionCode = "MANAGE")
    public ApiResult<OperationsContinuityModels.RestoreDrillView> restoreDrill(
            @PathVariable Long backupId,
            @Valid @RequestBody OperationsContinuityModels.RestoreDrillRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.restoreDrill(AuthenticationContextHolder.require(), backupId, input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/upgrades/preflight")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "UPGRADE", actionCode = "MANAGE")
    public ApiResult<OperationsContinuityModels.UpgradePreflight> upgradePreflight(
            @Valid @RequestBody OperationsContinuityModels.UpgradeRequest input) {
        return ApiResult.ok(service.upgradePreflight(AuthenticationContextHolder.require(), input));
    }

    @PostMapping("/upgrades")
    @RequirePermission(resourceType = "OPERATIONS", resourceCode = "UPGRADE", actionCode = "MANAGE")
    public ApiResult<OperationsContinuityModels.UpgradeView> upgrade(
            @Valid @RequestBody OperationsContinuityModels.UpgradeRequest input, HttpServletRequest request) {
        return ApiResult.ok(service.upgrade(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }
}
