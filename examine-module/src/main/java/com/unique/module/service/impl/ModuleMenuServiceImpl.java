package com.unique.module.service.impl;

import com.unique.core.entity.user.bo.SimpleMenu;
import com.unique.module.entity.bo.ModuleMenuBO;
import com.unique.module.entity.po.ModuleMenu;
import com.unique.module.mapper.ModuleMenuMapper;
import com.unique.module.service.IModuleMenuService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;


import cn.hutool.core.collection.CollectionUtil;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * <p>
 * 模块菜单功能权限配置表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-07-01
 */
@Service
public class ModuleMenuServiceImpl extends ServiceImpl<ModuleMenuMapper, ModuleMenu> implements IModuleMenuService {

    /**
    * 导出时查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @Override
    public BasePage<Map<String, Object>> queryPageList(SearchBO search) {
        BasePage<Map<String, Object>> basePage = getBaseMapper().queryPageList(search.parse(),search);
        return basePage;
    }

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    @Override
    public void addOrUpdate(ModuleMenuBO baseModel) {
        Long moduleId = baseModel.getModuleId();
        List<ModuleMenu> menuList = baseModel.getMenuList();
        if (CollectionUtil.isNotEmpty(menuList)) {
            menuList.forEach(r->{
                r.setModuleId(moduleId);
            });
            saveOrUpdateBatch(menuList);
        }
    }


    /**
    * 查询字段配置
    *
    * @param moduleId 主键ID
    * @return data
    */
    @Override
    public List<ModuleMenu> queryModuleMenuList(Long moduleId){
        List<ModuleMenu> ret = lambdaQuery().eq(ObjectUtil.isNotEmpty(moduleId), ModuleMenu::getModuleId, moduleId).list();
        return ret;
    }



    /**
    * 删除客户数据
    *
    * @param ids ids
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        removeByIds(ids);
        //crmActionRecordService.deleteActionRecord(CrmEnum.CUSTOMER, ids);
    }

    @Override
    public List<SimpleMenu> querySimpleMenu(Long moduleId,Long userId) {
        return getBaseMapper().querySimpleMenu(moduleId,userId);
    }

}
