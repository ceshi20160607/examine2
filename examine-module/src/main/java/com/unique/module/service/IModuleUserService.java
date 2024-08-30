package com.unique.module.service;

import com.unique.core.entity.user.bo.SimpleUser;
import com.unique.module.entity.po.ModuleUser;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
public interface IModuleUserService extends IService<ModuleUser> {


    /**
    * 查询所有数据
    *
    * @param search 搜索数据
    * @return data
    */
    BasePage<ModuleUser> queryPageList(SearchBO search);

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    void addOrUpdate(ModuleUser baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    ModuleUser queryById(Long id);

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);


    //--------------------------------------------------
    List<SimpleUser> queryAllUsers(Long moduleId);
}
