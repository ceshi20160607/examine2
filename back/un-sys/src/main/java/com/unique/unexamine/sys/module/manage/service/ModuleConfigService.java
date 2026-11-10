package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.ComputedFieldService;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.CreateGroupRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.CreateModuleRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.FieldConfig;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.FieldDefinitionView;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.FormView;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.ModuleView;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.NavigationGroup;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.NavigationModule;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.SaveFieldRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.UpdateModuleRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldMigrationDto;
import com.unique.unexamine.sys.module.manage.dto.ModulePageStatusDto;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 模块、字段和布局保存后立即生效；旧值处理、配置与操作日志在同一事务提交。 */
@Service
public class ModuleConfigService {
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnModuleGroupService groups;
    private final IUnModuleService modules;
    private final IUnModuleFieldService fields;
    private final IUnModuleTenantService tenantModules;
    private final IUnModuleRecordService records;
    private final IUnSysSystemConfigService systemConfigs;
    private final IUnSysLogOptService logs;
    private final ConfigurationCache cache;
    private final ObjectMapper json;
    private final ModuleFieldService fieldRules;
    private final ModulePermissionService permissions;
    private final ModuleMenuService menuService;
    private final ModuleFieldMigrationService migrations;
    private final ModulePageService pageService;
    private final ModulePageStatusService pageStatuses;

    /** 注入生成的基础服务及已复用的权限、校验和提交后缓存失效能力。 */
    public ModuleConfigService(SystemManageService systems,IUnSysSystemService systemRows,
            IUnModuleGroupService groups,IUnModuleService modules,IUnModuleFieldService fields,
            IUnModuleTenantService tenantModules,ObjectMapper json,ModuleFieldService fieldRules,
            ModulePermissionService permissions,ModuleMenuService menuService,ModuleFieldMigrationService migrations,
            ModulePageService pageService,ModulePageStatusService pageStatuses,
            IUnModuleRecordService records,IUnSysSystemConfigService systemConfigs,IUnSysLogOptService logs,
            ConfigurationCache cache) {
        this.systems=systems; this.systemRows=systemRows; this.groups=groups; this.modules=modules;
        this.fields=fields; this.tenantModules=tenantModules; this.json=json; this.fieldRules=fieldRules;
        this.permissions=permissions; this.menuService=menuService; this.migrations=migrations;
        this.pageService=pageService; this.pageStatuses=pageStatuses;
        this.records=records; this.systemConfigs=systemConfigs; this.logs=logs; this.cache=cache;
    }

    /** 主租户管理员创建模块组，保存后即可用于创建模块和导航。 */
    @Transactional(rollbackFor=Exception.class)
    public Long createGroup(String token,Long systemId,Long tenantId,CreateGroupRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        if (request == null || request.code() == null || request.name() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块组资料不能为空");
        if (!context.tenantId().equals(systemRows.queryById(systemId).getRootTenantId())) forbidden("模块组只由主租户配置");
        String code=code(request.code());
        if (groups.count(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getSystemId,systemId)
                .eq(UnModuleGroup::getTenantId,context.tenantId()).eq(UnModuleGroup::getCode,code))>0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"模块组编码已存在");
        var group=new UnModuleGroup(); group.setSystemId(systemId); group.setTenantId(context.tenantId());
        group.setCode(code); group.setName(request.name().trim()); group.setStatus("ACTIVE");
        group.setCreateAccountId(systems.accountId(token,systemId)); groups.addOrUpdate(group);
        audit(token,context,"MODULE_GROUP_CREATE","MODULE_GROUP",group.getId(),request); changed(context);
        return group.getId();
    }

