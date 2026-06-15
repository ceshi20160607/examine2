package com.unique.examine.module.manage.service;

import java.util.List;

import com.unique.examine.module.manage.bo.ActionConfigSaveBO;
import com.unique.examine.module.manage.bo.AppSaveBO;
import com.unique.examine.module.manage.bo.AppUpdateBO;
import com.unique.examine.module.manage.bo.ConfigStatusBO;
import com.unique.examine.module.manage.bo.FieldSaveBO;
import com.unique.examine.module.manage.bo.FieldUpdateBO;
import com.unique.examine.module.manage.bo.MenuConfigSaveBO;
import com.unique.examine.module.manage.bo.ModuleSaveBO;
import com.unique.examine.module.manage.bo.ModuleUpdateBO;
import com.unique.examine.module.manage.bo.PageSchemaSaveBO;
import com.unique.examine.module.manage.bo.PublishRequestBO;
import com.unique.examine.module.manage.vo.ActionConfigVO;
import com.unique.examine.module.manage.vo.AppVO;
import com.unique.examine.module.manage.vo.FieldTypeVO;
import com.unique.examine.module.manage.vo.FieldVO;
import com.unique.examine.module.manage.vo.MenuConfigVO;
import com.unique.examine.module.manage.vo.ModuleVO;
import com.unique.examine.module.manage.vo.PageSchemaVO;
import com.unique.examine.module.manage.vo.PublishCheckResultVO;
import com.unique.examine.module.manage.vo.PublishVersionVO;

/**
 * 应用、模块、字段和页面配置服务。
 */
public interface ModuleConfigService {

    /**
     * 查询系统应用列表。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param keyword 关键字
     * @param status 应用状态
     * @return 应用列表
     */
    List<AppVO> listApps(Long systemId, String tenantId, String keyword, String status);

    /**
     * 创建应用草稿。
     *
     * @param systemId 系统 ID
     * @param saveBO 应用保存入参
     * @return 应用
     */
    AppVO createApp(Long systemId, AppSaveBO saveBO);

    /**
     * 查询应用详情。
     *
     * @param systemId 系统 ID
     * @param appId 应用 ID
     * @return 应用
     */
    AppVO getApp(Long systemId, Long appId);

    /**
     * 更新应用。
     *
     * @param systemId 系统 ID
     * @param appId 应用 ID
     * @param updateBO 应用更新入参
     * @return 应用
     */
    AppVO updateApp(Long systemId, Long appId, AppUpdateBO updateBO);

    /**
     * 变更应用状态。
     *
     * @param systemId 系统 ID
     * @param appId 应用 ID
     * @param statusBO 状态入参
     * @return 应用
     */
    AppVO changeAppStatus(Long systemId, Long appId, ConfigStatusBO statusBO);

    /**
     * 查询应用下模块列表。
     *
     * @param systemId 系统 ID
     * @param appId 应用 ID
     * @param keyword 关键字
     * @param status 模块状态
     * @return 模块列表
     */
    List<ModuleVO> listModules(Long systemId, Long appId, String keyword, String status);

    /**
     * 创建模块草稿。
     *
     * @param systemId 系统 ID
     * @param appId 应用 ID
     * @param saveBO 模块保存入参
     * @return 模块
     */
    ModuleVO createModule(Long systemId, Long appId, ModuleSaveBO saveBO);

    /**
     * 查询模块详情。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @return 模块
     */
    ModuleVO getModule(Long systemId, Long moduleId);

    /**
     * 更新模块基础信息。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param updateBO 模块更新入参
     * @return 模块
     */
    ModuleVO updateModule(Long systemId, Long moduleId, ModuleUpdateBO updateBO);

    /**
     * 变更模块状态。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param statusBO 状态入参
     * @return 模块
     */
    ModuleVO changeModuleStatus(Long systemId, Long moduleId, ConfigStatusBO statusBO);

    /**
     * 查询模块字段。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @return 字段列表
     */
    List<FieldVO> listFields(Long systemId, Long moduleId);

    /**
     * 创建字段草稿。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param saveBO 字段保存入参
     * @return 字段
     */
    FieldVO createField(Long systemId, Long moduleId, FieldSaveBO saveBO);

    /**
     * 更新字段。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param fieldId 字段 ID
     * @param updateBO 字段更新入参
     * @return 字段
     */
    FieldVO updateField(Long systemId, Long moduleId, Long fieldId, FieldUpdateBO updateBO);

    /**
     * 变更字段状态。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param fieldId 字段 ID
     * @param statusBO 状态入参
     * @return 字段
     */
    FieldVO changeFieldStatus(Long systemId, Long moduleId, Long fieldId, ConfigStatusBO statusBO);

    /**
     * 查询可用字段类型。
     *
     * @param systemId 系统 ID
     * @return 字段类型列表
     */
    List<FieldTypeVO> fieldTypes(Long systemId);

    /**
     * 发布检查。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @return 检查结果
     */
    PublishCheckResultVO publishCheck(Long systemId, Long moduleId);

    /**
     * 发布模块配置版本。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param requestBO 发布入参
     * @return 发布版本
     */
    PublishVersionVO publish(Long systemId, Long moduleId, PublishRequestBO requestBO);

    /**
     * 查询页面 schema。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param pageType 页面类型
     * @return 页面 schema
     */
    PageSchemaVO getPageSchema(Long systemId, Long moduleId, String pageType);

    /**
     * 保存页面 schema 草稿。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param pageType 页面类型
     * @param saveBO 保存入参
     * @return 页面 schema
     */
    PageSchemaVO savePageSchema(Long systemId, Long moduleId, String pageType, PageSchemaSaveBO saveBO);

    /**
     * 保存运行菜单配置。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param saveBO 菜单配置
     * @return 菜单配置
     */
    MenuConfigVO saveMenu(Long systemId, Long moduleId, MenuConfigSaveBO saveBO);

    /**
     * 保存模块动作配置。
     *
     * @param systemId 系统 ID
     * @param moduleId 模块 ID
     * @param saveBO 动作配置
     * @return 动作配置列表
     */
    List<ActionConfigVO> saveActions(Long systemId, Long moduleId, ActionConfigSaveBO saveBO);
}
