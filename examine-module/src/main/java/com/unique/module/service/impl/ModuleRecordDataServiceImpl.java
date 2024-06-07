package com.unique.module.service.impl;

import com.unique.module.entity.po.ModuleRecordData;
import com.unique.module.mapper.ModuleRecordDataMapper;
import com.unique.module.service.IModuleRecordDataService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import com.unique.core.utils.FieldUtil;
import com.unique.core.context.ConstModule;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.module.entity.po.ModuleField;

import com.unique.module.service.IModuleFieldService;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 主数据自定义字段存值表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
@Service
public class ModuleRecordDataServiceImpl extends ServiceImpl<ModuleRecordDataMapper, ModuleRecordData> implements IModuleRecordDataService {

}
