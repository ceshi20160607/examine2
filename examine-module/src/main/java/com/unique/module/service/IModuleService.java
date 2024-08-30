package com.unique.module.service;

import com.unique.module.entity.po.Module;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.module.entity.po.ModuleField;
import com.unique.module.entity.vo.ModuleVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 模块表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
public interface IModuleService extends IService<Module> {


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
    Map<String, Object> addOrUpdate(Module baseModel, boolean isExcel);

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

    List<Module> queryPageListTree(SearchBO search);
}
