package com.unique.module.service;

import com.unique.core.entity.user.bo.SimpleDept;
import com.unique.module.entity.po.ModuleDept;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 部门表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
public interface IModuleDeptService extends IService<ModuleDept> {


    /**
    * 查询所有数据
    *
    * @param search 搜索数据
    * @return data
    */
    BasePage<ModuleDept> queryPageList(SearchBO search);

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    void addOrUpdate(ModuleDept baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    ModuleDept queryById(Long id);

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);


    //--------------------------------------------------
    List<SimpleDept> queryAllDepts(Long moduleId);

    List<SimpleDept> queryDataDepts(Long moduleId,Long userId);
}
