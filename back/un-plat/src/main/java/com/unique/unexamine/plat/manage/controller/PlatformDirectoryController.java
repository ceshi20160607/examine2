package com.unique.unexamine.plat.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.DepartmentRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.DepartmentView;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberSearch;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberView;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleView;
import com.unique.unexamine.plat.manage.service.PlatformDirectoryManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 平台组织目录接口；请求接收后统一转交平台 manage Service。 */
@RestController
@RequestMapping("/api/platform/directory")
@Tag(name = "平台组织目录")
public class PlatformDirectoryController {
    private final PlatformDirectoryManageService service;

    public PlatformDirectoryController(PlatformDirectoryManageService service) {
        this.service = service;
    }

    /** 查询平台有效部门。 */
    @GetMapping("/departments")
    @Operation(summary = "查询平台部门")
    public Result<List<DepartmentView>> departments(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.departments(token));
    }

    /** 保存平台部门并立即生效。 */
    @PutMapping("/departments")
    @Operation(summary = "保存平台部门")
    public Result<DepartmentView> saveDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody DepartmentRequest request) {
        return Result.ok(service.saveDepartment(token, request));
    }

    /** 停用平台部门。 */
    @PostMapping("/departments/{departmentId}/disable")
    @Operation(summary = "停用平台部门")
    public Result<DepartmentView> disableDepartment(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long departmentId) {
        return Result.ok(service.disableDepartment(token, departmentId));
    }

    /** 分页查询平台成员。 */
    @GetMapping("/members")
    @Operation(summary = "查询平台成员")
    public Result<Page<MemberView>> members(
            @RequestHeader(value = "Authorization", required = false) String token,
            @ModelAttribute MemberSearch search) {
        return Result.ok(service.members(token, search));
    }

    /** 保存平台成员关系。 */
    @PutMapping("/members")
    @Operation(summary = "保存平台成员")
    public Result<MemberView> saveMember(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody MemberRequest request) {
        return Result.ok(service.saveMember(token, request));
    }

    /** 查询平台角色摘要。 */
    @GetMapping("/roles")
    @Operation(summary = "查询平台角色")
    public Result<List<RoleView>> roles(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.ok(service.roles(token));
    }
}
