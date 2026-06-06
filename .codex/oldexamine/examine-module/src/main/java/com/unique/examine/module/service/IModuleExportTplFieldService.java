package com.unique.examine.module.service;

import com.unique.examine.module.entity.po.ModuleExportTplField;
import com.baomidou.mybatisplus.extension.service.IService;
import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 导出模板字段配置（导出列） 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-14
 */
public interface IModuleExportTplFieldService extends IService<ModuleExportTplField> {

    /**
     * 查询字段配置
     * @author UNIQUE
     * @since 2026-04-14
     * @param id 主键ID
     * @return data
     */
    public ModuleExportTplField queryById(Serializable id);

    /**
     * 保存或新增信息
     * @author UNIQUE
     * @since 2026-04-14
     * @param entity entity
     */
    public void addOrUpdate(ModuleExportTplField entity);


    /**
     * 查询所有数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param search 搜索条件
     * @return list
     */
    public BasePage<ModuleExportTplField> queryPageList(PageEntity search);

    /**
     * 根据ID列表删除数据
     * @author UNIQUE
     * @since 2026-04-14
     * @param ids ids
     */
    public void deleteByIds(List<Serializable> ids);
}
