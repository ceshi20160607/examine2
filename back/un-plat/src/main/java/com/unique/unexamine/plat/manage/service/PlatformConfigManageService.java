package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatConfig;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.service.IUnPlatConfigService;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.ConfigRequest;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.ConfigSearch;
import com.unique.unexamine.plat.manage.dto.PlatformManageDto.ConfigView;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台配置管理；只有平台超级管理员可修改，保存后立即成为当前配置。 */
@Service
public class PlatformConfigManageService {
    private final AuthManageService auth;
    private final IUnPlatConfigService configs;
    private final IUnPlatLogOptService logs;

    /** 注入平台管理员校验、配置基础服务和平台审计日志。 */
    public PlatformConfigManageService(AuthManageService auth, IUnPlatConfigService configs,
                                       IUnPlatLogOptService logs) {
        this.auth = auth;
        this.configs = configs;
        this.logs = logs;
    }

    /** 按配置键和状态分页查询平台配置。 */
    public Page<ConfigView> query(String token, ConfigSearch search) {
        auth.requirePlatformAdmin(token);
        ConfigSearch criteria = search == null ? new ConfigSearch() : search;
        String keyword = criteria.getKeyword() == null ? null : criteria.getKeyword().trim();
        if (keyword != null && keyword.length() > 100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "配置键搜索内容过长");
        var where = new LambdaQueryWrapper<UnPlatConfig>().orderByDesc(UnPlatConfig::getId);
        if (keyword != null && !keyword.isBlank()) where.like(UnPlatConfig::getConfigKey, keyword);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = normalizeStatus(criteria.getStatus());
            where.eq(UnPlatConfig::getStatus, status);
        }
        Page<UnPlatConfig> page = configs.page(criteria.parse(), where);
        return new Page<ConfigView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /** 新增或修改平台配置，配置键不允许改作其他含义。 */
    @Transactional(rollbackFor = Exception.class)
    public ConfigView save(String token, ConfigRequest request) {
        var account = auth.requirePlatformAdmin(token);
        if (request == null || request.configKey() == null || request.configKey().isBlank()
                || request.configKey().length() > 100 || request.configValue() == null
                || request.configValue().isBlank() || request.configValue().length() > 20000
                || request.status() == null || request.status().isBlank() || request.status().length() > 20)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "配置内容无效");
        String key = request.configKey().trim().toLowerCase(Locale.ROOT);
        if (!key.matches("[a-z][a-z0-9_.-]{1,99}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "配置键格式无效");
        String status = normalizeStatus(request.status());
        UnPlatConfig config;
        if (request.id() == null) {
            config = new UnPlatConfig();
            config.setConfigKey(key);
            config.setCreateTime(LocalDateTime.now());
        } else {
            config = configs.queryById(request.id());
            if (config == null) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台配置不存在");
            }
            if (!key.equals(config.getConfigKey()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "配置键创建后不能修改");
        }
        config.setConfigValue(request.configValue());
        config.setStatus(status);
        config.setUpdateAccountId(account.getId());
        config.setUpdateTime(LocalDateTime.now());
        try { configs.addOrUpdate(config); }
        catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "配置键已存在");
        }
        audit(account.getId(), request.id() == null ? "PLATFORM_CONFIG_CREATE" : "PLATFORM_CONFIG_UPDATE", config.getId());
        return view(config);
    }

    /** 读取已启用的配置值，供平台内部能力使用；不存在时返回空。 */
    public String activeValue(String key) {
        if (key == null || key.isBlank()) return null;
        UnPlatConfig config = configs.getOne(new LambdaQueryWrapper<UnPlatConfig>()
                .eq(UnPlatConfig::getConfigKey, key.trim().toLowerCase(Locale.ROOT))
                .eq(UnPlatConfig::getStatus, "ACTIVE"), false);
        return config == null ? null : config.getConfigValue();
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "配置状态无效");
        return value;
    }

    private ConfigView view(UnPlatConfig config) {
        return new ConfigView(config.getId(), config.getConfigKey(), config.getConfigValue(), config.getStatus(),
                config.getUpdateAccountId(), config.getUpdateTime());
    }

    private void audit(Long accountId, String action, Long objectId) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId);
        log.setActionCode(action);
        log.setObjectType("PLATFORM_CONFIG");
        log.setObjectId(objectId);
        log.setCreateTime(LocalDateTime.now());
        logs.addOrUpdate(log);
    }
}
