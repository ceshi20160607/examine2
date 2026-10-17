package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePage;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionary;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleRule;
import com.unique.unexamine.moduleconfig.base.entity.CfgModuleMenu;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndex;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndexField;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleMenuBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgModuleRuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePageBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.foundation.base.entity.CoreQuotaPolicy;
import com.unique.unexamine.foundation.base.service.CoreQuotaPolicyBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;

@Service
public class ModuleConfigurationService {
    static final Set<String> CUSTOM_ACTION_FIELD_TYPES = Set.of(
            "TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE", "SECRET", "NUMBER", "MONEY",
            "BOOLEAN", "DATE", "DATETIME", "SINGLE_SELECT", "MULTI_SELECT", "STATUS", "RICH_TEXT", "JSON", "MEMBER", "DEPARTMENT", "CASCADE", "REFERENCE");
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
    private final ConfiguredModuleVersionBaseService versionService;
    private final CfgDictionaryBaseService dictionaryService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final FieldUniqueIndexSynchronizer fieldUniqueIndexSynchronizer;
    private final ModuleIdentifierIntegrity identifierIntegrity;
    private final SystemTenantBaseService tenantService;
    private final CoreQuotaPolicyBaseService quotaPolicyService;
    private final CfgModuleRuleBaseService ruleService;
    private final CfgQueryIndexBaseService indexService;
    private final CfgQueryIndexFieldBaseService indexFieldService;

    public ModuleConfigurationService(
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModuleFieldBaseService fieldService,
            ConfiguredModulePageBaseService pageService,
            CfgModuleMenuBaseService menuService,
            ConfiguredModuleActionBaseService actionService,
            ConfiguredModulePublicationBaseService publicationService,
            ConfiguredModuleVersionBaseService versionService,
            CfgDictionaryBaseService dictionaryService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            FieldUniqueIndexSynchronizer fieldUniqueIndexSynchronizer,
            ModuleIdentifierIntegrity identifierIntegrity, SystemTenantBaseService tenantService,
            CoreQuotaPolicyBaseService quotaPolicyService,
            CfgModuleRuleBaseService ruleService,
            CfgQueryIndexBaseService indexService,
            CfgQueryIndexFieldBaseService indexFieldService) {
        this.groupService = groupService;
        this.moduleService = moduleService;
        this.fieldService = fieldService;
        this.pageService = pageService;
        this.menuService = menuService;
        this.actionService = actionService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.dictionaryService = dictionaryService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.fieldUniqueIndexSynchronizer = fieldUniqueIndexSynchronizer;
        this.identifierIntegrity = identifierIntegrity;
        this.tenantService = tenantService;
        this.quotaPolicyService = quotaPolicyService;
        this.ruleService = ruleService;
        this.indexService = indexService;
        this.indexFieldService = indexFieldService;
    }

