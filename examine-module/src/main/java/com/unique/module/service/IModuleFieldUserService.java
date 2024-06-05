package com.unique.module.service;

import com.unique.module.entity.bo.ModuleFieldUserBO;
import com.unique.module.entity.po.ModuleFieldUser;
import com.baomidou.mybatisplus.extension.service.IService;

import com.unique.core.entity.base.bo.SearchBO;
import com.unique.core.common.BasePage;

import com.unique.module.entity.po.ModuleField;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 自定义字段关联用户表 服务类
 * </p>
 *
 * @author UNIQUE
 * @since 2024-05-28
 */
public interface IModuleFieldUserService extends IService<ModuleFieldUser> {


    /**
     * 查询字段配置
     *
     * @param moduleId 主键ID
     */
    List<ModuleField> queryFieldHead(Long moduleId);


    /**
     * 查询字段配置
     *
     * @param moduleId 主键ID
     */
    List<ModuleField> queryFieldSearch(Long moduleId);

    /**
     * 配置列表上的字段排序
     *
     * @param moduleFieldUserBO
     */
    void changeFieldSort(ModuleFieldUserBO moduleFieldUserBO);
}
