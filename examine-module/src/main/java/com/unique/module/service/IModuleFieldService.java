package com.unique.module.service;

import com.unique.module.entity.bo.ModuleFieldBO;
import com.unique.module.entity.po.ModuleField;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 自定义字段表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
public interface IModuleFieldService extends IService<ModuleField> {

    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<ModuleField> queryField(Long id);
    /**
    * 查询字段配置
    *
    * @param id 主键ID
    */
    List<List<ModuleField>> queryFormField(Long id);

    /**
    * 保存或新增信息
    *
    * @param baseModel
    */
    void addOrUpdate(ModuleFieldBO baseModel, boolean isExcel);


}
