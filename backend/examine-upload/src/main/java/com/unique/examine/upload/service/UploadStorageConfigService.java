package com.unique.examine.upload.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.upload.entity.PO.UploadStorageConfig;
import com.unique.examine.upload.mapper.UploadStorageConfigMapper;
import org.springframework.stereotype.Service;

@Service
public class UploadStorageConfigService {

    private final UploadStorageConfigMapper mapper;

    public UploadStorageConfigService(UploadStorageConfigMapper mapper) {
        this.mapper = mapper;
    }

    public UploadStorageConfig getEnabledOrDefaultLocal() {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        UploadStorageConfig cfg = mapper.selectOne(new LambdaQueryWrapper<UploadStorageConfig>()
                .eq(UploadStorageConfig::getSystemId, systemId)
                .eq(UploadStorageConfig::getTenantId, tenantId)
                .eq(UploadStorageConfig::getStorageType, "local")
                .eq(UploadStorageConfig::getStatus, 1)
                .last("limit 1"));
        if (cfg != null) {
            return cfg;
        }
        return mapper.selectOne(new LambdaQueryWrapper<UploadStorageConfig>()
                .eq(UploadStorageConfig::getSystemId, 0L)
                .eq(UploadStorageConfig::getTenantId, 0L)
                .eq(UploadStorageConfig::getStorageType, "local")
                .eq(UploadStorageConfig::getStatus, 1)
                .last("limit 1"));
    }
}

