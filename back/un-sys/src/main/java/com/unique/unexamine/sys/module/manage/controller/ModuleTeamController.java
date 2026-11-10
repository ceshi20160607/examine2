package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.dto.ModuleTeamDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleTeamDto.View;
import com.unique.unexamine.sys.module.manage.service.ModuleTeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 记录团队只授予当前业务记录的查看或编辑权限。 */
@RestController
@RequestMapping("/api/systems/{systemId}/modules/{moduleId}/records/{recordId}/team")
@Tag(name = "模块记录团队")
public class ModuleTeamController {
    private final ModuleTeamService service;

    public ModuleTeamController(ModuleTeamService service) { this.service = service; }

    /** 读取当前记录额外加入的团队成员。 */
    @GetMapping
    @Operation(summary = "查询记录团队成员")
    public Result<List<View>> list(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId) {
        return Result.ok(service.list(token, systemId, tenantId, moduleId, recordId));
    }

    /** 加入成员或调整该成员的查看、编辑权限。 */
    @PostMapping
    @Operation(summary = "保存记录团队权限")
    public Result<View> save(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId,
            @Valid @RequestBody SaveRequest request) {
        return Result.ok(service.save(token, systemId, tenantId, moduleId, recordId, request));
    }

    /** 撤销成员对该记录的团队权限。 */
    @DeleteMapping("/{accountId}")
    @Operation(summary = "撤销记录团队权限")
    public Result<Void> revoke(@RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long moduleId, @PathVariable Long recordId,
            @PathVariable Long accountId) {
        service.revoke(token, systemId, tenantId, moduleId, recordId, accountId);
        return Result.ok();
    }
}
