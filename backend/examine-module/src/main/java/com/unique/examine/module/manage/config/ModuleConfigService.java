package com.unique.examine.module.manage.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.module.base.entity.ModuleActionConfig;
import com.unique.examine.module.base.entity.ModuleDefinition;
import com.unique.examine.module.base.entity.ModuleDictItem;
import com.unique.examine.module.base.entity.ModuleDictType;
import com.unique.examine.module.base.entity.ModuleFieldDefinition;
import com.unique.examine.module.base.entity.ModuleGroup;
import com.unique.examine.module.base.entity.ModuleImportExportConfig;
import com.unique.examine.module.base.entity.ModuleListScene;
import com.unique.examine.module.base.entity.ModulePrintTemplate;
import com.unique.examine.module.base.entity.ModulePublishVersion;
import com.unique.examine.module.base.service.ModuleActionConfigBaseService;
import com.unique.examine.module.base.service.ModuleDefinitionBaseService;
import com.unique.examine.module.base.service.ModuleDictItemBaseService;
import com.unique.examine.module.base.service.ModuleDictTypeBaseService;
import com.unique.examine.module.base.service.ModuleFieldDefinitionBaseService;
import com.unique.examine.module.base.service.ModuleGroupBaseService;
import com.unique.examine.module.base.service.ModuleImportExportConfigBaseService;
import com.unique.examine.module.base.service.ModuleListSceneBaseService;
import com.unique.examine.module.base.service.ModulePrintTemplateBaseService;
import com.unique.examine.module.base.service.ModulePublishVersionBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.module.manage.config.ModuleConfigModels.ActionConfigVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ActionSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ColumnMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.ColumnSchema;
import com.unique.examine.module.manage.config.ModuleConfigModels.DetailSectionMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictItemSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictItemVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictTypeQueryRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictTypeSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.DictTypeVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.DynamicListSchema;
import com.unique.examine.module.manage.config.ModuleConfigModels.EmptyStateVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ExportTemplateMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldDefinitionVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldMappingMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldPermissionMetadata;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldQueryRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.FieldSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.FilterSchema;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImpactRef;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImportExportConfigSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImportExportConfigVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImportExportRule;
import com.unique.examine.module.manage.config.ModuleConfigModels.ImportTemplateMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleGroupSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleGroupVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleNavigationMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleQueryRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.ModuleVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PageMeta;
import com.unique.examine.module.manage.config.ModuleConfigModels.PermissionBindingVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PrintTemplateSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.PrintTemplateVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishCheckItem;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishCheckResultVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.PublishResult;
import com.unique.examine.module.manage.config.ModuleConfigModels.ResultContract;
import com.unique.examine.module.manage.config.ModuleConfigModels.RowDetailTarget;
import com.unique.examine.module.manage.config.ModuleConfigModels.SceneSaveRequest;
import com.unique.examine.module.manage.config.ModuleConfigModels.SceneSchemaVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.SelectionRule;
import com.unique.examine.module.manage.config.ModuleConfigModels.SortSchema;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Module configuration service backed by persisted module configuration tables.
 */
@Service
public class ModuleConfigService {

    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final int DELETED_NO = 0;
    private static final int DELETED_YES = 1;
    private static final String DRAFT = "DRAFT";
    private static final String PUBLISHED = "PUBLISHED";
    private static final String DEFAULT_GROUP_CODE = "business";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ModuleSystemContextResolver contextResolver;
    private final ModuleGroupBaseService groupBaseService;
    private final ModuleDefinitionBaseService moduleBaseService;
    private final ModuleFieldDefinitionBaseService fieldBaseService;
    private final ModuleDictTypeBaseService dictTypeBaseService;
    private final ModuleDictItemBaseService dictItemBaseService;
    private final ModuleListSceneBaseService sceneBaseService;
    private final ModuleActionConfigBaseService actionBaseService;
    private final ModuleImportExportConfigBaseService importExportBaseService;
    private final ModulePrintTemplateBaseService printTemplateBaseService;
    private final ModulePublishVersionBaseService publishVersionBaseService;
    private final ObjectMapper objectMapper;

