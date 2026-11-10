package com.unique.unexamine.sys.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.DepartmentRequest;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.DepartmentView;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.MemberSearch;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.MemberView;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.RoleView;
import com.unique.unexamine.sys.manage.service.SystemDirectoryService;
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

/** 系统组织目录入口；保存、停用和查询统一交给 manage Service。 */
@RestController
@RequestMapping("/api/systems/{systemId}/directory")
@Tag(name = "系统组织目录")
public class SystemDirectoryController {
    private final SystemDirectoryService service;

    public SystemDirectoryController(SystemDirectoryService service) {
        this.service = service;
    }

    /** 查询当前租户的有效部门。 */
    @GetMapping("/departments")
    @Operation(summary = "查询部门")
    public Result<List<DepartmentView>> departments(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.departments(token, systemId, tenantId));
    }

    /** 保存部门并立即生效。 */
    @PutMapping("/departments")
    @Operation(summary = "保存部门")
    public Result<DepartmentView> saveDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @Valid @RequestBody DepartmentRequest request) {
        return Result.ok(service.saveDepartment(token, systemId, tenantId, request));
    }

    /** 停用部门；历史成员关系仍保留。 */
    @PostMapping("/departments/{departmentId}/disable")
    @Operation(summary = "停用部门")
    public Result<DepartmentView> disableDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long departmentId) {
        return Result.ok(service.disableDepartment(token, systemId, tenantId, departmentId));
    }

    /** 查询当前租户成员和部门关系。 */
    @GetMapping("/members")
    @Operation(summary = "查询租户成员")
    public Result<Page<MemberView>> members(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId, @org.springframework.web.bind.annotation.ModelAttribute MemberSearch search) {
        return Result.ok(service.members(token, systemId, tenantId, search));
    }

    /** 查询当前租户角色。 */
    @GetMapping("/roles")
    @Operation(summary = "查询租户角色")
    public Result<List<RoleView>> roles(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) Long tenantId,
            @PathVariable Long systemId) {
        return Result.ok(service.roles(token, systemId, tenantId));
    }
}
