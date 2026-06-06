package com.unique.examine.module.service.impl;

import com.unique.examine.core.module.ModuleAuthCacheCoordinator;
import com.unique.examine.module.entity.po.ModuleRolePerm;
import com.unique.examine.module.mapper.ModuleRolePermMapper;
import com.unique.examine.module.service.IModuleRolePermService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 角色权限 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Service
public class ModuleRolePermServiceImpl extends ServiceImpl<ModuleRolePermMapper, ModuleRolePerm> implements IModuleRolePermService {

    @Autowired(required = false)
    private ModuleAuthCacheCoordinator moduleAuthCacheCoordinator;

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    @Override
    public ModuleRolePerm queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-14
     * @param entity entity
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(ModuleRolePerm entity) {
        ModuleRolePerm old = null;
        if (entity != null && entity.getId() != null) {
            old = getById(entity.getId());
        }
        saveOrUpdate(entity);
        if (moduleAuthCacheCoordinator == null) {
            return;
        }
        ModuleRolePerm src = entity != null ? entity : old;
        if (src == null || src.getRoleId() == null) {
            return;
        }
        moduleAuthCacheCoordinator.invalidateForRole(
                src.getSystemId() == null ? 0L : src.getSystemId(),
                src.getTenantId() == null ? 0L : src.getTenantId(),
                src.getRoleId()
        );
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<ModuleRolePerm> queryPageList(PageEntity search) {
        return lambdaQuery().page(search.parse());
    }

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param ids ids
     */
    @Override
    public void deleteByIds(List<Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
              return;
        }
        List<ModuleRolePerm> rows = listByIds(ids);
        removeByIds(ids);
        if (moduleAuthCacheCoordinator == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (ModuleRolePerm rp : rows) {
            if (rp == null || rp.getRoleId() == null) {
                continue;
            }
            moduleAuthCacheCoordinator.invalidateForRole(
                    rp.getSystemId() == null ? 0L : rp.getSystemId(),
                    rp.getTenantId() == null ? 0L : rp.getTenantId(),
                    rp.getRoleId()
            );
        }
    }
}