    @Transactional
    public ConfiguredModuleGroup createGroup(AuthenticatedContext context, CreateModuleGroupRequest request, String traceId) {
        requireSystemContext(context);
        String groupCode = resolveGroupCode(context, request.code());
        ConfiguredModuleGroup group = new ConfiguredModuleGroup();
        group.setSystemId(context.systemId());
        group.setOwnerTenantId(context.tenantId());
        group.setCode(groupCode);
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

    /** 编辑模块组后，已发布模块的导航名称与顺序直接按当前分组配置读取。 */
    @Transactional
    public ConfiguredModuleGroup updateGroup(AuthenticatedContext context, Long groupId,
                                             UpdateModuleGroupRequest request, String traceId) {
        ConfiguredModuleGroup group = requireOwnedGroup(context, groupId);
        if (!request.version().equals(group.getVersion())) {
            throw new DomainException("MODULE_GROUP_VERSION_CONFLICT", "模块组已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        group.setName(request.name().strip());
        group.setSortOrder(request.sortOrder());
        if (groupService.updateById(group) == 0) {
            throw new DomainException("MODULE_GROUP_VERSION_CONFLICT", "模块组已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_GROUP_UPDATED", "MODULE_GROUP", groupId.toString(), "SUCCESS",
                Map.of("code", group.getCode(), "sortOrder", group.getSortOrder()));
        return group;
    }

    /** 仅删除空分组；软删除保留编码，防止历史引用被新分组接管。 */
    @Transactional
    public ConfiguredModuleGroup deleteGroup(AuthenticatedContext context, Long groupId, Integer version, String traceId) {
        ConfiguredModuleGroup group = requireOwnedGroup(context, groupId);
        if (!version.equals(group.getVersion())) {
            throw new DomainException("MODULE_GROUP_VERSION_CONFLICT", "模块组已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (moduleService.selectCount(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, context.systemId())
                .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                .eq(ConfiguredModule::getGroupId, groupId)
                .ne(ConfiguredModule::getStatus, "DELETED")) > 0) {
            throw new DomainException("MODULE_GROUP_IN_USE", "模块组仍有模块，请先处理组内模块", HttpStatus.CONFLICT);
        }
        group.setStatus("DELETED");
        if (groupService.updateById(group) == 0) {
            throw new DomainException("MODULE_GROUP_VERSION_CONFLICT", "模块组已被其他操作修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_GROUP_DELETED", "MODULE_GROUP", groupId.toString(), "SUCCESS", Map.of("code", group.getCode()));
        return group;
    }

    private ConfiguredModuleGroup requireOwnedGroup(AuthenticatedContext context, Long groupId) {
        requireSystemContext(context);
        ConfiguredModuleGroup group = groupService.selectById(groupId);
        if (group == null || !context.systemId().equals(group.getSystemId())
                || !context.tenantId().equals(group.getOwnerTenantId()) || !"ACTIVE".equals(group.getStatus())) {
            throw notFound("模块组不存在");
        }
        return group;
    }

    @Transactional(readOnly = true)
    public ModuleConfigurationOverview overview(AuthenticatedContext context) {
        requireSystemContext(context);
        List<ConfiguredModuleGroup> groups = groupService.selectList(Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                .eq(ConfiguredModuleGroup::getSystemId, context.systemId())
                .eq(ConfiguredModuleGroup::getOwnerTenantId, context.tenantId())
                .eq(ConfiguredModuleGroup::getStatus, "ACTIVE")
                .orderByAsc(ConfiguredModuleGroup::getSortOrder, ConfiguredModuleGroup::getId));
        List<ConfiguredModule> modules = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, context.systemId())
                .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                .ne(ConfiguredModule::getStatus, "DELETED")
                .orderByAsc(ConfiguredModule::getGroupId, ConfiguredModule::getId));
        return new ModuleConfigurationOverview(groups, modules);
    }

    @Transactional
    public ModuleDraft createModule(AuthenticatedContext context, CreateModuleRequest request, String traceId) {
        lockConfigurationQuotaScope(context);
        requireQuotaCapacity(context, "MODULE_COUNT");
        ConfiguredModuleGroup group = resolveModuleGroup(context, request, traceId);
        ConfiguredModule module = new ConfiguredModule();
        module.setSystemId(context.systemId());
        module.setOwnerTenantId(context.tenantId());
        module.setGroupId(group.getId());
        module.setCode(resolveModuleCode(context, request.code()));
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
                Map.of("code", module.getCode(), "defaultPages", 3, "defaultMenus", 1, "defaultActions", 12));
        return draft(context, module.getId());
    }

    /** 复制当前草稿配置；新模块没有业务记录、发布版本和角色授权。 */
    @Transactional
    public ModuleDraft copyModule(AuthenticatedContext context, Long sourceId, CopyModuleRequest request, String traceId) {
        ConfiguredModule source = requireOwnedDraft(context, sourceId);
        if (!request.version().equals(source.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "源模块草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        lockConfigurationQuotaScope(context);
        requireQuotaCapacity(context, "MODULE_COUNT");
        ConfiguredModuleGroup group = requireOwnedGroup(context, request.groupId());
        ModuleDraft sourceDraft = draft(context, sourceId);
        ConfiguredModule copy = new ConfiguredModule();
        BeanUtils.copyProperties(source, copy);
        copy.setId(null);
        copy.setGroupId(group.getId());
        copy.setCode(resolveModuleCode(context, null));
        copy.setName(request.name().strip());
        copy.setStatus("DRAFT");
        copy.setDraftRevision(1);
        copy.setCreatedByMemberId(context.memberId());
        copy.setCreateTime(null);
        copy.setUpdateTime(null);
        copy.setVersion(0);
        moduleService.insert(copy);

        // 子表列和索引字段使用数据库 ID 引用，必须先复制主字段再重新映射。
        Map<Long, Long> fieldIds = new HashMap<>();
        for (ConfiguredModuleField field : sourceDraft.fields()) {
            if (field.getParentFieldId() != null) continue;
            copyField(field, sourceId, copy, null, fieldIds);
        }
        for (ConfiguredModuleField field : sourceDraft.fields()) {
            if (field.getParentFieldId() == null) continue;
            Long parentId = fieldIds.get(field.getParentFieldId());
            if (parentId == null) throw new DomainException("MODULE_COPY_INVALID", "源模块子表字段引用已失效", HttpStatus.CONFLICT);
            copyField(field, sourceId, copy, parentId, fieldIds);
        }
        for (ConfiguredModulePage page : sourceDraft.pages()) {
            ConfiguredModulePage target = new ConfiguredModulePage();
            BeanUtils.copyProperties(page, target);
            target.setId(null);
            target.setModuleId(copy.getId());
            target.setCreateTime(null);
            target.setUpdateTime(null);
            target.setVersion(0);
            pageService.insert(target);
        }
        Map<Long, Long> menuIds = new HashMap<>();
        List<CfgModuleMenu> remainingMenus = new java.util.ArrayList<>(sourceDraft.menus());
        while (!remainingMenus.isEmpty()) {
            int previousCount = remainingMenus.size();
            var iterator = remainingMenus.iterator();
            while (iterator.hasNext()) {
                CfgModuleMenu menu = iterator.next();
                if (menu.getParentId() != null && !menuIds.containsKey(menu.getParentId())) continue;
                copyMenu(menu, source, copy, menu.getParentId() == null ? null : menuIds.get(menu.getParentId()), menuIds);
                iterator.remove();
            }
            if (remainingMenus.size() == previousCount) {
                throw new DomainException("MODULE_COPY_INVALID", "源模块运行菜单引用已失效或存在循环", HttpStatus.CONFLICT);
            }
        }
        for (ConfiguredModuleAction action : sourceDraft.actions()) {
            ConfiguredModuleAction target = new ConfiguredModuleAction();
            BeanUtils.copyProperties(action, target);
            target.setId(null);
            target.setModuleId(copy.getId());
            target.setCreateTime(null);
            target.setUpdateTime(null);
            target.setVersion(0);
            actionService.insert(target);
        }
        for (CfgModuleRule rule : ruleService.selectList(Wrappers.<CfgModuleRule>lambdaQuery()
                .eq(CfgModuleRule::getModuleId, sourceId).ne(CfgModuleRule::getStatus, "DELETED"))) {
            CfgModuleRule target = new CfgModuleRule();
            BeanUtils.copyProperties(rule, target);
            target.setId(null);
            target.setModuleId(copy.getId());
            target.setTestStatus("NOT_TESTED");
            target.setLastTestInputJson(null);
            target.setLastTestResultJson(null);
            target.setLastTestedByMemberId(null);
            target.setLastTestedTime(null);
            target.setCreateTime(null);
            target.setUpdateTime(null);
            target.setVersion(0);
            ruleService.insert(target);
        }
        for (CfgQueryIndex index : indexService.selectList(Wrappers.<CfgQueryIndex>lambdaQuery()
                .eq(CfgQueryIndex::getModuleId, sourceId).ne(CfgQueryIndex::getStatus, "DELETED"))) {
            if (FieldUniqueIndexSynchronizer.managed(index)) continue;
            CfgQueryIndex target = new CfgQueryIndex();
            BeanUtils.copyProperties(index, target);
            target.setId(null);
            target.setModuleId(copy.getId());
            target.setCreateTime(null);
            target.setUpdateTime(null);
            target.setVersion(0);
            indexService.insert(target);
            for (CfgQueryIndexField field : indexFieldService.selectList(Wrappers.<CfgQueryIndexField>lambdaQuery()
                    .eq(CfgQueryIndexField::getQueryIndexId, index.getId()))) {
                Long fieldId = fieldIds.get(field.getFieldId());
                if (fieldId == null) throw new DomainException("MODULE_COPY_INVALID", "源模块索引字段引用已失效", HttpStatus.CONFLICT);
                CfgQueryIndexField targetField = new CfgQueryIndexField();
                BeanUtils.copyProperties(field, targetField);
                targetField.setId(null);
                targetField.setModuleId(copy.getId());
                targetField.setQueryIndexId(target.getId());
                targetField.setFieldId(fieldId);
                targetField.setCreateTime(null);
                indexFieldService.insert(targetField);
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_DRAFT_COPIED", "MODULE", copy.getId().toString(), "SUCCESS",
                Map.of("sourceModuleId", sourceId, "moduleCode", copy.getCode()));
        return draft(context, copy.getId());
    }

    private void copyField(ConfiguredModuleField field, Long sourceId, ConfiguredModule copy,
                           Long parentId, Map<Long, Long> fieldIds) {
        ConfiguredModuleField target = new ConfiguredModuleField();
        BeanUtils.copyProperties(field, target);
        target.setId(null);
        target.setModuleId(copy.getId());
        target.setParentFieldId(parentId);
        if (sourceId.equals(field.getReferenceModuleId())) target.setReferenceModuleId(copy.getId());
        target.setCreateTime(null);
        target.setUpdateTime(null);
        target.setVersion(0);
        fieldService.insert(target);
        fieldIds.put(field.getId(), target.getId());
        fieldUniqueIndexSynchronizer.sync(target);
    }

    private void copyMenu(CfgModuleMenu menu, ConfiguredModule source, ConfiguredModule copy,
                          Long parentId, Map<Long, Long> menuIds) {
        CfgModuleMenu target = new CfgModuleMenu();
        BeanUtils.copyProperties(menu, target);
        target.setId(null);
        target.setModuleId(copy.getId());
        target.setParentId(parentId);
        if (source.getCode().equals(menu.getCode())) target.setCode(copy.getCode());
        if (source.getName().equals(menu.getName())) target.setName(copy.getName());
        if (("/runtime/" + source.getCode()).equals(menu.getRoutePath())) target.setRoutePath("/runtime/" + copy.getCode());
        target.setCreateTime(null);
        target.setUpdateTime(null);
        target.setVersion(0);
        menuService.insert(target);
        menuIds.put(menu.getId(), target.getId());
    }

    /** 模块名称属于可发布草稿，编码保持不变以保护权限和外部引用。 */
    @Transactional
    public ConfiguredModule renameModule(AuthenticatedContext context, Long moduleId,
                                         RenameModuleRequest request, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        if (!request.version().equals(module.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "模块草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        String previousName = module.getName();
        module.setName(request.name().strip());
        // 默认运行菜单沿用模块名称时同步改名；管理员单独配置过的菜单标题保持原样。
        for (CfgModuleMenu menu : menuService.selectList(Wrappers.<CfgModuleMenu>lambdaQuery()
                .eq(CfgModuleMenu::getModuleId, moduleId)
                .eq(CfgModuleMenu::getCode, module.getCode()))) {
            if (previousName.equals(menu.getName())) {
                menu.setName(module.getName());
                if (menuService.updateById(menu) == 0) {
                    throw new DomainException("DRAFT_VERSION_CONFLICT", "模块菜单草稿已被其他操作修改", HttpStatus.CONFLICT);
                }
            }
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_DRAFT_RENAMED", "MODULE", moduleId.toString(), "SUCCESS",
                Map.of("code", module.getCode(), "name", module.getName()));
        return module;
    }

    @Transactional
    public ConfiguredModuleField createField(
            AuthenticatedContext context,
            Long moduleId,
            CreateModuleFieldRequest request,
            String traceId) {
        lockConfigurationQuotaScope(context);
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        requireFieldQuotaCapacity(context);
        String fieldType = request.fieldType().strip().toUpperCase(Locale.ROOT);
        if (!FIELD_TYPES.contains(fieldType)) {
            throw new DomainException("FIELD_TYPE_UNSUPPORTED", "当前字段类型不受支持", HttpStatus.BAD_REQUEST);
        }
        validateFieldDefinition(context, fieldType, request.dictionaryId(), request.referenceModuleId(), request.config(), traceId);
        validateParentField(context, moduleId, fieldType, request.parentFieldId());
        if (request.parentFieldId() != null && Boolean.TRUE.equals(request.uniqueValue())) {
            invalidField("子表列不能使用主记录唯一索引");
        }
        if ("SECRET".equals(fieldType) && (Boolean.TRUE.equals(request.searchable()) || Boolean.TRUE.equals(request.uniqueValue()))) {
            invalidField("密码/密钥不支持搜索或唯一性索引");
        }
        if ("JSON".equals(fieldType) && Boolean.TRUE.equals(request.searchable())) invalidField("结构化数据使用是否已填写筛选，不支持全文搜索");
        ConfiguredModuleField field = new ConfiguredModuleField();
        field.setSystemId(context.systemId());
        field.setOwnerTenantId(context.tenantId());
        field.setModuleId(moduleId);
        field.setCode(resolveFieldCode(moduleId, request.code()));
        field.setName(request.name().strip());
        field.setFieldType(fieldType);
        field.setRequired(request.required());
        field.setUniqueValue(Boolean.TRUE.equals(request.uniqueValue()));
        field.setSearchable(Boolean.TRUE.equals(request.searchable()));
        field.setDictionaryId(request.dictionaryId());
        field.setReferenceModuleId(request.referenceModuleId());
        field.setParentFieldId(request.parentFieldId());
        field.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        field.setStatus("ACTIVE");
        field.setConfigJson(toJson(request.config()));
        field.setVersion(0);
        fieldService.insert(field);
        fieldUniqueIndexSynchronizer.sync(field);
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
        if (field == null || "DELETED".equals(field.getStatus()) || !moduleId.equals(field.getModuleId()) || !context.systemId().equals(field.getSystemId())
                || !context.tenantId().equals(field.getOwnerTenantId())) {
            throw notFound("字段不存在");
        }
        if (!request.version().equals(field.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "字段草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        Long dictionaryId = request.dictionaryId() == null ? field.getDictionaryId() : request.dictionaryId();
        Long referenceModuleId = request.referenceModuleId() == null ? field.getReferenceModuleId() : request.referenceModuleId();
        validateFieldDefinition(context, field.getFieldType(), dictionaryId, referenceModuleId, request.config(), traceId);
        // 已写入的子表列不能通过修改字段配置转移到另一张子表，避免历史明细失去定义。
        if (request.parentFieldId() != null && !request.parentFieldId().equals(field.getParentFieldId())) {
            invalidField("字段所属子表不能修改，请新建列字段");
        }
        if ("ACTIVE".equals(request.status())) {
            validateParentField(context, moduleId, field.getFieldType(), field.getParentFieldId());
        }
        if (field.getParentFieldId() != null && Boolean.TRUE.equals(request.uniqueValue())) {
            invalidField("子表列不能使用主记录唯一索引");
        }
        if ("SECRET".equals(field.getFieldType()) && (Boolean.TRUE.equals(request.searchable()) || Boolean.TRUE.equals(request.uniqueValue()))) {
            invalidField("密码/密钥不支持搜索或唯一性索引");
        }
        if ("JSON".equals(field.getFieldType()) && Boolean.TRUE.equals(request.searchable())) invalidField("结构化数据使用是否已填写筛选，不支持全文搜索");
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
        fieldUniqueIndexSynchronizer.sync(field);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_FIELD_DRAFT_UPDATED", "MODULE_FIELD", fieldId.toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "status", field.getStatus()));
        return field;
    }

    /** 删除只作用于从未发布的草稿字段；已发布字段须先停用并保留历史版本。 */
    @Transactional
    public ConfiguredModuleField deleteField(AuthenticatedContext context, Long moduleId, Long fieldId,
                                             Integer version, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        ConfiguredModuleField field = fieldService.selectById(fieldId);
        if (field == null || "DELETED".equals(field.getStatus()) || !moduleId.equals(field.getModuleId())
                || !context.systemId().equals(field.getSystemId())
                || !context.tenantId().equals(field.getOwnerTenantId())) throw notFound("字段不存在");
        if (!version.equals(field.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "字段草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        if (everPublished(moduleId)) {
            throw new DomainException("MODULE_FIELD_IN_USE", "模块已有发布版本，请停用字段并发布新版本；历史字段和数据须保留", HttpStatus.CONFLICT);
        }
        if (!fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getParentFieldId, fieldId)
                .ne(ConfiguredModuleField::getStatus, "DELETED")).isEmpty()) {
            throw new DomainException("MODULE_FIELD_IN_USE", "子表仍有列字段，请先处理子表列", HttpStatus.CONFLICT);
        }
        String quotedCode = "\"" + field.getCode() + "\"";
        boolean pageUsesField = pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                .eq(ConfiguredModulePage::getModuleId, moduleId)
                .eq(ConfiguredModulePage::getStatus, "ACTIVE")).stream()
                .anyMatch(page -> page.getLayoutJson() != null && page.getLayoutJson().contains(quotedCode));
        boolean ruleUsesField = ruleService.selectList(Wrappers.<CfgModuleRule>lambdaQuery()
                .eq(CfgModuleRule::getModuleId, moduleId)
                .eq(CfgModuleRule::getStatus, "ACTIVE")).stream()
                .anyMatch(rule -> rule.getExpressionText() != null && rule.getExpressionText().contains(field.getCode()));
        boolean fieldUsesField = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId)
                .ne(ConfiguredModuleField::getId, fieldId)
                .ne(ConfiguredModuleField::getStatus, "DELETED")).stream()
                .anyMatch(other -> other.getConfigJson() != null && other.getConfigJson().contains(quotedCode));
        boolean linkedFieldUsesField = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getSystemId, context.systemId())
                .eq(ConfiguredModuleField::getReferenceModuleId, moduleId)
                .ne(ConfiguredModuleField::getStatus, "DELETED")).stream()
                .anyMatch(other -> other.getConfigJson() != null && other.getConfigJson().contains(quotedCode));
        boolean actionUsesField = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getStatus, "ACTIVE")).stream()
                .anyMatch(action -> action.getConfigJson() != null && action.getConfigJson().contains(quotedCode));
        boolean moduleUsesField = field.getCode().equals(module.getTitleFieldCode())
                || field.getCode().equals(module.getStatusFieldCode());
        List<Long> activeIndexIds = indexService.selectList(Wrappers.<CfgQueryIndex>lambdaQuery()
                .eq(CfgQueryIndex::getModuleId, moduleId)
                .eq(CfgQueryIndex::getStatus, "ACTIVE")).stream()
                .filter(index -> !FieldUniqueIndexSynchronizer.managed(index))
                .map(CfgQueryIndex::getId).toList();
        boolean indexUsesField = !activeIndexIds.isEmpty() && !indexFieldService.selectList(
                Wrappers.<CfgQueryIndexField>lambdaQuery().in(CfgQueryIndexField::getQueryIndexId, activeIndexIds)
                        .eq(CfgQueryIndexField::getFieldId, fieldId)).isEmpty();
        if (pageUsesField || ruleUsesField || fieldUsesField || linkedFieldUsesField || actionUsesField
                || moduleUsesField || indexUsesField) {
            throw new DomainException("MODULE_FIELD_IN_USE", "字段仍被页面、关联字段、动作、规则、模块属性或索引引用，请先移除关联配置", HttpStatus.CONFLICT);
        }
        field.setStatus("DELETED");
        if (fieldService.updateById(field) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "字段草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        fieldUniqueIndexSynchronizer.sync(field);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_FIELD_DRAFT_DELETED", "MODULE_FIELD", fieldId.toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "fieldCode", field.getCode()));
        return field;
    }

