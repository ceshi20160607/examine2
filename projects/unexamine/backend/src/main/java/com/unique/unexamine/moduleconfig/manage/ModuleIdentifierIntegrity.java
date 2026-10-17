package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.manage.repository.ModuleIdentifierLockRepository;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 模块标识同时用于权限、共享和动作入口，系统内必须唯一；租户扩展复用基础模块身份。 */
@Service
public class ModuleIdentifierIntegrity {
    private final ConfiguredModuleBaseService moduleService;
    private final ModuleIdentifierLockRepository lockRepository;

    public ModuleIdentifierIntegrity(ConfiguredModuleBaseService moduleService,
                                     ModuleIdentifierLockRepository lockRepository) {
        this.moduleService = moduleService;
        this.lockRepository = lockRepository;
    }

    /** 在调用方事务内串行创建及发布，避免两个租户同时检查通过后写入相同标识。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSystem(Long systemId) {
        if (lockRepository.lockSystem(systemId) == null) {
            throw new IllegalStateException("系统不存在，无法锁定模块标识");
        }
    }

    /** 停用标识也保留，防止历史记录、审计和权限引用被新模块接管。 */
    public boolean occupied(Long systemId, String code) {
        return moduleService.selectCount(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, systemId)
                .eq(ConfiguredModule::getCode, code)) > 0;
    }

    /** 旧的重复配置不自动合并或替换；要求先处理冲突，原数据和版本保持不变。 */
    public boolean hasActiveConflict(ConfiguredModule module) {
        return moduleService.selectCount(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, module.getSystemId())
                .eq(ConfiguredModule::getCode, module.getCode())
                .ne(ConfiguredModule::getId, module.getId())
                .in(ConfiguredModule::getStatus, "ACTIVE", "DRAFT")) > 0;
    }

    public void requireUnambiguous(ConfiguredModule module) {
        if (hasActiveConflict(module)) {
            throw new DomainException("MODULE_CODE_CONFLICT", "模块标识存在历史冲突，请先处理重复模块；原数据和版本未改变",
                    HttpStatus.CONFLICT);
        }
    }
}
