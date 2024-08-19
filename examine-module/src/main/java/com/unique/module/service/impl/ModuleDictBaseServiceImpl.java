package com.unique.module.service.impl;

import com.unique.module.entity.po.ModuleDictBase;
import com.unique.module.mapper.ModuleDictBaseMapper;
import com.unique.module.service.IModuleDictBaseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import com.unique.core.utils.BaseUtil;
import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * <p>
 * 数据字段基础表 服务实现类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-08-19
 */
@Service
public class ModuleDictBaseServiceImpl extends ServiceImpl<ModuleDictBaseMapper, ModuleDictBase> implements IModuleDictBaseService {

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
    * @param newModel
    */
    @Override
    public Map<String, Object> addOrUpdate(ModuleDictBase newModel, boolean isExcel) {
        Map<String, Object> map = new HashMap<>();
        LocalDateTime nowtime = LocalDateTime.now();

        newModel.setUpdateTime(nowtime);
        if (ObjectUtil.isEmpty(newModel.getId())){
            newModel.setId(BaseUtil.getNextId());
            newModel.setCreateTime(nowtime);
            save(newModel);
        }else {
            updateById(newModel);
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
        LambdaQueryWrapper<ModuleDictBase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ModuleDictBase::getId, id);
        Map<String, Object> recordMap = getMap(queryWrapper);
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
    }

}
