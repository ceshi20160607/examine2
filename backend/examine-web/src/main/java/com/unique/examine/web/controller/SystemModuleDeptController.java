package com.unique.examine.web.controller;

import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleDept;
import com.unique.examine.module.manage.SystemModuleDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "自建系统态-module部门")
@RestController
@RequestMapping("/v1/system/module/depts")
public class SystemModuleDeptController {

    @Autowired
    private SystemModuleDeptService systemModuleDeptService;

    @Operation(summary = "部门列表")
    @GetMapping("/apps/{appId}")
    public ApiResult<List<ModuleDept>> list(@PathVariable Long appId) {
        return ApiResult.ok(systemModuleDeptService.listDepts(appId, AuthContextHolder.getPlatId()));
    }

    @Operation(summary = "部门选择项（DEPARTMENT 字段）")
    @GetMapping("/apps/{appId}/picker")
    public ApiResult<List<Map<String, Object>>> picker(@PathVariable Long appId) {
        return ApiResult.ok(systemModuleDeptService.listPickerOptions(appId, AuthContextHolder.getPlatId()));
    }

    public record UpsertDeptBody(
            Long id,
            Long parentId,
            String deptCode,
            String deptName,
            Integer sortNo,
            Integer status,
            String remark
    ) {}

    @Operation(summary = "新增/更新部门")
    @PostMapping("/apps/{appId}/upsert")
    public ApiResult<ModuleDept> upsert(@PathVariable Long appId, @RequestBody UpsertDeptBody body) {
        return ApiResult.ok(systemModuleDeptService.upsertDept(
                appId,
                AuthContextHolder.getPlatId(),
                new SystemModuleDeptService.UpsertDeptCmd(
                        body.id(), body.parentId(), body.deptCode(), body.deptName(),
                        body.sortNo(), body.status(), body.remark()
                )));
    }

    public record DeleteIdsBody(List<Long> ids) {}

    @Operation(summary = "删除部门")
    @PostMapping("/delete")
    public ApiResult<Void> delete(@RequestBody DeleteIdsBody body) {
        systemModuleDeptService.deleteDepts(AuthContextHolder.getPlatId(), body == null ? null : body.ids());
        return ApiResult.ok();
    }
}
