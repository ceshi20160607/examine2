package com.unique.examine.module.service.impl;

import com.unique.examine.module.entity.po.ModuleListFilterTpl;
import com.unique.examine.module.mapper.ModuleListFilterTplMapper;
import com.unique.examine.module.service.IModuleListFilterTplService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 列表筛选模板（可按菜单覆盖） 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
@Service
public class ModuleListFilterTplServiceImpl extends ServiceImpl<ModuleListFilterTplMapper, ModuleListFilterTpl> implements IModuleListFilterTplService {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    @Override
    public ModuleListFilterTpl queryById(Serializable id) {
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
    public void addOrUpdate(ModuleListFilterTpl entity) {
        saveOrUpdate(entity);
    }


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    @Override
    public BasePage<ModuleListFilterTpl> queryPageList(PageEntity search) {
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
        removeByIds(ids);
    }
}
