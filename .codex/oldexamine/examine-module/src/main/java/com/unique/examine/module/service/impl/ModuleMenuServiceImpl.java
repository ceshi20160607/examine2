package com.unique.examine.module.service.impl;

import com.unique.examine.core.module.ModuleMenuAclRuntimeCache;
import com.unique.examine.module.entity.po.ModuleMenu;
import com.unique.examine.module.mapper.ModuleMenuMapper;
import com.unique.examine.module.service.IModuleMenuService;
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
 * 应用菜单（权限与接口门统一挂菜单） 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Service
public class ModuleMenuServiceImpl extends ServiceImpl<ModuleMenuMapper, ModuleMenu> implements IModuleMenuService {

    @Autowired(required = false)
    private ModuleMenuAclRuntimeCache moduleMenuAclRuntimeCache;

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    @Override
    public ModuleMenu queryById(Serializable id) {
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
    public void addOrUpdate(ModuleMenu entity) {
        Long appId = entity == null ? null : entity.getAppId();
        if (appId == null && entity != null && entity.getId() != null) {
            ModuleMenu old = getById(entity.getId());
            appId = old == null ? null : old.getAppId();
        }
        saveOrUpdate(entity);
        if (moduleMenuAclRuntimeCache != null && appId != null) {
            moduleMenuAclRuntimeCache.evictByAppId(appId);
        }
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<ModuleMenu> queryPageList(PageEntity search) {
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
        List<ModuleMenu> rows = listByIds(ids);
        removeByIds(ids);
        if (moduleMenuAclRuntimeCache == null || rows == null || rows.isEmpty()) {
            return;
        }
        for (ModuleMenu m : rows) {
            if (m != null && m.getAppId() != null) {
                moduleMenuAclRuntimeCache.evictByAppId(m.getAppId());
            }
        }
    }
}
