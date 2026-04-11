package com.unique.examine.module.service.impl;

import com.unique.examine.module.entity.po.ModuleRole;
import com.unique.examine.module.mapper.ModuleRoleMapper;
import com.unique.examine.module.service.IModuleRoleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 应用角色 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Service
public class ModuleRoleServiceImpl extends ServiceImpl<ModuleRoleMapper, ModuleRole> implements IModuleRoleService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-10
     * @param id 主键ID
     * @return data
     */
    @Override
    public ModuleRole queryById(Serializable id) {
        return getById(id);
    }

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-10
     * @param entity entity
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addOrUpdate(ModuleRole entity) {
        saveOrUpdate(entity);
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<ModuleRole> queryPageList(PageEntity search) {
        return lambdaQuery().page(search.parse());
    }

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-10
     * @param ids ids
     */
    @Override
    public void deleteByIds(List<Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
              return;
        }
        removeByIds(ids);
    }
}
