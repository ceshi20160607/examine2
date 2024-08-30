package com.unique.module.service.impl;

import com.unique.core.entity.user.bo.SimpleDept;
import com.unique.module.entity.po.ModuleDept;
import com.unique.module.mapper.ModuleDeptMapper;
import com.unique.module.service.IModuleDeptService;
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
 * 部门表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-28
 */
@Service
public class ModuleDeptServiceImpl extends ServiceImpl<ModuleDeptMapper, ModuleDept> implements IModuleDeptService {

    /**
    * 导出时查询所有数据
    *
    * @param search 业务查询对象
    * @return data
    */
    @Override
    public BasePage<ModuleDept> queryPageList(SearchBO search) {
        BasePage<ModuleDept> basePage = getBaseMapper().queryPageList(search.parse(),search);
        return basePage;
    }

    /**
    * 保存或新增信息
    *
    * @param newModel
    */
    @Override
    public void addOrUpdate(ModuleDept newModel, boolean isExcel) {
        LocalDateTime nowtime = LocalDateTime.now();

        newModel.setUpdateTime(nowtime);
        if (ObjectUtil.isEmpty(newModel.getId())){
            newModel.setId(BaseUtil.getNextId());
            newModel.setCreateTime(nowtime);
            newModel.setCreateUserId(StpUtil.getLoginIdAsLong());
            save(newModel);
            //actionRecordUtil.addRecord(newModel.getId(), CrmEnum.CUSTOMER, newModel.getName());
        }else {
            ModuleDept  old = getById(newModel.getId());
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
    public ModuleDept queryById(Long id) {
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



    //--------------------------------------------------

    @Override
    public List<SimpleDept> queryAllDepts(Long moduleId) {
        return getBaseMapper().queryAllDepts(moduleId);
    }

    @Override
    public List<SimpleDept> queryDataDepts(Long moduleId,Long userId) {
        return getBaseMapper().queryDataDepts(moduleId,userId);
    }

}
