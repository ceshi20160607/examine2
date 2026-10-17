package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.moduleconfig.base.entity.BusinessModule;
import com.unique.unexamine.moduleconfig.base.entity.ModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ModuleField;
import com.unique.unexamine.moduleconfig.base.entity.ModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ModuleMenu;
import com.unique.unexamine.moduleconfig.base.entity.ModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.BusinessModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleMenuBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleVersionBaseService;
import com.unique.unexamine.system.manage.SystemManageService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/** 系统内模块草稿与发布；运行态只能读取已发布版本，不直接读取草稿字段。 */
@Service
public class ModuleManageService {
    private final SystemManageService systems;
    private final SystemPermissionService access;
    private final ModuleGroupBaseService groups;
    private final BusinessModuleBaseService modules;
    private final ModuleFieldBaseService fields;
    private final ModuleMenuBaseService menus;
    private final ModuleActionBaseService actions;
    private final ModuleVersionBaseService versions;
    private final ObjectMapper json;

    public ModuleManageService(SystemManageService systems, SystemPermissionService access,
                               ModuleGroupBaseService groups, BusinessModuleBaseService modules,
                               ModuleFieldBaseService fields, ModuleMenuBaseService menus, ModuleActionBaseService actions,
                               ModuleVersionBaseService versions, ObjectMapper json) {
        this.systems = systems;
        this.access = access;
        this.groups = groups;
        this.modules = modules;
        this.fields = fields;
        this.menus = menus;
        this.actions = actions;
        this.versions = versions;
        this.json = json;
    }

