package com.unique.examine.upload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.upload.entity.PO.UploadFile;
import com.unique.examine.upload.entity.PO.UploadStorageConfig;
import com.unique.examine.upload.mapper.UploadFileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UploadFileService {

    private final UploadFileMapper uploadFileMapper;
    private final UploadStorageConfigService storageConfigService;

    public UploadFileService(UploadFileMapper uploadFileMapper, UploadStorageConfigService storageConfigService) {
        this.uploadFileMapper = uploadFileMapper;
        this.storageConfigService = storageConfigService;
    }

    public UploadFile getById(Long id) {
        return uploadFileMapper.selectById(id);
    }

    public List<UploadFile> listLatest(int limit) {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        int l = Math.max(1, Math.min(limit, 200));
        return uploadFileMapper.selectList(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getSystemId, systemId)
                .eq(UploadFile::getTenantId, tenantId)
                .orderByDesc(UploadFile::getCreateTime)
                .last("limit " + l));
    }

    @Transactional(rollbackFor = Exception.class)
    public UploadFile uploadLocal(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        UploadStorageConfig cfg = storageConfigService.getEnabledOrDefaultLocal();
        if (cfg == null || !StringUtils.hasText(cfg.getLocalRootPath())) {
            throw new BusinessException("未配置本地存储（un_upload_storage_config.local_root_path）");
        }

        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        Long platId = AuthContextHolder.getPlatId();

        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int idx = originalName.lastIndexOf('.');
        if (idx >= 0 && idx < originalName.length() - 1) {
            ext = originalName.substring(idx + 1);
        }

        LocalDate today = LocalDate.now();
        String subDir = today.getYear() + File.separator
                + String.format("%02d", today.getMonthValue()) + File.separator
                + String.format("%02d", today.getDayOfMonth());
        String safeName = System.currentTimeMillis() + "_" + Math.abs(originalName.hashCode());
        if (StringUtils.hasText(ext)) {
            safeName = safeName + "." + ext;
        }

        File dir = new File(cfg.getLocalRootPath(), subDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建本地目录失败: " + dir.getAbsolutePath());
        }
        File target = new File(dir, safeName);

        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("保存文件失败: " + e.getMessage());
        }

        UploadFile uf = new UploadFile();
        uf.setSystemId(systemId);
        uf.setTenantId(tenantId);
        uf.setUploaderPlatId(platId);
        uf.setCreateUserId(platId);
        uf.setUpdateUserId(platId);

        uf.setOriginalName(originalName);
        uf.setFileExt(ext);
        uf.setContentType(file.getContentType());
        uf.setFileSize(file.getSize());

        uf.setStorageType("local");
        uf.setStorageConfigId(cfg.getId());
        uf.setLocalAbsPath(target.getAbsolutePath());
        uf.setStatus(1);

        if (StringUtils.hasText(cfg.getLocalPublicBaseUrl())) {
            String base = cfg.getLocalPublicBaseUrl();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String rel = subDir.replace(File.separatorChar, '/') + "/" + safeName;
            uf.setPublicUrl(base + "/" + rel);
        }

        uploadFileMapper.insert(uf);
        return uf;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UploadFile uf = uploadFileMapper.selectById(id);
        if (uf == null) {
            return;
        }
        uf.setStatus(2);
        uf.setUpdateUserId(AuthContextHolder.getPlatId());
        uf.setUpdateTime(LocalDateTime.now());
        uploadFileMapper.updateById(uf);
    }

    public String buildDownloadFilename(UploadFile f) {
        String name = (f == null || !StringUtils.hasText(f.getOriginalName())) ? "file" : f.getOriginalName();
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }
}

