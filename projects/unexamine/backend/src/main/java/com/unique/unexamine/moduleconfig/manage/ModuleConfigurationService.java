package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionary;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleMenu;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleMenuBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ModuleConfigurationService {
    static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "NUMBER", "PERCENT", "MONEY",
            "DATE", "DATETIME", "DATE_RANGE", "TIME", "TIME_RANGE", "SINGLE_SELECT", "MULTI_SELECT", "CASCADE",
            "BOOLEAN", "MEMBER", "DEPARTMENT", "ORGANIZATION", "ATTACHMENT", "IMAGE", "FILE", "FILE_GROUP",
            "AUTO_NUMBER", "REFERENCE", "LOOKUP", "SUBTABLE", "ADDRESS", "LOCATION", "RATING", "PROGRESS",
            "TAG", "BARCODE", "QRCODE", "SIGNATURE", "RICH_TEXT", "JSON", "SECRET", "STATUS",
            "SYSTEM_CREATED_BY", "SYSTEM_CREATED_AT", "SYSTEM_UPDATED_BY", "SYSTEM_UPDATED_AT",
            "FORMULA", "SUMMARY", "CALCULATION", "AGGREGATE", "AI_FILL");

    private final ConfiguredModuleGroupBaseService groupService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final ConfiguredModulePageBaseService pageService;
    private final CfgModuleMenuBaseService menuService;
    private final ConfiguredModuleActionBaseService actionService;
    private final ConfiguredModulePublicationBaseService publicationService;
    private final CfgDictionaryBaseService dictionaryService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ModuleConfigurationService(
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModulePageBaseService pageService,
            CfgModuleMenuBaseService menuService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModulePublicationBaseService publicationService,
            CfgDictionaryBaseService dictionaryService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.groupService = groupService;
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.pageService = pageService;
        this.menuService = menuService;
        this.actionService = actionService;
        this.publicationService = publicationService;
        this.dictionaryService = dictionaryService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ConfiguredModuleGroup createGroup(AuthenticatedContext context, CreateModuleGroupRequest request, String traceId) {
        requireSystemContext(context);
        ConfiguredModuleGroup group = new ConfiguredModuleGroup();
        group.setSystemId(context.systemId());
        group.setOwnerTenantId(context.tenantId());
        group.setCode(normalizeCode(request.code()));
        group.setName(request.name().strip());
        group.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        group.setStatus("ACTIVE");
        group.setCreatedByMemberId(context.memberId());
        group.setVersion(0);
        groupService.insert(group);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_GROUP_DRAFT_CREATED", "MODULE_GROUP", group.getId().toString(), "SUCCESS",
                Map.of("code", group.getCode()));
        return group;
    }

    @Transactional(readOnly = true)
    public ModuleConfigurationOverview overview(AuthenticatedContext context) {
        requireSystemContext(context);
        List<ConfiguredModuleGroup> groups = groupService.selectList(Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                .eq(ConfiguredModuleGroup::getSystemId, context.systemId())
                .eq(ConfiguredModuleGroup::getOwnerTenantId, context.tenantId())
                .orderByAsc(ConfiguredModuleGroup::getSortOrder, ConfiguredModuleGroup::getId));
        List<ConfiguredModule> modules = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, context.systemId())
                .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                .orderByAsc(ConfiguredModule::getGroupId, ConfiguredModule::getId));
        return new ModuleConfigurationOverview(groups, modules);
    }

    @Transactional
    public ModuleDraft createModule(AuthenticatedContext context, CreateModuleRequest request, String traceId) {
        requireSystemContext(context);
        ConfiguredModuleGroup group = groupService.selectById(request.groupId());
        if (group == null || !context.systemId().equals(group.getSystemId()) || !context.tenantId().equals(group.getOwnerTenantId())) {
            throw notFound("模块组不存在");
        }
        ConfiguredModule module = new ConfiguredModule();
        module.setSystemId(context.systemId());
        module.setOwnerTenantId(context.tenantId());
        module.setGroupId(group.getId());
        module.setCode(normalizeCode(request.code()));
        module.setName(request.name().strip());
        module.setStatus("DRAFT");
        module.setDraftRevision(1);
        module.setCreatedByMemberId(context.memberId());
        module.setVersion(0);
        moduleService.insert(module);

        createDefaultPage(module, "LIST", "默认列表");
        createDefaultPage(module, "FORM", "默认表单");
        createDefaultPage(module, "DETAIL", "默认详情");
        createDefaultMenu(module);
        createDefaultAction(module, "LIST", "列表", "MODULE_ENTRY", 0);
        createDefaultAction(module, "DETAIL", "详情", "ROW", 10);
        createDefaultAction(module, "CREATE", "新建", "LIST_TOOLBAR", 20);
        createDefaultAction(module, "UPDATE", "编辑", "DETAIL_HEADER", 30);
        createDefaultAction(module, "ARCHIVE", "归档", "DETAIL_MORE", 40);
        createDefaultAction(module, "DELETE", "删除", "DETAIL_MORE", 50);
        createDefaultAction(module, "RESTORE", "恢复", "DETAIL_HEADER", 60);
        createDefaultAction(module, "TRANSFER", "转交负责人", "DETAIL_MORE", 70);
        createDefaultAction(module, "CONVERT", "转化记录", "DETAIL_MORE", 80);
        createDefaultAction(module, "SHARE", "共享给租户", "DETAIL_MORE", 90);
        createDefaultAction(module, "IMPORT", "导入", "LIST_TOOLBAR", 100);
        createDefaultAction(module, "EXPORT", "导出", "LIST_TOOLBAR", 110);

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_DRAFT_CREATED", "MODULE", module.getId().toString(), "SUCCESS",
                Map.of("code", module.getCode(), "defaultPages", 3, "defaultMenus", 1, "defaultActions", 11));
        return draft(context, module.getId());
    }

    @Transactional
    public ConfiguredModuleField createField(
            AuthenticatedContext context,
            Long moduleId,
            CreateModuleFieldRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        String fieldType = request.fieldType().strip().toUpperCase(Locale.ROOT);
        if (!FIELD_TYPES.contains(fieldType)) {
            throw new DomainException("FIELD_TYPE_UNSUPPORTED", "当前字段类型不受支持", HttpStatus.BAD_REQUEST);
        }
        validateFieldDefinition(context, fieldType, request.dictionaryId(), request.referenceModuleId(), request.config(), traceId);
        ConfiguredModuleField field = new ConfiguredModuleField();
        field.setSystemId(context.systemId());
        field.setOwnerTenantId(context.tenantId());
        field.setModuleId(moduleId);
        field.setCode(normalizeCode(request.code()));
        field.setName(request.name().strip());
        field.setFieldType(fieldType);
        field.setRequired(request.required());
        field.setUniqueValue(Boolean.TRUE.equals(request.uniqueValue()));
        field.setSearchable(Boolean.TRUE.equals(request.searchable()));
        field.setDictionaryId(request.dictionaryId());
        field.setReferenceModuleId(request.referenceModuleId());
        field.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        field.setStatus("ACTIVE");
        field.setConfigJson(toJson(request.config()));
        field.setVersion(0);
        fieldService.insert(field);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_FIELD_DRAFT_CREATED", "MODULE_FIELD", field.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "fieldCode", field.getCode(), "fieldType", fieldType));
        return field;
    }

    @Transactional
    public ConfiguredModuleField updateField(
            AuthenticatedContext context,
            Long moduleId,
            Long fieldId,
            UpdateModuleFieldRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        ConfiguredModuleField field = fieldService.selectById(fieldId);
        if (field == null || !moduleId.equals(field.getModuleId()) || !context.systemId().equals(field.getSystemId())
                || !context.tenantId().equals(field.getOwnerTenantId())) {
            throw notFound("字段不存在");
        }
        if (!request.version().equals(field.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "字段草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        Long dictionaryId = request.dictionaryId() == null ? field.getDictionaryId() : request.dictionaryId();
        Long referenceModuleId = request.referenceModuleId() == null ? field.getReferenceModuleId() : request.referenceModuleId();
        validateFieldDefinition(context, field.getFieldType(), dictionaryId, referenceModuleId, request.config(), traceId);
        field.setName(request.name().strip());
        field.setRequired(request.required());
        if (request.uniqueValue() != null) field.setUniqueValue(request.uniqueValue());
        if (request.searchable() != null) field.setSearchable(request.searchable());
        field.setDictionaryId(dictionaryId);
        field.setReferenceModuleId(referenceModuleId);
        field.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        field.setStatus(request.status());
        field.setConfigJson(toJson(request.config()));
        if (fieldService.updateById(field) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "字段草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_FIELD_DRAFT_UPDATED", "MODULE_FIELD", fieldId.toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "status", field.getStatus()));
        return field;
    }

    @Transactional
    public ConfiguredModulePage updatePage(
            AuthenticatedContext context,
            Long moduleId,
            String pageType,
            UpdatePageConfigurationRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        ConfiguredModulePage page = pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                        .eq(ConfiguredModulePage::getModuleId, moduleId)
                        .eq(ConfiguredModulePage::getPageType, pageType.toUpperCase(Locale.ROOT)))
                .stream().findFirst().orElseThrow(() -> notFound("页面不存在"));
        if (!request.version().equals(page.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "页面草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        page.setLayoutJson(toJson(request.layout()));
        if (pageService.updateById(page) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "页面草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PAGE_DRAFT_UPDATED", "MODULE_PAGE", page.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "pageType", page.getPageType()));
        return page;
    }

    @Transactional
    public ConfiguredModulePage createPage(
            AuthenticatedContext context,
            Long moduleId,
            CreateModulePageRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        String pageType = request.pageType().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("DASHBOARD", "CUSTOM").contains(pageType)) {
            throw new DomainException("PAGE_TYPE_RESERVED", "列表、表单和详情页面由模块自动创建", HttpStatus.BAD_REQUEST);
        }
        if (!pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                .eq(ConfiguredModulePage::getModuleId, moduleId)
                .eq(ConfiguredModulePage::getPageType, pageType)).isEmpty()) {
            throw new DomainException("PAGE_TYPE_DUPLICATED", "当前页面类型已经存在", HttpStatus.CONFLICT);
        }
        ConfiguredModulePage page = new ConfiguredModulePage();
        page.setSystemId(context.systemId());
        page.setOwnerTenantId(context.tenantId());
        page.setModuleId(moduleId);
        page.setPageType(pageType);
        page.setName(request.name().strip());
        page.setLayoutJson(toJson(request.layout()));
        page.setStatus("ACTIVE");
        page.setVersion(0);
        pageService.insert(page);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PAGE_DRAFT_CREATED", "MODULE_PAGE", page.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "pageType", pageType));
        return page;
    }

    @Transactional
    public ConfiguredModulePage deletePage(
            AuthenticatedContext context,
            Long moduleId,
            String pageType,
            Integer version,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        String normalizedType = pageType.strip().toUpperCase(Locale.ROOT);
        if (Set.of("LIST", "FORM", "DETAIL").contains(normalizedType)) {
            throw new DomainException("DEFAULT_PAGE_DELETE_FORBIDDEN", "列表、表单和详情页面不能删除", HttpStatus.BAD_REQUEST);
        }
        ConfiguredModulePage page = pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                        .eq(ConfiguredModulePage::getModuleId, moduleId)
                        .eq(ConfiguredModulePage::getPageType, normalizedType))
                .stream().findFirst().orElseThrow(() -> notFound("页面不存在"));
        if (!context.systemId().equals(page.getSystemId()) || !context.tenantId().equals(page.getOwnerTenantId())) {
            throw notFound("页面不存在");
        }
        if (!version.equals(page.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "页面草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        if (pageService.deleteById(page.getId()) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "页面草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PAGE_DRAFT_DELETED", "MODULE_PAGE", page.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "pageType", normalizedType, "pageName", page.getName()));
        return page;
    }

    @Transactional
    public CfgModuleMenu updateMenu(
            AuthenticatedContext context,
            Long moduleId,
            Long menuId,
            UpdateModuleMenuRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        CfgModuleMenu menu = menuService.selectById(menuId);
        if (menu == null || !moduleId.equals(menu.getModuleId()) || !context.systemId().equals(menu.getSystemId())
                || !context.tenantId().equals(menu.getOwnerTenantId())) {
            throw notFound("运行菜单不存在");
        }
        if (!request.version().equals(menu.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "菜单草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        if (request.parentId() != null) {
            CfgModuleMenu parent = menuService.selectById(request.parentId());
            if (parent == null || !context.systemId().equals(parent.getSystemId())
                    || !context.tenantId().equals(parent.getOwnerTenantId())) {
                throw new DomainException("MENU_PARENT_INVALID", "上级菜单不在当前系统租户", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (menuId.equals(parent.getId()) || isDescendant(menuId, parent)) {
                throw new DomainException("MENU_HIERARCHY_CYCLE", "菜单层级不能形成循环", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        String routePath = request.routePath().strip();
        if (!routePath.matches("^/runtime/[a-z][a-z0-9_]{0,99}$")) {
            throw new DomainException("MENU_ROUTE_INVALID", "运行菜单路径必须是 /runtime/模块编码", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        menu.setParentId(request.parentId());
        menu.setName(request.name().strip());
        menu.setIcon(request.icon() == null || request.icon().isBlank() ? null : request.icon().strip());
        menu.setRoutePath(routePath);
        menu.setSortOrder(request.sortOrder());
        menu.setVisible(request.visible());
        menu.setStatus(request.status());
        if (menuService.updateById(menu) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "菜单草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_MENU_DRAFT_UPDATED", "MODULE_MENU", menu.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "routePath", routePath, "visible", request.visible()));
        return menu;
    }

    @Transactional
    public ConfiguredModuleAction updateAction(
            AuthenticatedContext context,
            Long moduleId,
            String actionCode,
            UpdateActionConfigurationRequest request,
            String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        ConfiguredModuleAction action = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                        .eq(ConfiguredModuleAction::getModuleId, moduleId)
                        .eq(ConfiguredModuleAction::getCode, actionCode.toUpperCase(Locale.ROOT)))
                .stream().findFirst().orElseThrow(() -> notFound("动作不存在"));
        if (!request.version().equals(action.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "动作草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        action.setLocation(request.location());
        action.setConfigJson(toJson(request.config()));
        if (actionService.updateById(action) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "动作草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_ACTION_DRAFT_UPDATED", "MODULE_ACTION", action.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "actionCode", action.getCode()));
        return action;
    }

    @Transactional
    public ModuleDraft draft(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        List<ConfiguredModuleField> fields = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId)
                .orderByAsc(ConfiguredModuleField::getSortOrder, ConfiguredModuleField::getId));
        List<ConfiguredModulePage> pages = pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                .eq(ConfiguredModulePage::getModuleId, moduleId)
                .orderByAsc(ConfiguredModulePage::getId));
        List<CfgModuleMenu> menus = menuService.selectList(Wrappers.<CfgModuleMenu>lambdaQuery()
                .eq(CfgModuleMenu::getModuleId, moduleId)
                .orderByAsc(CfgModuleMenu::getSortOrder, CfgModuleMenu::getId));
        if (menus.isEmpty()) {
            createDefaultMenu(module);
            menus = menuService.selectList(Wrappers.<CfgModuleMenu>lambdaQuery()
                    .eq(CfgModuleMenu::getModuleId, moduleId)
                    .orderByAsc(CfgModuleMenu::getSortOrder, CfgModuleMenu::getId));
        }
        List<ConfiguredModuleAction> actions = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .orderByAsc(ConfiguredModuleAction::getSortOrder, ConfiguredModuleAction::getId));
        boolean published = !publicationService.selectList(Wrappers.<ConfiguredModulePublication>lambdaQuery()
                .eq(ConfiguredModulePublication::getModuleId, moduleId)).isEmpty();
        return new ModuleDraft(module, fields, pages, menus, actions, published);
    }

    private void createDefaultPage(ConfiguredModule module, String type, String name) {
        ConfiguredModulePage page = new ConfiguredModulePage();
        page.setSystemId(module.getSystemId());
        page.setOwnerTenantId(module.getOwnerTenantId());
        page.setModuleId(module.getId());
        page.setPageType(type);
        page.setName(name);
        page.setLayoutJson("{\"components\":[]}");
        page.setStatus("ACTIVE");
        page.setVersion(0);
        pageService.insert(page);
    }

    private void createDefaultMenu(ConfiguredModule module) {
        CfgModuleMenu menu = new CfgModuleMenu();
        menu.setSystemId(module.getSystemId());
        menu.setOwnerTenantId(module.getOwnerTenantId());
        menu.setModuleId(module.getId());
        menu.setCode(module.getCode());
        menu.setName(module.getName());
        menu.setRoutePath("/runtime/" + module.getCode());
        menu.setSortOrder(10);
        menu.setVisible(true);
        menu.setStatus("ACTIVE");
        menu.setVersion(0);
        menuService.insert(menu);
    }

    private boolean isDescendant(Long menuId, CfgModuleMenu candidateParent) {
        CfgModuleMenu cursor = candidateParent;
        Set<Long> visited = new java.util.HashSet<>();
        while (cursor != null && cursor.getParentId() != null && visited.add(cursor.getId())) {
            if (menuId.equals(cursor.getParentId())) return true;
            cursor = menuService.selectById(cursor.getParentId());
        }
        return false;
    }

    private void createDefaultAction(ConfiguredModule module, String code, String name, String location, int order) {
        ConfiguredModuleAction action = new ConfiguredModuleAction();
        action.setSystemId(module.getSystemId());
        action.setOwnerTenantId(module.getOwnerTenantId());
        action.setModuleId(module.getId());
        action.setCode(code);
        action.setName(name);
        action.setActionType("BUILTIN");
        action.setLocation(location);
        action.setSortOrder(order);
        action.setStatus("ACTIVE");
        action.setConfigJson("{}");
        action.setVersion(0);
        actionService.insert(action);
    }

    private ConfiguredModule requireOwnedDraft(AuthenticatedContext context, Long moduleId) {
        requireSystemContext(context);
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || !context.systemId().equals(module.getSystemId()) || !context.tenantId().equals(module.getOwnerTenantId())) {
            throw notFound("模块不存在");
        }
        return module;
    }

    private void validateFieldDefinition(
            AuthenticatedContext context,
            String fieldType,
            Long dictionaryId,
            Long referenceModuleId,
            com.fasterxml.jackson.databind.JsonNode config,
            String traceId) {
        if (("SECRET".equals(fieldType) || config.path("sensitive").asBoolean(false))
                && !permissionChecker.allows(context, "CONFIG", "MODULE_FIELD", "CONFIGURE_SENSITIVE")) {
            auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                    context.memberId(), "CONFIG:MODULE_FIELD:CONFIGURE_SENSITIVE",
                    Map.of("fieldType", fieldType, "entry", "MODULE_FIELD_DRAFT"));
            throw new DomainException("PERMISSION_DENIED", "没有配置敏感字段的权限", HttpStatus.FORBIDDEN);
        }
        CfgDictionary dictionary = null;
        if (dictionaryId != null) {
            dictionary = dictionaryService.selectById(dictionaryId);
            if (dictionary == null || !context.systemId().equals(dictionary.getSystemId())
                    || !context.tenantId().equals(dictionary.getOwnerTenantId())) {
                throw new DomainException("DICTIONARY_NOT_FOUND", "绑定字典不存在", HttpStatus.NOT_FOUND);
            }
        }
        if (referenceModuleId != null) {
            ConfiguredModule reference = moduleService.selectById(referenceModuleId);
            if (reference == null || !context.systemId().equals(reference.getSystemId())
                    || !context.tenantId().equals(reference.getOwnerTenantId())) {
                throw new DomainException("REFERENCE_MODULE_NOT_FOUND", "关联模块不存在", HttpStatus.NOT_FOUND);
            }
        }
        if (Set.of("SINGLE_SELECT", "MULTI_SELECT", "STATUS", "TAG").contains(fieldType)
                && dictionaryId == null && (!config.path("options").isArray() || config.path("options").isEmpty())) {
            invalidField("选项字段必须绑定字典或至少配置一个选项");
        }
        if ("CASCADE".equals(fieldType)) {
            if ((dictionaryId == null) == (referenceModuleId == null)) {
                invalidField("级联字段必须且只能选择层级字典或模块数据源之一");
            }
            if (dictionary != null && !Boolean.TRUE.equals(dictionary.getHierarchical())) {
                invalidField("级联字段只能绑定层级字典");
            }
            int maxDepth = config.path("maxDepth").asInt(0);
            if (maxDepth < 1) invalidField("级联字段必须配置大于 0 的最大层级");
        }
        if ("MONEY".equals(fieldType)) {
            if (config.path("currency").asText().isBlank()) invalidField("金额字段必须配置币种");
            int precision = config.path("precision").asInt(-1);
            if (precision < 0 || precision > 6) invalidField("金额精度必须在 0 到 6 之间");
        }
        if (Set.of("REFERENCE", "LOOKUP", "SUBTABLE", "SUMMARY", "AGGREGATE").contains(fieldType)
                && referenceModuleId == null) {
            invalidField("该字段类型必须选择目标模块");
        }
        if ("AUTO_NUMBER".equals(fieldType) && config.path("sequenceCode").asText().isBlank()) {
            invalidField("自动编号字段必须配置编号规则编码");
        }
        if (Set.of("FORMULA", "CALCULATION").contains(fieldType)
                && config.path("expression").asText().isBlank()) {
            invalidField("公式或计算字段必须配置表达式");
        }
        if ("AI_FILL".equals(fieldType)
                && (config.path("modelCode").asText().isBlank() || !config.path("humanConfirmation").asBoolean(false))) {
            invalidField("AI 填充字段必须配置已授权模型并启用人工确认");
        }
        if (fieldType.startsWith("SYSTEM_") && !config.path("readOnly").asBoolean(false)) {
            invalidField("系统字段必须配置为只读");
        }
    }

    private void invalidField(String message) {
        throw new DomainException("FIELD_CONFIG_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private void touch(ConfiguredModule module) {
        module.setDraftRevision(module.getDraftRevision() + 1);
        if (moduleService.updateById(module) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "模块草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
    }

    private void requireSystemContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
    }

    private String normalizeCode(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize module configuration", exception);
        }
    }

    private DomainException notFound(String message) {
        return new DomainException("CONFIGURATION_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
