package com.unique.module.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.unique.core.enums.FieldTypeEnum;
import com.unique.core.enums.IsOrNotEnum;
import com.unique.core.enums.SystemCodeEnum;
import com.unique.core.exception.BaseException;
import com.unique.core.utils.BaseUtil;
import com.unique.module.entity.bo.ModuleFieldBO;
import com.unique.module.entity.po.ModuleField;
import com.unique.module.entity.po.ModuleFieldUser;
import com.unique.module.mapper.ModuleFieldMapper;
import com.unique.module.service.IModuleFieldService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.unique.module.service.IModuleFieldUserService;
import com.unique.module.service.IModuleRecordService;
import org.springframework.stereotype.Service;

import com.unique.core.utils.FieldUtil;
import com.unique.module.entity.po.ModuleRecordData;

import com.unique.module.service.IModuleRecordDataService;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;

import cn.hutool.core.util.ObjectUtil;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 自定义字段表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Service
public class ModuleFieldServiceImpl extends ServiceImpl<ModuleFieldMapper, ModuleField> implements IModuleFieldService {

    @Autowired
    private IModuleRecordService moduleRecordService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;
    @Autowired
    private IModuleFieldUserService moduleFieldUserService;



    /**
    * 查询字段配置
    *
    * @param moduleId 模块ID
    * @return data
    */
    @Override
    public List<ModuleField> queryField(Long moduleId) {
        List<ModuleField> fieldList = lambdaQuery()
                .eq(ModuleField::getModuleId, moduleId)
                .eq(ModuleField::getAddFlag, IsOrNotEnum.ONE.getType())
                .list();
        if (CollectionUtil.isEmpty(fieldList)){
            fieldList = new ArrayList<>();
        }
        return fieldList;
    }
    /**
    * 查询字段配置
    *
    * @param moduleId 模块ID
    * @return data
    */
    @Override
    public List<List<ModuleField>> queryFormField(Long moduleId) {
        List<ModuleField> fieldList = queryField(moduleId);
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
    public void addOrUpdate(ModuleFieldBO newModel, boolean isExcel) {
        Long moduleId = newModel.getModuleId();
        if (ObjectUtil.isEmpty(moduleId)){
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID);
        }
        LocalDateTime nowtime = LocalDateTime.now();
        //移除字段
        List<ModuleField> removeFields = newModel.getRemoveList();
        if (CollectionUtil.isNotEmpty(removeFields)) {
            List<Long> removeExtendFieldIds = new ArrayList<>();
            List<Long> removeAllFieldIds = new ArrayList<>();
            List<String> removeBaseFieldNames = new ArrayList<>();

            removeFields.stream().forEach(r->{
                removeAllFieldIds.add(r.getId());
                switch (FieldTypeEnum.parse(r.getFieldType())) {
                    case EXTEND:
                        removeExtendFieldIds.add(r.getId());
                        break;
                    case MAIN:
                        removeBaseFieldNames.add(r.getFieldName());
                        break;
                }
            });
            //删除字段
            if (CollectionUtil.isNotEmpty(removeAllFieldIds)) {
                removeByIds(removeAllFieldIds);
                //删除用户关联
                LambdaQueryWrapper<ModuleFieldUser> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.in(ModuleFieldUser::getFieldId,removeAllFieldIds);
                queryWrapper.eq(ModuleFieldUser::getModuleId, moduleId);
                moduleFieldUserService.remove(queryWrapper);
            }
            //删除扩展字段在主表的字段
            if (CollectionUtil.isNotEmpty(removeBaseFieldNames)) {
                //关联的数据=》删除
                moduleRecordService.updateNullByFieldNameWithModuleId(removeBaseFieldNames, moduleId);
            }
            //删除扩展字段在data表字段
            if (CollectionUtil.isNotEmpty(removeExtendFieldIds)) {
                //关联的数据=》删除
                LambdaQueryWrapper<ModuleRecordData> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.in(ModuleRecordData::getFieldId,removeExtendFieldIds);
                moduleRecordDataService.remove(queryWrapper);
            }
        }
        //更新字段
        List<ModuleField> updateFields = newModel.getFieldList();
        if (CollectionUtil.isNotEmpty(updateFields)){
            List<ModuleField> inserts = new ArrayList<>();
            List<ModuleField> updates = new ArrayList<>();
            List<Long> insertIds = new ArrayList<>();
            updateFields.forEach(r->{
                r.setUpdateTime(nowtime);
                r.setUpdateUserId(StpUtil.getLoginIdAsLong());
                if (ObjectUtil.isEmpty(r.getId())) {
                    r.setModuleId(moduleId);
                    r.setCreateUserId(StpUtil.getLoginIdAsLong());
                    r.setCreateTime(nowtime);
                    Long nextId = BaseUtil.getNextId();
                    r.setId(nextId);
                    inserts.add(r);
                }else{
                    updates.add(r);
                }
            });
            if (CollectionUtil.isNotEmpty(inserts)) {
                saveBatch(inserts);
                //对应模块下的用户关联的字段做添加
                LambdaQueryWrapper<ModuleFieldUser> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(ModuleFieldUser::getModuleId,moduleId);
                moduleFieldUserService.remove(queryWrapper);
            }
            if (CollectionUtil.isNotEmpty(updates)) {
                updateBatchById(updates);
            }
            //todo:新增字段=》旧字段=》记录
            //actionRecordUtil.addRecord(newModel.getId(), CrmEnum.CUSTOMER, newModel.getName());
        }
    }

}
