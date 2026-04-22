package com.unique.examine.web.manage.module;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleExportJob;
import com.unique.examine.module.service.IModuleExportJobService;
import com.unique.examine.upload.entity.po.UploadFile;
import com.unique.examine.upload.service.IUploadFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class SystemModuleExportJobRunner {

    @Autowired
    private IModuleExportJobService moduleExportJobService;
    @Autowired
    private SystemModuleExportService systemModuleExportService;
    @Autowired
    private IUploadFileService uploadFileService;
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${examine.upload.local-root-path:data/uploads}")
    private String localRootPath;

    @Scheduled(fixedDelayString = "${examine.export-job.poll-ms:2000}")
    public void pollAndRunOnce() {
        ModuleExportJob job = moduleExportJobService.lambdaQuery()
                .eq(ModuleExportJob::getStatus, 0)
                .orderByAsc(ModuleExportJob::getId)
                .last("limit 1")
                .one();
        if (job == null || job.getId() == null) {
            return;
        }

        boolean claimed = moduleExportJobService.lambdaUpdate()
                .eq(ModuleExportJob::getId, job.getId())
                .eq(ModuleExportJob::getStatus, 0)
                .set(ModuleExportJob::getStatus, 1)
                .update();
        if (!claimed) {
            return;
        }

        try {
            runJob(job);
        } catch (Exception e) {
            moduleExportJobService.lambdaUpdate()
                    .eq(ModuleExportJob::getId, job.getId())
                    .set(ModuleExportJob::getStatus, 3)
                    .set(ModuleExportJob::getErrorMsg, truncateMsg(e.getMessage()))
                    .update();
        } finally {
            AuthContextHolder.clear();
        }
    }

    private void runJob(ModuleExportJob job) throws Exception {
        AuthContextHolder.setPlatId(job.getCreateUserId());
        AuthContextHolder.setSystemId(job.getSystemId());
        AuthContextHolder.setTenantId(job.getTenantId());

        ModuleRecordDslQuery q = null;
        if (StringUtils.hasText(job.getQueryJson())) {
            q = objectMapper.readValue(job.getQueryJson(), ModuleRecordDslQuery.class);
        }

        byte[] bytes = systemModuleExportService.exportCsvBytes(job.getTplId(), job.getCreateUserId(), q);

        LocalDate d = LocalDate.now();
        String dir = d.getYear() + "/" + String.format("%02d", d.getMonthValue()) + "/" + String.format("%02d", d.getDayOfMonth());
        String safeName = UUID.randomUUID().toString().replace("-", "");
        String filename = "export_" + safeName + ".csv";

        String rootStr = StringUtils.hasText(localRootPath) ? localRootPath.trim() : "data/uploads";
        Path root = Paths.get(rootStr).toAbsolutePath().normalize();
        Path targetDir = root.resolve(String.valueOf(job.getSystemId())).resolve(String.valueOf(job.getTenantId()))
                .resolve("exports").resolve(dir).normalize();
        Files.createDirectories(targetDir);
        Path absPath = targetDir.resolve(filename).normalize();
        Files.write(absPath, bytes);

        UploadFile uf = new UploadFile();
        uf.setSystemId(job.getSystemId());
        uf.setTenantId(job.getTenantId());
        uf.setUploaderPlatId(job.getCreateUserId());
        uf.setOriginalName(filename);
        uf.setFileExt("csv");
        uf.setContentType("text/csv");
        uf.setFileSize((long) bytes.length);
        uf.setStorageType("local");
        uf.setLocalAbsPath(absPath.toString());
        uf.setStatus(1);
        uf.setCreateUserId(job.getCreateUserId());
        uf.setUpdateUserId(job.getCreateUserId());
        uploadFileService.save(uf);

        moduleExportJobService.lambdaUpdate()
                .eq(ModuleExportJob::getId, job.getId())
                .set(ModuleExportJob::getStatus, 2)
                .set(ModuleExportJob::getResultFileId, uf.getId())
                .set(ModuleExportJob::getErrorMsg, null)
                .update();
    }

    private static String truncateMsg(String s) {
        if (s == null) {
            return "failed";
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return "failed";
        }
        return t.length() > 500 ? t.substring(0, 500) : t;
    }
}

