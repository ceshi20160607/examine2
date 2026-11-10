package com.unique.unexamine.sys.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.service.SystemOperationsService;
import com.unique.unexamine.sys.manage.service.SystemSqlBackupService;
import com.unique.unexamine.sys.manage.dto.SystemBackupDto;
import com.unique.unexamine.core.bo.SearchBO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.io.IOException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/** 系统后台运维入口，与平台作用域分开授权。 */
@RestController
@RequestMapping("/api/systems/{systemId}/operations")
@Tag(name="系统运维")
public class SystemOperationsController {
    private final SystemOperationsService service;
    private final SystemSqlBackupService backups;
    public SystemOperationsController(SystemOperationsService service,SystemSqlBackupService backups) {
        this.service=service; this.backups=backups;
    }

    @GetMapping("/health")
    @Operation(summary="查询系统体检和租户作业状态")
    public Result<SystemOperationsService.Snapshot> health(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.inspect(token,systemId,tenantId));
    }

    @GetMapping("/backups")
    @Operation(summary="查询本系统 SQL 备份")
    public Result<Page<SystemBackupDto.View>> backups(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@ModelAttribute SearchBO search) {
        return Result.ok(backups.list(token,systemId,tenantId,search));
    }

    @PostMapping("/backups")
    @Operation(summary="导出本系统表结构和业务数据为 SQL")
    public Result<SystemBackupDto.View> backup(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId) {
        return Result.ok(backups.create(token,systemId,tenantId));
    }

    @GetMapping("/backups/{backupId}/download")
    @Operation(summary="下载本系统 SQL 备份")
    public ResponseEntity<FileSystemResource> download(
            @RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long backupId) throws IOException {
        var file=backups.download(token,systemId,tenantId,backupId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/sql"))
                .contentLength(java.nio.file.Files.size(file))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=system-"+systemId+"-backup-"+backupId+".sql")
                .body(new FileSystemResource(file));
    }
}
