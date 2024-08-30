package com.unique.module.service;

import com.unique.core.entity.user.bo.SimpleMenu;
import com.unique.module.entity.bo.ModuleMenuBO;
import com.unique.module.entity.po.ModuleMenu;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;


import java.util.List;
import java.util.Map;

/**
 * <p>
 * 模块菜单功能权限配置表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
public interface IModuleMenuService extends IService<ModuleMenu> {


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
    void addOrUpdate(ModuleMenuBO baseModel);

    /**
    * 查询字段配置
    *
    * @param moduleId     主键ID
    * @return data
    */
    List<ModuleMenu> queryModuleMenuList(Long moduleId);


    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);


    //--------------------------------------------------
    List<SimpleMenu> querySimpleMenu(Long moduleId,Long userId);
}
