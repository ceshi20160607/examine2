package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
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
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    /** 注入生成的基础服务及已复用的权限、校验和提交后缓存失效能力。 */
    public ModuleConfigService(SystemManageService systems,IUnSysSystemService systemRows,
            IUnModuleGroupService groups,IUnModuleService modules,IUnModuleFieldService fields,
            IUnModuleTenantService tenantModules,ObjectMapper json,ModuleFieldService fieldRules,
            ModulePermissionService permissions,ModuleMenuService menuService,ModuleFieldMigrationService migrations,
            IUnModuleRecordService records,IUnSysSystemConfigService systemConfigs,IUnSysLogOptService logs,
            ConfigurationCache cache) {
        this.systems=systems; this.systemRows=systemRows; this.groups=groups; this.modules=modules;
        this.fields=fields; this.tenantModules=tenantModules; this.json=json; this.fieldRules=fieldRules;
        this.permissions=permissions; this.menuService=menuService; this.migrations=migrations;
        this.records=records; this.systemConfigs=systemConfigs; this.logs=logs; this.cache=cache;
    }

    /** 主租户管理员创建模块组，保存后即可用于创建模块和导航。 */
    @Transactional(rollbackFor=Exception.class)
    public Long createGroup(String token,Long systemId,Long tenantId,CreateGroupRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        if (!context.tenantId().equals(systemRows.queryById(systemId).getRootTenantId())) forbidden("模块组只由主租户配置");
        String code=code(request.code());
        if (groups.count(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getSystemId,systemId)
                .eq(UnModuleGroup::getTenantId,context.tenantId()).eq(UnModuleGroup::getCode,code))>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"模块组编码已存在");
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
        var group=groups.queryById(request.groupId());
        Long root=systemRows.queryById(systemId).getRootTenantId();
        if (!systemId.equals(group.getSystemId()) || !root.equals(group.getTenantId()) || !"ACTIVE".equals(group.getStatus()))
            forbidden("模块组不属于当前系统或未启用");
        String code=code(request.code());
        if (modules.count(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId,systemId)
                .eq(UnModule::getTenantId,context.tenantId()).eq(UnModule::getCode,code))>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"模块编码已存在");
        var module=new UnModule(); module.setSystemId(systemId); module.setTenantId(context.tenantId());
        module.setGroupId(group.getId()); module.setCode(code); module.setName(request.name().trim());
        module.setStatus("ACTIVE"); module.setCreateAccountId(systems.accountId(token,systemId)); modules.addOrUpdate(module);
        var relation=new UnModuleTenant(); relation.setSystemId(systemId); relation.setTenantId(context.tenantId());
        relation.setModuleId(module.getId()); relation.setStatus("ACTIVE"); tenantModules.addOrUpdate(relation);
        saveFieldInTransaction(token,context,module,new SaveFieldRequest("name","标题",1,true,0,
                0,0,100,Map.of(),Map.of(),false,false));
        menuService.initialize(module);
        audit(token,context,"MODULE_CREATE","MODULE",module.getId(),request); changed(context);
        return view(module);
    }

    /** 当前模块资料修改立即影响运行入口，不创建发布副本。 */
    @Transactional(rollbackFor=Exception.class)
    public ModuleView updateModule(String token,Long systemId,Long tenantId,Long moduleId,UpdateModuleRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); lockModule(systemId,moduleId);
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

    /** 字段变更先校验现有数据，处理成功后与布局、日志一起保存生效。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Long saveField(String token,Long systemId,Long tenantId,Long moduleId,SaveFieldRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); lockModule(systemId,moduleId);
        var module=ownedModule(systemId,moduleId,context.tenantId());
        Long id=saveFieldInTransaction(token,context,module,request); changed(context); return id;
    }

    /** 预览尚未保存的新配置对旧值的影响，不持久化任何草稿。 */
    public ModuleFieldMigrationDto.Preview previewMigration(String token,Long systemId,Long tenantId,
            Long moduleId,Long fieldId,SaveFieldRequest proposed) {
        var context=systems.requireAdminContext(token,systemId,tenantId); ownedModule(systemId,moduleId,context.tenantId());
        var field=fields.queryById(fieldId);
        if (!moduleId.equals(field.getModuleId()) || !field.getFieldKey().equals(code(proposed.fieldKey())))
            forbidden("字段不属于当前模块或编码不一致");
        var definition=fieldRules.definition(systemId,context.tenantId(),field.getFieldKey(),proposed.fieldName(),
                proposed.fieldType(),proposed.required(),proposed.config());
        return migrations.preview(token,systemId,moduleId,field,definition);
    }

    /** 新建表单应用当前菜单和字段权限，位置、行与宽度原样传递。 */
    public FormView newForm(String token,Long systemId,Long tenantId,Long moduleId) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"CREATE");
        var context=access.context(); activeModule(systemId,moduleId,context.tenantId());
        return new FormView(moduleId,access.policy().filterReadable(currentFields(moduleId),UnModuleField::getFieldKey)
                .stream().map(field->runtimeFieldConfig(systemId,context.tenantId(),field,
                        access.policy().writable(field.getFieldKey()))).toList());
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
                catch (ResponseStatusException ex) { if (ex.getStatusCode().is4xxClientError()) continue; throw ex; }
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
        configuration.put("writable",writable); return new FieldConfig(field.getFieldKey(),configuration);
    }

    /** 所有业务入口读取同一份当前字段及保存顺序。 */
    public List<UnModuleField> currentFields(Long moduleId) {
        return fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,moduleId)
                .eq(UnModuleField::getStatus,"ACTIVE").orderByAsc(UnModuleField::getSortNo).orderByAsc(UnModuleField::getId));
    }

    /** 字段元信息、校验与布局均取当前持久化值。 */
    public FieldConfig fieldConfig(UnModuleField field) {
        Map<String,Object> configuration=new LinkedHashMap<>();
        configuration.put("fieldName",field.getFieldName()); configuration.put("fieldType",field.getFieldType());
        configuration.put("required",field.getRequiredFlag());
        configuration.put("hidden",field.getHiddenFlag()); configuration.put("readonly",field.getReadonlyFlag());
        configuration.put("config",fieldRules.rules(field));
        configuration.put("rowNo",field.getRowNo()); configuration.put("columnNo",field.getColumnNo());
        configuration.put("widthPercent",field.getWidthPercent());
        return new FieldConfig(field.getFieldKey(),configuration);
    }

    /** 模块必须仍存在、已启用并属于当前系统和允许继承的租户。 */
    public UnModule activeModule(Long systemId,Long moduleId,Long tenantId) {
        var module=modules.queryById(moduleId); Long root=systemRows.queryById(systemId).getRootTenantId();
        if (!systemId.equals(module.getSystemId()) || (!tenantId.equals(module.getTenantId()) && !root.equals(module.getTenantId())))
            forbidden("模块不属于当前系统租户");
        if (!"ACTIVE".equals(module.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"模块已停用");
        return module;
    }

    /** 配置保存和记录写入共用模块行锁，防止唯一校验与数据迁移相互穿插。 */
    public void lockModule(Long systemId,Long moduleId) {
        if (modules.getOne(new LambdaQueryWrapper<UnModule>().eq(UnModule::getId,moduleId)
                .eq(UnModule::getSystemId,systemId).last("FOR UPDATE"))==null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"模块不存在");
    }

    private Long saveFieldInTransaction(String token,SystemManageDto.SystemContext context,
            UnModule module,SaveFieldRequest request) {
        String requestedKey=code(request.fieldKey());
        String fieldKey=requestedKey;
        var type=com.unique.unexamine.core.field.FieldType.parse(request.fieldType());
        if (fieldKey!=null && !"name".equals(fieldKey) && Set.of("id","num","status","moduleid","tenantid","systemid",
                "owneruserid","createuserid","deletedtime").contains(fieldKey)) throw new IllegalArgumentException("字段编码与系统字段冲突");
        if ("name".equals(fieldKey) && type!=com.unique.unexamine.core.field.FieldType.TEXT) throw new IllegalArgumentException("标题必须使用文本类型");
        if (request.sortNo()<0) throw new IllegalArgumentException("字段顺序无效");
        validateLayout(request.rowNo(),request.columnNo(),request.widthPercent());
        String definitionCode=fieldKey==null?"field_pending":fieldKey;
        var definition=fieldRules.definition(module.getSystemId(),module.getTenantId(),definitionCode,request.fieldName(),
                type.code(),request.required(),request.config());
        var field=fieldKey==null?null:fields.getOne(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,module.getId())
                .eq(UnModuleField::getTenantId,module.getTenantId()).eq(UnModuleField::getFieldKey,fieldKey),false);
        boolean existing=field!=null;
        if (!existing && (fieldKey==null || (!"name".equals(fieldKey) && !fieldKey.startsWith("item_") && !fieldKey.startsWith("field_")))) {
            fieldKey=ModuleRecordStorage.allocate(definition,new java.util.HashSet<>(fields.allFieldKeysForStorage(module.getId())));
            definition=fieldRules.definition(module.getSystemId(),module.getTenantId(),fieldKey,request.fieldName(),
                    type.code(),request.required(),request.config());
        }
        if (fieldKey.startsWith("item_") && !ModuleRecordStorage.isMain(definition))
            throw new IllegalArgumentException("字段类型与主表预留槽位不匹配");
        if (fieldKey.startsWith("field_") && ModuleRecordStorage.isMain(definition))
            throw new IllegalArgumentException("可用主表槽位不能手工指定为 field_* 扩展字段");
        validateRelationDefinition(module,definition);
        Map<String,Object> before=existing?fieldConfig(field).configuration():Map.of();
        if (existing && type.code()!=field.getFieldType().intValue() && records.count(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getModuleId,module.getId()))>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"字段类型变更涉及已有记录，须先确定旧值迁移办法");
        if (!existing) {
            field=new UnModuleField(); field.setSystemId(module.getSystemId()); field.setTenantId(module.getTenantId());
            field.setModuleId(module.getId()); field.setFieldKey(fieldKey);
            if (request.optionMappings()!=null && !request.optionMappings().isEmpty())
                throw new IllegalArgumentException("新字段不能指定旧值迁移映射");
        } else migrations.apply(module.getSystemId(),module.getId(),field,definition,request.optionMappings(),
                systems.accountId(token,module.getSystemId()),token);
        fieldRules.validateExisting(module.getSystemId(),module.getId(),field,definition);
        field.setFieldName(request.fieldName().trim()); field.setFieldType((short)type.code()); field.setRequiredFlag(request.required());
        field.setReadonlyFlag(request.readonly()); field.setHiddenFlag(request.hidden()); field.setSortNo(request.sortNo());
        field.setRowNo(request.rowNo()); field.setColumnNo(request.columnNo()); field.setWidthPercent((byte)request.widthPercent());
        fieldRules.writeRules(field,definition); field.setStatus("ACTIVE");
        fields.addOrUpdate(field);
        audit(token,context,existing?"MODULE_FIELD_UPDATE":"MODULE_FIELD_CREATE","MODULE_FIELD",field.getId(),
                Map.of("moduleId",module.getId(),"before",before,"after",fieldConfig(field).configuration()));
        return field.getId();
    }

    /** 关联字段只能指向当前系统已存在的模块，并引用其当前有效字段。 */
    private void validateRelationDefinition(UnModule source,com.unique.unexamine.core.field.FieldDefinition definition) {
        if (definition.type()!=com.unique.unexamine.core.field.FieldType.RELATION) return;
        Long targetId=((Number)definition.rules().get("relationModuleId")).longValue();
        UnModule target=modules.getById(targetId);
        Long root=systemRows.queryById(source.getSystemId()).getRootTenantId();
        if (target==null || !source.getSystemId().equals(target.getSystemId())
                || !List.of(source.getTenantId(),root).contains(target.getTenantId())
                || !"ACTIVE".equals(target.getStatus()))
            throw new IllegalArgumentException("关联目标模块不存在或不属于当前系统租户");
        Map<?,?> relation=(Map<?,?>)definition.rules().get("relationConfig");
        List<String> keys=((List<?>)relation.get("displayFieldKeys")).stream().map(String.class::cast).toList();
        long matched=fields.count(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,targetId)
                .eq(UnModuleField::getStatus,"ACTIVE").in(UnModuleField::getFieldKey,keys));
        if (matched!=keys.size()) throw new IllegalArgumentException("关联展示字段必须全部属于当前有效目标模块");
    }

    /** 所在行、行内位置和占位宽度由设计器明确保存，避免缺失行时把后续字段拼入同一行。 */
    private void validateLayout(int row,int column,int width) {
        if (row<0 || column<0 || width<1 || width>100)
            throw new IllegalArgumentException("字段布局须包含有效的所在行、行内位置和占位宽度");
    }

    /** 配置维护只允许修改当前租户自己的模块。 */
    private UnModule ownedModule(Long systemId,Long moduleId,Long tenantId) {
        var module=modules.queryById(moduleId);
        if (!systemId.equals(module.getSystemId()) || !tenantId.equals(module.getTenantId())) forbidden("模块不属于当前系统租户");
        return module;
    }
    private String code(String value) {
        if (value==null || value.isBlank()) return null;
        String result=value.trim().toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z][a-z0-9_]{1,79}")) throw new IllegalArgumentException("配置编码须以字母开头并只包含字母、数字和下划线");
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
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("模块配置无法保存",ex); }
    }
    private Map<String,Object> readMap(String value) {
        if (value==null) return Map.of();
        try { return json.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("字段配置损坏",ex); }
    }
    private ModuleView view(UnModule module) { return new ModuleView(module.getId(),module.getCode(),module.getName(),module.getStatus()); }
    private void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,message); }
}
