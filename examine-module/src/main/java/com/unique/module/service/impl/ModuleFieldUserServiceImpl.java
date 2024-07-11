package com.unique.module.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.unique.core.enums.IsOrNotEnum;
import com.unique.core.utils.BaseUtil;
import com.unique.module.entity.bo.ModuleFieldUserBO;
import com.unique.module.entity.po.ModuleFieldUser;
import com.unique.module.mapper.ModuleFieldUserMapper;
import com.unique.module.service.IModuleFieldUserService;
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
 * 自定义字段关联用户表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Service
public class ModuleFieldUserServiceImpl extends ServiceImpl<ModuleFieldUserMapper, ModuleFieldUser> implements IModuleFieldUserService {

    @Autowired
    private IModuleFieldService moduleFieldService;
    @Autowired
    private IModuleRecordDataService moduleRecordDataService;

    @Override
    public void changeFieldSort(ModuleFieldUserBO moduleFieldUserBO) {
        initFieldSort(moduleFieldUserBO.getModuleId());
        if (CollectionUtil.isNotEmpty(moduleFieldUserBO.getHiddenIds())) {
            lambdaUpdate().set(ModuleFieldUser::getHiddenFlag, IsOrNotEnum.ONE.getType())
                    .in(ModuleFieldUser::getId, moduleFieldUserBO.getHiddenIds())
                    .eq(ModuleFieldUser::getModuleId,moduleFieldUserBO.getModuleId())
                    .eq(ModuleFieldUser::getUserId,StpUtil.getLoginIdAsLong())
                    .update();
        }
        if (CollectionUtil.isNotEmpty(moduleFieldUserBO.getSortIds())) {
            for (int i = 0; i < moduleFieldUserBO.getSortIds().size(); i++) {
                lambdaUpdate().set(ModuleFieldUser::getSortFlag, i)
                        .in(ModuleFieldUser::getId, moduleFieldUserBO.getHiddenIds())
                        .eq(ModuleFieldUser::getModuleId,moduleFieldUserBO.getModuleId())
                        .eq(ModuleFieldUser::getUserId,StpUtil.getLoginIdAsLong())
                        .update();
            }
        }
    }
    @Override
    public List<ModuleField> queryFieldHead(Long moduleId) {
        List<ModuleField> fieldList = new ArrayList<>();
        initFieldSort(moduleId);
        //去除掉控制的字段
        List<ModuleFieldUser> hiddenList = lambdaQuery()
                .eq(ModuleFieldUser::getModuleId, moduleId)
                .eq(ModuleFieldUser::getUserId, StpUtil.getLoginIdAsLong())
                .eq(ModuleFieldUser::getHiddenFlag, IsOrNotEnum.ONE.getType())
                .list();
        //获取所有字段
        fieldList = moduleFieldService.lambdaQuery()
                .eq(ModuleField::getModuleId, moduleId)
                .eq(ModuleField::getIndexFlag, IsOrNotEnum.ONE.getType())
                .list();
        if (CollectionUtil.isNotEmpty(hiddenList)) {
            List<Long> hiddenIds = hiddenList.stream().map(ModuleFieldUser::getFieldId).collect(Collectors.toList());
            fieldList.removeIf(field -> hiddenIds.contains(field.getId()));
        }
        return fieldList;
    }
    @Override
    public List<ModuleField> queryFieldSearch(Long moduleId) {
        List<ModuleField> fieldList = queryFieldHead(moduleId);
        //
        return fieldList;
    }

    /**
     * 初始化列表的字段
     * @param moduleId
     */
    private void initFieldSort(Long moduleId) {
        LocalDateTime nowtime = LocalDateTime.now();
        Long count = lambdaQuery()
                .eq(ModuleFieldUser::getModuleId, moduleId)
                .eq(ModuleFieldUser::getUserId, StpUtil.getLoginIdAsLong())
                .count();
        if (count<=0L){
            List<ModuleField> fieldList = moduleFieldService.lambdaQuery()
                    .eq(ModuleField::getModuleId, moduleId)
                    .eq(ModuleField::getIndexFlag, IsOrNotEnum.ONE.getType())
                    .list();
            if (CollectionUtil.isNotEmpty(fieldList)) {
                List<ModuleFieldUser> retList = new ArrayList<>();
                fieldList.forEach(r->{
                    ModuleFieldUser item = new ModuleFieldUser();
                    item.setId(BaseUtil.getNextId());
                    item.setModuleId(moduleId);
                    item.setFieldId(r.getId());
                    item.setUserId(StpUtil.getLoginIdAsLong());
                    item.setCreateUserId(StpUtil.getLoginIdAsLong());
                    item.setCreateTime(nowtime);
                    retList.add(item);
                });
                saveBatch(retList);
            }
        }
    }

}
