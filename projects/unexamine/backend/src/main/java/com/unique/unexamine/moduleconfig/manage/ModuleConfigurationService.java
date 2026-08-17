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
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
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
    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "MULTILINE_TEXT", "NUMBER", "MONEY", "DATE", "DATETIME", "SINGLE_SELECT",
            "BOOLEAN", "MEMBER", "DEPARTMENT", "STATUS");

    private final ConfiguredModuleGroupBaseService groupService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModuleFieldBaseService fieldService;
    private final ConfiguredModulePageBaseService pageService;
    private final ConfiguredModuleActionBaseService actionService;
    private final ConfiguredModulePublicationBaseService publicationService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ModuleConfigurationService(
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModulePageBaseService pageService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModulePublicationBaseService publicationService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.groupService = groupService;
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.pageService = pageService;
        this.actionService = actionService;
        this.publicationService = publicationService;
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
        createDefaultAction(module, "LIST", "列表", "MODULE_ENTRY", 0);
        createDefaultAction(module, "DETAIL", "详情", "ROW", 10);
        createDefaultAction(module, "CREATE", "新建", "LIST_TOOLBAR", 20);
        createDefaultAction(module, "UPDATE", "编辑", "DETAIL_HEADER", 30);

        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_DRAFT_CREATED", "MODULE", module.getId().toString(), "SUCCESS",
                Map.of("code", module.getCode(), "defaultPages", 3, "defaultActions", 4));
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
        ConfiguredModuleField field = new ConfiguredModuleField();
        field.setSystemId(context.systemId());
        field.setOwnerTenantId(context.tenantId());
        field.setModuleId(moduleId);
        field.setCode(normalizeCode(request.code()));
        field.setName(request.name().strip());
        field.setFieldType(fieldType);
        field.setRequired(request.required());
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
        field.setName(request.name().strip());
        field.setRequired(request.required());
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

    @Transactional(readOnly = true)
    public ModuleDraft draft(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        List<ConfiguredModuleField> fields = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId)
                .orderByAsc(ConfiguredModuleField::getSortOrder, ConfiguredModuleField::getId));
        List<ConfiguredModulePage> pages = pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                .eq(ConfiguredModulePage::getModuleId, moduleId)
                .orderByAsc(ConfiguredModulePage::getId));
        List<ConfiguredModuleAction> actions = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .orderByAsc(ConfiguredModuleAction::getSortOrder, ConfiguredModuleAction::getId));
        boolean published = !publicationService.selectList(Wrappers.<ConfiguredModulePublication>lambdaQuery()
                .eq(ConfiguredModulePublication::getModuleId, moduleId)).isEmpty();
        return new ModuleDraft(module, fields, pages, actions, published);
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

    private void createDefaultAction(ConfiguredModule module, String code, String name, String location, int order) {
        ConfiguredModuleAction action = new ConfiguredModuleAction();
        action.setSystemId(module.getSystemId());
        action.setOwnerTenantId(module.getOwnerTenantId());
        action.setModuleId(module.getId());
        action.setCode(code);
        action.setName(name);
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
