package com.unique.examine.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleExportJob;
import com.unique.examine.upload.entity.po.UploadFile;
import com.unique.examine.upload.service.IUploadFileService;
import com.unique.examine.web.service.SystemModuleExportJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

@Tag(name = "自建系统态-module导出任务")
@RestController
@RequestMapping("/v1/system/module/export-jobs")
public class SystemModuleExportJobController {

    @Autowired
    private SystemModuleExportJobService exportJobService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IUploadFileService uploadFileService;

    @Operation(summary = "创建导出任务（按 tplId；异步生成文件）")
    @PostMapping("/tpls/{tplId}")
    public ApiResult<Map<String, Object>> create(@PathVariable("tplId") Long tplId,
                                                 @RequestBody(required = false) ModuleRecordDslQuery query) {
        Long platId = AuthContextHolder.getPlatId();
        ModuleExportJob job = exportJobService.createJob(tplId, platId, query == null ? null : safeQueryJson(query));
        return ApiResult.ok(Map.of("jobId", job.getId(), "job", job));
    }

    @Operation(summary = "查询导出任务")
    @GetMapping("/{jobId}")
    public ApiResult<Map<String, Object>> get(@PathVariable("jobId") Long jobId) {
        Long platId = AuthContextHolder.getPlatId();
        ModuleExportJob job = exportJobService.getJob(jobId, platId);
        Map<String, Object> m = new HashMap<>();
        m.put("job", job);
        if (job.getResultFileId() != null && job.getResultFileId() > 0) {
            UploadFile uf = uploadFileService.getById(job.getResultFileId());
            if (uf != null
                    && Objects.equals(uf.getSystemId(), job.getSystemId())
                    && Objects.equals(uf.getTenantId(), job.getTenantId())
                    && uf.getStatus() != null
                    && uf.getStatus() == 1) {
                m.put("file", uf);
                m.put("viewUrl", "/v1/system/uploads/" + uf.getId() + "/view");
                m.put("downloadUrl", "/v1/system/uploads/" + uf.getId() + "/download");
            }
        }
        return ApiResult.ok(m);
    }

    private String safeQueryJson(ModuleRecordDslQuery query) {
        try {
            return objectMapper.writeValueAsString(query);
        } catch (Exception e) {
            throw new BusinessException(400, "query 序列化失败");
        }
    }
}

