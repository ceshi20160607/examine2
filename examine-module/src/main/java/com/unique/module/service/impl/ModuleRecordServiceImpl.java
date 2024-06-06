package com.unique.module.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.unique.module.entity.bo.ModuleRecordBO;
import com.unique.module.entity.po.ModuleRecord;
import com.unique.module.mapper.ModuleRecordMapper;
import com.unique.module.service.IModuleRecordService;
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
 * 主数据基础表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Service
public class ModuleRecordServiceImpl extends ServiceImpl<ModuleRecordMapper, ModuleRecord> implements IModuleRecordService {

    @Autowired
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
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public List<ModuleField> queryField(Long id) {
        Map<String, Object> record = queryById(id);
        List<ModuleField> vos = new ArrayList<>();
        if (ObjectUtil.isNotEmpty(record.get(ConstModule.MODULE_ID_BASE))) {
            vos = moduleFieldService.queryField(Long.valueOf(record.get(ConstModule.MODULE_ID_BASE).toString()));
            if (CollectionUtil.isNotEmpty(vos)) {
                vos.forEach(r->{
                    if (ObjectUtil.isNotEmpty(record.get(r.getFieldName()))) {
                        r.setDefaultValue(record.get(r.getFieldName()).toString());
                    }
                });
            }
        }
        return vos;
    }
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    * @return data
    */
    @Override
    public List<List<ModuleField>> queryFormField(Long id) {
        List<ModuleField> fieldList = queryField(id);
        List<List<ModuleField>> vos = FieldUtil.getFieldFormList(fieldList,ModuleField::getAxisy,ModuleField::getAxisx);

        for (List<ModuleField> filedVOList : vos) {
            filedVOList.forEach(field -> {
            });
        }

        return vos;
    }
    /**
    * 保存或新增信息
    *
    * @param newModel
    */
    @Override
    public Map<String, Object> addOrUpdate(ModuleRecordBO newModel, boolean isExcel) {
        Map<String, Object> map = new HashMap<>();
        Long moduleId = newModel.getModuleId();
        LocalDateTime nowtime = LocalDateTime.now();

        ModuleRecord entity = newModel.getEntity();
        entity.setModuleId(moduleId);
        entity.setUpdateTime(nowtime);
        if (ObjectUtil.isEmpty(entity.getId())){

            entity.setCreateTime(nowtime);
            entity.setCreateUserId(StpUtil.getLoginIdAsLong());
            entity.setOwnerUserId(StpUtil.getLoginIdAsLong());
            if (save(entity)) {
                Long recordId = entity.getId();
                //扩展字段
                if (CollectionUtil.isNotEmpty(newModel.getFieldList())) {
                    List<ModuleRecordData> moduleRecordData = newModel.getFieldList().stream().map(r -> {
                        ModuleRecordData recordData = new ModuleRecordData();
                        recordData.setModuleId(moduleId);
                        recordData.setRecordId(recordId);
                        recordData.setFieldId(r.getId());
                        recordData.setName(r.getFieldName());
                        recordData.setValue(r.getValue());
                        recordData.setCreateTime(nowtime);
                        return recordData;
                    }).collect(Collectors.toList());
                    moduleRecordDataService.saveBatch(moduleRecordData);
                }
            }
            //actionRecordUtil.addRecord(newModel.getId(), CrmEnum.CUSTOMER, newModel.getName());
        }else {
            Long recordId = entity.getId();
            ModuleRecord  old = getById(recordId);
            updateById(entity);

            //扩展字段
            if (CollectionUtil.isNotEmpty(newModel.getFieldList())) {
                moduleRecordDataService.remove(new LambdaQueryWrapper<ModuleRecordData>().eq(ModuleRecordData::getRecordId, recordId));
                List<ModuleRecordData> moduleRecordData = newModel.getFieldList().stream().map(r -> {
                    ModuleRecordData recordData = new ModuleRecordData();
                    recordData.setModuleId(moduleId);
                    recordData.setRecordId(recordId);
                    recordData.setFieldId(r.getId());
                    recordData.setName(r.getFieldName());
                    recordData.setValue(r.getValue());
                    recordData.setCreateTime(nowtime);
                    return recordData;
                }).collect(Collectors.toList());
                moduleRecordDataService.saveBatch(moduleRecordData);
            }
            //actionRecordUtil.updateRecord(BeanUtil.beanToMap(old), BeanUtil.beanToMap(newModel), CrmEnum.CUSTOMER, newModel.getName(), newModel.getId());
        }
        map.put("id", entity.getId());
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
        LambdaQueryWrapper<ModuleRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModuleRecord::getId, id);
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
    * 查询详情
    *
    * @param id     主键ID
    */
    @Override
    public List<ModuleField> information(Long id) {
        List<ModuleField> collect = queryField(id);
        return collect;
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
    public void updateNullByFieldNameWithModuleId(List<String> removeBaseFieldNames, Long moduleId) {
        getBaseMapper().updateNullByFieldNameWithModuleId(removeBaseFieldNames,moduleId);
    }

}
