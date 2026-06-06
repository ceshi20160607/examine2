package com.unique.examine.module.manage.service;

import com.unique.examine.module.manage.bo.ModuleExportJobBO;
import com.unique.examine.module.manage.bo.ModuleFieldSaveBO;
import com.unique.examine.module.manage.bo.ModuleModelSaveBO;
import com.unique.examine.module.manage.bo.ModulePageSaveBO;
import com.unique.examine.module.manage.bo.ModuleRecordSaveBO;
import com.unique.examine.module.manage.bo.ModuleStatusBO;
import com.unique.examine.module.manage.vo.ModuleManageVO;

import java.util.List;

/**
 * 动态模块管理服务。
 */
public interface ModuleManageService {

    /**
     * 查询应用下模块模型。
     *
     * @param appId 应用 ID
     * @return 模块列表
     */
    List<ModuleManageVO> listModels(Long appId);

    /**
     * 创建模块模型。
     *
     * @param bo 模块模型入参
     * @return 模块信息
     */
    ModuleManageVO createModel(ModuleModelSaveBO bo);

    /**
     * 变更模块状态。
     *
     * @param id 模块 ID
     * @param bo 状态入参
     * @return 模块信息
     */
    ModuleManageVO updateModelStatus(Long id, ModuleStatusBO bo);

    /**
     * 查询模块字段。
     *
     * @param moduleId 模块 ID
     * @return 字段列表
     */
    List<ModuleManageVO> listFields(Long moduleId);

    /**
     * 创建字段及选项。
     *
     * @param bo 字段入参
     * @return 字段信息
     */
    ModuleManageVO createField(ModuleFieldSaveBO bo);

    /**
     * 查询模块页面。
     *
     * @param moduleId 模块 ID
     * @return 页面列表
     */
    List<ModuleManageVO> listPages(Long moduleId);

    /**
     * 创建页面配置。
     *
     * @param bo 页面入参
     * @return 页面信息
     */
    ModuleManageVO createPage(ModulePageSaveBO bo);

    /**
     * 查询模块记录。
     *
     * @param moduleId 模块 ID
     * @return 记录列表
     */
    List<ModuleManageVO> listRecords(Long moduleId);

    /**
     * 创建运行态记录。
     *
     * @param bo 记录入参
     * @return 记录详情
     */
    ModuleManageVO createRecord(ModuleRecordSaveBO bo);

    /**
     * 更新运行态记录。
     *
     * @param id 记录 ID
     * @param bo 记录入参
     * @return 记录详情
     */
    ModuleManageVO updateRecord(Long id, ModuleRecordSaveBO bo);

    /**
     * 查询记录详情。
     *
     * @param id 记录 ID
     * @return 记录详情
     */
    ModuleManageVO getRecord(Long id);

    /**
     * 删除运行态记录。
     *
     * @param id 记录 ID
     * @return 记录信息
     */
    ModuleManageVO deleteRecord(Long id);

    /**
     * 创建导出任务。
     *
     * @param bo 导出任务入参
     * @return 导出任务信息
     */
    ModuleManageVO createExportJob(ModuleExportJobBO bo);
}
