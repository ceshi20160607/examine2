package com.unique.examine.manage.service.impl;

import com.unique.examine.base.entity.AuditLog;
import com.unique.examine.base.entity.GlobalConfig;
import com.unique.examine.base.service.IAuditLogService;
import com.unique.examine.base.service.IGlobalConfigService;
import com.unique.examine.manage.bo.GlobalConfigSaveBO;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.service.OpsManageService;
import com.unique.examine.manage.vo.HealthVO;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.SimpleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpsManageServiceImpl implements OpsManageService {
    private final IGlobalConfigService configService;
    private final IAuditLogService auditLogService;
    private final EntityMapConverter converter;

    @Override
    public HealthVO health() {
        HealthVO vo = new HealthVO();
        vo.setServiceStatus("UP");
        try { configService.count(); vo.setDatabaseStatus("UP"); } catch (Exception ex) { vo.setDatabaseStatus("DOWN"); }
        vo.setRedisStatus("NOT_CONFIGURED");
        vo.setStorageStatus(configService.count(Wrappers.<GlobalConfig>lambdaQuery().eq(GlobalConfig::getConfigKey, "STORAGE_HEALTH_CHECK").eq(GlobalConfig::getStatus, StatusEnums.ENABLED)) > 0 ? "CONFIGURED" : "NOT_CONFIGURED");
        vo.setScriptVersionStatus(configService.count(Wrappers.<GlobalConfig>lambdaQuery().eq(GlobalConfig::getConfigKey, "SCRIPT_VERSION_CHECK").eq(GlobalConfig::getStatus, StatusEnums.ENABLED)) > 0 ? "CONFIGURED" : "NOT_CONFIGURED");
        return vo;
    }

    @Override
    public PageResult<SimpleVO> auditLogs(long pageNo, long pageSize, Long systemId, Long tenantId, String actionType) {
        IPage<AuditLog> page = auditLogService.page(Page.of(pageNo, pageSize), Wrappers.<AuditLog>lambdaQuery().eq(systemId != null, AuditLog::getSystemId, systemId).eq(tenantId != null, AuditLog::getTenantId, tenantId).eq(actionType != null && !actionType.isBlank(), AuditLog::getActionType, actionType).orderByDesc(AuditLog::getCreatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    public PageResult<SimpleVO> configs(long pageNo, long pageSize, Long systemId, Long tenantId) {
        IPage<GlobalConfig> page = configService.page(Page.of(pageNo, pageSize), Wrappers.<GlobalConfig>lambdaQuery().eq(systemId != null, GlobalConfig::getSystemId, systemId).eq(tenantId != null, GlobalConfig::getTenantId, tenantId).orderByDesc(GlobalConfig::getUpdatedAt));
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveConfig(GlobalConfigSaveBO bo) {
        GlobalConfig config = new GlobalConfig();
        config.setSystemId(bo.getSystemId() == null ? 0L : bo.getSystemId()); config.setTenantId(bo.getTenantId() == null ? 0L : bo.getTenantId()); config.setConfigKey(bo.getConfigKey()); config.setConfigValue(bo.getConfigValue()); config.setSecretPlaceholderFlag(bo.getSecretPlaceholderFlag() == null ? 0 : bo.getSecretPlaceholderFlag()); config.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        configService.save(config);
        return converter.toSimple(config);
    }
}
