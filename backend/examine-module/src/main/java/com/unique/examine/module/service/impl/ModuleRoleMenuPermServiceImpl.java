package com.unique.examine.module.service.impl;

import com.unique.examine.core.module.ModuleAuthCacheCoordinator;
import com.unique.examine.module.entity.po.ModuleRoleMenuPerm;
import com.unique.examine.module.mapper.ModuleRoleMenuPermMapper;
import com.unique.examine.module.service.IModuleRoleMenuPermService;
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
 * 角色菜单权限 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Service
public class ModuleRoleMenuPermServiceImpl extends ServiceImpl<ModuleRoleMenuPermMapper, ModuleRoleMenuPerm> implements IModuleRoleMenuPermService {

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
    public ModuleRoleMenuPerm queryById(Serializable id) {
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
    public void addOrUpdate(ModuleRoleMenuPerm entity) {
        ModuleRoleMenuPerm old = null;
        if (entity != null && entity.getId() != null) {
            old = getById(entity.getId());
        }
        saveOrUpdate(entity);
        if (moduleAuthCacheCoordinator == null) {
            return;
        }
        ModuleRoleMenuPerm src = entity != null ? entity : old;
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
    public BasePage<ModuleRoleMenuPerm> queryPageList(PageEntity search) {
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
        List<ModuleRoleMenuPerm> rows = listByIds(ids);
        removeByIds(ids);
        if (moduleAuthCacheCoordinator == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (ModuleRoleMenuPerm rp : rows) {
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
