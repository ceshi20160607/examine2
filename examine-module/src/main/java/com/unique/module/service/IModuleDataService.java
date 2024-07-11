package com.unique.module.service;

import com.unique.module.entity.po.ModuleData;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.module.entity.po.ModuleField;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户模块的数据权限 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
public interface IModuleDataService extends IService<ModuleData> {


    /**
    * 查询所有数据
    *
    * @param search 搜索数据
    * @return data
    */
    BasePage<Map<String, Object>> queryPageList(SearchBO search);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<ModuleField> queryField(Long id);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<List<ModuleField>> queryFormField(Long id);

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    Map<String, Object> addOrUpdate(ModuleData baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    Map<String, Object>  queryById(Long id);

    /**
    * 查询详情
    *
    * @param id     主键ID
    */
    public List<ModuleField> information(Long id);


    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);

}
