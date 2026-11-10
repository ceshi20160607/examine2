package com.unique.unexamine.plat.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.core.ops.OperationsHealthService;
import com.unique.unexamine.plat.manage.service.PlatformOperationsService;
import com.unique.unexamine.plat.manage.service.PlatformBackupService;
import com.unique.unexamine.plat.manage.dto.PlatformBackupDto;
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

/** 平台运维入口。 */
@RestController
@RequestMapping("/api/platform/operations")
@Tag(name="平台运维")
public class PlatformOperationsController {
    private final PlatformOperationsService service;
    private final PlatformBackupService backups;
    public PlatformOperationsController(PlatformOperationsService service,PlatformBackupService backups) {
        this.service=service; this.backups=backups;
    }

    @GetMapping("/health")
    @Operation(summary="体检数据库、Redis、文件存储和部署配置")
    public Result<OperationsHealthService.Snapshot> health(
            @RequestHeader(value="Authorization",required=false) String token) {
        return Result.ok(service.inspect(token));
    }

    @GetMapping("/backups")
    @Operation(summary="查询 SQL 运维备份")
    public Result<Page<PlatformBackupDto.View>> backups(@RequestHeader(value="Authorization",required=false) String token,
            @ModelAttribute SearchBO search) {
        return Result.ok(backups.list(token,search));
    }

    @PostMapping("/backups")
    @Operation(summary="导出平台项目整库表结构和数据为 SQL")
    public Result<PlatformBackupDto.View> backup(@RequestHeader(value="Authorization",required=false) String token) {
        return Result.ok(backups.create(token));
    }

    @GetMapping("/backups/{backupId}/download")
    @Operation(summary="下载平台项目整库 SQL 备份")
    public ResponseEntity<FileSystemResource> download(@RequestHeader(value="Authorization",required=false) String token,
            @PathVariable Long backupId) throws IOException {
        var file=backups.download(token,backupId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/sql"))
                .contentLength(java.nio.file.Files.size(file))
                .header(HttpHeaders.CACHE_CONTROL,"no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=platform-backup-"+backupId+".sql")
                .body(new FileSystemResource(file));
    }
}
