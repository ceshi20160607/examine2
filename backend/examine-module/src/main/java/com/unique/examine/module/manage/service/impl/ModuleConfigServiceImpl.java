package com.unique.examine.module.manage.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.Action;
import com.unique.examine.module.base.entity.App;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.FieldOption;
import com.unique.examine.module.base.entity.Menu;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.entity.PageSchema;
import com.unique.examine.module.base.entity.PublishVersion;
import com.unique.examine.module.base.service.IActionService;
import com.unique.examine.module.base.service.IAppService;
import com.unique.examine.module.base.service.IFieldOptionService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IMenuService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPageSchemaService;
import com.unique.examine.module.base.service.IPublishVersionService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.manage.bo.ActionConfigBO;
import com.unique.examine.module.manage.bo.ActionConfigSaveBO;
import com.unique.examine.module.manage.bo.AppSaveBO;
import com.unique.examine.module.manage.bo.AppUpdateBO;
import com.unique.examine.module.manage.bo.ConfigStatusBO;
import com.unique.examine.module.manage.bo.FieldOptionBO;
import com.unique.examine.module.manage.bo.FieldSaveBO;
import com.unique.examine.module.manage.bo.FieldUpdateBO;
import com.unique.examine.module.manage.bo.MenuConfigSaveBO;
import com.unique.examine.module.manage.bo.ModuleSaveBO;
import com.unique.examine.module.manage.bo.ModuleUpdateBO;
import com.unique.examine.module.manage.bo.PageSchemaSaveBO;
import com.unique.examine.module.manage.bo.PublishRequestBO;
import com.unique.examine.module.manage.enums.ModuleConfigErrorCode;
import com.unique.examine.module.manage.service.ModuleConfigService;
import com.unique.examine.module.manage.vo.ActionConfigVO;
import com.unique.examine.module.manage.vo.AppVO;
import com.unique.examine.module.manage.vo.CheckIssueVO;
import com.unique.examine.module.manage.vo.FieldOptionVO;
import com.unique.examine.module.manage.vo.FieldTypeVO;
import com.unique.examine.module.manage.vo.FieldVO;
import com.unique.examine.module.manage.vo.MenuConfigVO;
import com.unique.examine.module.manage.vo.ModuleVO;
import com.unique.examine.module.manage.vo.PageSchemaVO;
import com.unique.examine.module.manage.vo.PublishCheckResultVO;
import com.unique.examine.module.manage.vo.PublishVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 应用、模块、字段和页面配置服务实现。
 */
@Service
@RequiredArgsConstructor
public class ModuleConfigServiceImpl implements ModuleConfigService {

    private static final String ACTIVE_DELETE_MARKER = "0";

    private static final String DEFAULT_STATUS = "DRAFT";

    private static final String ENABLED = "ENABLED";

    private static final String DISABLED = "DISABLED";

    private static final String ARCHIVED = "ARCHIVED";

    private static final String DELETED = "DELETED";

    private static final String PUBLISHED = "PUBLISHED";

    private static final byte YES = 1;

    private static final byte NO = 0;

    private static final Set<String> FIELD_TYPES = Set.of("TEXT", "TEXTAREA", "NUMBER", "DECIMAL", "DATE",
            "DATETIME", "SELECT", "MULTI_SELECT", "RADIO", "CHECKBOX", "DICT", "RELATION", "SUB_TABLE",
            "ATTACHMENT", "IMAGE", "SERIAL", "BOOLEAN", "ADDRESS", "TAG", "JSON");

    private static final Set<String> OPTION_FIELD_TYPES = Set.of("SELECT", "MULTI_SELECT", "RADIO", "CHECKBOX",
            "TAG");

    private static final Set<String> DICT_FIELD_TYPES = Set.of("DICT");

    private static final Set<String> UNIQUE_UNSUPPORTED_TYPES = Set.of("TEXTAREA", "MULTI_SELECT", "ATTACHMENT",
            "IMAGE", "SUB_TABLE", "ADDRESS", "TAG", "JSON");

    private static final Map<String, String> PAGE_NAMES = Map.of(
            "LIST", "默认列表",
            "FORM", "默认表单",
            "DETAIL", "默认详情");

    private final IAppService appService;

    private final IModelService modelService;

    private final IFieldService fieldService;

    private final IFieldOptionService fieldOptionService;

    private final IPageSchemaService pageSchemaService;

    private final IMenuService menuService;

    private final IActionService actionService;

    private final IPublishVersionService publishVersionService;

    private final IRecordService recordService;

    private final PermissionService permissionService;

    private final ObjectMapper objectMapper;