    public ModuleConfigService(ModuleSystemContextResolver contextResolver,
                               ModuleGroupBaseService groupBaseService,
                               ModuleDefinitionBaseService moduleBaseService,
                               ModuleFieldDefinitionBaseService fieldBaseService,
                               ModuleDictTypeBaseService dictTypeBaseService,
                               ModuleDictItemBaseService dictItemBaseService,
                               ModuleListSceneBaseService sceneBaseService,
                               ModuleActionConfigBaseService actionBaseService,
                               ModuleImportExportConfigBaseService importExportBaseService,
                               ModulePrintTemplateBaseService printTemplateBaseService,
                               ModulePublishVersionBaseService publishVersionBaseService,
                               ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.groupBaseService = groupBaseService;
        this.moduleBaseService = moduleBaseService;
        this.fieldBaseService = fieldBaseService;
        this.dictTypeBaseService = dictTypeBaseService;
        this.dictItemBaseService = dictItemBaseService;
        this.sceneBaseService = sceneBaseService;
        this.actionBaseService = actionBaseService;
        this.importExportBaseService = importExportBaseService;
        this.printTemplateBaseService = printTemplateBaseService;
        this.publishVersionBaseService = publishVersionBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Return module groups used by runtime top navigation.
     *
     * @param systemId system id
     * @return module group list
     */
    public List<ModuleGroupVO> moduleGroups(String systemId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        return groupBaseService.list(new LambdaQueryWrapper<ModuleGroup>()
                        .eq(ModuleGroup::getSystemId, context.systemId())
                        .eq(ModuleGroup::getTenantId, context.tenantId())
                        .eq(ModuleGroup::getDeleted, DELETED_NO)
                        .orderByAsc(ModuleGroup::getSortOrder)
                        .orderByAsc(ModuleGroup::getId))
                .stream()
                .map(this::toGroupVO)
                .toList();
    }

    /**
     * Save a module group configuration.
     *
     * @param systemId system id
     * @param groupId optional group id
     * @param request save request
     * @return saved group
     */
    @Transactional(rollbackFor = Exception.class)
    public ModuleGroupVO saveModuleGroup(String systemId, String groupId, ModuleGroupSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        requireText(request == null ? null : request.name(), "模块组名称不能为空");
        ModuleGroup group = StringUtils.hasText(groupId) ? requireGroup(context, groupId) : new ModuleGroup();
        if (Objects.isNull(group.getId())) {
            group.setSystemId(context.systemId());
            group.setTenantId(context.tenantId());
            group.setGroupCode(uniqueGroupCode(context, normalizeCode(request.name(), DEFAULT_GROUP_CODE)));
            group.setCreatedAt(LocalDateTime.now());
            group.setDeleted(DELETED_NO);
        }
        group.setGroupName(request.name());
        group.setSortOrder(Objects.isNull(request.sort()) ? 99 : request.sort());
        group.setVisibleRoleIds(toJson(emptyListWhenNull(request.visibleRoleIds())));
        group.setPublishStatus(safeText(request.publishStatus(), DRAFT));
        group.setPublishedVersion(safeText(group.getPublishedVersion(), "grp_v1"));
        group.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateGroup(group);
        return toGroupVO(group);
    }

    /**
     * Search module configuration records.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query filters
     * @return module page
     */
    public PageResult<ModuleVO> modules(String systemId, PageRequest pageRequest, ModuleQueryRequest query) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<ModuleDefinition> wrapper = moduleQuery(context, query);
        long total = moduleBaseService.count(wrapper);
        List<ModuleVO> records = moduleBaseService.list(moduleQuery(context, query)
                        .orderByAsc(ModuleDefinition::getGroupId)
                        .orderByDesc(ModuleDefinition::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(module -> toModuleVO(module, groupName(module.getGroupId())))
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Save a module configuration and ensure default runtime configuration exists.
     *
     * @param systemId system id
     * @param moduleId optional module id
     * @param request save request
     * @return saved module
     */
    @Transactional(rollbackFor = Exception.class)
    public ModuleVO saveModule(String systemId, String moduleId, ModuleSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        requireText(request == null ? null : request.name(), "模块名称不能为空");
        String moduleCode = safeText(request.moduleCode(), normalizeCode(request.name(), "module"));
        Long groupId = resolveGroupId(context, request.groupId());
        ModuleDefinition module = StringUtils.hasText(moduleId) ? requireModule(context, moduleId)
                : new ModuleDefinition();
        if (Objects.isNull(module.getId())) {
            module.setSystemId(context.systemId());
            module.setTenantId(context.tenantId());
            module.setModuleCode(uniqueModuleCode(context, moduleCode));
            module.setCreatedAt(LocalDateTime.now());
            module.setDeleted(DELETED_NO);
        }
        module.setGroupId(groupId);
        module.setModuleName(request.name());
        module.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        module.setPublishStatus(safeText(module.getPublishStatus(), DRAFT));
        module.setCurrentVersion(safeText(module.getCurrentVersion(), "mod_v1"));
        module.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateModule(module);
        ensureModuleDefaults(context, module);
        return toModuleVO(module, groupName(module.getGroupId()));
    }

    /**
     * Return one module detail.
     *
     * @param systemId system id
     * @param moduleId module id
     * @return module detail
     */
    public ModuleVO moduleDetail(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        return toModuleVO(module, groupName(module.getGroupId()));
    }

    /**
     * Search field configuration records.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param pageRequest page request
     * @param query query filters
     * @return field page
     */
    public PageResult<FieldDefinitionVO> fields(String systemId, String moduleId, PageRequest pageRequest,
                                                FieldQueryRequest query) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<ModuleFieldDefinition> wrapper = fieldQuery(module.getId(), query);
        long total = fieldBaseService.count(wrapper);
        List<FieldDefinitionVO> records = fieldBaseService.list(fieldQuery(module.getId(), query)
                        .orderByAsc(ModuleFieldDefinition::getSortOrder)
                        .orderByAsc(ModuleFieldDefinition::getId)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toFieldVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Save a field definition with runtime permission metadata.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param fieldId optional field id
     * @param request save request
     * @return saved field
     */
    @Transactional(rollbackFor = Exception.class)
    public FieldDefinitionVO saveField(String systemId, String moduleId, String fieldId, FieldSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        requireText(request == null ? null : request.fieldCode(), "字段编码不能为空");
        requireText(request.name(), "字段名称不能为空");
        ModuleFieldDefinition field = StringUtils.hasText(fieldId) ? requireField(module.getId(), fieldId)
                : new ModuleFieldDefinition();
        if (Objects.isNull(field.getId())) {
            ensureFieldCodeUnique(module.getId(), request.fieldCode());
            field.setSystemId(context.systemId());
            field.setTenantId(context.tenantId());
            field.setModuleId(module.getId());
            field.setFieldCode(request.fieldCode());
            field.setSortOrder(nextFieldSort(module.getId()));
            field.setCreatedAt(LocalDateTime.now());
            field.setDeleted(DELETED_NO);
        }
        field.setFieldName(request.name());
        field.setFieldType(safeText(request.fieldType(), "TEXT"));
        field.setStorageType(safeText(request.storageType(), storageType(request.fieldType())));
        field.setRequired(Boolean.TRUE.equals(request.required()) ? 1 : 0);
        field.setSortable(Boolean.TRUE.equals(request.sortable()) ? 1 : 0);
        field.setFilterOperators(toJson(defaultOperators(request.fieldType())));
        field.setMaskRule(toJson(safeText(request.maskRule(), "NONE")));
        field.setImportExportRule(toJson(Objects.isNull(request.importExportRule())
                ? defaultImportExportRule(request.fieldCode()) : request.importExportRule()));
        field.setDictTypeId(parseNullableId(request.dictTypeId()));
        field.setStatus(ENABLED);
        field.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateField(field);
        return toFieldVO(field);
    }

    /**
     * Search dictionary types.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query filters
     * @return dictionary type page
     */
    public PageResult<DictTypeVO> dictTypes(String systemId, PageRequest pageRequest, DictTypeQueryRequest query) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<ModuleDictType> wrapper = dictTypeQuery(context, query);
        long total = dictTypeBaseService.count(wrapper);
        List<DictTypeVO> records = dictTypeBaseService.list(dictTypeQuery(context, query)
                        .orderByDesc(ModuleDictType::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toDictTypeVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Save a dictionary type.
     *
     * @param systemId system id
     * @param request save request
     * @return saved dictionary type
     */
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO saveDictType(String systemId, DictTypeSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        requireText(request == null ? null : request.dictCode(), "字典编码不能为空");
        requireText(request.dictName(), "字典名称不能为空");
        ModuleDictType dictType = dictTypeBaseService.getOne(new LambdaQueryWrapper<ModuleDictType>()
                .eq(ModuleDictType::getSystemId, context.systemId())
                .eq(ModuleDictType::getTenantId, context.tenantId())
                .eq(ModuleDictType::getDictCode, request.dictCode())
                .eq(ModuleDictType::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(dictType)) {
            dictType = new ModuleDictType();
            dictType.setSystemId(context.systemId());
            dictType.setTenantId(context.tenantId());
            dictType.setDictCode(request.dictCode());
            dictType.setCreatedAt(LocalDateTime.now());
            dictType.setDeleted(DELETED_NO);
        }
        dictType.setDictName(request.dictName());
        dictType.setDictKind(safeText(request.dictKind(), "NORMAL"));
        dictType.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        dictType.setPublishedVersion(safeText(dictType.getPublishedVersion(), "dict_v1"));
        dictType.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateDictType(dictType);
        return toDictTypeVO(dictType);
    }

    /**
     * Return dictionary items.
     *
     * @param systemId system id
     * @param dictTypeId dictionary type id
     * @return dictionary items
     */
    public List<DictItemVO> dictItems(String systemId, String dictTypeId) {
        contextResolver.resolve(systemId);
        Long id = contextResolver.parseRequiredId(dictTypeId, "字典类型ID格式不正确");
        return dictItemsByType(id);
    }

    /**
     * Save a dictionary item.
     *
     * @param systemId system id
     * @param dictTypeId dictionary type id
     * @param request save request
     * @return saved dictionary item
     */
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO saveDictItem(String systemId, String dictTypeId, DictItemSaveRequest request) {
        contextResolver.resolve(systemId);
        Long id = contextResolver.parseRequiredId(dictTypeId, "字典类型ID格式不正确");
        requireText(request == null ? null : request.itemCode(), "字典项编码不能为空");
        requireText(request.itemName(), "字典项名称不能为空");
        ModuleDictItem item = dictItemBaseService.getOne(new LambdaQueryWrapper<ModuleDictItem>()
                .eq(ModuleDictItem::getDictTypeId, id)
                .eq(ModuleDictItem::getItemCode, request.itemCode())
                .last("LIMIT 1"), false);
        if (Objects.isNull(item)) {
            item = new ModuleDictItem();
            item.setDictTypeId(id);
            item.setItemCode(request.itemCode());
            item.setCreatedAt(LocalDateTime.now());
        }
        item.setParentId(parseNullableId(safeText(request.parentId(), "0")));
        item.setItemName(request.itemName());
        item.setColor(request.color());
        item.setIcon(request.icon());
        item.setSemantic(request.semantic());
        item.setSortOrder(Objects.isNull(request.sort()) ? 99 : request.sort());
        item.setDefaultFlag(Boolean.TRUE.equals(request.defaultFlag()) ? 1 : 0);
        item.setKanbanEnabled(Boolean.TRUE.equals(request.kanbanEnabled()) ? 1 : 0);
        item.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        item.setDisabledAt(Objects.equals(item.getStatus(), DISABLED) ? LocalDateTime.now() : null);
        item.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateDictItem(item);
        return toDictItemVO(item);
    }

    /**
     * Return scene/list schema configurations.
     *
     * @param systemId system id
     * @param moduleId module id
     * @return scene schemas
     */
    public List<SceneSchemaVO> scenes(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        return sceneBaseService.list(new LambdaQueryWrapper<ModuleListScene>()
                        .eq(ModuleListScene::getModuleId, module.getId())
                        .orderByDesc(ModuleListScene::getDefaultFlag)
                        .orderByAsc(ModuleListScene::getId))
                .stream()
                .map(scene -> toSceneVO(module, scene))
                .toList();
    }

    /**
     * Save a scene configuration.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request save request
     * @return saved scene
     */
    @Transactional(rollbackFor = Exception.class)
    public SceneSchemaVO saveScene(String systemId, String moduleId, SceneSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        requireText(request == null ? null : request.sceneCode(), "场景编码不能为空");
        requireText(request.sceneName(), "场景名称不能为空");
        ModuleListScene scene = sceneBaseService.getOne(new LambdaQueryWrapper<ModuleListScene>()
                .eq(ModuleListScene::getModuleId, module.getId())
                .eq(ModuleListScene::getSceneCode, request.sceneCode())
                .last("LIMIT 1"), false);
        if (Objects.isNull(scene)) {
            scene = new ModuleListScene();
            scene.setModuleId(module.getId());
            scene.setSceneCode(request.sceneCode());
            scene.setCreatedAt(LocalDateTime.now());
        }
        scene.setSceneName(request.sceneName());
        scene.setColumnsConfig(toJson(emptyListWhenNull(request.columnFieldIds())));
        scene.setFiltersConfig(toJson(emptyListWhenNull(request.filterFieldIds())));
        scene.setSortConfig(toJson(emptyListWhenNull(request.sortFieldIds())));
        scene.setRowClickTarget("moduleRecordDetailDrawer");
        scene.setDefaultFlag(Boolean.TRUE.equals(request.defaultScene()) ? 1 : 0);
        scene.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateScene(scene);
        return toSceneVO(module, scene);
    }

    /**
     * Return the runtime list schema, including columns, filters, sorts, row target, and batch restrictions.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param sceneId optional scene id
     * @return list schema
     */
    public DynamicListSchema listSchema(String systemId, String moduleId, String sceneId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleListScene scene = resolveScene(module.getId(), sceneId);
        List<ModuleFieldDefinition> fields = fieldsForModule(module.getId());
        List<ActionConfigVO> allActions = actions(systemId, String.valueOf(module.getId()));
        return new DynamicListSchema(String.valueOf(module.getId()), module.getModuleCode(),
                Objects.isNull(scene) ? null : String.valueOf(scene.getId()),
                Objects.isNull(scene) ? "default" : scene.getSceneCode(),
                columns(fields), filters(fields), sorters(fields),
                new PageMeta(20, List.of(10, 20, 50, 100), true, false),
                rowDetailTarget(module.getId()),
                allActions.stream().filter(action -> "BATCH_BAR".equals(action.position())).toList(),
                allActions.stream().filter(action -> "TOOLBAR".equals(action.position())).toList(),
                importExportConfig(systemId, String.valueOf(module.getId())),
                new EmptyStateVO("暂无" + module.getModuleName(), "可通过新增、导入模板或快捷创建生成业务数据",
                        "record.create", null),
                context.permissionSnapshotId(), printTemplates(systemId, String.valueOf(module.getId())));
    }

    /**
     * Return module action configurations.
     *
     * @param systemId system id
     * @param moduleId module id
     * @return action list
     */
    public List<ActionConfigVO> actions(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        List<ActionConfigVO> actions = actionBaseService.list(new LambdaQueryWrapper<ModuleActionConfig>()
                        .eq(ModuleActionConfig::getModuleId, module.getId())
                        .orderByAsc(ModuleActionConfig::getId))
                .stream()
                .map(this::toActionVO)
                .toList();
        return actions.isEmpty() ? defaultActions(module.getModuleCode()) : actions;
    }

    /**
     * Save a page action configuration.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request save request
     * @return saved action
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionConfigVO saveAction(String systemId, String moduleId, ActionSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        requireText(request == null ? null : request.actionCode(), "动作编码不能为空");
        requireText(request.actionName(), "动作名称不能为空");
        ModuleActionConfig action = actionBaseService.getOne(new LambdaQueryWrapper<ModuleActionConfig>()
                .eq(ModuleActionConfig::getModuleId, module.getId())
                .eq(ModuleActionConfig::getActionCode, request.actionCode())
                .last("LIMIT 1"), false);
        if (Objects.isNull(action)) {
            action = new ModuleActionConfig();
            action.setModuleId(module.getId());
            action.setActionCode(request.actionCode());
            action.setCreatedAt(LocalDateTime.now());
        }
        action.setActionName(request.actionName());
        action.setActionType(safeText(request.actionType(), "ROW"));
        action.setSelectionRule(toJson(Objects.isNull(request.selectionRule())
                ? defaultSelectionRule(request.actionType()) : request.selectionRule()));
        action.setPermissionCode(safeText(request.permissionCode(), "module." + module.getModuleCode() + ".action"));
        action.setResultContract(actionPayload(request.position(), request.resultContract()));
        action.setStatus(Boolean.FALSE.equals(request.enabled()) ? DISABLED : ENABLED);
        action.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateAction(action);
        return toActionVO(action);
    }

    /**
     * Return permission bindings for runtime enforcement.
     *
     * @param systemId system id
     * @param moduleId module id
     * @return permission bindings
     */
    public List<PermissionBindingVO> permissions(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        List<FieldPermissionMetadata> fieldPermissions = fieldsForModule(module.getId()).stream()
                .map(this::fieldPermission)
                .toList();
        return List.of(new PermissionBindingVO("bind_module_" + module.getId(), "MODULE",
                String.valueOf(module.getId()), "module." + module.getModuleCode() + ".read",
                List.of(), "ALLOW", "systemId == currentSystem && tenantId == currentTenant",
                fieldPermissions, context.permissionVersion()));
    }

    /**
     * Return import/export configuration metadata without executing jobs.
     *
     * @param systemId system id
     * @param moduleId module id
     * @return import/export config
     */
    public ImportExportConfigVO importExportConfig(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleImportExportConfig config = importExportBaseService.getOne(
                new LambdaQueryWrapper<ModuleImportExportConfig>()
                        .eq(ModuleImportExportConfig::getModuleId, module.getId())
                        .last("LIMIT 1"), false);
        if (Objects.isNull(config)) {
            return defaultImportExportConfig(module);
        }
        return toImportExportVO(module, config);
    }

    /**
     * Save import/export metadata without starting import/export execution.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request save request
     * @return saved config
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportExportConfigVO saveImportExportConfig(String systemId, String moduleId,
                                                       ImportExportConfigSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        ModuleImportExportConfig config = importExportBaseService.getOne(
                new LambdaQueryWrapper<ModuleImportExportConfig>()
                        .eq(ModuleImportExportConfig::getModuleId, module.getId())
                        .last("LIMIT 1"), false);
        if (Objects.isNull(config)) {
            config = new ModuleImportExportConfig();
            config.setModuleId(module.getId());
        }
        config.setImportEnabled(Boolean.FALSE.equals(request.importSupported()) ? DISABLED : ENABLED);
        config.setExportEnabled(Boolean.FALSE.equals(request.exportSupported()) ? DISABLED : ENABLED);
        config.setExportAllEnabled(ENABLED);
        config.setTemplateFileId("tpl_" + module.getModuleCode());
        config.setResultTaskRequired(ENABLED);
        config.setPrecheckRule(toJson(importExportPayload(request)));
        config.setPermissionCode("module." + module.getModuleCode() + ".import_export");
        config.setUpdatedAt(LocalDateTime.now());
        saveOrUpdateImportExport(config);
        return toImportExportVO(module, config);
    }

    /**
     * Return print template configurations.
     *
     * @param systemId system id
     * @param moduleId module id
     * @return print templates
     */
    public List<PrintTemplateVO> printTemplates(String systemId, String moduleId) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        return printTemplateBaseService.list(new LambdaQueryWrapper<ModulePrintTemplate>()
                        .eq(ModulePrintTemplate::getModuleId, module.getId())
                        .orderByAsc(ModulePrintTemplate::getId))
                .stream()
                .map(this::toPrintTemplateVO)
                .toList();
    }

    /**
     * Save a print template configuration.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request save request
     * @return saved print template
     */
    @Transactional(rollbackFor = Exception.class)
    public PrintTemplateVO savePrintTemplate(String systemId, String moduleId, PrintTemplateSaveRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        requireText(request == null ? null : request.templateCode(), "打印模板编码不能为空");
        requireText(request.templateName(), "打印模板名称不能为空");
        ModulePrintTemplate template = printTemplateBaseService.getOne(new LambdaQueryWrapper<ModulePrintTemplate>()
                .eq(ModulePrintTemplate::getModuleId, module.getId())
                .eq(ModulePrintTemplate::getTemplateCode, request.templateCode())
                .last("LIMIT 1"), false);
        if (Objects.isNull(template)) {
            template = new ModulePrintTemplate();
            template.setModuleId(module.getId());
            template.setTemplateCode(request.templateCode());
            template.setCreatedAt(LocalDateTime.now());
        }
        template.setTemplateName(request.templateName());
        template.setTemplateFileId(safeText(request.previewFileId(), "file_print_" + request.templateCode()));
        template.setFieldMapping(toJson(emptyListWhenNull(request.boundFieldCodes())));
        template.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        template.setUpdatedAt(LocalDateTime.now());
        saveOrUpdatePrintTemplate(template);
        return toPrintTemplateVO(template);
    }

    /**
     * Return publish check result for module configuration.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request publish request
     * @return publish check result
     */
    public PublishCheckResultVO publishCheck(String systemId, String moduleId, PublishRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        List<PublishCheckItem> failures = fieldsForModule(module.getId()).isEmpty()
                ? List.of(new PublishCheckItem("FIELD_EMPTY", "字段配置", "ERROR",
                "MODULE", String.valueOf(module.getId()), "模块至少需要一个字段才能发布。", "新增字段后重试"))
                : List.of();
        List<PublishCheckItem> warnings = actions(systemId, moduleId).isEmpty()
                ? List.of(new PublishCheckItem("ACTION_EMPTY", "动作配置", "WARNING",
                "MODULE", String.valueOf(module.getId()), "模块没有配置动作，运行态只能查看数据。", "按需新增页面动作"))
                : List.of();
        return new PublishCheckResultVO(failures.isEmpty(), failures, warnings,
                List.of(new ImpactRef("RUNTIME_LIST", String.valueOf(module.getId()), module.getModuleName(),
                        "REFRESH_SCHEMA"),
                        new ImpactRef("PERMISSION_SNAPSHOT", context.permissionSnapshotId(), "权限快照",
                                "RECALCULATE_CACHE")),
                RequestContext.current().traceId());
    }

    /**
     * Publish module group or module configuration.
     *
     * @param systemId system id
     * @param targetId target id
     * @param targetType target type
     * @param request publish request
     * @return publish result
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishResult publishTarget(String systemId, String targetId, String targetType, PublishRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        String version = targetType + "_v" + System.currentTimeMillis();
        Long objectId = contextResolver.parseRequiredId(targetId, "发布对象ID格式不正确");
        if ("MODULE_GROUP".equals(targetType)) {
            ModuleGroup group = requireGroup(context, targetId);
            group.setPublishStatus(PUBLISHED);
            group.setPublishedVersion(version);
            group.setUpdatedAt(LocalDateTime.now());
            groupBaseService.updateById(group);
        } else {
            ModuleDefinition module = requireModule(context, targetId);
            PublishCheckResultVO checkResult = publishCheck(systemId, targetId, request);
            if (!checkResult.passed()) {
                String message = checkResult.failureItems().stream()
                        .findFirst()
                        .map(item -> "模块发布检查未通过：" + item.message())
                        .orElse("模块发布检查未通过");
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
            }
            module.setPublishStatus(PUBLISHED);
            module.setCurrentVersion(version);
            module.setUpdatedAt(LocalDateTime.now());
            moduleBaseService.updateById(module);
        }
        savePublishVersion(context, targetType, objectId, version, PUBLISHED);
        RequestContext requestContext = RequestContext.current();
        return new PublishResult("PUBLISHED_" + targetType, targetId, version,
                requestContext.traceId(), "aud_" + requestContext.traceId(), null, LocalDateTime.now());
    }

    /**
     * Roll back module configuration to the previous published version.
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request publish request
     * @return rollback result
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishResult rollbackModule(String systemId, String moduleId, PublishRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        ModuleDefinition module = requireModule(context, moduleId);
        module.setPublishStatus(DRAFT);
        module.setCurrentVersion("rollback_" + System.currentTimeMillis());
        module.setUpdatedAt(LocalDateTime.now());
        moduleBaseService.updateById(module);
        savePublishVersion(context, "MODULE", module.getId(), module.getCurrentVersion(), "ROLLED_BACK");
        RequestContext requestContext = RequestContext.current();
        return new PublishResult("ROLLED_BACK", moduleId, module.getCurrentVersion(),
                requestContext.traceId(), "aud_" + requestContext.traceId(), null, LocalDateTime.now());
    }

    private ModuleGroupVO toGroupVO(ModuleGroup group) {
        return new ModuleGroupVO(String.valueOf(group.getId()), String.valueOf(group.getSystemId()),
                String.valueOf(group.getTenantId()), group.getGroupName(), group.getSortOrder(),
                readJsonList(group.getVisibleRoleIds()), group.getPublishStatus(), group.getPublishedVersion(),
                group.getUpdatedAt());
    }

    private ModuleVO toModuleVO(ModuleDefinition module, String topGroupName) {
        return new ModuleVO(String.valueOf(module.getId()), String.valueOf(module.getSystemId()),
                String.valueOf(module.getTenantId()), String.valueOf(module.getGroupId()),
                module.getModuleCode(), module.getModuleName(), module.getStatus(), module.getPublishStatus(),
                module.getCurrentVersion(), rowDetailTarget(module.getId()),
                new ModuleNavigationMeta(safeText(topGroupName, "业务模块"), module.getModuleName(),
                        "/systems/" + module.getSystemId() + "/modules/" + module.getId(), List.of(), true),
                module.getUpdatedAt());
    }

    private FieldDefinitionVO toFieldVO(ModuleFieldDefinition field) {
        String maskRule = readJsonString(field.getMaskRule(), "NONE");
        return new FieldDefinitionVO(String.valueOf(field.getId()), String.valueOf(field.getModuleId()),
                field.getFieldCode(), field.getFieldName(), field.getFieldType(), field.getStorageType(),
                readJsonList(field.getFilterOperators()), Objects.equals(field.getSortable(), ENABLED),
                Objects.equals(field.getRequired(), ENABLED), field.getStatus(),
                Objects.isNull(field.getDictTypeId()) ? null : String.valueOf(field.getDictTypeId()),
                fieldPermission(field), maskRule,
                readJsonObject(field.getImportExportRule(), ImportExportRule.class,
                        defaultImportExportRule(field.getFieldCode())),
                new ColumnMeta(defaultWidth(field.getFieldType()), true, true, false, defaultAlign(field.getFieldType())));
    }

    private DictTypeVO toDictTypeVO(ModuleDictType dictType) {
        return new DictTypeVO(String.valueOf(dictType.getId()), String.valueOf(dictType.getSystemId()),
                String.valueOf(dictType.getTenantId()), dictType.getDictCode(), dictType.getDictName(),
                dictType.getDictKind(), dictType.getStatus(), dictType.getPublishedVersion(),
                dictItemsByType(dictType.getId()), dictType.getUpdatedAt());
    }

    private DictItemVO toDictItemVO(ModuleDictItem item) {
        return new DictItemVO(String.valueOf(item.getId()), String.valueOf(item.getDictTypeId()),
                Objects.isNull(item.getParentId()) || item.getParentId() == 0 ? null : String.valueOf(item.getParentId()),
                item.getItemCode(), item.getItemName(), item.getColor(), item.getIcon(), item.getSemantic(),
                item.getSortOrder(), Objects.equals(item.getDefaultFlag(), ENABLED),
                Objects.equals(item.getKanbanEnabled(), ENABLED), item.getStatus(),
                Objects.equals(item.getStatus(), DISABLED) ? "字典项已停用，仅历史数据展示" : null);
    }

    private SceneSchemaVO toSceneVO(ModuleDefinition module, ModuleListScene scene) {
        return new SceneSchemaVO(String.valueOf(scene.getId()), String.valueOf(module.getId()), scene.getSceneCode(),
                scene.getSceneName(), Objects.equals(scene.getDefaultFlag(), ENABLED), List.of(),
                listSchema(String.valueOf(module.getSystemId()), String.valueOf(module.getId()),
                        String.valueOf(scene.getId())),
                detailSections(fieldsForModule(module.getId())), scene.getUpdatedAt());
    }

    private ActionConfigVO toActionVO(ModuleActionConfig action) {
        Map<String, Object> payload = readJsonMap(action.getResultContract());
        String position = String.valueOf(payload.getOrDefault("position", defaultPosition(action.getActionType())));
        ResultContract resultContract = objectMapper.convertValue(payload.getOrDefault("resultContract",
                defaultResultContract(action.getActionType())), ResultContract.class);
        return new ActionConfigVO(action.getActionCode(), action.getActionName(), action.getActionType(), position,
                readJsonObject(action.getSelectionRule(), SelectionRule.class, defaultSelectionRule(action.getActionType())),
                action.getPermissionCode(), resultContract, Objects.equals(action.getStatus(), ENABLED),
                Objects.equals(action.getStatus(), ENABLED) ? null : "动作已停用", true);
    }

    private ImportExportConfigVO toImportExportVO(ModuleDefinition module, ModuleImportExportConfig config) {
        Map<String, Object> payload = readJsonMap(config.getPrecheckRule());
        return new ImportExportConfigVO(String.valueOf(config.getId()), String.valueOf(module.getId()),
                Objects.equals(config.getImportEnabled(), ENABLED), Objects.equals(config.getExportEnabled(), ENABLED),
                convertList(payload.get("importTemplates"), ImportTemplateMeta.class,
                        defaultImportExportConfig(module).importTemplates()),
                convertList(payload.get("exportTemplates"), ExportTemplateMeta.class,
                        defaultImportExportConfig(module).exportTemplates()),
                convertList(payload.get("fieldMappings"), FieldMappingMeta.class,
                        defaultImportExportConfig(module).fieldMappings()),
                convertList(payload.get("duplicateStrategies"), String.class, List.of("SKIP", "OVERWRITE", "REPORT_ERROR")),
                convertList(payload.get("supportedFormats"), String.class, List.of("XLSX", "CSV")),
                true, true, "TASK-BE-022");
    }

    private PrintTemplateVO toPrintTemplateVO(ModulePrintTemplate template) {
        return new PrintTemplateVO(String.valueOf(template.getId()), String.valueOf(template.getModuleId()),
                template.getTemplateCode(), template.getTemplateName(), "print_v1", template.getStatus(),
                false, List.of(), readJsonList(template.getFieldMapping()), template.getTemplateFileId(),
                Objects.equals(template.getStatus(), ENABLED) ? PUBLISHED : DRAFT);
    }

    private LambdaQueryWrapper<ModuleDefinition> moduleQuery(ModuleSystemContext context, ModuleQueryRequest query) {
        LambdaQueryWrapper<ModuleDefinition> wrapper = new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getSystemId, context.systemId())
                .eq(ModuleDefinition::getTenantId, context.tenantId())
                .eq(ModuleDefinition::getDeleted, DELETED_NO);
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.groupId())) {
                wrapper.eq(ModuleDefinition::getGroupId, contextResolver.parseRequiredId(query.groupId(), "模块组ID格式不正确"));
            }
            if (Objects.nonNull(query.status())) {
                wrapper.eq(ModuleDefinition::getStatus, query.status());
            }
            if (StringUtils.hasText(query.publishStatus())) {
                wrapper.eq(ModuleDefinition::getPublishStatus, query.publishStatus());
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(ModuleDefinition::getModuleName, query.keyword())
                        .or().like(ModuleDefinition::getModuleCode, query.keyword()));
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<ModuleFieldDefinition> fieldQuery(Long moduleId, FieldQueryRequest query) {
        LambdaQueryWrapper<ModuleFieldDefinition> wrapper = new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO);
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.fieldType())) {
                wrapper.eq(ModuleFieldDefinition::getFieldType, query.fieldType());
            }
            if (Objects.nonNull(query.status())) {
                wrapper.eq(ModuleFieldDefinition::getStatus, query.status());
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(ModuleFieldDefinition::getFieldName, query.keyword())
                        .or().like(ModuleFieldDefinition::getFieldCode, query.keyword()));
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<ModuleDictType> dictTypeQuery(ModuleSystemContext context, DictTypeQueryRequest query) {
        LambdaQueryWrapper<ModuleDictType> wrapper = new LambdaQueryWrapper<ModuleDictType>()
                .eq(ModuleDictType::getSystemId, context.systemId())
                .eq(ModuleDictType::getTenantId, context.tenantId())
                .eq(ModuleDictType::getDeleted, DELETED_NO);
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.dictKind())) {
                wrapper.eq(ModuleDictType::getDictKind, query.dictKind());
            }
            if (Objects.nonNull(query.status())) {
                wrapper.eq(ModuleDictType::getStatus, query.status());
            }
            if (StringUtils.hasText(query.keyword())) {
                wrapper.and(value -> value.like(ModuleDictType::getDictName, query.keyword())
                        .or().like(ModuleDictType::getDictCode, query.keyword()));
            }
        }
        return wrapper;
    }

    private ModuleGroup requireGroup(ModuleSystemContext context, String groupId) {
        Long id = contextResolver.parseRequiredId(groupId, "模块组ID格式不正确");
        ModuleGroup group = groupBaseService.getOne(new LambdaQueryWrapper<ModuleGroup>()
                .eq(ModuleGroup::getId, id)
                .eq(ModuleGroup::getSystemId, context.systemId())
                .eq(ModuleGroup::getTenantId, context.tenantId())
                .eq(ModuleGroup::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(group)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "模块组不存在");
        }
        return group;
    }

    private ModuleDefinition requireModule(ModuleSystemContext context, String moduleId) {
        Long id = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        ModuleDefinition module = moduleBaseService.getOne(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getId, id)
                .eq(ModuleDefinition::getSystemId, context.systemId())
                .eq(ModuleDefinition::getTenantId, context.tenantId())
                .eq(ModuleDefinition::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(module)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "模块不存在");
        }
        return module;
    }

    private ModuleFieldDefinition requireField(Long moduleId, String fieldId) {
        Long id = contextResolver.parseRequiredId(fieldId, "字段ID格式不正确");
        ModuleFieldDefinition field = fieldBaseService.getOne(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getId, id)
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(field)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "字段不存在");
        }
        return field;
    }

    private Long resolveGroupId(ModuleSystemContext context, String groupId) {
        if (StringUtils.hasText(groupId)) {
            return requireGroup(context, groupId).getId();
        }
        ModuleGroup group = groupBaseService.getOne(new LambdaQueryWrapper<ModuleGroup>()
                .eq(ModuleGroup::getSystemId, context.systemId())
                .eq(ModuleGroup::getTenantId, context.tenantId())
                .eq(ModuleGroup::getGroupCode, DEFAULT_GROUP_CODE)
                .eq(ModuleGroup::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(group)) {
            return group.getId();
        }
        ModuleGroup defaultGroup = new ModuleGroup();
        defaultGroup.setSystemId(context.systemId());
        defaultGroup.setTenantId(context.tenantId());
        defaultGroup.setGroupCode(DEFAULT_GROUP_CODE);
        defaultGroup.setGroupName("业务模块");
        defaultGroup.setSortOrder(10);
        defaultGroup.setVisibleRoleIds(toJson(List.of()));
        defaultGroup.setPublishStatus(PUBLISHED);
        defaultGroup.setPublishedVersion("grp_v1");
        defaultGroup.setCreatedAt(LocalDateTime.now());
        defaultGroup.setUpdatedAt(LocalDateTime.now());
        defaultGroup.setDeleted(DELETED_NO);
        groupBaseService.saveEntity(defaultGroup);
        return defaultGroup.getId();
    }

    private void ensureModuleDefaults(ModuleSystemContext context, ModuleDefinition module) {
        ModuleDictType statusDict = ensureStatusDict(context, module);
        ensureDefaultField(context, module, "title", "标题", "TEXT", null, true, true);
        ensureDefaultField(context, module, "status", "状态", "SELECT", statusDict.getId(), true, true);
        ensureDefaultField(context, module, "ownerDept", "所属部门", "TEXT", null, false, true);
        ensureDefaultScene(module);
        ensureDefaultActions(module);
        ensureDefaultImportExport(module);
    }

    private ModuleDictType ensureStatusDict(ModuleSystemContext context, ModuleDefinition module) {
        String dictCode = module.getModuleCode() + "_status";
        ModuleDictType dictType = dictTypeBaseService.getOne(new LambdaQueryWrapper<ModuleDictType>()
                .eq(ModuleDictType::getSystemId, context.systemId())
                .eq(ModuleDictType::getTenantId, context.tenantId())
                .eq(ModuleDictType::getDictCode, dictCode)
                .eq(ModuleDictType::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(dictType)) {
            dictType = new ModuleDictType();
            dictType.setSystemId(context.systemId());
            dictType.setTenantId(context.tenantId());
            dictType.setDictCode(dictCode);
            dictType.setDictName(module.getModuleName() + "状态");
            dictType.setDictKind("STATUS");
            dictType.setStatus(ENABLED);
            dictType.setPublishedVersion("dict_v1");
            dictType.setCreatedAt(LocalDateTime.now());
            dictType.setUpdatedAt(LocalDateTime.now());
            dictType.setDeleted(DELETED_NO);
            dictTypeBaseService.saveEntity(dictType);
        }
        ensureDictItem(dictType.getId(), "DRAFT", "草稿", "GRAY", "pencil", 10, true);
        ensureDictItem(dictType.getId(), "ACTIVE", "使用中", "BLUE", "circle-dot", 20, false);
        ensureDictItem(dictType.getId(), "ARCHIVED", "已归档", "RED", "archive", 90, false);
        return dictType;
    }

    private void ensureDictItem(Long dictTypeId, String itemCode, String itemName, String color, String icon,
                                int sort, boolean defaultFlag) {
        ModuleDictItem item = dictItemBaseService.getOne(new LambdaQueryWrapper<ModuleDictItem>()
                .eq(ModuleDictItem::getDictTypeId, dictTypeId)
                .eq(ModuleDictItem::getItemCode, itemCode)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(item)) {
            return;
        }
        ModuleDictItem newItem = new ModuleDictItem();
        newItem.setDictTypeId(dictTypeId);
        newItem.setParentId(0L);
        newItem.setItemCode(itemCode);
        newItem.setItemName(itemName);
        newItem.setColor(color);
        newItem.setIcon(icon);
        newItem.setSemantic(itemName);
        newItem.setSortOrder(sort);
        newItem.setDefaultFlag(defaultFlag ? 1 : 0);
        newItem.setKanbanEnabled(ENABLED);
        newItem.setStatus(ENABLED);
        newItem.setCreatedAt(LocalDateTime.now());
        newItem.setUpdatedAt(LocalDateTime.now());
        dictItemBaseService.saveEntity(newItem);
    }

    private void ensureDefaultField(ModuleSystemContext context, ModuleDefinition module, String fieldCode,
                                    String fieldName, String fieldType, Long dictTypeId, boolean required,
                                    boolean sortable) {
        if (fieldBaseService.count(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, module.getId())
                .eq(ModuleFieldDefinition::getFieldCode, fieldCode)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)) > 0) {
            return;
        }
        ModuleFieldDefinition field = new ModuleFieldDefinition();
        field.setSystemId(context.systemId());
        field.setTenantId(context.tenantId());
        field.setModuleId(module.getId());
        field.setFieldCode(fieldCode);
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        field.setStorageType(storageType(fieldType));
        field.setRequired(required ? 1 : 0);
        field.setSortable(sortable ? 1 : 0);
        field.setFilterOperators(toJson(defaultOperators(fieldType)));
        field.setMaskRule(toJson("NONE"));
        field.setImportExportRule(toJson(defaultImportExportRule(fieldCode)));
        field.setDictTypeId(dictTypeId);
        field.setSortOrder(nextFieldSort(module.getId()));
        field.setStatus(ENABLED);
        field.setCreatedAt(LocalDateTime.now());
        field.setUpdatedAt(LocalDateTime.now());
        field.setDeleted(DELETED_NO);
        fieldBaseService.saveEntity(field);
    }

    private void ensureDefaultScene(ModuleDefinition module) {
        if (sceneBaseService.count(new LambdaQueryWrapper<ModuleListScene>()
                .eq(ModuleListScene::getModuleId, module.getId())
                .eq(ModuleListScene::getSceneCode, "all")) > 0) {
            return;
        }
        ModuleListScene scene = new ModuleListScene();
        scene.setModuleId(module.getId());
        scene.setSceneCode("all");
        scene.setSceneName("全部" + module.getModuleName());
        scene.setColumnsConfig(toJson(List.of()));
        scene.setFiltersConfig(toJson(List.of()));
        scene.setSortConfig(toJson(List.of()));
        scene.setRowClickTarget("moduleRecordDetailDrawer");
        scene.setDefaultFlag(ENABLED);
        scene.setCreatedAt(LocalDateTime.now());
        scene.setUpdatedAt(LocalDateTime.now());
        sceneBaseService.saveEntity(scene);
    }

    private void ensureDefaultActions(ModuleDefinition module) {
        for (ActionConfigVO action : defaultActions(module.getModuleCode())) {
            if (actionBaseService.count(new LambdaQueryWrapper<ModuleActionConfig>()
                    .eq(ModuleActionConfig::getModuleId, module.getId())
                    .eq(ModuleActionConfig::getActionCode, action.actionCode())) > 0) {
                continue;
            }
            ModuleActionConfig entity = new ModuleActionConfig();
            entity.setModuleId(module.getId());
            entity.setActionCode(action.actionCode());
            entity.setActionName(action.actionName());
            entity.setActionType(action.actionType());
            entity.setSelectionRule(toJson(action.selectionRule()));
            entity.setPermissionCode(action.permissionCode());
            entity.setResultContract(actionPayload(action.position(), action.resultContract()));
            entity.setStatus(action.enabled() ? ENABLED : DISABLED);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            actionBaseService.saveEntity(entity);
        }
    }

    private void ensureDefaultImportExport(ModuleDefinition module) {
        if (importExportBaseService.count(new LambdaQueryWrapper<ModuleImportExportConfig>()
                .eq(ModuleImportExportConfig::getModuleId, module.getId())) > 0) {
            return;
        }
        ModuleImportExportConfig config = new ModuleImportExportConfig();
        config.setModuleId(module.getId());
        config.setImportEnabled(ENABLED);
        config.setExportEnabled(ENABLED);
        config.setExportAllEnabled(ENABLED);
        config.setTemplateFileId("tpl_" + module.getModuleCode());
        config.setResultTaskRequired(ENABLED);
        config.setPrecheckRule(toJson(importExportPayload(defaultImportExportConfig(module))));
        config.setPermissionCode("module." + module.getModuleCode() + ".import_export");
        config.setUpdatedAt(LocalDateTime.now());
        importExportBaseService.saveEntity(config);
    }

    private List<ModuleFieldDefinition> fieldsForModule(Long moduleId) {
        return fieldBaseService.list(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)
                .eq(ModuleFieldDefinition::getStatus, ENABLED)
                .orderByAsc(ModuleFieldDefinition::getSortOrder)
                .orderByAsc(ModuleFieldDefinition::getId));
    }

    private ModuleListScene resolveScene(Long moduleId, String sceneId) {
        LambdaQueryWrapper<ModuleListScene> wrapper = new LambdaQueryWrapper<ModuleListScene>()
                .eq(ModuleListScene::getModuleId, moduleId);
        if (StringUtils.hasText(sceneId)) {
            Long id = parseNullableId(sceneId);
            if (Objects.nonNull(id)) {
                wrapper.eq(ModuleListScene::getId, id);
            } else {
                wrapper.eq(ModuleListScene::getSceneCode, sceneId);
            }
        } else {
            wrapper.eq(ModuleListScene::getDefaultFlag, ENABLED);
        }
        return sceneBaseService.getOne(wrapper.orderByDesc(ModuleListScene::getDefaultFlag)
                .orderByAsc(ModuleListScene::getId).last("LIMIT 1"), false);
    }

    private List<ColumnSchema> columns(List<ModuleFieldDefinition> fields) {
        return fields.stream()
                .map(field -> new ColumnSchema(String.valueOf(field.getId()), field.getFieldCode(),
                        field.getFieldName(), defaultWidth(field.getFieldType()), true, true, false,
                        defaultAlign(field.getFieldType()), "READABLE", readJsonString(field.getMaskRule(), "NONE")))
                .toList();
    }

    private List<FilterSchema> filters(List<ModuleFieldDefinition> fields) {
        return fields.stream()
                .map(field -> new FilterSchema(String.valueOf(field.getId()), field.getFieldCode(),
                        field.getFieldName(), field.getFieldType(), readJsonList(field.getFilterOperators()),
                        true, "title".equals(field.getFieldCode()) || "status".equals(field.getFieldCode()),
                        Objects.isNull(field.getDictTypeId()) ? List.of() : dictItemsByType(field.getDictTypeId()),
                        "READABLE"))
                .toList();
    }

    private List<SortSchema> sorters(List<ModuleFieldDefinition> fields) {
        List<SortSchema> fieldSorts = fields.stream()
                .filter(field -> Objects.equals(field.getSortable(), ENABLED))
                .map(field -> new SortSchema(String.valueOf(field.getId()), field.getFieldCode(),
                        field.getFieldName(), "ASC", false, true))
                .toList();
        return fieldSorts.isEmpty()
                ? List.of(new SortSchema("sys_updated_at", "updatedAt", "更新时间", "DESC", true, true))
                : fieldSorts;
    }

    private List<DetailSectionMeta> detailSections(List<ModuleFieldDefinition> fields) {
        return List.of(new DetailSectionMeta("base", "基础资料",
                fields.stream().map(ModuleFieldDefinition::getFieldCode).toList(), "module.read", true),
                new DetailSectionMeta("operationLogs", "操作记录", List.of("operator", "action", "operatedAt"),
                        "module.history.read", true));
    }

    private List<ActionConfigVO> defaultActions(String moduleCode) {
        return List.of(
                new ActionConfigVO("record.create", "新增", "CREATE", "TOOLBAR",
                        new SelectionRule("NONE", 0, 0, List.of(), true, null),
                        "module." + moduleCode + ".create", defaultResultContract("CREATE"), true, null, true),
                new ActionConfigVO("record.export", "导出", "EXPORT", "TOOLBAR",
                        new SelectionRule("OPTIONAL_MULTI", 0, 5000, List.of(), true, "导出会按字段权限脱敏"),
                        "module." + moduleCode + ".export", defaultResultContract("EXPORT"), true, null, true),
                new ActionConfigVO("record.edit", "编辑", "UPDATE", "ROW",
                        new SelectionRule("SINGLE", 1, 1, List.of(), true, null),
                        "module." + moduleCode + ".edit", defaultResultContract("UPDATE"), true, null, true),
                new ActionConfigVO("record.delete", "删除", "DELETE", "ROW",
                        new SelectionRule("SINGLE", 1, 1, List.of("DRAFT"), true, "仅草稿且无审批记录可删除"),
                        "module." + moduleCode + ".delete", defaultResultContract("DELETE"), true, null, true),
                new ActionConfigVO("record.batchArchive", "批量归档", "BATCH", "BATCH_BAR",
                        new SelectionRule("MULTI", 1, 200, List.of("ACTIVE"), true, "仅同租户可批量归档"),
                        "module." + moduleCode + ".batch_archive", defaultResultContract("BATCH"), true, null, true)
        );
    }

    private ResultContract defaultResultContract(String actionType) {
        if ("EXPORT".equals(actionType) || "BATCH".equals(actionType)) {
            return new ResultContract("ASYNC_TASK", true, true, "asyncTaskDrawer", "taskId",
                    List.of("QUEUED", "RUNNING", "SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELED"));
        }
        return new ResultContract("SYNC_RESULT", true, false, "runtimeActionResultDrawer", "traceId",
                List.of("SUCCESS", "FAILED", "BLOCKED"));
    }

    private SelectionRule defaultSelectionRule(String actionType) {
        return "BATCH".equals(actionType)
                ? new SelectionRule("MULTI", 1, 200, List.of(), true, null)
                : new SelectionRule("SINGLE", 1, 1, List.of(), true, null);
    }

    private ImportExportConfigVO defaultImportExportConfig(ModuleDefinition module) {
        return new ImportExportConfigVO("imp_exp_" + module.getId(), String.valueOf(module.getId()),
                true, true,
                List.of(new ImportTemplateMeta(module.getModuleCode() + "_import", module.getModuleName() + "导入模板",
                        "tpl_" + module.getModuleCode(), "tpl_v1", fieldsForModule(module.getId()).stream()
                        .filter(field -> Objects.equals(field.getRequired(), ENABLED))
                        .map(ModuleFieldDefinition::getFieldCode).toList())),
                List.of(new ExportTemplateMeta(module.getModuleCode() + "_export", module.getModuleName() + "导出模板",
                        "XLSX", "MASK_DENIED_FIELDS", fieldsForModule(module.getId()).stream()
                        .map(ModuleFieldDefinition::getFieldCode).toList())),
                fieldsForModule(module.getId()).stream()
                        .map(field -> new FieldMappingMeta(field.getFieldName(), field.getFieldCode(),
                                Objects.equals(field.getRequired(), ENABLED), "trim"))
                        .toList(),
                List.of("SKIP", "OVERWRITE", "REPORT_ERROR"), List.of("XLSX", "CSV"), true, true,
                "TASK-BE-022");
    }

    private Map<String, Object> importExportPayload(ImportExportConfigSaveRequest request) {
        ImportExportConfigVO fallback = new ImportExportConfigVO(null, null, true, true, List.of(), List.of(),
                List.of(), List.of("SKIP", "OVERWRITE", "REPORT_ERROR"), List.of("XLSX", "CSV"), true,
                true, "TASK-BE-022");
        return Map.of("importTemplates", emptyListWhenNull(request == null ? fallback.importTemplates() : request.importTemplates()),
                "exportTemplates", emptyListWhenNull(request == null ? fallback.exportTemplates() : request.exportTemplates()),
                "fieldMappings", emptyListWhenNull(request == null ? fallback.fieldMappings() : request.fieldMappings()),
                "duplicateStrategies", emptyListWhenNull(request == null ? fallback.duplicateStrategies() : request.duplicateStrategies()),
                "supportedFormats", emptyListWhenNull(request == null ? fallback.supportedFormats() : request.supportedFormats()));
    }

    private Map<String, Object> importExportPayload(ImportExportConfigVO config) {
        return Map.of("importTemplates", config.importTemplates(), "exportTemplates", config.exportTemplates(),
                "fieldMappings", config.fieldMappings(), "duplicateStrategies", config.duplicateStrategies(),
                "supportedFormats", config.supportedFormats());
    }

    private FieldPermissionMetadata fieldPermission(ModuleFieldDefinition field) {
        String code = field.getFieldCode();
        return new FieldPermissionMetadata("field." + code + ".read", "field." + code + ".write",
                List.of(), List.of(), true, true, readJsonString(field.getMaskRule(), "NONE"), "perm_live");
    }

    private RowDetailTarget rowDetailTarget(Long moduleId) {
        return new RowDetailTarget("DRAWER", "/runtime/modules/" + moduleId + "/records/{recordId}",
                "moduleRecordDetailDrawer", true);
    }

    private String actionPayload(String position, ResultContract resultContract) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("position", safeText(position, "ROW"));
        payload.put("resultContract", Objects.isNull(resultContract) ? defaultResultContract("ROW") : resultContract);
        return toJson(payload);
    }

    private String groupName(Long groupId) {
        ModuleGroup group = groupBaseService.getById(groupId);
        return Objects.isNull(group) ? null : group.getGroupName();
    }

    private List<DictItemVO> dictItemsByType(Long dictTypeId) {
        return dictItemBaseService.list(new LambdaQueryWrapper<ModuleDictItem>()
                        .eq(ModuleDictItem::getDictTypeId, dictTypeId)
                        .orderByAsc(ModuleDictItem::getSortOrder)
                        .orderByAsc(ModuleDictItem::getId))
                .stream()
                .map(this::toDictItemVO)
                .toList();
    }

    private String uniqueGroupCode(ModuleSystemContext context, String baseCode) {
        String code = baseCode;
        int index = 1;
        while (groupBaseService.count(new LambdaQueryWrapper<ModuleGroup>()
                .eq(ModuleGroup::getSystemId, context.systemId())
                .eq(ModuleGroup::getTenantId, context.tenantId())
                .eq(ModuleGroup::getGroupCode, code)
                .eq(ModuleGroup::getDeleted, DELETED_NO)) > 0) {
            code = baseCode + "_" + index++;
        }
        return code;
    }

    private String uniqueModuleCode(ModuleSystemContext context, String baseCode) {
        String code = baseCode;
        int index = 1;
        while (moduleBaseService.count(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getSystemId, context.systemId())
                .eq(ModuleDefinition::getTenantId, context.tenantId())
                .eq(ModuleDefinition::getModuleCode, code)
                .eq(ModuleDefinition::getDeleted, DELETED_NO)) > 0) {
            code = baseCode + "_" + index++;
        }
        return code;
    }

    private int nextFieldSort(Long moduleId) {
        return (int) fieldBaseService.count(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO)) * 10 + 10;
    }

    private void savePublishVersion(ModuleSystemContext context, String objectType, Long objectId, String version,
                                    String status) {
        ModulePublishVersion publishVersion = new ModulePublishVersion();
        publishVersion.setSystemId(context.systemId());
        publishVersion.setTenantId(context.tenantId());
        publishVersion.setObjectType(objectType);
        publishVersion.setObjectId(objectId);
        publishVersion.setVersionNo(version);
        publishVersion.setPublishStatus(status);
        publishVersion.setImpactRefs(toJson(List.of("RUNTIME_SCHEMA", "PERMISSION_SNAPSHOT")));
        publishVersion.setFailureItems(toJson(List.of()));
        publishVersion.setTraceId(RequestContext.current().traceId());
        publishVersion.setCreatedAt(LocalDateTime.now());
        publishVersionBaseService.saveEntity(publishVersion);
    }

    private void saveOrUpdateGroup(ModuleGroup group) {
        if (Objects.isNull(group.getId())) {
            groupBaseService.saveEntity(group);
        } else {
            groupBaseService.updateById(group);
        }
    }

    private void saveOrUpdateModule(ModuleDefinition module) {
        if (Objects.isNull(module.getId())) {
            moduleBaseService.saveEntity(module);
        } else {
            moduleBaseService.updateById(module);
        }
    }

    private void saveOrUpdateField(ModuleFieldDefinition field) {
        if (Objects.isNull(field.getId())) {
            fieldBaseService.saveEntity(field);
        } else {
            fieldBaseService.updateById(field);
        }
    }

    private void ensureFieldCodeUnique(Long moduleId, String fieldCode) {
        long count = fieldBaseService.count(new LambdaQueryWrapper<ModuleFieldDefinition>()
                .eq(ModuleFieldDefinition::getModuleId, moduleId)
                .eq(ModuleFieldDefinition::getFieldCode, fieldCode)
                .eq(ModuleFieldDefinition::getDeleted, DELETED_NO));
        if (count > 0) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "字段编码已存在");
        }
    }

    private void saveOrUpdateDictType(ModuleDictType dictType) {
        if (Objects.isNull(dictType.getId())) {
            dictTypeBaseService.saveEntity(dictType);
        } else {
            dictTypeBaseService.updateById(dictType);
        }
    }

    private void saveOrUpdateDictItem(ModuleDictItem item) {
        if (Objects.isNull(item.getId())) {
            dictItemBaseService.saveEntity(item);
        } else {
            dictItemBaseService.updateById(item);
        }
    }

    private void saveOrUpdateScene(ModuleListScene scene) {
        if (Objects.isNull(scene.getId())) {
            sceneBaseService.saveEntity(scene);
        } else {
            sceneBaseService.updateById(scene);
        }
    }

    private void saveOrUpdateAction(ModuleActionConfig action) {
        if (Objects.isNull(action.getId())) {
            actionBaseService.saveEntity(action);
        } else {
            actionBaseService.updateById(action);
        }
    }

    private void saveOrUpdateImportExport(ModuleImportExportConfig config) {
        if (Objects.isNull(config.getId())) {
            importExportBaseService.saveEntity(config);
        } else {
            importExportBaseService.updateById(config);
        }
    }

    private void saveOrUpdatePrintTemplate(ModulePrintTemplate template) {
        if (Objects.isNull(template.getId())) {
            printTemplateBaseService.saveEntity(template);
        } else {
            printTemplateBaseService.updateById(template);
        }
    }

    private ImportExportRule defaultImportExportRule(String fieldCode) {
        return new ImportExportRule(true, true, "title".equals(fieldCode), fieldCode, "NONE");
    }

    private List<String> defaultOperators(String fieldType) {
        return switch (safeText(fieldType, "TEXT")) {
            case "SELECT", "MULTI_SELECT", "DEPARTMENT", "USER" -> List.of("EQ", "IN", "IS_NULL", "IS_NOT_NULL");
            case "NUMBER", "DATE", "DATETIME" -> List.of("EQ", "GT", "GTE", "LT", "LTE", "IS_NULL", "IS_NOT_NULL");
            default -> List.of("EQ", "LIKE", "IN", "IS_NULL", "IS_NOT_NULL");
        };
    }

    private String storageType(String fieldType) {
        return switch (safeText(fieldType, "TEXT")) {
            case "NUMBER" -> "DECIMAL";
            case "DATE", "DATETIME" -> "DATETIME";
            case "MULTI_SELECT", "ATTACHMENT", "JSON" -> "JSON";
            default -> "VARCHAR";
        };
    }

    private int defaultWidth(String fieldType) {
        return switch (safeText(fieldType, "TEXT")) {
            case "NUMBER" -> 120;
            case "SELECT", "STATUS" -> 110;
            case "DATE", "DATETIME" -> 150;
            default -> 180;
        };
    }

    private String defaultAlign(String fieldType) {
        return "NUMBER".equals(fieldType) ? "RIGHT" : "LEFT";
    }

    private String defaultPosition(String actionType) {
        return switch (safeText(actionType, "ROW")) {
            case "CREATE", "EXPORT", "IMPORT" -> "TOOLBAR";
            case "BATCH" -> "BATCH_BAR";
            default -> "ROW";
        };
    }

    private int pageNo(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
    }

    private int pageSize(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private Long parseNullableId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeCode(String value, String fallback) {
        String candidate = safeText(value, fallback);
        String normalized = candidate.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private <T> List<T> emptyListWhenNull(List<T> value) {
        return Objects.isNull(value) ? List.of() : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "JSON序列化失败");
        }
    }

    private List<String> readJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String readJsonString(String json, String fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, String.class);
        } catch (Exception ex) {
            return json;
        }
    }

    private <T> T readJsonObject(String json, Class<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <T> List<T> convertList(Object value, Class<T> type, List<T> fallback) {
        if (Objects.isNull(value)) {
            return fallback;
        }
        try {
            return objectMapper.convertValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