    /** 创建模块、默认标题和十个菜单动作，事务提交后即可使用。 */
    @Transactional(rollbackFor=Exception.class)
    public ModuleView createModule(String token,Long systemId,Long tenantId,CreateModuleRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        if (request == null || request.groupId() == null || request.code() == null || request.name() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块资料不能为空");
        var group=groups.queryById(request.groupId());
        Long root=systemRows.queryById(systemId).getRootTenantId();
        if (group==null || !systemId.equals(group.getSystemId()) || !root.equals(group.getTenantId()) || !"ACTIVE".equals(group.getStatus()))
            forbidden("模块组不属于当前系统或未启用");
        String code=code(request.code());
        if (modules.count(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId,systemId)
                .eq(UnModule::getTenantId,context.tenantId()).eq(UnModule::getCode,code))>0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"模块编码已存在");
        var module=new UnModule(); module.setSystemId(systemId); module.setTenantId(context.tenantId());
        module.setGroupId(group.getId()); module.setCode(code); module.setName(request.name().trim());
        module.setStatus("ACTIVE"); module.setCreateAccountId(systems.accountId(token,systemId)); modules.addOrUpdate(module);
        var relation=new UnModuleTenant(); relation.setSystemId(systemId); relation.setTenantId(context.tenantId());
        relation.setModuleId(module.getId()); relation.setStatus("ACTIVE"); tenantModules.addOrUpdate(relation);
        Long titleFieldId=saveFieldInTransaction(token,context,module,new SaveFieldRequest("name","标题",1,true,
                Map.of(),Map.of(),false,false));
        pageService.initialize(module,titleFieldId);
        menuService.initialize(module);
        audit(token,context,"MODULE_CREATE","MODULE",module.getId(),request); changed(context);
        return view(module);
    }

    /** 当前模块资料修改立即影响运行入口，不创建发布副本。 */
    @Transactional(rollbackFor=Exception.class)
    public ModuleView updateModule(String token,Long systemId,Long tenantId,Long moduleId,UpdateModuleRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); lockModule(systemId,moduleId);
        if (request == null || request.name() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块资料不能为空");
        var module=ownedModule(systemId,moduleId,context.tenantId());
        var before=json.convertValue(module,new TypeReference<Map<String,Object>>(){});
        module.setName(request.name().trim()); module.setDescription(request.description());
        module.setStatus(request.enabled()?"ACTIVE":"DISABLED");
        // 说明允许清空，显式更新避免通用非空字段策略保留旧说明。
        modules.update(new LambdaUpdateWrapper<UnModule>().eq(UnModule::getId,moduleId)
                .set(UnModule::getName,module.getName()).set(UnModule::getDescription,module.getDescription())
                .set(UnModule::getStatus,module.getStatus()));
        menuService.renameDirectory(module);
        audit(token,context,"MODULE_UPDATE","MODULE",moduleId,Map.of("before",before,"after",module));
        changed(context); return view(module);
    }

