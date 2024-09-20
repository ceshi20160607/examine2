package com.unique.module.service.impl;

import com.unique.core.entity.user.bo.SimpleRole;
import com.unique.module.entity.po.ModuleRoleUser;
import com.unique.module.mapper.ModuleRoleUserMapper;
import com.unique.module.service.IModuleRoleUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import cn.dev33.satoken.stp.StpUtil;
import com.unique.core.utils.BaseUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 用户角色对应关系表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
@Service
public class ModuleRoleUserServiceImpl extends ServiceImpl<ModuleRoleUserMapper, ModuleRoleUser> implements IModuleRoleUserService {

    /**
    * 导出时查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @Override
    public BasePage<ModuleRoleUser> queryPageList(SearchBO search) {
        BasePage<ModuleRoleUser> basePage = getBaseMapper().queryPageList(search.parse(),search);
        return basePage;
    }

    /**
    * 保存或新增信息
    *
    * @param newModel
    */
    @Override
    public void addOrUpdate(ModuleRoleUser newModel, boolean isExcel) {
        LocalDateTime nowtime = LocalDateTime.now();

        newModel.setUpdateTime(nowtime);
        if (ObjectUtil.isEmpty(newModel.getId())){
            newModel.setId(BaseUtil.getNextId());
            newModel.setCreateTime(nowtime);
            newModel.setCreateUserId(StpUtil.getLoginIdAsLong());
            save(newModel);
            //actionRecordUtil.addRecord(newModel.getId(), CrmEnum.CUSTOMER, newModel.getName());
        }else {
            ModuleRoleUser  old = getById(newModel.getId());
            updateById(newModel);
            //actionRecordUtil.updateRecord(BeanUtil.beanToMap(old), BeanUtil.beanToMap(newModel), CrmEnum.CUSTOMER, newModel.getName(), newModel.getId());
        }
    }

    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public ModuleRoleUser queryById(Long id) {
        return getById(id);
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
    }

    @Override
    public List<SimpleRole> queryAllRoleUser(Long moduleId,Long roleId, List<Long> userIds) {
        return getBaseMapper().queryAllRoleUser(moduleId,roleId,userIds);
    }

}