    /** 删除未发布模块时保留内部标识以防旧引用被新模块接管，运行入口与配置入口立即隐藏。 */
    @Transactional
    public ConfiguredModule deleteModule(AuthenticatedContext context, Long moduleId, Integer version, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        if (!version.equals(module.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "模块草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        if (everPublished(moduleId)) {
            throw new DomainException("MODULE_IN_USE", "模块已有发布版本，可能承载业务数据、权限或流程；请先处理依赖，不能直接删除", HttpStatus.CONFLICT);
        }
        if (!fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getSystemId, context.systemId())
                .eq(ConfiguredModuleField::getReferenceModuleId, moduleId)
                .ne(ConfiguredModuleField::getModuleId, moduleId)
                .ne(ConfiguredModuleField::getStatus, "DELETED")).isEmpty()) {
            throw new DomainException("MODULE_IN_USE", "其他模块字段仍关联此模块，请先解除关联", HttpStatus.CONFLICT);
        }
        for (ConfiguredModuleField field : fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId)
                .ne(ConfiguredModuleField::getStatus, "DELETED"))) {
            field.setStatus("DELETED");
            if (fieldService.updateById(field) == 0) {
                throw new DomainException("DRAFT_VERSION_CONFLICT", "模块字段已被其他操作修改", HttpStatus.CONFLICT);
            }
            fieldUniqueIndexSynchronizer.sync(field);
        }
        module.setStatus("DELETED");
        if (moduleService.updateById(module) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "模块草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_DRAFT_DELETED", "MODULE", moduleId.toString(), "SUCCESS",
                Map.of("moduleCode", module.getCode(), "published", false));
        return module;
    }

    private boolean everPublished(Long moduleId) {
        return versionService.selectCount(Wrappers.<ConfiguredModuleVersion>lambdaQuery()
                .eq(ConfiguredModuleVersion::getModuleId, moduleId)) > 0;
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
        if ("FORM".equals(page.getPageType())) {
            Set<String> activeCodes = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                    .eq(ConfiguredModuleField::getModuleId, moduleId).eq(ConfiguredModuleField::getStatus, "ACTIVE"))
                    .stream().map(ConfiguredModuleField::getCode).collect(java.util.stream.Collectors.toSet());
            var problems = FormLayoutProtocol.validate(objectMapper.valueToTree(request.layout()), activeCodes);
            if (!problems.isEmpty()) throw new DomainException("FORM_LAYOUT_INVALID", problems.getFirst().message(), HttpStatus.BAD_REQUEST);
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
        // 同一模块每种专用页面仅允许一份，页面编码与默认页面保持一致。
        page.setPageCode(pageType.toLowerCase(Locale.ROOT));
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

    /** 页面改名保留 pageCode，发布后由运行态读取新的页面名称。 */
    @Transactional
    public ConfiguredModulePage renamePage(AuthenticatedContext context, Long moduleId, String pageType,
                                           RenameModulePageRequest request, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        ConfiguredModulePage page = pageService.selectList(Wrappers.<ConfiguredModulePage>lambdaQuery()
                        .eq(ConfiguredModulePage::getModuleId, moduleId)
                        .eq(ConfiguredModulePage::getPageType, pageType.strip().toUpperCase(Locale.ROOT)))
                .stream().findFirst().orElseThrow(() -> notFound("页面不存在"));
        if (!context.systemId().equals(page.getSystemId()) || !context.tenantId().equals(page.getOwnerTenantId())) {
            throw notFound("页面不存在");
        }
        if (!request.version().equals(page.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "页面草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        page.setName(request.name().strip());
        if (pageService.updateById(page) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "页面草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PAGE_DRAFT_RENAMED", "MODULE_PAGE", page.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "pageCode", page.getPageCode(), "name", page.getName()));
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
                        .eq(ConfiguredModuleAction::getOwnerTenantId, module.getOwnerTenantId())
                        .eq(ConfiguredModuleAction::getCode, actionCode.toUpperCase(Locale.ROOT)))
                .stream().findFirst().orElseThrow(() -> notFound("动作不存在"));
        if (!request.version().equals(action.getVersion())) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "动作草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        if ("APPROVAL".equals(action.getCode())
                && !Set.of("ROW", "DETAIL_HEADER", "DETAIL_MORE").contains(request.location())) {
            throw new DomainException("MODULE_APPROVAL_LOCATION_INVALID", "审批动作只能放在单条记录的操作区",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("DASHBOARD".equals(action.getCode()) && !"MODULE_ENTRY".equals(request.location())) {
            throw new DomainException("MODULE_DASHBOARD_LOCATION_INVALID", "仪表盘入口只能放在模块入口",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        action.setLocation(request.location());
        if (Set.of("PRINT", "APPROVAL", "DASHBOARD").contains(action.getCode()) && request.status() != null) {
            action.setStatus(request.status());
        }
        if ("CUSTOM".equals(action.getActionType())) {
            validateCustomActionConfig(moduleId, request.config());
        }
        if ("DETAIL".equals(action.getCode())) {
            // 评论和团队开关属于发布动作快照；拒绝字符串等模糊配置，避免运行态误判。
            for (String key : List.of("commentEnabled", "teamEnabled")) {
                if (request.config().has(key) && !request.config().path(key).isBoolean()) {
                    throw new DomainException("MODULE_FUNCTION_CONFIG_INVALID", "模块协作功能开关必须为布尔值",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
        }
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

    /** 创建可配置记录动作；动作编码和内部配置由服务端生成，管理员只操作业务名称与效果。 */
    @Transactional
    public ConfiguredModuleAction createCustomAction(
            AuthenticatedContext context, Long moduleId, CreateCustomActionRequest request, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        LinkedHashMap<String, Object> config = new LinkedHashMap<>();
        config.put("operation", request.operation() == null ? "UPDATE_FIELDS" : request.operation());
        config.put("parameterFieldCodes", request.parameterFieldCodes());
        if ("SET_STATUS".equals(config.get("operation"))) config.put("statusValue", blankToNull(request.statusValue()));
        config.put("confirmation", request.confirmation());
        config.put("confirmationText", blankToNull(request.confirmationText()));
        JsonNode configNode = objectMapper.valueToTree(config);
        validateCustomActionConfig(moduleId, configNode);

        ConfiguredModuleAction action = new ConfiguredModuleAction();
        action.setSystemId(module.getSystemId());
        action.setOwnerTenantId(module.getOwnerTenantId());
        action.setModuleId(moduleId);
        action.setCode("ACTION_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        action.setName(request.name().strip());
        action.setActionType("CUSTOM");
        action.setLocation(request.location());
        action.setSortOrder(actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)).stream()
                .map(ConfiguredModuleAction::getSortOrder).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 10);
        action.setStatus("ACTIVE");
        action.setConfigJson(toJson(configNode));
        action.setVersion(0);
        actionService.insert(action);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_CUSTOM_ACTION_CREATED", "MODULE_ACTION", action.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "actionCode", action.getCode(), "parameterCount", request.parameterFieldCodes().size()));
        return action;
    }

    /** 打印属于可选模块动作；已有模块由所属人在草稿中补建，发布后才影响运行入口。 */
    @Transactional
    public ConfiguredModuleAction createPrintAction(AuthenticatedContext context, Long moduleId, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        if (!actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getCode, "PRINT")).isEmpty()) {
            throw new DomainException("MODULE_PRINT_ACTION_EXISTS", "打印动作已存在", HttpStatus.CONFLICT);
        }
        int order = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)).stream()
                .map(ConfiguredModuleAction::getSortOrder).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 10;
        ConfiguredModuleAction action = createDefaultAction(module, "PRINT", "打印", "DETAIL_MORE", order);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_PRINT_ACTION_CREATED", "MODULE_ACTION", action.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "actionCode", "PRINT"));
        return action;
    }

    /** 审批是可选模块动作；先进入草稿，发布后再由模块 Flow 绑定指定审批流程。 */
    @Transactional
    public ConfiguredModuleAction createApprovalAction(AuthenticatedContext context, Long moduleId, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        if (!actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getCode, "APPROVAL")).isEmpty()) {
            throw new DomainException("MODULE_APPROVAL_ACTION_EXISTS", "审批动作已存在", HttpStatus.CONFLICT);
        }
        int order = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)).stream()
                .map(ConfiguredModuleAction::getSortOrder).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 10;
        ConfiguredModuleAction action = createDefaultAction(module, "APPROVAL", "发起审批", "DETAIL_HEADER", order);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_APPROVAL_ACTION_CREATED", "MODULE_ACTION", action.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "actionCode", "APPROVAL"));
        return action;
    }

    /** 仪表盘入口属于可选模块动作；仪表盘页面仍由现有仪表盘配置和发布。 */
    @Transactional
    public ConfiguredModuleAction createDashboardAction(AuthenticatedContext context, Long moduleId, String traceId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        if (!actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getCode, "DASHBOARD")).isEmpty()) {
            throw new DomainException("MODULE_DASHBOARD_ACTION_EXISTS", "仪表盘入口已存在", HttpStatus.CONFLICT);
        }
        int order = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)).stream()
                .map(ConfiguredModuleAction::getSortOrder).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 10;
        ConfiguredModuleAction action = createDefaultAction(module, "DASHBOARD", "模块首页", "MODULE_ENTRY", order);
        touch(module);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_DASHBOARD_ACTION_CREATED", "MODULE_ACTION", action.getId().toString(), "SUCCESS",
                Map.of("moduleId", moduleId, "actionCode", "DASHBOARD"));
        return action;
    }

    /** 校验命名动作只使用受支持的效果及当前模块可写字段。 */
    private void validateCustomActionConfig(Long moduleId, JsonNode config) {
        ConfiguredModule module = moduleService.selectById(moduleId);
        List<JsonNode> fields = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .eq(ConfiguredModuleField::getModuleId, moduleId)
                        .eq(ConfiguredModuleField::getOwnerTenantId, module.getOwnerTenantId()))
                .stream().map(field -> (JsonNode) objectMapper.valueToTree(field)).toList();
        validateCustomActionConfig(config, fields);
    }

    /** 基础配置与扩展共用效果校验；固定状态不接受客户端伪造的字段写入清单。 */
    void validateCustomActionConfig(JsonNode config, List<JsonNode> fields) {
        String operation = config.path("operation").asText();
        JsonNode parameters = config.path("parameterFieldCodes");
        if ("SET_STATUS".equals(operation)) {
            JsonNode status = config.path("statusValue");
            String value = status.isTextual() ? status.asText() : "";
            if (!parameters.isArray() || !parameters.isEmpty() || value.isBlank() || value.length() > 64
                    || !value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) {
                throw new DomainException("CUSTOM_ACTION_CONFIG_INVALID", "固定状态动作必须填写有效状态，且不能配置填写字段",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            return;
        }
        if (!"UPDATE_FIELDS".equals(operation)
                || !parameters.isArray() || parameters.isEmpty() || parameters.size() > 20) {
            throw new DomainException("CUSTOM_ACTION_CONFIG_INVALID", "手动动作必须配置 1 至 20 个填写字段",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Set<String> requested = new java.util.LinkedHashSet<>();
        parameters.forEach(node -> requested.add(node.asText()));
        if (requested.size() != parameters.size()) {
            throw new DomainException("CUSTOM_ACTION_CONFIG_INVALID", "手动动作字段不能重复",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Set<String> writable = fields.stream().filter(field -> "ACTIVE".equals(field.path("status").asText())
                        && !field.hasNonNull("parentFieldId")
                        && CUSTOM_ACTION_FIELD_TYPES.contains(field.path("fieldType").asText()))
                .filter(field -> {
                    try { return !objectMapper.readTree(field.path("configJson").asText("{}")).path("readOnly").asBoolean(false); }
                    catch (JsonProcessingException exception) { return false; }
                }).map(field -> field.path("code").asText()).collect(java.util.stream.Collectors.toSet());
        if (!writable.containsAll(requested)) {
            throw new DomainException("CUSTOM_ACTION_FIELD_INVALID", "手动动作引用了不存在、停用或只读的字段",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** 手动动作仅开放已有可视化输入控件支持的可写字段，损坏配置按不可写处理。 */
    private boolean customActionFieldWritable(ConfiguredModuleField field) {
        if (field.getParentFieldId() != null || !CUSTOM_ACTION_FIELD_TYPES.contains(field.getFieldType())) return false;
        try {
            return !objectMapper.readTree(field.getConfigJson()).path("readOnly").asBoolean(false);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Transactional
    public ModuleDraft draft(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = requireOwnedDraft(context, moduleId);
        List<ConfiguredModuleField> fields = fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                .eq(ConfiguredModuleField::getModuleId, moduleId)
                .eq(ConfiguredModuleField::getOwnerTenantId, module.getOwnerTenantId())
                .ne(ConfiguredModuleField::getStatus, "DELETED")
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
                .eq(ConfiguredModuleAction::getOwnerTenantId, module.getOwnerTenantId())
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
        page.setPageCode(type.toLowerCase(Locale.ROOT));
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

    private ConfiguredModuleAction createDefaultAction(ConfiguredModule module, String code, String name, String location, int order) {
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
        return action;
    }

    private ConfiguredModule requireOwnedDraft(AuthenticatedContext context, Long moduleId) {
        requireSystemContext(context);
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || "DELETED".equals(module.getStatus()) || !context.systemId().equals(module.getSystemId()) || !context.tenantId().equals(module.getOwnerTenantId())) {
            throw notFound("模块不存在");
        }
        return module;
    }

    /** 模块字段与租户扩展共用配置语义，绑定来源由服务端校验。 */
    void validateFieldDefinition(
            AuthenticatedContext context,
            String fieldType,
            Long dictionaryId,
            Long referenceModuleId,
            com.fasterxml.jackson.databind.JsonNode config,
            String traceId) {
        if (!FIELD_TYPES.contains(fieldType) || config == null || !config.isObject()) {
            invalidField("字段类型或配置结构无效");
        }
        try {
            TextFieldProtocol.validateConfiguration(fieldType, config);
            StructuredContentFieldProtocol.validateConfiguration(fieldType, config);
            SecretFieldProtocol.validateConfiguration(fieldType, config);
        } catch (IllegalArgumentException exception) {
            invalidField(exception.getMessage());
        }
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
            SystemTenant owner = reference == null ? null : tenantService.selectById(reference.getOwnerTenantId());
            // 基础模块身份可以被本租户扩展引用，目标记录仍由运行时按本租户隔离和授权。
            boolean inherited = owner != null && Boolean.TRUE.equals(owner.getMain())
                    && "ACTIVE".equals(owner.getStatus());
            if (reference == null || "DELETED".equals(reference.getStatus()) || !context.systemId().equals(reference.getSystemId())
                    || !(context.tenantId().equals(reference.getOwnerTenantId()) || inherited)) {
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
            if (referenceModuleId != null) validateModuleCascadeFields(referenceModuleId, config);
        }
        if ("MONEY".equals(fieldType)) {
            if (config.path("currency").asText().isBlank()) invalidField("金额字段必须配置币种");
            int precision = config.path("precision").asInt(-1);
            if (precision < 0 || precision > 6) invalidField("金额精度必须在 0 到 6 之间");
        }
        if (Set.of("REFERENCE", "LOOKUP", "SUMMARY", "AGGREGATE").contains(fieldType)
                && referenceModuleId == null) {
            invalidField("该字段类型必须选择目标模块");
        }
        if ("SUBTABLE".equals(fieldType) && referenceModuleId != null) {
            invalidField("子表明细由当前模块的列字段定义，不选择目标模块");
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

    /** 子表列必须绑定同模块的有效子表字段，主记录字段及子表容器不允许形成嵌套。 */
    private void validateParentField(AuthenticatedContext context, Long moduleId, String fieldType, Long parentFieldId) {
        if (parentFieldId == null) return;
        if ("SUBTABLE".equals(fieldType) || fieldType.startsWith("SYSTEM_")) {
            invalidField("子表不能嵌套，也不能把系统字段作为子表列");
        }
        ConfiguredModuleField parent = fieldService.selectById(parentFieldId);
        if (parent == null || !moduleId.equals(parent.getModuleId())
                || !context.systemId().equals(parent.getSystemId())
                || !context.tenantId().equals(parent.getOwnerTenantId())
                || !"ACTIVE".equals(parent.getStatus()) || !"SUBTABLE".equals(parent.getFieldType())
                || parent.getParentFieldId() != null) {
            invalidField("所属子表字段不存在、已停用或不属于当前模块");
        }
    }

    /** 模块级联依赖目标模块的命名显示、唯一值及自关联父级字段，不能只保存目标模块 ID。 */
    private void validateModuleCascadeFields(Long targetModuleId, JsonNode config) {
        String display = config.path("displayFieldCode").asText("").strip();
        String value = config.path("valueFieldCode").asText("").strip();
        String parent = config.path("parentFieldCode").asText("").strip();
        if (display.isBlank() || value.isBlank() || parent.isBlank()) {
            invalidField("模块级联必须选择显示字段、唯一值字段和父级关联字段");
        }
        ConfiguredModulePublication publication = publicationService.selectList(
                Wrappers.<ConfiguredModulePublication>lambdaQuery()
                        .eq(ConfiguredModulePublication::getModuleId, targetModuleId))
                .stream().findFirst().orElse(null);
        ConfiguredModuleVersion version = publication == null || publication.getCurrentVersionId() == null
                ? null : versionService.selectById(publication.getCurrentVersionId());
        if (version == null) invalidField("模块级联目标模块必须先发布");
        JsonNode targetFields;
        try {
            targetFields = objectMapper.readTree(version.getSnapshotJson()).path("fields");
        } catch (JsonProcessingException exception) {
            invalidField("模块级联目标模块的发布字段不可读取");
            return;
        }
        boolean displayValid = Set.of("title", "recordNumber").contains(display)
                || java.util.stream.StreamSupport.stream(targetFields.spliterator(), false)
                .anyMatch(field -> display.equals(field.path("code").asText())
                        && Set.of("TEXT", "MULTILINE_TEXT").contains(field.path("fieldType").asText()));
        boolean valueValid = Set.of("id", "recordNumber").contains(value)
                || java.util.stream.StreamSupport.stream(targetFields.spliterator(), false)
                .anyMatch(field -> value.equals(field.path("code").asText())
                        && field.path("uniqueValue").asBoolean(false) && field.path("searchable").asBoolean(false));
        boolean parentValid = java.util.stream.StreamSupport.stream(targetFields.spliterator(), false)
                .anyMatch(field -> parent.equals(field.path("code").asText())
                        && "REFERENCE".equals(field.path("fieldType").asText())
                        && targetModuleId.equals(field.path("referenceModuleId").asLong()));
        if (!displayValid || !valueValid || !parentValid) {
            invalidField("模块级联字段必须来自目标模块的有效字段，父级字段须关联目标模块自身");
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

    /** 模块与租户扩展新增字段共用系统行锁，保证配额计数和插入串行执行。 */
    void lockConfigurationQuotaScope(AuthenticatedContext context) {
        requireSystemContext(context);
        identifierIntegrity.lockSystem(context.systemId());
    }

    /** 主模块与租户扩展共用实际字段数门槛，仅在新字段落库前调用。 */
    void requireFieldQuotaCapacity(AuthenticatedContext context) {
        requireQuotaCapacity(context, "FIELD_COUNT");
    }

    /** 总量配额统计可编辑的模块/字段定义；停用仍占容量，已删除草稿不占容量。 */
    private void requireQuotaCapacity(AuthenticatedContext context, String quotaCode) {
        CoreQuotaPolicy policy = quotaPolicyService.selectOne(Wrappers.<CoreQuotaPolicy>lambdaQuery()
                .eq(CoreQuotaPolicy::getContextType, "SYSTEM")
                .eq(CoreQuotaPolicy::getSystemId, context.systemId())
                .eq(CoreQuotaPolicy::getTenantId, context.tenantId())
                .eq(CoreQuotaPolicy::getQuotaCode, quotaCode)
                .eq(CoreQuotaPolicy::getPeriodType, "TOTAL")
                .eq(CoreQuotaPolicy::getStatus, "ACTIVE")
                .last("FOR UPDATE"));
        if (policy == null) return;
        // 锁定读取实际定义，避免 MySQL 可重复读事务沿用等待锁之前形成的旧快照。
        long configured = "MODULE_COUNT".equals(quotaCode)
                ? moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .select(ConfiguredModule::getId)
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                        .ne(ConfiguredModule::getStatus, "DELETED")
                        .last("FOR UPDATE")).size()
                : fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                        .select(ConfiguredModuleField::getId)
                        .eq(ConfiguredModuleField::getSystemId, context.systemId())
                        .eq(ConfiguredModuleField::getOwnerTenantId, context.tenantId())
                        .ne(ConfiguredModuleField::getStatus, "DELETED")
                        .last("FOR UPDATE")).size();
        if (configured >= policy.getHardLimit()) {
            boolean moduleQuota = "MODULE_COUNT".equals(quotaCode);
            throw new DomainException(moduleQuota ? "TENANT_MODULE_QUOTA_EXCEEDED" : "TENANT_FIELD_QUOTA_EXCEEDED",
                    moduleQuota ? "当前租户模块数已达到配额上限" : "当前租户字段数已达到配额上限",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private String normalizeCode(String value) {
        String code = value.strip().toLowerCase(Locale.ROOT);
        // 配置编码会进入权限、导入导出及应用引用，服务入口也必须拒绝非法字符。
        if (!code.matches("[a-z][a-z0-9_]{1,49}")) {
            throw new DomainException("MODULE_CONFIG_CODE_INVALID", "配置编码只能包含英文、数字和下划线，且需以字母开头",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return code;
    }

    private ConfiguredModuleGroup resolveModuleGroup(
            AuthenticatedContext context,
            CreateModuleRequest request,
            String traceId) {
        if (request.groupId() != null) {
            return requireOwnedGroup(context, request.groupId());
        }
        if (request.groupName() == null || request.groupName().isBlank()) {
            throw new DomainException("MODULE_GROUP_REQUIRED", "请选择业务分组或填写新分组名称", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        ConfiguredModuleGroup group = new ConfiguredModuleGroup();
        group.setSystemId(context.systemId());
        group.setOwnerTenantId(context.tenantId());
        group.setCode(resolveGroupCode(context, null));
        group.setName(request.groupName().strip());
        Integer nextSortOrder = groupService.selectList(Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                        .eq(ConfiguredModuleGroup::getSystemId, context.systemId())
                        .eq(ConfiguredModuleGroup::getOwnerTenantId, context.tenantId()))
                .stream().map(ConfiguredModuleGroup::getSortOrder).max(Integer::compareTo).orElse(0) + 10;
        group.setSortOrder(nextSortOrder);
        group.setStatus("ACTIVE");
        group.setCreatedByMemberId(context.memberId());
        group.setVersion(0);
        groupService.insert(group);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "MODULE_GROUP_DRAFT_CREATED", "MODULE_GROUP", group.getId().toString(), "SUCCESS",
                Map.of("code", group.getCode(), "createdWithModule", true));
        return group;
    }

    private String resolveGroupCode(AuthenticatedContext context, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = normalizeCode(requestedCode);
            if (groupService.selectList(Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                    .eq(ConfiguredModuleGroup::getOwnerTenantId, context.tenantId())
                    .eq(ConfiguredModuleGroup::getCode, normalized)).isEmpty()) {
                return normalized;
            }
            throw new DomainException("MODULE_GROUP_CODE_EXISTS", "业务分组标识已经存在", HttpStatus.CONFLICT);
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String generated = generatedCode("group");
            if (groupService.selectList(Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                    .eq(ConfiguredModuleGroup::getOwnerTenantId, context.tenantId())
                    .eq(ConfiguredModuleGroup::getCode, generated)).isEmpty()) {
                return generated;
            }
        }
        throw codeGenerationFailed();
    }

    private String resolveModuleCode(AuthenticatedContext context, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = normalizeCode(requestedCode);
            if (!identifierIntegrity.occupied(context.systemId(), normalized)) {
                return normalized;
            }
            throw new DomainException("MODULE_CODE_EXISTS", "模块标识已经存在", HttpStatus.CONFLICT);
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String generated = generatedCode("module");
            if (!identifierIntegrity.occupied(context.systemId(), generated)) {
                return generated;
            }
        }
        throw codeGenerationFailed();
    }

    private String resolveFieldCode(Long moduleId, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String normalized = normalizeCode(requestedCode);
            if (fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                    .eq(ConfiguredModuleField::getModuleId, moduleId)
                    .eq(ConfiguredModuleField::getCode, normalized)).isEmpty()) {
                return normalized;
            }
            throw new DomainException("MODULE_FIELD_CODE_EXISTS", "字段标识已经存在", HttpStatus.CONFLICT);
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String generated = generatedCode("field");
            if (fieldService.selectList(Wrappers.<ConfiguredModuleField>lambdaQuery()
                    .eq(ConfiguredModuleField::getModuleId, moduleId)
                    .eq(ConfiguredModuleField::getCode, generated)).isEmpty()) {
                return generated;
            }
        }
        throw codeGenerationFailed();
    }

    private String generatedCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private DomainException codeGenerationFailed() {
        return new DomainException("STABLE_CODE_GENERATION_FAILED", "内部标识生成失败，请重试", HttpStatus.CONFLICT);
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