    /** 字段变更先校验现有数据，字段定义与页面布局各自保存。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Long saveField(String token,Long systemId,Long tenantId,Long moduleId,SaveFieldRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); lockModule(systemId,moduleId);
        if (request == null || request.fieldName() == null || request.fieldType() == null || request.config() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段资料不能为空");
        var module=ownedModule(systemId,moduleId,context.tenantId());
        Long id=saveFieldInTransaction(token,context,module,request); changed(context); return id;
    }

    /** 逻辑删除自定义字段；历史值、槽位和操作日志保留，标题字段不可删除。 */
    @Transactional(rollbackFor=Exception.class)
    public void deleteField(String token,Long systemId,Long tenantId,Long moduleId,Long fieldId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); lockModule(systemId,moduleId);
        var module=ownedModule(systemId,moduleId,context.tenantId());
        var field=fields.getOne(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getId,fieldId).eq(UnModuleField::getModuleId,module.getId())
                .eq(UnModuleField::getTenantId,module.getTenantId()),false);
        if (field==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"字段不存在");
        if ("name".equals(field.getFieldKey()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"模块标题字段不可删除");
        validateFieldReferences(module, field);
        Map<String,Object> before=fieldConfig(field).configuration();
        if (!fields.deleteByIds(List.of(field.getId())))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段删除失败");
        audit(token,context,"MODULE_FIELD_DELETE","MODULE_FIELD",field.getId(),
                Map.of("moduleId",module.getId(),"before",before));
        changed(context);
    }

    /** 删除字段前检查公式、选项联动和模块页面，避免配置引用已停用字段。 */
    private void validateFieldReferences(UnModule module, UnModuleField target) {
        if (pageService.hasActiveFieldReference(target.getId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段仍被模块页面使用，请先从页面移除");
        List<UnModuleField> sameModule = currentFields(module.getId());
        for (UnModuleField field : sameModule) {
            if (field.getId().equals(target.getId())) continue;
            FieldDefinition definition = fieldRules.configured(field.getFieldKey(), fieldConfig(field).configuration());
            if (definition.type() == FieldType.FORMULA
                    && ComputedFieldService.references(ComputedFieldService.formula(definition))
                    .contains(target.getFieldKey()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "字段仍被公式“" + field.getFieldName() + "”引用，不能删除");
            if (optionDisplayTargets(definition).contains(target.getFieldKey()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "字段仍被选项联动规则引用，不能删除");
        }
    }

    /** 提取选项联动目标字段，结构不合法时沿用字段配置校验的失败结果。 */
    private Set<String> optionDisplayTargets(FieldDefinition definition) {
        Set<String> result = new HashSet<>();
        Object raw = definition.rules().get("optionDisplayRules");
        if (!(raw instanceof List<?> rules)) return result;
        for (Object item : rules) if (item instanceof Map<?, ?> rule && rule.get("targetFieldKeys") instanceof List<?> keys)
            for (Object key : keys) if (key instanceof String text) result.add(text);
        return result;
    }

    /** 预览尚未保存的新配置对旧值的影响，不持久化任何草稿。 */
    public ModuleFieldMigrationDto.Preview previewMigration(String token,Long systemId,Long tenantId,
            Long moduleId,Long fieldId,SaveFieldRequest proposed) {
        var context=systems.requireAdminContext(token,systemId,tenantId); ownedModule(systemId,moduleId,context.tenantId());
        if (proposed == null || proposed.fieldKey() == null || proposed.fieldName() == null
                || proposed.fieldType() == null || proposed.config() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段变更资料不能为空");
        var field=fields.queryById(fieldId);
        if (field==null || !moduleId.equals(field.getModuleId()) || !field.getFieldKey().equals(code(proposed.fieldKey())))
            forbidden("字段不属于当前模块或编码不一致");
        var definition=fieldRules.definition(systemId,context.tenantId(),field.getFieldKey(),proposed.fieldName(),
                proposed.fieldType(),proposed.required(),proposed.config());
        return migrations.preview(token,systemId,moduleId,field,definition);
    }

    /** 新建表单应用当前菜单和字段权限，位置、行与宽度原样传递。 */
    public FormView newForm(String token,Long systemId,Long tenantId,Long moduleId) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"CREATE");
        var context=access.context(); activeModule(systemId,moduleId,context.tenantId());
        Map<Long,UnModuleField> byId=new HashMap<>(); currentFields(moduleId).forEach(field->byId.put(field.getId(),field));
        return new FormView(moduleId,pageService.activeFields(moduleId,"CREATE",null).stream()
                .filter(slot->byId.containsKey(slot.getFieldId()))
                .map(slot->new AbstractMap.SimpleEntry<>(slot,byId.get(slot.getFieldId())))
                .filter(entry->access.policy().readable(entry.getValue().getFieldKey()))
                .map(entry->runtimePageFieldConfig(systemId,context.tenantId(),entry.getValue(),entry.getKey(),
                        access.policy().writable(entry.getValue().getFieldKey()))).toList());
    }

    /** 字段类型目录供设计器使用，未具备完整业务能力的类型不能保存使用。 */
    public List<ModuleFieldDto.TypeView> fieldTypes(String token,Long systemId,Long tenantId) {
        systems.requireAdminContext(token,systemId,tenantId); return fieldRules.types();
    }

    /** 管理员直接读取当前已保存字段，稳定 ID 用于菜单与角色授权。 */
    public List<FieldDefinitionView> fieldViews(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); activeModule(systemId,moduleId,context.tenantId());
        return currentFields(moduleId).stream().map(field->new FieldDefinitionView(field.getId(),field.getFieldKey(),
                fieldConfig(field).configuration())).toList();
    }

    /** 运行导航按主租户模块组和具体 LIST 菜单授权过滤。 */
    public List<NavigationGroup> navigation(String token,Long systemId,Long tenantId) {
        var context=systems.context(token,systemId,tenantId);
        Long root=systemRows.queryById(systemId).getRootTenantId();
        var availableGroups=groups.list(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getSystemId,systemId)
                .eq(UnModuleGroup::getTenantId,root).eq(UnModuleGroup::getStatus,"ACTIVE")
                .orderByAsc(UnModuleGroup::getSortNo).orderByAsc(UnModuleGroup::getId));
        var availableModules=modules.list(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId,systemId)
                .in(UnModule::getTenantId,new HashSet<>(List.of(root,context.tenantId()))).eq(UnModule::getStatus,"ACTIVE")
                .orderByAsc(UnModule::getSortNo).orderByAsc(UnModule::getId));
        List<NavigationGroup> result=new ArrayList<>();
        for (var group:availableGroups) {
            List<NavigationModule> items=new ArrayList<>();
            for (var module:availableModules) {
                if (!group.getId().equals(module.getGroupId())) continue;
                try { permissions.require(token,systemId,context.tenantId(),module.getId(),"LIST"); }
                catch (BaseException ex) { if (ex.getCode() >= 400 && ex.getCode() < 500) continue; throw ex; }
                items.add(new NavigationModule(module.getId(),module.getCode(),module.getName(),module.getTenantId(),
                        !module.getTenantId().equals(context.tenantId())));
            }
            if (!items.isEmpty()) result.add(new NavigationGroup(group.getId(),group.getCode(),group.getName(),items));
        }
        return result;
    }

    /** 展示选项名称与可编辑许可；不更改原布局。 */
    public FieldConfig runtimeFieldConfig(Long systemId,Long tenantId,UnModuleField field,boolean writable) {
        var configuration=fieldRules.configuration(systemId,tenantId,field.getFieldKey(),fieldConfig(field).configuration());
        // 只读是字段自身的最终约束，不能因为角色或动作具备写权限就把表单显示为可编辑。
        configuration.put("writable",writable && !Boolean.TRUE.equals(field.getReadonlyFlag()));
        return new FieldConfig(field.getFieldKey(),configuration);
    }

    /** 运行表单仅在当前页面字段上附加该页面的行列及宽度。 */
    public FieldConfig runtimePageFieldConfig(Long systemId,Long tenantId,UnModuleField field,
            UnModulePageField slot,boolean writable) {
        FieldConfig base=runtimeFieldConfig(systemId,tenantId,field,writable);
        base.configuration().put("rowNo",slot.getRowNo());
        base.configuration().put("columnNo",slot.getColumnNo());
        base.configuration().put("widthPercent",Byte.toUnsignedInt(slot.getWidthPercent()));
        return base;
    }

    /** 页面服务固定当前展示用途，不把字段定义顺序当作页面布局。 */
    public List<UnModulePageField> pageFields(Long moduleId,String kind) {
        return pageService.activeFields(moduleId,kind,null);
    }

    /** 页面审批状态组在列表、详情和关联筛选中使用同一份配置。 */
    public List<ModulePageStatusDto.View> approvalStatusFields(Long moduleId) {
        return pageStatuses.active(moduleId);
    }

    /** 审批状态由 examine 运行实例提供，模块只消费状态组结果。 */
    public Map<Long,Map<String,String>> approvalStatuses(Long systemId,Long tenantId,Long moduleId,List<Long> recordIds) {
        return pageStatuses.statuses(systemId,tenantId,moduleId,recordIds);
    }

    /** 页面状态字段的筛选在记录分页前执行，关联列表使用同一入口。 */
    public void applyApprovalStatusFilters(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UnModuleRecord> where,
            Long systemId,Long moduleId,Map<String,String> requested) {
        pageStatuses.applyFilters(where,systemId,moduleId,requested);
    }

    /** 所有业务入口读取同一份当前字段及保存顺序。 */
    public List<UnModuleField> currentFields(Long moduleId) {
        return fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,moduleId)
                .eq(UnModuleField::getStatus,"ACTIVE").orderByAsc(UnModuleField::getId));
    }

    /** 字段元信息与校验规则取字段定义，布局由模块页面读取。 */
    public FieldConfig fieldConfig(UnModuleField field) {
        Map<String,Object> configuration=new LinkedHashMap<>();
        configuration.put("fieldName",field.getFieldName()); configuration.put("fieldType",field.getFieldType());
        configuration.put("required",field.getRequiredFlag());
        configuration.put("hidden",field.getHiddenFlag()); configuration.put("readonly",field.getReadonlyFlag());
        configuration.put("config",fieldRules.rules(field));
        return new FieldConfig(field.getFieldKey(),configuration);
    }

    /** 模块必须仍存在、已启用并属于当前系统和允许继承的租户。 */
    public UnModule activeModule(Long systemId,Long moduleId,Long tenantId) {
        var module=modules.queryById(moduleId);
        var system=systemRows.queryById(systemId);
        if (module==null || system==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"模块不存在");
        Long root=system.getRootTenantId();
        if (!systemId.equals(module.getSystemId()) || (!tenantId.equals(module.getTenantId()) && !root.equals(module.getTenantId())))
            forbidden("模块不属于当前系统租户");
        if (!"ACTIVE".equals(module.getStatus())) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"模块已停用");
        return module;
    }

    /** 配置保存和记录写入共用模块行锁，防止唯一校验与数据迁移相互穿插。 */
    public void lockModule(Long systemId,Long moduleId) {
        if (modules.getOne(new LambdaQueryWrapper<UnModule>().eq(UnModule::getId,moduleId)
                .eq(UnModule::getSystemId,systemId).last("FOR UPDATE"))==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"模块不存在");
    }

    private Long saveFieldInTransaction(String token,SystemManageDto.SystemContext context,
            UnModule module,SaveFieldRequest request) {
        String requestedKey=code(request.fieldKey());
        String fieldKey=requestedKey;
        com.unique.unexamine.core.field.FieldType type;
        try {
            type=com.unique.unexamine.core.field.FieldType.parse(request.fieldType());
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        if (fieldKey!=null && !"name".equals(fieldKey) && Set.of("id","num","status","checkstatus","moduleid","tenantid","systemid",
                "owneruserid","createuserid","deletedtime").contains(fieldKey)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段编码与系统字段冲突");
        if ("name".equals(fieldKey) && type!=com.unique.unexamine.core.field.FieldType.TEXT) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"标题必须使用文本类型");
        String definitionCode=fieldKey==null?"field_pending":fieldKey;
        var definition=fieldRules.definition(module.getSystemId(),module.getTenantId(),definitionCode,request.fieldName(),
                type.code(),request.required(),request.config());
        var field=fieldKey==null?null:fields.getOne(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,module.getId())
                .eq(UnModuleField::getTenantId,module.getTenantId()).eq(UnModuleField::getFieldKey,fieldKey),false);
        boolean existing=field!=null;
        // 删除字段只做逻辑删除，主表槽位中的旧值仍可能保留；新字段不能重新占用任何历史 field_key，
        // 否则重新使用 item_* 会把旧字段的主表值误解释为新字段值。
        if (!existing && fieldKey!=null && fields.allFieldKeysForStorage(module.getId()).contains(fieldKey))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段编码已被历史字段占用");
        if (!existing && (fieldKey==null || (!"name".equals(fieldKey) && !fieldKey.startsWith("item_") && !fieldKey.startsWith("field_")))) {
            fieldKey=ModuleRecordStorage.allocate(definition,new java.util.HashSet<>(fields.allFieldKeysForStorage(module.getId())));
            definition=fieldRules.definition(module.getSystemId(),module.getTenantId(),fieldKey,request.fieldName(),
                    type.code(),request.required(),request.config());
        }
        if (fieldKey.startsWith("item_") && !ModuleRecordStorage.isMain(definition))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段类型与主表预留槽位不匹配");
        if (fieldKey.startsWith("field_") && ModuleRecordStorage.isMain(definition))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"可用主表槽位不能手工指定为 field_* 扩展字段");
        validateRelationDefinition(module,definition);
        validateComputedReferences(module,fieldKey,definition);
        validateOptionDisplayReferences(module,fieldKey,definition);
        Map<String,Object> before=existing?fieldConfig(field).configuration():Map.of();
        FieldDefinition previousDefinition = existing
                ? fieldRules.configured(field.getFieldKey(), fieldConfig(field).configuration()) : null;
        if (existing && type.code()!=field.getFieldType().intValue() && records.count(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getModuleId,module.getId()))>0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段类型变更涉及已有记录，须先确定旧值迁移办法");
        if (!existing) {
            field=new UnModuleField(); field.setSystemId(module.getSystemId()); field.setTenantId(module.getTenantId());
            field.setModuleId(module.getId()); field.setFieldKey(fieldKey);
            if (request.optionMappings()!=null && !request.optionMappings().isEmpty())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"新字段不能指定旧值迁移映射");
        } else migrations.apply(module.getSystemId(),module.getId(),field,definition,request.optionMappings(),
                systems.accountId(token,module.getSystemId()),token);
        if (existing) migrations.migrateStorage(module.getSystemId(), module.getId(), field,
                previousDefinition, definition, systems.accountId(token, module.getSystemId()));
        fieldRules.validateExisting(module.getSystemId(),module.getId(),field,definition);
        field.setFieldName(request.fieldName().trim()); field.setFieldType((short)type.code()); field.setRequiredFlag(request.required());
        field.setReadonlyFlag(request.readonly()); field.setHiddenFlag(request.hidden());
        fieldRules.writeRules(field,definition); field.setStatus("ACTIVE");
        fields.addOrUpdate(field);
        audit(token,context,existing?"MODULE_FIELD_UPDATE":"MODULE_FIELD_CREATE","MODULE_FIELD",field.getId(),
                Map.of("moduleId",module.getId(),"before",before,"after",fieldConfig(field).configuration()));
        return field.getId();
    }

    /** 关联字段只定义目标模块；目标摘要字段由模块关联页面配置。 */
    private void validateRelationDefinition(UnModule source,com.unique.unexamine.core.field.FieldDefinition definition) {
        if (definition.type()!=com.unique.unexamine.core.field.FieldType.RELATION) return;
        Long targetId=((Number)definition.rules().get("relationModuleId")).longValue();
        UnModule target=modules.getById(targetId);
        Long root=systemRows.queryById(source.getSystemId()).getRootTenantId();
        if (target==null || !source.getSystemId().equals(target.getSystemId())
                || !List.of(source.getTenantId(),root).contains(target.getTenantId())
                || !"ACTIVE".equals(target.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"关联目标模块不存在或不属于当前系统租户");
    }

    /** 公式只能引用当前模块的有效字段，避免保存后运行时才发现拼写错误或自循环。 */
    private void validateComputedReferences(UnModule module,String fieldKey,
            com.unique.unexamine.core.field.FieldDefinition definition) {
        if (definition.type()!=FieldType.FORMULA) return;
        Set<String> valid=new HashSet<>(fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,module.getId()).eq(UnModuleField::getStatus,"ACTIVE"))
                .stream().map(UnModuleField::getFieldKey).toList());
        valid.add(fieldKey);
        for (String reference:ComputedFieldService.references(ComputedFieldService.formula(definition))) {
            if (!valid.contains(reference)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"公式引用了不存在或已停用的字段: "+reference);
            if (reference.equals(fieldKey)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"公式字段不能引用自身");
        }
    }

    /** 选项联动的目标字段必须是同一模块当前有效字段，避免页面保存后静默失效。 */
    private void validateOptionDisplayReferences(UnModule module,String fieldKey,
            com.unique.unexamine.core.field.FieldDefinition definition) {
        if (!(definition.rules().get("optionDisplayRules") instanceof List<?> rules)) return;
        Set<String> valid=new HashSet<>(fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,module.getId()).eq(UnModuleField::getStatus,"ACTIVE"))
                .stream().map(UnModuleField::getFieldKey).toList());
        valid.add(fieldKey);
        for (Object raw:rules) {
            if (!(raw instanceof Map<?,?> rule) || !(rule.get("targetFieldKeys") instanceof List<?> targets))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"选项联动规则结构无效");
            for (Object target:targets) {
                if (!(target instanceof String key) || !valid.contains(key) || key.equals(fieldKey))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"选项联动目标字段不存在或不能指向自身");
            }
        }
    }

    /** 配置维护只允许修改当前租户自己的模块。 */
    private UnModule ownedModule(Long systemId,Long moduleId,Long tenantId) {
        var module=modules.queryById(moduleId);
        if (module==null || !systemId.equals(module.getSystemId()) || !tenantId.equals(module.getTenantId())) forbidden("模块不属于当前系统租户");
        return module;
    }
    private String code(String value) {
        if (value==null || value.isBlank()) return null;
        String result=value.trim().toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z][a-z0-9_]{1,79}")) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"配置编码须以字母开头并只包含字母、数字和下划线");
        return result;
    }
    private void audit(String token,SystemManageDto.SystemContext context,String action,String objectType,Long id,Object detail) {
        var log=new UnSysLogOpt(); log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,context.systemId())); log.setActionCode(action); log.setObjectType(objectType);
        log.setObjectId(id); log.setDetailJson(writeJson(detail)); logs.addOrUpdate(log);
    }
    private void changed(SystemManageDto.SystemContext context) {
        systemConfigs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,context.systemId())
                .setSql("permission_revision=permission_revision+1"));
        cache.invalidateAfterCommit("permission:"+context.systemId()+":"+context.tenantId());
    }
    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块配置无法保存",ex); }
    }
    private Map<String,Object> readMap(String value) {
        if (value==null) return Map.of();
        try { return json.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){}); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段配置损坏",ex); }
    }
    private ModuleView view(UnModule module) { return new ModuleView(module.getId(),module.getCode(),module.getName(),module.getStatus()); }
    private void forbidden(String message) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,message); }
}