    /**
     * 查询应用列表。
     */
    @Override
    public List<AppVO> listApps(Long systemId, String tenantId, String keyword, String status) {
        permissionService.requireOperation("APP_VIEW");
        return appService.lambdaQuery()
                .eq(App::getSystemId, systemId)
                .eq(App::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .filter(app -> !ARCHIVED.equals(app.getAppStatus()))
                .filter(app -> !StringUtils.hasText(tenantId) || Objects.equals(app.getTenantId(), Long.valueOf(tenantId)))
                .filter(app -> !StringUtils.hasText(status) || Objects.equals(app.getAppStatus(), status))
                .filter(app -> !StringUtils.hasText(keyword) || contains(app.getName(), keyword)
                        || contains(app.getCode(), keyword))
                .sorted(Comparator.comparing(App::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(App::getCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toAppVO)
                .toList();
    }

    /**
     * 创建应用草稿。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVO createApp(Long systemId, AppSaveBO saveBO) {
        permissionService.requireOperation("APP_CREATE");
        Long tenantId = resolveTenant(saveBO.getTenantId());
        ensureAppCodeAvailable(systemId, tenantId, saveBO.getCode(), null);
        String status = defaultText(saveBO.getStatus(), DEFAULT_STATUS);
        validateAppStatus(status);
        App app = new App()
                .setSystemId(systemId)
                .setTenantId(tenantId)
                .setName(saveBO.getName())
                .setCode(saveBO.getCode())
                .setIcon(saveBO.getIcon())
                .setDescription(saveBO.getDescription())
                .setAppStatus(status)
                .setModuleCount(0)
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setVersion(1)
                .setDeleteMarker(ACTIVE_DELETE_MARKER)
                .setCreatedBy(currentMemberId())
                .setUpdatedBy(currentMemberId())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        appService.save(app);
        return toAppVO(app);
    }

    /**
     * 查询应用详情。
     */
    @Override
    public AppVO getApp(Long systemId, Long appId) {
        permissionService.requireOperation("APP_VIEW");
        return toAppVO(activeApp(systemId, appId));
    }

    /**
     * 更新应用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVO updateApp(Long systemId, Long appId, AppUpdateBO updateBO) {
        permissionService.requireOperation("APP_EDIT");
        App app = writableApp(systemId, appId);
        ensureVersion(app.getVersion(), updateBO.getVersion(), ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
        app.setName(defaultText(updateBO.getName(), app.getName()))
                .setIcon(updateBO.getIcon())
                .setDescription(updateBO.getDescription())
                .setSortOrder(defaultInteger(updateBO.getSortOrder(), app.getSortOrder()))
                .setVersion(nextVersion(app.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        appService.updateById(app);
        return toAppVO(app);
    }

    /**
     * 变更应用状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVO changeAppStatus(Long systemId, Long appId, ConfigStatusBO statusBO) {
        permissionService.requireOperation("APP_STATUS");
        App app = activeApp(systemId, appId);
        ensureVersion(app.getVersion(), statusBO.getVersion(), ModuleConfigErrorCode.APP_STATUS_INVALID);
        validateAppStatus(statusBO.getTargetStatus());
        app.setAppStatus(statusBO.getTargetStatus())
                .setVersion(nextVersion(app.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        if (ARCHIVED.equals(statusBO.getTargetStatus())) {
            app.setDeleteMarker(String.valueOf(app.getAppId()));
        }
        appService.updateById(app);
        return toAppVO(app);
    }

    /**
     * 查询模块列表。
     */
    @Override
    public List<ModuleVO> listModules(Long systemId, Long appId, String keyword, String status) {
        permissionService.requireOperation("MODULE_VIEW");
        activeApp(systemId, appId);
        return modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getAppId, appId)
                .eq(Model::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .filter(model -> !ARCHIVED.equals(model.getModuleStatus()))
                .filter(model -> !StringUtils.hasText(status) || Objects.equals(model.getModuleStatus(), status))
                .filter(model -> !StringUtils.hasText(keyword) || contains(model.getName(), keyword)
                        || contains(model.getCode(), keyword))
                .sorted(Comparator.comparing(Model::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Model::getCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toModuleVO)
                .toList();
    }

    /**
     * 创建模块草稿。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleVO createModule(Long systemId, Long appId, ModuleSaveBO saveBO) {
        permissionService.requireOperation("MODULE_CREATE");
        App app = writableApp(systemId, appId);
        ensureModuleCodeAvailable(systemId, appId, saveBO.getCode(), null);
        Model model = new Model()
                .setSystemId(systemId)
                .setTenantId(app.getTenantId())
                .setAppId(appId)
                .setName(saveBO.getName())
                .setCode(saveBO.getCode())
                .setDescription(saveBO.getDescription())
                .setModuleStatus(DEFAULT_STATUS)
                .setTitleFieldId(parseLong(saveBO.getTitleFieldId()))
                .setRecordNoFieldId(parseLong(saveBO.getRecordNoFieldId()))
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setVersion(1)
                .setDeleteMarker(ACTIVE_DELETE_MARKER)
                .setCreatedBy(currentMemberId())
                .setUpdatedBy(currentMemberId())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        modelService.save(model);
        app.setModuleCount(moduleCount(systemId, appId))
                .setVersion(nextVersion(app.getVersion()))
                .setUpdatedAt(LocalDateTime.now());
        appService.updateById(app);
        return toModuleVO(model);
    }

    /**
     * 查询模块详情。
     */
    @Override
    public ModuleVO getModule(Long systemId, Long moduleId) {
        permissionService.requireOperation("MODULE_VIEW");
        return toModuleVO(activeModule(systemId, moduleId));
    }

    /**
     * 更新模块。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleVO updateModule(Long systemId, Long moduleId, ModuleUpdateBO updateBO) {
        permissionService.requireOperation("MODULE_EDIT");
        Model model = writableModule(systemId, moduleId);
        ensureVersion(model.getVersion(), updateBO.getVersion(), ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
        validateFieldReferenceIfPresent(systemId, moduleId, parseLong(updateBO.getTitleFieldId()));
        validateFieldReferenceIfPresent(systemId, moduleId, parseLong(updateBO.getRecordNoFieldId()));
        model.setName(defaultText(updateBO.getName(), model.getName()))
                .setDescription(updateBO.getDescription())
                .setTitleFieldId(parseLongOrDefault(updateBO.getTitleFieldId(), model.getTitleFieldId()))
                .setRecordNoFieldId(parseLongOrDefault(updateBO.getRecordNoFieldId(), model.getRecordNoFieldId()))
                .setSortOrder(defaultInteger(updateBO.getSortOrder(), model.getSortOrder()))
                .setVersion(nextVersion(model.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        modelService.updateById(model);
        return toModuleVO(model);
    }

    /**
     * 变更模块状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModuleVO changeModuleStatus(Long systemId, Long moduleId, ConfigStatusBO statusBO) {
        permissionService.requireOperation("MODULE_STATUS");
        Model model = activeModule(systemId, moduleId);
        ensureVersion(model.getVersion(), statusBO.getVersion(), ModuleConfigErrorCode.MODULE_STATUS_INVALID);
        validateModuleStatus(statusBO.getTargetStatus());
        model.setModuleStatus(statusBO.getTargetStatus())
                .setVersion(nextVersion(model.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        if (ARCHIVED.equals(statusBO.getTargetStatus())) {
            model.setDeleteMarker(String.valueOf(model.getModuleId()));
        }
        modelService.updateById(model);
        return toModuleVO(model);
    }

    /**
     * 查询模块字段。
     */
    @Override
    public List<FieldVO> listFields(Long systemId, Long moduleId) {
        permissionService.requireOperation("FIELD_VIEW");
        activeModule(systemId, moduleId);
        return activeFields(systemId, moduleId).stream()
                .sorted(Comparator.comparing(Field::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Field::getCode, Comparator.nullsLast(String::compareTo)))
                .map(this::toFieldVO)
                .toList();
    }

    /**
     * 创建字段草稿。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldVO createField(Long systemId, Long moduleId, FieldSaveBO saveBO) {
        permissionService.requireOperation("FIELD_CREATE");
        Model model = writableModule(systemId, moduleId);
        validateFieldType(saveBO.getFieldType(), saveBO.getUnique());
        validateFieldConfig(saveBO.getFieldType(), saveBO.getRelationConfig(), saveBO.getSerialConfig());
        ensureFieldCodeAvailable(systemId, moduleId, saveBO.getCode(), null);
        Field field = new Field()
                .setSystemId(systemId)
                .setTenantId(model.getTenantId())
                .setModuleId(moduleId)
                .setName(saveBO.getName())
                .setCode(saveBO.getCode())
                .setFieldType(saveBO.getFieldType())
                .setRequiredFlag(flag(saveBO.getRequired()))
                .setUniqueFlag(flag(saveBO.getUnique()))
                .setIndexFlag(flag(saveBO.getIndexed()))
                .setDefaultValueJson(toJson(saveBO.getDefaultValue()))
                .setDictTypeId(parseLong(saveBO.getDictTypeId()))
                .setRelationConfigJson(toJson(saveBO.getRelationConfig()))
                .setSubTableConfigJson(toJson(saveBO.getSubTableConfig()))
                .setSerialConfigJson(toJson(saveBO.getSerialConfig()))
                .setValidationJson(toJson(saveBO.getValidation()))
                .setDisplayConfigJson(toJson(saveBO.getDisplayConfig()))
                .setFieldStatus(defaultText(saveBO.getStatus(), ENABLED))
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setVersion(1)
                .setDeleteMarker(ACTIVE_DELETE_MARKER)
                .setCreatedBy(currentMemberId())
                .setUpdatedBy(currentMemberId())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        validateFieldStatus(field.getFieldStatus());
        fieldService.save(field);
        replaceOptions(field, saveBO.getOptions());
        touchModule(model);
        return toFieldVO(field);
    }

    /**
     * 更新字段。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldVO updateField(Long systemId, Long moduleId, Long fieldId, FieldUpdateBO updateBO) {
        permissionService.requireOperation("FIELD_EDIT");
        Field field = activeField(systemId, moduleId, fieldId);
        ensureVersion(field.getVersion(), updateBO.getVersion(), ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
        validateFieldType(field.getFieldType(), updateBO.getUnique());
        validateFieldConfig(field.getFieldType(), updateBO.getRelationConfig(), updateBO.getSerialConfig());
        field.setName(defaultText(updateBO.getName(), field.getName()))
                .setRequiredFlag(flagOrDefault(updateBO.getRequired(), field.getRequiredFlag()))
                .setUniqueFlag(flagOrDefault(updateBO.getUnique(), field.getUniqueFlag()))
                .setIndexFlag(flagOrDefault(updateBO.getIndexed(), field.getIndexFlag()))
                .setDefaultValueJson(toJsonOrDefault(updateBO.getDefaultValue(), field.getDefaultValueJson()))
                .setDictTypeId(parseLongOrDefault(updateBO.getDictTypeId(), field.getDictTypeId()))
                .setRelationConfigJson(toJsonOrDefault(updateBO.getRelationConfig(), field.getRelationConfigJson()))
                .setSubTableConfigJson(toJsonOrDefault(updateBO.getSubTableConfig(), field.getSubTableConfigJson()))
                .setSerialConfigJson(toJsonOrDefault(updateBO.getSerialConfig(), field.getSerialConfigJson()))
                .setValidationJson(toJsonOrDefault(updateBO.getValidation(), field.getValidationJson()))
                .setDisplayConfigJson(toJsonOrDefault(updateBO.getDisplayConfig(), field.getDisplayConfigJson()))
                .setSortOrder(defaultInteger(updateBO.getSortOrder(), field.getSortOrder()))
                .setVersion(nextVersion(field.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        fieldService.updateById(field);
        if (Objects.nonNull(updateBO.getOptions())) {
            replaceOptions(field, updateBO.getOptions());
        }
        touchModule(activeModule(systemId, moduleId));
        return toFieldVO(field);
    }

    /**
     * 变更字段状态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldVO changeFieldStatus(Long systemId, Long moduleId, Long fieldId, ConfigStatusBO statusBO) {
        permissionService.requireOperation("FIELD_STATUS");
        Field field = activeField(systemId, moduleId, fieldId);
        ensureVersion(field.getVersion(), statusBO.getVersion(), ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
        validateFieldStatus(statusBO.getTargetStatus());
        if (DELETED.equals(statusBO.getTargetStatus()) && hasRecordData(systemId, moduleId)) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_DELETE_HAS_DATA);
        }
        field.setFieldStatus(statusBO.getTargetStatus())
                .setVersion(nextVersion(field.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        if (DELETED.equals(statusBO.getTargetStatus())) {
            field.setDeleteMarker(String.valueOf(field.getFieldId()));
        }
        fieldService.updateById(field);
        touchModule(activeModule(systemId, moduleId));
        return toFieldVO(field);
    }

    /**
     * 查询字段类型。
     */
    @Override
    public List<FieldTypeVO> fieldTypes(Long systemId) {
        permissionService.requireOperation("FIELD_TYPE_VIEW");
        return FIELD_TYPES.stream()
                .sorted()
                .map(type -> FieldTypeVO.builder()
                        .code(type)
                        .name(type)
                        .uniqueSupported(!UNIQUE_UNSUPPORTED_TYPES.contains(type))
                        .optionSupported(OPTION_FIELD_TYPES.contains(type))
                        .dictSupported(DICT_FIELD_TYPES.contains(type))
                        .build())
                .toList();
    }

    /**
     * 执行发布检查，不生成版本。
     */
    @Override
    public PublishCheckResultVO publishCheck(Long systemId, Long moduleId) {
        permissionService.requireOperation("MODULE_PUBLISH");
        Model model = activeModule(systemId, moduleId);
        return buildPublishCheck(model);
    }

    /**
     * 发布模块配置版本。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PublishVersionVO publish(Long systemId, Long moduleId, PublishRequestBO requestBO) {
        permissionService.requireOperation("MODULE_PUBLISH");
        Model model = activeModule(systemId, moduleId);
        ensureVersion(model.getVersion(), requestBO.getModuleVersion(), ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
        PublishCheckResultVO checkResult = buildPublishCheck(model);
        if (!Boolean.TRUE.equals(checkResult.getPassed())) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_PUBLISH_CHECK_FAILED,
                    "模块发布检查失败，requestId=" + checkResult.getRequestId());
        }
        PublishVersion publishVersion = new PublishVersion()
                .setSystemId(systemId)
                .setTenantId(model.getTenantId())
                .setAppId(model.getAppId())
                .setModuleId(moduleId)
                .setVersionNo(checkResult.getNextVersionNo())
                .setPublishStatus(PUBLISHED)
                .setFieldSnapshotJson(toJson(activeFields(systemId, moduleId).stream().map(this::toFieldVO).toList()))
                .setPageSnapshotJson(toJson(pageSchemas(systemId, moduleId).stream().map(this::toPageSchemaVO).toList()))
                .setMenuActionSnapshotJson(toJson(menuActionSnapshot(systemId, moduleId)))
                .setFlowBindingSnapshotJson(toJson(flowBindingSnapshot(model)))
                .setExportTemplateSnapshotJson("[]")
                .setCheckResultJson(toJson(checkResult))
                .setPublishRemark(requestBO.getPublishRemark())
                .setPublishedBy(currentMemberId())
                .setPublishedAt(LocalDateTime.now())
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        publishVersionService.save(publishVersion);
        model.setCurrentPublishVersionId(publishVersion.getPublishVersionId())
                .setModuleStatus(PUBLISHED)
                .setVersion(nextVersion(model.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        modelService.updateById(model);
        return toPublishVersionVO(publishVersion);
    }

    /**
     * 查询页面 schema。
     */
    @Override
    public PageSchemaVO getPageSchema(Long systemId, Long moduleId, String pageType) {
        permissionService.requireOperation("PAGE_VIEW");
        activeModule(systemId, moduleId);
        PageSchema schema = pageSchema(systemId, moduleId, normalizePageType(pageType));
        return Objects.isNull(schema) ? emptyPageSchema(moduleId, normalizePageType(pageType)) : toPageSchemaVO(schema);
    }

    /**
     * 保存页面 schema。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageSchemaVO savePageSchema(Long systemId, Long moduleId, String pageType, PageSchemaSaveBO saveBO) {
        permissionService.requireOperation("PAGE_EDIT");
        Model model = writableModule(systemId, moduleId);
        String normalizedType = normalizePageType(pageType);
        validatePageFieldRefs(systemId, moduleId, saveBO.getSchema());
        PageSchema schema = pageSchema(systemId, moduleId, normalizedType);
        if (Objects.isNull(schema)) {
            schema = new PageSchema()
                    .setSystemId(systemId)
                    .setTenantId(model.getTenantId())
                    .setModuleId(moduleId)
                    .setPageType(normalizedType)
                    .setSchemaCode("default")
                    .setSchemaName(PAGE_NAMES.get(normalizedType))
                    .setDraftVersion(1)
                    .setSchemaStatus(DEFAULT_STATUS)
                    .setVersion(1)
                    .setDeleteMarker(ACTIVE_DELETE_MARKER)
                    .setCreatedBy(currentMemberId())
                    .setCreatedAt(LocalDateTime.now());
            pageSchemaService.save(schema);
        } else {
            ensureVersion(schema.getDraftVersion(), saveBO.getDraftVersion(),
                    ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
            schema.setDraftVersion(nextVersion(schema.getDraftVersion()))
                    .setVersion(nextVersion(schema.getVersion()));
        }
        schema.setSchemaJson(toJson(saveBO.getSchema()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        pageSchemaService.updateById(schema);
        touchModule(model);
        return toPageSchemaVO(schema);
    }

    /**
     * 保存运行菜单。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MenuConfigVO saveMenu(Long systemId, Long moduleId, MenuConfigSaveBO saveBO) {
        permissionService.requireOperation("MENU_EDIT");
        Model model = writableModule(systemId, moduleId);
        ensureMenuCodeAvailable(systemId, saveBO.getCode(), moduleId);
        Menu menu = menuService.lambdaQuery()
                .eq(Menu::getSystemId, systemId)
                .eq(Menu::getModuleId, moduleId)
                .eq(Menu::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(menu)) {
            menu = new Menu()
                    .setSystemId(systemId)
                    .setTenantId(model.getTenantId())
                    .setAppId(model.getAppId())
                    .setModuleId(moduleId)
                    .setMenuType("RUNTIME")
                    .setDeleteMarker(ACTIVE_DELETE_MARKER)
                    .setCreatedBy(currentMemberId())
                    .setCreatedAt(LocalDateTime.now());
            menuService.save(menu);
        }
        menu.setParentId(parseLongOrDefault(saveBO.getMenuParentId(), 0L))
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setRoutePath(saveBO.getRoutePath())
                .setIcon(saveBO.getIcon())
                .setVisibleFlag(flagOrDefault(saveBO.getVisible(), YES))
                .setEnabledFlag(flagOrDefault(saveBO.getEnabled(), YES))
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        menuService.updateById(menu);
        touchModule(model);
        return toMenuConfigVO(menu);
    }

    /**
     * 保存动作配置。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ActionConfigVO> saveActions(Long systemId, Long moduleId, ActionConfigSaveBO saveBO) {
        permissionService.requireOperation("ACTION_EDIT");
        Model model = writableModule(systemId, moduleId);
        Set<String> codes = new LinkedHashSet<>();
        for (ActionConfigBO action : defaultList(saveBO.getActions())) {
            if (!codes.add(action.getActionCode())) {
                throw new BusinessException(ModuleConfigErrorCode.MODULE_MENU_CODE_DUPLICATED,
                        "动作编码重复：" + action.getActionCode());
            }
        }
        actionService.lambdaUpdate()
                .eq(Action::getSystemId, systemId)
                .eq(Action::getModuleId, moduleId)
                .eq(Action::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .set(Action::getDeleteMarker, "REPLACED")
                .update();
        List<Action> actions = defaultList(saveBO.getActions()).stream()
                .map(actionBO -> new Action()
                        .setSystemId(systemId)
                        .setTenantId(model.getTenantId())
                        .setModuleId(moduleId)
                        .setActionCode(actionBO.getActionCode())
                        .setActionName(actionBO.getActionName())
                        .setActionType(defaultText(actionBO.getActionType(), "BUTTON"))
                        .setDangerFlag(flag(actionBO.getDanger()))
                        .setConfirmRequired(flag(actionBO.getConfirmRequired()))
                        .setEnabledFlag(flagOrDefault(actionBO.getEnabled(), YES))
                        .setConfigJson(toJson(actionBO.getConfig()))
                        .setSortOrder(defaultInteger(actionBO.getSortOrder(), 100))
                        .setDeleteMarker(ACTIVE_DELETE_MARKER)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now()))
                .toList();
        if (!actions.isEmpty()) {
            actionService.saveBatch(actions);
        }
        touchModule(model);
        return actions.stream().map(this::toActionConfigVO).toList();
    }

    private PublishCheckResultVO buildPublishCheck(Model model) {
        List<CheckIssueVO> issues = new ArrayList<>();
        List<Field> fields = activeFields(model.getSystemId(), model.getModuleId());
        if (fields.isEmpty()) {
            issues.add(issue("MODULE_FIELD_EMPTY", "MODULE", model.getModuleId(), model.getCode(), "模块至少需要一个启用字段"));
        }
        fields.forEach(field -> {
            if (!FIELD_TYPES.contains(field.getFieldType())) {
                issues.add(issue("FIELD_TYPE_UNSUPPORTED", "FIELD", field.getFieldId(), field.getCode(),
                        "字段类型不支持"));
            }
            if (Objects.equals(field.getUniqueFlag(), YES) && UNIQUE_UNSUPPORTED_TYPES.contains(field.getFieldType())) {
                issues.add(issue("FIELD_UNIQUE_UNSUPPORTED", "FIELD", field.getFieldId(), field.getCode(),
                        "当前字段类型不支持唯一约束"));
            }
        });
        pageSchemas(model.getSystemId(), model.getModuleId()).forEach(schema -> {
            try {
                validatePageFieldRefs(model.getSystemId(), model.getModuleId(), readJson(schema.getSchemaJson()));
            } catch (BusinessException ex) {
                issues.add(issue("MODULE_PAGE_FIELD_MISSING", "PAGE", schema.getSchemaId(), schema.getSchemaCode(),
                        ex.getMessage()));
            }
        });
        Menu menu = menuService.lambdaQuery()
                .eq(Menu::getSystemId, model.getSystemId())
                .eq(Menu::getModuleId, model.getModuleId())
                .eq(Menu::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(menu)) {
            issues.add(issue("MODULE_MENU_MISSING", "MENU", model.getModuleId(), model.getCode(), "模块运行菜单未配置"));
        }
        return PublishCheckResultVO.builder()
                .requestId(currentRequestId())
                .moduleId(toId(model.getModuleId()))
                .passed(issues.isEmpty())
                .nextVersionNo(nextPublishVersionNo(model.getSystemId(), model.getModuleId()))
                .issues(issues)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private void validatePageFieldRefs(Long systemId, Long moduleId, Object schema) {
        Set<String> fieldIds = new LinkedHashSet<>();
        Set<String> fieldCodes = new LinkedHashSet<>();
        collectFieldReferences(schema, null, fieldIds, fieldCodes);
        if (fieldIds.isEmpty() && fieldCodes.isEmpty()) {
            return;
        }
        Map<String, Field> byId = new LinkedHashMap<>();
        Map<String, Field> byCode = new LinkedHashMap<>();
        activeFields(systemId, moduleId).forEach(field -> {
            byId.put(String.valueOf(field.getFieldId()), field);
            byCode.put(field.getCode(), field);
        });
        List<String> missing = new ArrayList<>();
        fieldIds.stream().filter(id -> !byId.containsKey(id)).forEach(missing::add);
        fieldCodes.stream().filter(code -> !byCode.containsKey(code)).forEach(missing::add);
        if (!missing.isEmpty()) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_PAGE_FIELD_MISSING,
                    "页面引用字段不存在：" + String.join(",", missing));
        }
    }

    @SuppressWarnings("unchecked")
    private void collectFieldReferences(Object node, String key, Set<String> fieldIds, Set<String> fieldCodes) {
        if (Objects.isNull(node)) {
            return;
        }
        if (node instanceof Map<?, ?> map) {
            map.forEach((entryKey, value) -> collectFieldReferences(value, String.valueOf(entryKey), fieldIds, fieldCodes));
            return;
        }
        if (node instanceof List<?> list) {
            list.forEach(item -> collectFieldReferences(item, key, fieldIds, fieldCodes));
            return;
        }
        String value = String.valueOf(node);
        if (!StringUtils.hasText(value) || !StringUtils.hasText(key)) {
            return;
        }
        String normalizedKey = key.toLowerCase();
        if (normalizedKey.equals("fieldid") || normalizedKey.equals("fieldids")) {
            fieldIds.add(value);
        } else if (normalizedKey.equals("fieldcode") || normalizedKey.equals("fieldcodes")) {
            fieldCodes.add(value);
        }
    }

    private void replaceOptions(Field field, List<FieldOptionBO> options) {
        fieldOptionService.lambdaUpdate()
                .eq(FieldOption::getFieldId, field.getFieldId())
                .eq(FieldOption::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .set(FieldOption::getDeleteMarker, "REPLACED")
                .update();
        if (CollectionUtils.isEmpty(options)) {
            return;
        }
        Set<String> codes = new LinkedHashSet<>();
        List<FieldOption> rows = options.stream()
                .peek(option -> {
                    if (!codes.add(option.getCode())) {
                        throw new BusinessException(ModuleConfigErrorCode.FIELD_CODE_DUPLICATED,
                                "字段选项编码重复：" + option.getCode());
                    }
                })
                .map(option -> new FieldOption()
                        .setFieldId(field.getFieldId())
                        .setCode(option.getCode())
                        .setLabel(option.getLabel())
                        .setValue(option.getValue())
                        .setColor(option.getColor())
                        .setEnabledFlag(flagOrDefault(option.getEnabled(), YES))
                        .setSortOrder(defaultInteger(option.getSortOrder(), 100))
                        .setDeleteMarker(ACTIVE_DELETE_MARKER)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now()))
                .toList();
        fieldOptionService.saveBatch(rows);
    }

    private App activeApp(Long systemId, Long appId) {
        App app = appService.lambdaQuery()
                .eq(App::getSystemId, systemId)
                .eq(App::getAppId, appId)
                .eq(App::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(app)) {
            throw new BusinessException(ModuleConfigErrorCode.APP_NOT_FOUND);
        }
        return app;
    }

    private App writableApp(Long systemId, Long appId) {
        App app = activeApp(systemId, appId);
        if (ARCHIVED.equals(app.getAppStatus())) {
            throw new BusinessException(ModuleConfigErrorCode.APP_STATUS_INVALID);
        }
        return app;
    }

    private Model activeModule(Long systemId, Long moduleId) {
        Model model = modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getModuleId, moduleId)
                .eq(Model::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(model)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_NOT_FOUND);
        }
        return model;
    }

    private Model writableModule(Long systemId, Long moduleId) {
        Model model = activeModule(systemId, moduleId);
        if (ARCHIVED.equals(model.getModuleStatus())) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_STATUS_INVALID);
        }
        return model;
    }

    private Field activeField(Long systemId, Long moduleId, Long fieldId) {
        Field field = fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .eq(Field::getModuleId, moduleId)
                .eq(Field::getFieldId, fieldId)
                .eq(Field::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(field) || DELETED.equals(field.getFieldStatus())) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_NOT_FOUND);
        }
        return field;
    }

    private List<Field> activeFields(Long systemId, Long moduleId) {
        return fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .eq(Field::getModuleId, moduleId)
                .eq(Field::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .filter(field -> !DELETED.equals(field.getFieldStatus()))
                .toList();
    }

    private List<PageSchema> pageSchemas(Long systemId, Long moduleId) {
        return pageSchemaService.lambdaQuery()
                .eq(PageSchema::getSystemId, systemId)
                .eq(PageSchema::getModuleId, moduleId)
                .eq(PageSchema::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list();
    }

    private PageSchema pageSchema(Long systemId, Long moduleId, String pageType) {
        return pageSchemaService.lambdaQuery()
                .eq(PageSchema::getSystemId, systemId)
                .eq(PageSchema::getModuleId, moduleId)
                .eq(PageSchema::getPageType, pageType)
                .eq(PageSchema::getSchemaCode, "default")
                .eq(PageSchema::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
    }

    private void ensureAppCodeAvailable(Long systemId, Long tenantId, String code, Long excludeId) {
        App exists = appService.lambdaQuery()
                .eq(App::getSystemId, systemId)
                .eq(App::getTenantId, tenantId)
                .eq(App::getCode, code)
                .eq(App::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.nonNull(exists) && !Objects.equals(exists.getAppId(), excludeId)) {
            throw new BusinessException(ModuleConfigErrorCode.APP_CODE_DUPLICATED);
        }
    }

    private void ensureModuleCodeAvailable(Long systemId, Long appId, String code, Long excludeId) {
        Model exists = modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getAppId, appId)
                .eq(Model::getCode, code)
                .eq(Model::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.nonNull(exists) && !Objects.equals(exists.getModuleId(), excludeId)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_CODE_DUPLICATED);
        }
    }

    private void ensureFieldCodeAvailable(Long systemId, Long moduleId, String code, Long excludeId) {
        Field exists = fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .eq(Field::getModuleId, moduleId)
                .eq(Field::getCode, code)
                .eq(Field::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.nonNull(exists) && !Objects.equals(exists.getFieldId(), excludeId)) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_CODE_DUPLICATED);
        }
    }

    private void ensureMenuCodeAvailable(Long systemId, String code, Long moduleId) {
        List<Menu> menus = menuService.lambdaQuery()
                .eq(Menu::getSystemId, systemId)
                .eq(Menu::getCode, code)
                .eq(Menu::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list();
        boolean duplicate = menus.stream().anyMatch(menu -> !Objects.equals(menu.getModuleId(), moduleId));
        if (duplicate) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_MENU_CODE_DUPLICATED);
        }
    }

    private void validateFieldType(String fieldType, Boolean unique) {
        if (!FIELD_TYPES.contains(fieldType)) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_TYPE_UNSUPPORTED);
        }
        if (Boolean.TRUE.equals(unique) && UNIQUE_UNSUPPORTED_TYPES.contains(fieldType)) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_TYPE_UNSUPPORTED, "当前字段类型不支持唯一约束");
        }
    }

    private void validateFieldConfig(String fieldType, Object relationConfig, Object serialConfig) {
        if ("RELATION".equals(fieldType) && Objects.isNull(relationConfig)) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_RELATION_INVALID);
        }
        if ("SERIAL".equals(fieldType) && Objects.isNull(serialConfig)) {
            throw new BusinessException(ModuleConfigErrorCode.FIELD_SERIAL_RULE_INVALID);
        }
    }

    private void validateFieldReferenceIfPresent(Long systemId, Long moduleId, Long fieldId) {
        if (Objects.nonNull(fieldId)) {
            activeField(systemId, moduleId, fieldId);
        }
    }

    private void validateAppStatus(String status) {
        if (!Set.of(DEFAULT_STATUS, ENABLED, DISABLED, ARCHIVED).contains(status)) {
            throw new BusinessException(ModuleConfigErrorCode.APP_STATUS_INVALID);
        }
    }

    private void validateModuleStatus(String status) {
        if (!Set.of(DEFAULT_STATUS, PUBLISHED, DISABLED, ARCHIVED).contains(status)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_STATUS_INVALID);
        }
    }

    private void validateFieldStatus(String status) {
        if (!Set.of(ENABLED, DISABLED, DELETED).contains(status)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_STATUS_INVALID);
        }
    }

    private void ensureVersion(Integer currentVersion, Integer requestVersion, ModuleConfigErrorCode errorCode) {
        if (!Objects.equals(currentVersion, requestVersion)) {
            throw new BusinessException(errorCode);
        }
    }

    private boolean hasRecordData(Long systemId, Long moduleId) {
        return recordService.lambdaQuery()
                .eq(com.unique.examine.module.base.entity.Record::getSystemId, systemId)
                .eq(com.unique.examine.module.base.entity.Record::getModuleId, moduleId)
                .count() > 0;
    }

    private Integer moduleCount(Long systemId, Long appId) {
        return Math.toIntExact(modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getAppId, appId)
                .eq(Model::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .count());
    }

    private Integer fieldCount(Long systemId, Long moduleId) {
        return Math.toIntExact(fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .eq(Field::getModuleId, moduleId)
                .eq(Field::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .count());
    }

    private Integer pageSchemaCount(Long systemId, Long moduleId) {
        return Math.toIntExact(pageSchemaService.lambdaQuery()
                .eq(PageSchema::getSystemId, systemId)
                .eq(PageSchema::getModuleId, moduleId)
                .eq(PageSchema::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .count());
    }

    private Integer nextPublishVersionNo(Long systemId, Long moduleId) {
        PublishVersion latest = publishVersionService.lambdaQuery()
                .eq(PublishVersion::getSystemId, systemId)
                .eq(PublishVersion::getModuleId, moduleId)
                .orderByDesc(PublishVersion::getVersionNo)
                .last("limit 1")
                .one();
        return Objects.isNull(latest) ? 1 : latest.getVersionNo() + 1;
    }

    private void touchModule(Model model) {
        model.setVersion(nextVersion(model.getVersion()))
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        modelService.updateById(model);
    }

    private Map<String, Object> menuActionSnapshot(Long systemId, Long moduleId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("menus", menuService.lambdaQuery()
                .eq(Menu::getSystemId, systemId)
                .eq(Menu::getModuleId, moduleId)
                .eq(Menu::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .map(this::toMenuConfigVO)
                .toList());
        snapshot.put("actions", actionService.lambdaQuery()
                .eq(Action::getSystemId, systemId)
                .eq(Action::getModuleId, moduleId)
                .eq(Action::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .map(this::toActionConfigVO)
                .toList());
        return snapshot;
    }

    private Map<String, Object> flowBindingSnapshot(Model model) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("flowBindingId", toId(model.getFlowBindingId()));
        return snapshot;
    }

    private AppVO toAppVO(App app) {
        return AppVO.builder()
                .appId(toId(app.getAppId()))
                .systemId(toId(app.getSystemId()))
                .tenantId(toId(app.getTenantId()))
                .name(app.getName())
                .code(app.getCode())
                .icon(app.getIcon())
                .description(app.getDescription())
                .status(app.getAppStatus())
                .moduleCount(app.getModuleCount())
                .publishedVersion(toId(app.getCurrentAppVersionId()))
                .sortOrder(app.getSortOrder())
                .version(app.getVersion())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    private ModuleVO toModuleVO(Model model) {
        return ModuleVO.builder()
                .moduleId(toId(model.getModuleId()))
                .systemId(toId(model.getSystemId()))
                .tenantId(toId(model.getTenantId()))
                .appId(toId(model.getAppId()))
                .name(model.getName())
                .code(model.getCode())
                .description(model.getDescription())
                .status(model.getModuleStatus())
                .publishedVersion(toId(model.getCurrentPublishVersionId()))
                .flowBindingId(toId(model.getFlowBindingId()))
                .titleFieldId(toId(model.getTitleFieldId()))
                .recordNoFieldId(toId(model.getRecordNoFieldId()))
                .fieldCount(fieldCount(model.getSystemId(), model.getModuleId()))
                .pageSchemaCount(pageSchemaCount(model.getSystemId(), model.getModuleId()))
                .sortOrder(model.getSortOrder())
                .version(model.getVersion())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .build();
    }

    private FieldVO toFieldVO(Field field) {
        return FieldVO.builder()
                .fieldId(toId(field.getFieldId()))
                .systemId(toId(field.getSystemId()))
                .tenantId(toId(field.getTenantId()))
                .moduleId(toId(field.getModuleId()))
                .name(field.getName())
                .code(field.getCode())
                .fieldType(field.getFieldType())
                .required(Objects.equals(field.getRequiredFlag(), YES))
                .unique(Objects.equals(field.getUniqueFlag(), YES))
                .indexed(Objects.equals(field.getIndexFlag(), YES))
                .defaultValue(field.getDefaultValueJson())
                .dictTypeId(toId(field.getDictTypeId()))
                .relationConfig(field.getRelationConfigJson())
                .subTableConfig(field.getSubTableConfigJson())
                .serialConfig(field.getSerialConfigJson())
                .validation(field.getValidationJson())
                .displayConfig(field.getDisplayConfigJson())
                .status(field.getFieldStatus())
                .sortOrder(field.getSortOrder())
                .version(field.getVersion())
                .options(fieldOptions(field.getFieldId()))
                .createdAt(field.getCreatedAt())
                .updatedAt(field.getUpdatedAt())
                .build();
    }

    private List<FieldOptionVO> fieldOptions(Long fieldId) {
        return fieldOptionService.lambdaQuery()
                .eq(FieldOption::getFieldId, fieldId)
                .eq(FieldOption::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .sorted(Comparator.comparing(FieldOption::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(FieldOption::getCode, Comparator.nullsLast(String::compareTo)))
                .map(option -> FieldOptionVO.builder()
                        .optionId(toId(option.getOptionId()))
                        .fieldId(toId(option.getFieldId()))
                        .code(option.getCode())
                        .label(option.getLabel())
                        .value(option.getValue())
                        .color(option.getColor())
                        .enabled(Objects.equals(option.getEnabledFlag(), YES))
                        .sortOrder(option.getSortOrder())
                        .build())
                .toList();
    }

    private PageSchemaVO toPageSchemaVO(PageSchema schema) {
        return PageSchemaVO.builder()
                .schemaId(toId(schema.getSchemaId()))
                .moduleId(toId(schema.getModuleId()))
                .pageType(schema.getPageType())
                .schemaCode(schema.getSchemaCode())
                .schemaName(schema.getSchemaName())
                .schema(schema.getSchemaJson())
                .draftVersion(schema.getDraftVersion())
                .status(schema.getSchemaStatus())
                .version(schema.getVersion())
                .updatedAt(schema.getUpdatedAt())
                .build();
    }

    private PageSchemaVO emptyPageSchema(Long moduleId, String pageType) {
        return PageSchemaVO.builder()
                .moduleId(toId(moduleId))
                .pageType(pageType)
                .schemaCode("default")
                .schemaName(PAGE_NAMES.get(pageType))
                .schema("{}")
                .draftVersion(0)
                .status(DEFAULT_STATUS)
                .version(0)
                .build();
    }

    private MenuConfigVO toMenuConfigVO(Menu menu) {
        return MenuConfigVO.builder()
                .menuId(toId(menu.getMenuId()))
                .parentId(toId(menu.getParentId()))
                .appId(toId(menu.getAppId()))
                .moduleId(toId(menu.getModuleId()))
                .code(menu.getCode())
                .name(menu.getName())
                .routePath(menu.getRoutePath())
                .icon(menu.getIcon())
                .visible(Objects.equals(menu.getVisibleFlag(), YES))
                .enabled(Objects.equals(menu.getEnabledFlag(), YES))
                .sortOrder(menu.getSortOrder())
                .build();
    }

    private ActionConfigVO toActionConfigVO(Action action) {
        return ActionConfigVO.builder()
                .actionId(toId(action.getActionId()))
                .moduleId(toId(action.getModuleId()))
                .actionCode(action.getActionCode())
                .actionName(action.getActionName())
                .actionType(action.getActionType())
                .danger(Objects.equals(action.getDangerFlag(), YES))
                .confirmRequired(Objects.equals(action.getConfirmRequired(), YES))
                .enabled(Objects.equals(action.getEnabledFlag(), YES))
                .config(action.getConfigJson())
                .sortOrder(action.getSortOrder())
                .build();
    }

    private PublishVersionVO toPublishVersionVO(PublishVersion publishVersion) {
        return PublishVersionVO.builder()
                .publishVersionId(toId(publishVersion.getPublishVersionId()))
                .moduleId(toId(publishVersion.getModuleId()))
                .versionNo(publishVersion.getVersionNo())
                .publishStatus(publishVersion.getPublishStatus())
                .publishRemark(publishVersion.getPublishRemark())
                .checkResult(publishVersion.getCheckResultJson())
                .publishedAt(publishVersion.getPublishedAt())
                .build();
    }

    private CheckIssueVO issue(String code, String targetType, Long targetId, String targetCode, String message) {
        return CheckIssueVO.builder()
                .code(code)
                .level("ERROR")
                .targetType(targetType)
                .targetId(toId(targetId))
                .targetCode(targetCode)
                .message(message)
                .build();
    }

    private Object readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException ex) {
            return Map.of("invalidJson", json);
        }
    }

    private String toJson(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT, "配置 JSON 序列化失败");
        }
    }

    private String toJsonOrDefault(Object value, String defaultValue) {
        return Objects.isNull(value) ? defaultValue : toJson(value);
    }

    private String normalizePageType(String pageType) {
        String normalized = pageType.toUpperCase();
        if (!PAGE_NAMES.containsKey(normalized)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT, "页面类型不支持");
        }
        return normalized;
    }

    private Long resolveTenant(String tenantId) {
        if (StringUtils.hasText(tenantId)) {
            return Long.valueOf(tenantId);
        }
        RequestContext context = RequestContextHolder.get();
        return Objects.nonNull(context) && StringUtils.hasText(context.getTenantId()) ? Long.valueOf(context.getTenantId()) : 0L;
    }

    private Long currentMemberId() {
        RequestContext context = RequestContextHolder.get();
        return Objects.nonNull(context) && StringUtils.hasText(context.getMemberId()) ? Long.valueOf(context.getMemberId()) : 0L;
    }

    private String currentRequestId() {
        RequestContext context = RequestContextHolder.get();
        return Objects.nonNull(context) && StringUtils.hasText(context.getRequestId()) ? context.getRequestId() : "local";
    }

    private Long parseLong(String value) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }

    private Long parseLongOrDefault(String value, Long defaultValue) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : defaultValue;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return Objects.nonNull(value) ? value : defaultValue;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer nextVersion(Integer version) {
        return Objects.isNull(version) ? 1 : version + 1;
    }

    private Byte flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? YES : NO;
    }

    private Byte flagOrDefault(Boolean value, Byte defaultValue) {
        return Objects.isNull(value) ? defaultValue : flag(value);
    }

    private String toId(Long id) {
        return Objects.isNull(id) ? null : String.valueOf(id);
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source) && source.contains(keyword);
    }

    private <T> List<T> defaultList(List<T> list) {
        return Objects.isNull(list) ? List.of() : list;
    }
}
