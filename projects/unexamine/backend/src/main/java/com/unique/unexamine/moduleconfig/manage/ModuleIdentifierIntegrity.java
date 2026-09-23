package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 模块标识同时用于权限、共享和动作入口，系统内必须唯一；租户扩展复用基础模块身份。 */
@Service
public class ModuleIdentifierIntegrity {
    private final JdbcTemplate jdbc;

    public ModuleIdentifierIntegrity(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 在调用方事务内串行创建及发布，避免两个租户同时检查通过后写入相同标识。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSystem(Long systemId) {
        jdbc.queryForObject("SELECT id FROM sys_system WHERE id = ? FOR UPDATE", Long.class, systemId);
    }

    /** 停用标识也保留，防止历史记录、审计和权限引用被新模块接管。 */
    public boolean occupied(Long systemId, String code) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cfg_module WHERE system_id = ? AND code = ?",
                Long.class, systemId, code) > 0;
    }

    /** 旧的重复配置不自动合并或替换；要求先处理冲突，原数据和版本保持不变。 */
    public boolean hasActiveConflict(ConfiguredModule module) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cfg_module WHERE system_id = ? AND code = ?"
                        + " AND id <> ? AND status IN ('ACTIVE', 'DRAFT')", Long.class,
                module.getSystemId(), module.getCode(), module.getId()) > 0;
    }

    public void requireUnambiguous(ConfiguredModule module) {
        if (hasActiveConflict(module)) {
            throw new DomainException("MODULE_CODE_CONFLICT", "模块标识存在历史冲突，请先处理重复模块；原数据和版本未改变",
                    HttpStatus.CONFLICT);
        }
    }
}
