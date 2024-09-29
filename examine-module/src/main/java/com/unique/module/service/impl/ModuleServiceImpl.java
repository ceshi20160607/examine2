package com.unique.module.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.unique.core.utils.BaseUtil;
import com.unique.module.entity.po.Module;
import com.unique.module.entity.vo.ModuleVO;
import com.unique.module.mapper.ModuleMapper;
import com.unique.module.service.IModuleMenuService;
import com.unique.module.service.IModuleService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import com.unique.core.utils.FieldUtil;
import com.unique.core.context.ConstModule;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.module.entity.po.ModuleField;
import com.unique.module.entity.po.ModuleRecordData;

import com.unique.module.service.IModuleFieldService;
import com.unique.module.service.IModuleRecordDataService;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 模块表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Service
public class ModuleServiceImpl extends ServiceImpl<ModuleMapper, Module> implements IModuleService {

    private IModuleFieldService moduleFieldService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;


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
    @Override
    public BasePage<Module> queryPageListBean(SearchBO search) {
        BasePage<Module> page = lambdaQuery().like(ObjectUtil.isNotEmpty(search.getKeyword()), Module::getName, search.getKeyword())
                .eq(ObjectUtil.isNotEmpty(search.getTypeFlag()), Module::getTypeFlag, search.getTypeFlag())
                .eq(ObjectUtil.isNotEmpty(search.getParentId()), Module::getParentId, search.getParentId())
                .eq(ObjectUtil.isNotEmpty(search.getRootId()), Module::getRootId, search.getRootId())
                .page(search.parse());
        return page;
    }


    /**
    * 保存或新增信息
    *
    * @param newModel
    */
    @Override
    public Map<String, Object> addOrUpdate(Module newModel, boolean isExcel) {
        Map<String, Object> map = new HashMap<>();
        LocalDateTime nowtime = LocalDateTime.now();

        newModel.setUpdateTime(nowtime);
        if (ObjectUtil.isEmpty(newModel.getId())){
            newModel.setId(BaseUtil.getNextId());
            newModel.setCreateTime(nowtime);
            newModel.setCreateUserId(StpUtil.getLoginIdAsLong());

            save(newModel);
            //actionRecordUtil.addRecord(newModel.getId(), CrmEnum.CUSTOMER, newModel.getName());

        }else {
            Module  old = getById(newModel.getId());
            updateById(newModel);
            //actionRecordUtil.updateRecord(BeanUtil.beanToMap(old), BeanUtil.beanToMap(newModel), CrmEnum.CUSTOMER, newModel.getName(), newModel.getId());
        }
        map.put("id", newModel.getId());
        return map;
    }


    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public Map<String, Object> queryById(Long id) {
        LambdaQueryWrapper<Module> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Module::getId, id);
        Map<String, Object> recordMap = getMap(queryWrapper);
        if (ObjectUtil.isNotEmpty(recordMap)) {
            List<ModuleRecordData> dataList = moduleRecordDataService.lambdaQuery().eq(ModuleRecordData::getRecordId, id).list();
            if (CollectionUtil.isNotEmpty(dataList)) {
                dataList.forEach(f->{
                    recordMap.put(f.getName(),f.getValue());
                });
            }
        }
        return recordMap;
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
        LambdaQueryWrapper<ModuleRecordData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ModuleRecordData::getRecordId,ids);
        moduleRecordDataService.remove(queryWrapper);
        //删除字段操作记录
        //crmActionRecordService.deleteActionRecord(CrmEnum.CUSTOMER, ids);
    }

    @Override
    public List<Module> queryPageListTree(SearchBO search) {
        List<Module> list = lambdaQuery().eq(ObjectUtil.isNotEmpty(search.getTypeFlag()), Module::getTypeFlag, search.getTypeFlag())
                .eq(ObjectUtil.isNotEmpty(search.getParentId()), Module::getParentId, search.getParentId())
                .eq(ObjectUtil.isNotEmpty(search.getRootId()), Module::getRootId, search.getRootId())
                .orderByAsc(Module::getSortNum)
                .list();
        Map<Long, List<Module>> listMap = list.stream().collect(Collectors.groupingBy(Module::getParentId));
        list.forEach(f->{
            f.setChildren(new ArrayList<>());
            if (ObjectUtil.isNotEmpty(listMap.get(f.getId()))) {
                f.setChildren(listMap.get(f.getId()));
            }
        });
        List<Module> ret = list.stream().filter(f -> f.getParentId() == 0).collect(Collectors.toList());
        return ret;
    }

}
