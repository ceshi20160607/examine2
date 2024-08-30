package com.unique.module.service;

import com.unique.core.entity.user.bo.SimpleUserRole;
import com.unique.module.entity.po.ModuleRoleUser;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户角色对应关系表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
public interface IModuleRoleUserService extends IService<ModuleRoleUser> {


    /**
    * 查询所有数据
    *
    * @param search 搜索数据
    * @return data
    */
    BasePage<ModuleRoleUser> queryPageList(SearchBO search);

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    void addOrUpdate(ModuleRoleUser baseModel, boolean isExcel);

    /**
    * 查询字段配置
    *
    * @param id     主键ID
    * @return data
    */
    ModuleRoleUser queryById(Long id);

    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    void deleteByIds(List<Long> ids);

    List<SimpleUserRole> queryDataType(Long moduleId, List<Long> list);
}
