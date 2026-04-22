package com.unique.examine.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleExportJob;
import com.unique.examine.module.entity.po.ModuleExportTpl;
import com.unique.examine.module.service.IModuleExportJobService;
import com.unique.examine.module.service.IModuleExportTplService;
import com.unique.examine.upload.entity.po.UploadFile;
import com.unique.examine.upload.service.IUploadFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class SystemModuleExportJobService {

    @Autowired
    private IModuleExportTplService moduleExportTplService;
    @Autowired
    private IModuleExportJobService moduleExportJobService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ModuleRecordFacadeService moduleRecordFacadeService;
    @Autowired
    private IUploadFileService uploadFileService;

    public ModuleExportJob createJob(Long tplId, Long operatorPlatId, String queryJson) {
        if (operatorPlatId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (tplId == null || tplId <= 0L) {
            throw new BusinessException(400, "tplId 不能为空");
        }
        ModuleExportTpl tpl = moduleExportTplService.getById(tplId);
        if (tpl == null) {
            throw new BusinessException(404, "tpl 不存在");
        }
        if (!Objects.equals(tpl.getSystemId(), systemId) || !Objects.equals(tpl.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 tpl");
        }

        // validate DSL query early (fast fail) if provided
        if (queryJson != null && !queryJson.isBlank()) {
            try {
                ModuleRecordDslQuery q = objectMapper.readValue(queryJson, ModuleRecordDslQuery.class);
                moduleRecordFacadeService.prepareDslQuery(q, tpl.getAppId(), tpl.getModelId(), 2000L);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(400, "queryJson 非法");
            }
        }

        ModuleExportJob job = new ModuleExportJob();
        job.setSystemId(systemId);
        job.setTenantId(tenantId);
        job.setAppId(tpl.getAppId());
        job.setModelId(tpl.getModelId());
        job.setTplId(tpl.getId());
        job.setFileType("csv");
        job.setStatus(0);
        job.setQueryJson(queryJson);
        job.setCreateUserId(operatorPlatId);
        job.setUpdateUserId(operatorPlatId);
        moduleExportJobService.save(job);
        return job;
    }

    public ModuleExportJob getJob(Long jobId, Long operatorPlatId) {
        if (operatorPlatId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (jobId == null || jobId <= 0L) {
            throw new BusinessException(400, "jobId 不能为空");
        }
        ModuleExportJob job = moduleExportJobService.getById(jobId);
        if (job == null) {
            throw new BusinessException(404, "job 不存在");
        }
        if (!Objects.equals(job.getSystemId(), systemId) || !Objects.equals(job.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 job");
        }
        return job;
    }

    public Map<String, Object> getJobDetail(Long jobId, Long operatorPlatId) {
        ModuleExportJob job = getJob(jobId, operatorPlatId);
        Map<String, Object> m = new HashMap<>();
        m.put("job", job);
        if (job.getResultFileId() != null && job.getResultFileId() > 0) {
            UploadFile uf = uploadFileService.getById(job.getResultFileId());
            // scope check (avoid leaking cross-scope files)
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
        return m;
    }
}

