package com.unique.module.service;

import com.unique.module.entity.po.ModuleDictBase;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 数据字段基础表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
public interface IModuleDictBaseService extends IService<ModuleDictBase> {


    /**
    * 查询所有数据
    *
    * @param search 搜索数据
    * @return data
    */
    BasePage<Map<String, Object>> queryPageList(SearchBO search);
    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    Map<String, Object> addOrUpdate(ModuleDictBase baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    Map<String, Object>  queryById(Long id);
    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);

}
