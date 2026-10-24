package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Backup;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Check;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.DeleteRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Deleted;
import com.unique.unexamine.sys.module.manage.service.ModuleDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/** 模块删除先检查，导出完成并确认后再提交删除选择。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}")
@Tag(name="模块删除")
public class ModuleDeletionController {
    private final ModuleDeletionService service;
    /** 注入删除业务服务。 */
    public ModuleDeletionController(ModuleDeletionService service) { this.service=service; }

    /** 返回当前业务数据数量，供页面决定是否显示删除方式选择。 */
    @GetMapping("/deletion-check")
    @Operation(summary="检查模块删除影响")
    public Result<Check> check(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) {
        return Result.ok(service.check(token,systemId,tenantId,moduleId));
    }

    /** 先生成完整备份，使用返回的 fileId 从公共文件下载接口取得文件。 */
    @PostMapping("/deletion-export")
    @Operation(summary="导出删除前完整业务数据")
    public Result<Backup> export(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId) throws IOException {
        return Result.ok(service.export(token,systemId,tenantId,moduleId));
    }

    /** EXPORTED 表示调用者已确认备份下载；DIRECT 表示明确选择直接删除。 */
    @DeleteMapping
    @Operation(summary="逻辑删除模块及所属业务数据")
    public Result<Deleted> delete(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,
            @PathVariable Long systemId,@PathVariable Long moduleId,
            @RequestBody(required=false) DeleteRequest request) throws IOException {
        return Result.ok(service.delete(token,systemId,tenantId,moduleId,request));
    }
}
