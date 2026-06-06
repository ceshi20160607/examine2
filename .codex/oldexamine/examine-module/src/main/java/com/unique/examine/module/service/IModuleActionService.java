package com.unique.examine.module.service;

import com.unique.examine.module.entity.po.ModuleAction;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 系统内置动作字典（模块功能点） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface IModuleActionService extends IService<ModuleAction> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    public ModuleAction queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-14
     * @param entity entity
     */
    public void addOrUpdate(ModuleAction entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    public BasePage<ModuleAction> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
