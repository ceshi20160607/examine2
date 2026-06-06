package com.unique.examine.web.controller.module;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleExportJob;
import com.unique.examine.module.service.IModuleExportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "自建系统态-module导出任务（查询）")
@RestController
@RequestMapping("/v1/system/module/export-jobs")
public class SystemModuleExportJobQueryController {

    @Autowired
    private IModuleExportJobService moduleExportJobService;

    @Operation(summary = "导出任务分页（按 system/tenant 隔离；可选 tplId/modelId/status）")
    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "tplId", required = false) Long tplId,
            @RequestParam(value = "modelId", required = false) Long modelId,
            @RequestParam(value = "status", required = false) Integer status
    ) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        var q = moduleExportJobService.lambdaQuery()
                .eq(ModuleExportJob::getSystemId, systemId)
                .eq(ModuleExportJob::getTenantId, tenantId);
        if (tplId != null) {
            q.eq(ModuleExportJob::getTplId, tplId);
        }
        if (modelId != null) {
            q.eq(ModuleExportJob::getModelId, modelId);
        }
        if (status != null) {
            q.eq(ModuleExportJob::getStatus, status);
        }

        long total = q.count();
        List<ModuleExportJob> records = total == 0 ? List.of() : q.orderByDesc(ModuleExportJob::getId)
                .last("limit " + s + " offset " + offset)
                .list();

        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }
}

