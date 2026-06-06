package com.unique.examine.module.service;

import com.unique.examine.module.entity.po.ModuleListFilterField;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 列表筛选项配置（可筛字段/默认值/顺序） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface IModuleListFilterFieldService extends IService<ModuleListFilterField> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    public ModuleListFilterField queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-14
     * @param entity entity
     */
    public void addOrUpdate(ModuleListFilterField entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    public BasePage<ModuleListFilterField> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
