package com.unique.examine.manage.service.impl;

import com.unique.examine.base.entity.*;
import com.unique.examine.base.service.*;
import com.unique.examine.manage.bo.*;
import com.unique.examine.manage.converter.EntityMapConverter;
import com.unique.examine.manage.enums.ErrorCode;
import com.unique.examine.manage.enums.StatusEnums;
import com.unique.examine.manage.exception.BusinessException;
import com.unique.examine.manage.service.ConfigManageService;
import com.unique.examine.manage.service.PermissionService;
import com.unique.examine.manage.vo.PageResult;
import com.unique.examine.manage.vo.SimpleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ConfigManageServiceImpl implements ConfigManageService {
    private final IAppService appService;
    private final IAppVersionService appVersionService;
    private final IModuleService moduleService;
    private final IModuleFieldService moduleFieldService;
    private final IFieldOptionService fieldOptionService;
    private final IPageConfigService pageConfigService;
    private final IRuntimeMenuService runtimeMenuService;
    private final IDataDictionaryService dictionaryService;
    private final IDictionaryItemService dictionaryItemService;
    private final EntityMapConverter converter;
    private final PermissionService permissionService;

    @Override
    public PageResult<SimpleVO> apps(long pageNo, long pageSize, Long systemId, Long tenantId) {
        permissionService.requireScope(systemId, tenantId);
        IPage<App> page = appService.page(Page.of(pageNo, pageSize), Wrappers.<App>lambdaQuery()
                .eq(App::getSystemId, systemId).eq(App::getTenantId, tenantId).orderByAsc(App::getSortOrder));
        return simplePage(pageNo, pageSize, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveApp(AppSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "app:save");
        App app = new App();
        app.setSystemId(bo.getSystemId()); app.setTenantId(bo.getTenantId()); app.setAppName(bo.getAppName()); app.setAppCode(bo.getAppCode());
        app.setStatus(bo.getStatus() == null ? StatusEnums.DRAFT : bo.getStatus()); app.setSortOrder(bo.getSortOrder() == null ? 0 : bo.getSortOrder());
        appService.save(app);
        return converter.toSimple(app);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO publishApp(Long appId) {
        App app = appService.getById(appId);
        if (app == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"); }
        permissionService.requireAction(app.getSystemId(), app.getTenantId(), "app:publish");
        int nextVersion = appVersionService.list(Wrappers.<AppVersion>lambdaQuery().eq(AppVersion::getAppId, appId)).stream()
                .map(AppVersion::getVersionNo).filter(v -> v != null).max(Integer::compareTo).orElse(0) + 1;
        AppVersion version = new AppVersion();
        version.setSystemId(app.getSystemId()); version.setTenantId(app.getTenantId()); version.setAppId(app.getId());
        version.setVersionNo(nextVersion); version.setStatus(StatusEnums.PUBLISHED); version.setPublishedAt(LocalDateTime.now());
        version.setVersionNote("发布应用配置");
        appVersionService.save(version);
        app.setCurrentVersionId(version.getId()); app.setStatus(StatusEnums.ENABLED); appService.updateById(app);
        return converter.toSimple(version);
    }

    @Override
    public PageResult<SimpleVO> modules(long pageNo, long pageSize, Long appId) {
        App app = mustApp(appId);
        permissionService.requireScope(app.getSystemId(), app.getTenantId());
        IPage<com.unique.examine.base.entity.Module> page = moduleService.page(Page.of(pageNo, pageSize), Wrappers.<com.unique.examine.base.entity.Module>lambdaQuery().eq(com.unique.examine.base.entity.Module::getAppId, appId).orderByDesc(com.unique.examine.base.entity.Module::getUpdatedAt));
        return simplePage(pageNo, pageSize, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveModule(ModuleSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "module:save");
        com.unique.examine.base.entity.Module module = new com.unique.examine.base.entity.Module();
        module.setSystemId(bo.getSystemId()); module.setTenantId(bo.getTenantId()); module.setAppId(bo.getAppId()); module.setModuleName(bo.getModuleName());
        module.setModuleCode(bo.getModuleCode()); module.setModuleType(bo.getModuleType() == null ? "NORMAL" : bo.getModuleType());
        module.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        moduleService.save(module);
        return converter.toSimple(module);
    }

    @Override
    public PageResult<SimpleVO> fields(long pageNo, long pageSize, Long moduleId) {
        com.unique.examine.base.entity.Module module = mustModule(moduleId);
        permissionService.requireScope(module.getSystemId(), module.getTenantId());
        IPage<ModuleField> page = moduleFieldService.page(Page.of(pageNo, pageSize), Wrappers.<ModuleField>lambdaQuery().eq(ModuleField::getModuleId, moduleId).orderByAsc(ModuleField::getSortOrder));
        return simplePage(pageNo, pageSize, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveField(FieldSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "field:save");
        ModuleField field = new ModuleField();
        field.setSystemId(bo.getSystemId()); field.setTenantId(bo.getTenantId()); field.setModuleId(bo.getModuleId()); field.setFieldCode(bo.getFieldCode());
        field.setFieldName(bo.getFieldName()); field.setFieldType(bo.getFieldType()); field.setRequiredFlag(bo.getRequiredFlag() == null ? 0 : bo.getRequiredFlag());
        field.setUniqueFlag(bo.getUniqueFlag() == null ? 0 : bo.getUniqueFlag()); field.setDefaultValue(bo.getDefaultValue()); field.setEnumSource(bo.getEnumSource());
        field.setValidateRule(bo.getValidateRule()); field.setSortOrder(bo.getSortOrder() == null ? 0 : bo.getSortOrder()); field.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        moduleFieldService.save(field);
        return converter.toSimple(field);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveFieldOption(FieldOptionSaveBO bo) {
        ModuleField field = moduleFieldService.getById(bo.getFieldId());
        if (field == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "字段不存在"); }
        permissionService.requireAction(field.getSystemId(), field.getTenantId(), "field:save");
        FieldOption option = new FieldOption();
        option.setFieldId(bo.getFieldId()); option.setOptionLabel(bo.getOptionLabel()); option.setOptionValue(bo.getOptionValue());
        option.setSortOrder(bo.getSortOrder() == null ? 0 : bo.getSortOrder()); option.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        fieldOptionService.save(option);
        return converter.toSimple(option);
    }

    @Override
    public PageResult<SimpleVO> pages(long pageNo, long pageSize, Long moduleId) {
        com.unique.examine.base.entity.Module module = mustModule(moduleId);
        permissionService.requireScope(module.getSystemId(), module.getTenantId());
        IPage<PageConfig> page = pageConfigService.page(Page.of(pageNo, pageSize), Wrappers.<PageConfig>lambdaQuery().eq(PageConfig::getModuleId, moduleId).orderByDesc(PageConfig::getUpdatedAt));
        return simplePage(pageNo, pageSize, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO savePage(PageSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "page:save");
        PageConfig page = new PageConfig();
        page.setSystemId(bo.getSystemId()); page.setTenantId(bo.getTenantId()); page.setModuleId(bo.getModuleId()); page.setPageType(bo.getPageType());
        page.setAppVersionId(bo.getAppVersionId()); page.setLayoutJson(bo.getLayoutJson()); page.setBlockJson(bo.getBlockJson()); page.setStatus(bo.getStatus() == null ? StatusEnums.DRAFT : bo.getStatus());
        pageConfigService.save(page);
        return converter.toSimple(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO publishPage(Long pageId) {
        PageConfig page = pageConfigService.getById(pageId);
        if (page == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "页面不存在"); }
        permissionService.requireAction(page.getSystemId(), page.getTenantId(), "page:publish");
        page.setStatus(StatusEnums.PUBLISHED);
        pageConfigService.updateById(page);
        return converter.toSimple(page);
    }

    @Override
    public PageResult<SimpleVO> menus(long pageNo, long pageSize, Long systemId, Long tenantId) {
        permissionService.requireScope(systemId, tenantId);
        IPage<RuntimeMenu> page = runtimeMenuService.page(Page.of(pageNo, pageSize), Wrappers.<RuntimeMenu>lambdaQuery().eq(RuntimeMenu::getSystemId, systemId).eq(RuntimeMenu::getTenantId, tenantId).orderByAsc(RuntimeMenu::getSortOrder));
        return simplePage(pageNo, pageSize, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveMenu(RuntimeMenuSaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "menu:save");
        RuntimeMenu menu = new RuntimeMenu();
        menu.setSystemId(bo.getSystemId()); menu.setTenantId(bo.getTenantId()); menu.setParentId(bo.getParentId()); menu.setAppId(bo.getAppId()); menu.setModuleId(bo.getModuleId()); menu.setPageId(bo.getPageId());
        menu.setMenuName(bo.getMenuName()); menu.setMenuCode(bo.getMenuCode()); menu.setPermissionCode(bo.getPermissionCode()); menu.setSortOrder(bo.getSortOrder() == null ? 0 : bo.getSortOrder()); menu.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        runtimeMenuService.save(menu);
        return converter.toSimple(menu);
    }

    @Override
    public PageResult<SimpleVO> dictionaries(long pageNo, long pageSize, Long systemId, Long tenantId) {
        permissionService.requireScope(systemId, tenantId);
        IPage<DataDictionary> page = dictionaryService.page(Page.of(pageNo, pageSize), Wrappers.<DataDictionary>lambdaQuery().eq(DataDictionary::getSystemId, systemId).eq(DataDictionary::getTenantId, tenantId));
        return simplePage(pageNo, pageSize, page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveDictionary(DictionarySaveBO bo) {
        permissionService.requireAction(bo.getSystemId(), bo.getTenantId(), "dict:save");
        DataDictionary dictionary = new DataDictionary();
        dictionary.setSystemId(bo.getSystemId()); dictionary.setTenantId(bo.getTenantId()); dictionary.setDictCode(bo.getDictCode()); dictionary.setDictName(bo.getDictName()); dictionary.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        dictionaryService.save(dictionary);
        return converter.toSimple(dictionary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SimpleVO saveDictionaryItem(DictionaryItemSaveBO bo) {
        DataDictionary dictionary = dictionaryService.getById(bo.getDictId());
        if (dictionary == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "字典不存在"); }
        permissionService.requireAction(dictionary.getSystemId(), dictionary.getTenantId(), "dict:save");
        DictionaryItem item = new DictionaryItem();
        item.setDictId(bo.getDictId()); item.setItemLabel(bo.getItemLabel()); item.setItemValue(bo.getItemValue()); item.setSortOrder(bo.getSortOrder() == null ? 0 : bo.getSortOrder()); item.setStatus(bo.getStatus() == null ? StatusEnums.ENABLED : bo.getStatus());
        dictionaryItemService.save(item);
        return converter.toSimple(item);
    }

    private App mustApp(Long appId) {
        App app = appService.getById(appId);
        if (app == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "应用不存在"); }
        return app;
    }

    private com.unique.examine.base.entity.Module mustModule(Long moduleId) {
        com.unique.examine.base.entity.Module module = moduleService.getById(moduleId);
        if (module == null) { throw new BusinessException(ErrorCode.NOT_FOUND, "模块不存在"); }
        return module;
    }

    private <T> PageResult<SimpleVO> simplePage(long pageNo, long pageSize, IPage<T> page) {
        return new PageResult<>(pageNo, pageSize, page.getTotal(), page.getRecords().stream().map(converter::toSimple).toList());
    }
}