    /** 创建模块组；系统内编码唯一，模块组只负责导航组织。 */
    public ModuleGroup createGroup(Long accountId, Long systemId, String code, String name) {
        systems.requireOwner(accountId, systemId);
        if (groups.selectOne(new LambdaQueryWrapper<ModuleGroup>().eq(ModuleGroup::getSystemId, systemId)
                .eq(ModuleGroup::getCode, code)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模块组编码已存在");
        }
        ModuleGroup group = new ModuleGroup();
        group.setSystemId(systemId);
        group.setCode(code);
        group.setName(name);
        group.setSortOrder(0);
        group.setStatus("ACTIVE");
        groups.insert(group);
        return group;
    }

    /** 后台列出系统中的草稿和已发布模块组。 */
    public List<ModuleGroup> groups(Long accountId, Long systemId) {
        systems.requireOwner(accountId, systemId);
        return groups.selectList(new LambdaQueryWrapper<ModuleGroup>().eq(ModuleGroup::getSystemId, systemId)
                .eq(ModuleGroup::getStatus, "ACTIVE").orderByAsc(ModuleGroup::getSortOrder, ModuleGroup::getId));
    }

    /** 模块必须归属于当前系统的模块组，并生成默认列表菜单和基础页面动作。 */
    @Transactional
    public BusinessModule createModule(Long accountId, Long systemId, Long groupId, String code, String name) {
        systems.requireOwner(accountId, systemId);
        requireGroup(systemId, groupId);
        if (modules.selectOne(new LambdaQueryWrapper<BusinessModule>().eq(BusinessModule::getSystemId, systemId)
                .eq(BusinessModule::getCode, code)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模块编码已存在");
        }
        BusinessModule module = new BusinessModule();
        module.setSystemId(systemId);
        module.setGroupId(groupId);
        module.setCode(code);
        module.setName(name);
        module.setStatus("DRAFT");
        module.setDraftRevision(1);
        modules.insert(module);

        ModuleMenu menu = new ModuleMenu();
        menu.setModuleId(module.getId());
        menu.setCode("list");
        menu.setName(name);
        menu.setMenuType("LIST");
        menu.setSortOrder(0);
        menu.setStatus("ACTIVE");
        menus.insert(menu);
        addAction(module.getId(), "create", "新增", 0);
        addAction(module.getId(), "view", "查看", 1);
        addAction(module.getId(), "update", "编辑", 2);
        addAction(module.getId(), "export", "导出", 3);
        return module;
    }

    /** 后台列出指定模块组下的模块；不把草稿当作运行态。 */
    public List<BusinessModule> modules(Long accountId, Long systemId, Long groupId) {
        systems.requireOwner(accountId, systemId);
        requireGroup(systemId, groupId);
        return modules.selectList(new LambdaQueryWrapper<BusinessModule>().eq(BusinessModule::getSystemId, systemId)
                .eq(BusinessModule::getGroupId, groupId).orderByAsc(BusinessModule::getId));
    }

    /** 新增可配置基础字段，选择字段必须提供合法的 JSON 候选项。 */
    @Transactional
    public ModuleField addField(Long accountId, Long systemId, Long moduleId, String key, String label,
                                String type, boolean required, boolean searchable, String optionsJson) {
        BusinessModule module = requireOwnedModule(accountId, systemId, moduleId);
        if (!List.of("TEXT", "NUMBER", "DATE", "BOOLEAN", "SELECT").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前阶段不支持该字段类型");
        }
        if (fields.selectOne(new LambdaQueryWrapper<ModuleField>().eq(ModuleField::getModuleId, moduleId)
                .eq(ModuleField::getFieldKey, key)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "字段编码已存在");
        }
        if ("SELECT".equals(type)) {
            try {
                JsonNode options = json.readTree(optionsJson == null ? "" : optionsJson);
                if (!options.isArray() || options.isEmpty()) throw new IllegalArgumentException();
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单选字段需要非空 JSON 数组选项");
            }
        } else {
            optionsJson = null;
        }
        ModuleField field = new ModuleField();
        field.setModuleId(moduleId);
        field.setFieldKey(key);
        field.setLabel(label);
        field.setFieldType(type);
        field.setRequiredFlag(required);
        field.setSearchableFlag(searchable);
        field.setSortOrder(Math.toIntExact(fields.selectCount(new LambdaQueryWrapper<ModuleField>().eq(ModuleField::getModuleId, moduleId))));
        field.setOptionsJson(optionsJson);
        field.setStatus("ACTIVE");
        fields.insert(field);
        bumpDraft(module);
        return field;
    }

    /** 修改字段名称只改草稿，不影响已发布版本中的字段名称。 */
    @Transactional
    public ModuleField renameField(Long accountId, Long systemId, Long moduleId, Long fieldId, String label) {
        BusinessModule module = requireOwnedModule(accountId, systemId, moduleId);
        ModuleField field = fields.selectById(fieldId);
        if (field == null || !moduleId.equals(field.getModuleId()) || !"ACTIVE".equals(field.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字段不存在");
        }
        field.setLabel(label);
        fields.updateById(field);
        bumpDraft(module);
        return field;
    }

    /** 后台读取当前字段草稿。 */
    public List<ModuleField> fields(Long accountId, Long systemId, Long moduleId) {
        requireOwnedModule(accountId, systemId, moduleId);
        return fields.selectList(new LambdaQueryWrapper<ModuleField>().eq(ModuleField::getModuleId, moduleId)
                .eq(ModuleField::getStatus, "ACTIVE").orderByAsc(ModuleField::getSortOrder, ModuleField::getId));
    }

    /** 快照冻结模块组、菜单、动作和字段；发布前必须至少配置一个字段。 */
    @Transactional
    public ModuleVersion publish(Long accountId, Long systemId, Long moduleId) {
        BusinessModule module = requireOwnedModule(accountId, systemId, moduleId);
        ModuleGroup group = requireGroup(systemId, module.getGroupId());
        List<ModuleField> currentFields = fields(accountId, systemId, moduleId);
        if (currentFields.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置字段");
        List<ModuleMenu> currentMenus = menus.selectList(new LambdaQueryWrapper<ModuleMenu>()
                .eq(ModuleMenu::getModuleId, moduleId).eq(ModuleMenu::getStatus, "ACTIVE"));
        if (currentMenus.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置菜单");
        List<ModuleAction> currentActions = actions.selectList(new LambdaQueryWrapper<ModuleAction>()
                .eq(ModuleAction::getModuleId, moduleId).eq(ModuleAction::getEnabledFlag, true));
        ModuleVersion last = versions.selectList(new LambdaQueryWrapper<ModuleVersion>()
                .eq(ModuleVersion::getModuleId, moduleId).orderByDesc(ModuleVersion::getVersionNumber).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        ModuleVersion version = new ModuleVersion();
        version.setModuleId(moduleId);
        version.setVersionNumber(last == null ? 1 : last.getVersionNumber() + 1);
        version.setPublishedByAccountId(accountId);
        try {
            version.setSnapshotJson(json.writeValueAsString(new ModuleSnapshot(moduleId, module.getCode(), module.getName(),
                    group.getId(), group.getCode(), group.getName(), group.getSortOrder(),
                    currentFields.stream().map(item -> new FieldSnapshot(item.getId(), item.getFieldKey(), item.getLabel(), item.getFieldType(),
                            item.getRequiredFlag(), item.getSearchableFlag(), item.getSortOrder(), item.getOptionsJson())).toList(),
                    currentMenus.stream().map(item -> new MenuSnapshot(item.getCode(), item.getName(), item.getMenuType(), item.getSortOrder())).toList(),
                    currentActions.stream().map(item -> new ActionSnapshot(item.getCode(), item.getName(), item.getActionType())).toList())));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法生成模块发布快照", exception);
        }
        versions.insert(version);
        module.setPublishedVersionId(version.getId());
        module.setStatus("PUBLISHED");
        modules.updateById(module);
        return version;
    }

    /** 运行态导航以快照中的组名与模块名为准，草稿修改不会改变可见导航。 */
    public List<PublishedModule> navigation(Long accountId, Long systemId) {
        access.requireMembership(accountId, systemId);
        List<PublishedModule> result = new ArrayList<>();
        for (BusinessModule module : modules.selectList(new LambdaQueryWrapper<BusinessModule>()
                .eq(BusinessModule::getSystemId, systemId).eq(BusinessModule::getStatus, "PUBLISHED")
                .isNotNull(BusinessModule::getPublishedVersionId))) {
            ModuleVersion version = versions.selectById(module.getPublishedVersionId());
            if (version != null && access.canModule(accountId, systemId, module.getId(), "VIEW")) {
                ModuleSnapshot snapshot = readSnapshot(version);
                result.add(new PublishedModule(module.getId(), version.getId(), version.getVersionNumber(), snapshot,
                        access.canModule(accountId, systemId, module.getId(), "CREATE"),
                        access.canModule(accountId, systemId, module.getId(), "UPDATE"),
                        hasAction(snapshot, "export") && access.canModule(accountId, systemId, module.getId(), "EXPORT")));
            }
        }
        result.sort((left, right) -> {
            int group = Integer.compare(left.snapshot().groupSortOrder(), right.snapshot().groupSortOrder());
            return group != 0 ? group : Long.compare(left.moduleId(), right.moduleId());
        });
        return result;
    }

    /** 运行态模块详情读取已发布快照；未发布模块不可进入。 */
    public PublishedModule runtime(Long accountId, Long systemId, Long moduleId) {
        access.requireModule(accountId, systemId, moduleId, "VIEW");
        BusinessModule module = modules.selectById(moduleId);
        if (module.getPublishedVersionId() == null || !"PUBLISHED".equals(module.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块尚未发布");
        }
        ModuleVersion version = versions.selectById(module.getPublishedVersionId());
        ModuleSnapshot snapshot = readSnapshot(version);
        return new PublishedModule(moduleId, version.getId(), version.getVersionNumber(), snapshot,
                access.canModule(accountId, systemId, moduleId, "CREATE"),
                access.canModule(accountId, systemId, moduleId, "UPDATE"),
                hasAction(snapshot, "export") && access.canModule(accountId, systemId, moduleId, "EXPORT"));
    }

    private ModuleSnapshot readSnapshot(ModuleVersion version) {
        try { return json.readValue(version.getSnapshotJson(), ModuleSnapshot.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("模块快照无法读取", exception); }
    }

    private boolean hasAction(ModuleSnapshot snapshot, String code) {
        return snapshot.actions().stream().anyMatch(action -> code.equals(action.code()));
    }

    private void addAction(Long moduleId, String code, String name, int order) {
        ModuleAction action = new ModuleAction();
        action.setModuleId(moduleId);
        action.setCode(code);
        action.setName(name);
        action.setActionType(code.toUpperCase());
        action.setEnabledFlag(true);
        action.setSortOrder(order);
        actions.insert(action);
    }

    private void bumpDraft(BusinessModule module) {
        module.setDraftRevision(module.getDraftRevision() + 1);
        modules.updateById(module);
    }

    private ModuleGroup requireGroup(Long systemId, Long groupId) {
        ModuleGroup group = groups.selectById(groupId);
        if (group == null || !systemId.equals(group.getSystemId()) || !"ACTIVE".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块组不存在");
        }
        return group;
    }

    private BusinessModule requireOwnedModule(Long accountId, Long systemId, Long moduleId) {
        systems.requireOwner(accountId, systemId);
        BusinessModule module = modules.selectById(moduleId);
        if (module == null || !systemId.equals(module.getSystemId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块不存在");
        }
        return module;
    }

    /** 发布时冻结的基础字段配置。 */
    public record FieldSnapshot(Long id, String key, String label, String type, boolean required, boolean searchable, int sortOrder, String optionsJson) {}
    /** 发布时冻结的菜单配置。 */
    public record MenuSnapshot(String code, String name, String type, int sortOrder) {}
    /** 发布时冻结的动作配置。 */
    public record ActionSnapshot(String code, String name, String type) {}
    /** 单个模块的不可变发布内容。 */
    public record ModuleSnapshot(Long moduleId, String moduleCode, String moduleName, Long groupId, String groupCode,
                                 String groupName, int groupSortOrder, List<FieldSnapshot> fields,
                                 List<MenuSnapshot> menus, List<ActionSnapshot> actions) {}
    /** 运行态模块与当前发布版本。 */
    public record PublishedModule(Long moduleId, Long versionId, int versionNumber, ModuleSnapshot snapshot,
                                  boolean canCreate, boolean canUpdate, boolean canExport) {}
}
