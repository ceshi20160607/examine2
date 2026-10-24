package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.service.*;
import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.ActionView;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.Definition;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto.Settings;
import com.unique.unexamine.sys.module.manage.enums.DefaultModuleAction;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 菜单是唯一权限身份，模块菜单负责展示，动作保存当前执行配置。 */
@Service
public class ModuleMenuService {
    private static final Set<String> POSITIONS=Set.of("MODULE_ENTRY","LIST_TOOLBAR","BATCH","ROW","DETAIL_HEADER");
    private static final Set<String> EXECUTABLE=Set.of("CREATE","EDIT","LIST","DETAIL","DELETE","TRANSFER","CONVERT","UPDATE_FIELDS","EXPORT");
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnSysSystemConfigService systemConfigs;
    private final IUnSysLogOptService logs;
    private final IUnSysMenuService menus;
    private final IUnModuleService modules;
    private final IUnModuleMenuService bindings;
    private final IUnModuleActionService actions;
    private final IUnModuleFieldService fields;
    private final ModuleFieldService fieldRules;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    /** 注入生成的基础服务，菜单和动作的业务关联在本服务完成。 */
    public ModuleMenuService(SystemManageService systems,IUnSysSystemService systemRows,IUnSysSystemConfigService systemConfigs,
            IUnSysMenuService menus,IUnModuleService modules,IUnModuleMenuService bindings,IUnModuleActionService actions,
            IUnModuleFieldService fields,ModuleFieldService fieldRules,ConfigurationCache cache,ObjectMapper json,IUnSysLogOptService logs) {
        this.systems=systems; this.systemRows=systemRows; this.systemConfigs=systemConfigs; this.menus=menus;
        this.modules=modules; this.bindings=bindings; this.actions=actions; this.fields=fields; this.fieldRules=fieldRules; this.cache=cache; this.json=json; this.logs=logs;
    }

    /** 随模块创建事务初始化十个默认动作；已有配置原样保留。 */
    public void initialize(UnModule module) {
        UnSysMenu parent=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,module.getTenantId())
                .eq(UnSysMenu::getCode,"MODULE_"+module.getId()));
        if (parent==null) {
            parent=new UnSysMenu(); parent.setSystemId(module.getSystemId()); parent.setTenantId(module.getTenantId());
            parent.setCode("MODULE_"+module.getId()); parent.setName(module.getName()); parent.setMenuType("DIRECTORY");
            parent.setStatus("ACTIVE"); menus.addOrUpdate(parent);
        }
        for (var item:DefaultModuleAction.values()) {
            String code="MODULE_"+module.getId()+"_"+item.name();
            var existing=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,module.getTenantId()).eq(UnSysMenu::getCode,code));
            if (existing!=null) continue;
            Settings settings=new Settings(item.label(),item.name(),item.position(),List.of(),List.of(),null,List.of());
            create(module,module.getTenantId(),parent.getId(),code,settings,item.enabled(),item.ordinal());
        }
    }

    /** 管理员查看模块当前租户自定义菜单和可继承的主租户菜单。 */
    public List<ActionView> configuration(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); module(context,moduleId,false);
        return definitions(context,moduleId).stream().map(this::view).toList();
    }

    /** 菜单保存后直接更新名称、展示和执行规则，提交后失效权限缓存。 */
    @Transactional(rollbackFor=Exception.class)
    public ActionView save(String token,Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); var module=module(context,moduleId,false); lock(moduleId);
        Settings settings=new Settings(request.name().trim(),request.type().trim().toUpperCase(Locale.ROOT),request.position(),
                copy(request.fieldIds()),copy(request.conditions()),request.targetModuleId(),copy(request.mappings()));
        validate(context,moduleId,settings);
        if (request.enabled() && !EXECUTABLE.contains(settings.type())) throw new IllegalArgumentException("该动作执行能力尚未开放");
        Definition saved;
        if (request.menuId()==null) {
            if (!Set.of("UPDATE_FIELDS","TRANSFER","CONVERT").contains(settings.type())) throw new IllegalArgumentException("自定义动作执行类型无效");
            var parent=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,module.getTenantId()).eq(UnSysMenu::getCode,"MODULE_"+moduleId));
            saved=create(module,context.tenantId(),parent.getId(),"MODULE_"+moduleId+"_CUSTOM_"+UUID.randomUUID().toString().replace("-",""),settings,request.enabled(),100);
        } else {
            var definition=definition(context,moduleId,request.menuId(),false);
            if (!context.tenantId().equals(definition.action().getTenantId())) forbidden("不能修改其他租户定义的菜单动作");
            if (!definition.action().getExecutionType().equals(settings.type())) throw new IllegalArgumentException("已有菜单不能变更动作执行类型");
            var menu=definition.menu(); menu.setName(settings.name()); menus.addOrUpdate(menu);
            var binding=definition.binding(); binding.setDisplayPosition(settings.position()); binding.setStatus("ACTIVE"); bindings.addOrUpdate(binding);
            var action=definition.action(); action.setConfigJson(write(settings)); action.setEnabledFlag(request.enabled()); action.setStatus("ACTIVE");
            actions.addOrUpdate(action); saved=new Definition(module,menu,binding,action,settings);
        }
        var log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,systemId)); log.setActionCode("MODULE_MENU_SAVE");
        log.setObjectType("MODULE_MENU"); log.setObjectId(saved.menu().getId());
        log.setDetailJson(write(settings)); logs.addOrUpdate(log); changed(systemId,context.tenantId());
        return view(saved);
    }

    /** 基础接口解析自己对应的菜单，具体授权仍以菜单 ID 完成。 */
    public Definition defaultDefinition(SystemManageDto.SystemContext context,Long moduleId,String operation) {
        var module=module(context,moduleId,true);
        var menu=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,module.getTenantId())
                .eq(UnSysMenu::getCode,"MODULE_"+moduleId+"_"+operation));
        if (menu==null) forbidden("模块菜单不存在");
        return definition(context,moduleId,menu.getId(),true);
    }

    /** 解析菜单所属模块和租户，当前配置与启停每次检查，不从权限缓存猜测。 */
    public Definition definition(SystemManageDto.SystemContext context,Long moduleId,Long menuId,boolean runtime) {
        var module=module(context,moduleId,runtime); var menu=menus.queryById(menuId);
        var binding=bindings.getOne(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId,moduleId).eq(UnModuleMenu::getMenuId,menuId));
        var action=actions.getOne(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId,moduleId).eq(UnModuleAction::getMenuId,menuId));
        if (binding==null || action==null || !context.systemId().equals(menu.getSystemId())
                || !binding.getTenantId().equals(menu.getTenantId()) || !action.getTenantId().equals(menu.getTenantId())
                || !List.of(context.tenantId(),module.getTenantId()).contains(menu.getTenantId())) forbidden("菜单不属于当前模块或租户");
        if (runtime) {
            checkActive(menu);
            if (!"ACTIVE".equals(binding.getStatus()) || !"ACTIVE".equals(action.getStatus())
                    || !Boolean.TRUE.equals(action.getEnabledFlag())) forbidden("模块菜单动作已停用");
        }
        return new Definition(module,menu,binding,action,read(action.getConfigJson()));
    }

    /** 模块内候选操作仍需由调用方逐个鉴权后返回给登录人。 */
    public List<Definition> definitions(SystemManageDto.SystemContext context,Long moduleId) {
        var module=module(context,moduleId,false);
        return bindings.list(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId,moduleId)
                .in(UnModuleMenu::getTenantId,new HashSet<>(List.of(context.tenantId(),module.getTenantId()))).orderByAsc(UnModuleMenu::getSortNo)).stream()
                .map(binding->definition(context,moduleId,binding.getMenuId(),false)).toList();
    }

    /** 字段和映射按当前字段 ID 查验，不随字段名称修改漂移。 */
    private void validate(SystemManageDto.SystemContext context,Long moduleId,Settings settings) {
        if (settings.name().isBlank() || settings.name().length()>100 || !POSITIONS.contains(settings.position())) throw new IllegalArgumentException("动作名称或展示位置无效");
        Set<String> known=new HashSet<>(EXECUTABLE); known.addAll(Set.of("IMPORT","PRINT"));
        if (!known.contains(settings.type())) throw new IllegalArgumentException("动作执行类型无效");
        var source=currentFields(moduleId);
        if (settings.fieldIds().size()!=new HashSet<>(settings.fieldIds()).size() || !source.keySet().containsAll(settings.fieldIds())) throw new IllegalArgumentException("动作字段无效或重复");
        if ("UPDATE_FIELDS".equals(settings.type()) && settings.fieldIds().isEmpty()) throw new IllegalArgumentException("字段更新动作至少配置一个字段");
        for (var condition:settings.conditions()) if (condition==null || !source.containsKey(condition.fieldId())
                || condition.operator()==null || !Set.of("EQ","NE","EMPTY","NOT_EMPTY","GT","GE","LT","LE","IN").contains(condition.operator())) throw new IllegalArgumentException("动作条件字段或操作符无效");
        for (var condition:settings.conditions()) {
            if (Set.of("GT","GE","LT","LE").contains(condition.operator()) && !(condition.value() instanceof Number)) throw new IllegalArgumentException("大小比较条件的目标必须是数值");
            if ("IN".equals(condition.operator()) && !(condition.value() instanceof List<?>)) throw new IllegalArgumentException("范围条件的目标必须是集合");
        }
        if ("CONVERT".equals(settings.type()) && settings.targetModuleId()!=null) {
            if (moduleId.equals(settings.targetModuleId())) throw new IllegalArgumentException("转化目标不能是来源模块");
            module(context,settings.targetModuleId(),true);
            var target=currentFields(settings.targetModuleId()); Set<Long> mapped=new HashSet<>();
            for (var mapping:settings.mappings()) if (mapping==null || !source.containsKey(mapping.sourceFieldId())
                    || !target.containsKey(mapping.targetFieldId()) || !mapped.add(mapping.targetFieldId())) throw new IllegalArgumentException("转化映射无效或目标字段重复");
            for (var mapping:settings.mappings()) {
                var from=source.get(mapping.sourceFieldId()); var to=target.get(mapping.targetFieldId());
                fieldRules.validateMapping(fieldRules.configured(from.getFieldKey(),readField(from)),fieldRules.configured(to.getFieldKey(),readField(to)));
            }
            for (var field:target.values()) if (Boolean.TRUE.equals(field.getRequiredFlag())
                    && !mapped.contains(field.getId()) && field.getDefaultValue()==null)
                throw new IllegalArgumentException("目标必填字段没有映射或默认值："+field.getFieldName());
        }
    }

    private Definition create(UnModule module,Long tenantId,Long parentId,String code,Settings settings,boolean enabled,int order) {
        var menu=new UnSysMenu(); menu.setSystemId(module.getSystemId()); menu.setTenantId(tenantId); menu.setParentId(parentId);
        menu.setCode(code); menu.setName(settings.name()); menu.setMenuType(Set.of("LIST","DETAIL").contains(settings.type())?"PAGE":"ACTION");
        menu.setSortNo(order); menu.setStatus("ACTIVE"); menus.addOrUpdate(menu);
        var binding=new UnModuleMenu(); binding.setSystemId(module.getSystemId()); binding.setTenantId(tenantId); binding.setModuleId(module.getId());
        binding.setMenuId(menu.getId()); binding.setDisplayPosition(settings.position()); binding.setSortNo(order); binding.setStatus("ACTIVE"); bindings.addOrUpdate(binding);
        var action=new UnModuleAction(); action.setModuleId(module.getId()); action.setTenantId(tenantId); action.setMenuId(menu.getId());
        action.setExecutionType(settings.type()); action.setEnabledFlag(enabled); action.setConfigJson(write(settings)); action.setStatus("ACTIVE"); actions.addOrUpdate(action);
        return new Definition(module,menu,binding,action,settings);
    }
    private void checkActive(UnSysMenu menu) {
        Set<Long> visited=new HashSet<>();
        Long systemId=menu.getSystemId();
        while (menu!=null) {
            if (!systemId.equals(menu.getSystemId()) || !"ACTIVE".equals(menu.getStatus()) || !visited.add(menu.getId())) forbidden("菜单或上级目录不可用");
            Long parentId=menu.getParentId();
            menu=parentId==null?null:menus.getById(parentId);
            if (parentId!=null && menu==null) forbidden("上级目录不存在");
        }
    }
    private Map<Long,UnModuleField> currentFields(Long moduleId) {
        Map<Long,UnModuleField> result=new LinkedHashMap<>(); fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,moduleId).eq(UnModuleField::getStatus,"ACTIVE")).forEach(f->result.put(f.getId(),f)); return result;
    }
    private UnModule module(SystemManageDto.SystemContext context,Long moduleId,boolean runtime) {
        var module=modules.queryById(moduleId); Long root=systemRows.queryById(context.systemId()).getRootTenantId();
        if (!context.systemId().equals(module.getSystemId()) || !List.of(root,context.tenantId()).contains(module.getTenantId())) forbidden("模块不属于当前系统租户");
        if (runtime && !"ACTIVE".equals(module.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"模块已停用"); return module;
    }
    /** 模块资料改名同步导航目录，子动作名称仍保留管理员配置。 */
    public void renameDirectory(UnModule module) {
        menus.update(new LambdaUpdateWrapper<UnSysMenu>().eq(UnSysMenu::getSystemId,module.getSystemId())
                .eq(UnSysMenu::getCode,"MODULE_"+module.getId()).set(UnSysMenu::getName,module.getName()));
    }
    private void lock(Long moduleId) {
        if (modules.getOne(new LambdaQueryWrapper<UnModule>().eq(UnModule::getId,moduleId).last("FOR UPDATE"))==null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"模块不存在");
    }
    private void changed(Long systemId,Long tenantId) {
        systemConfigs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,systemId).setSql("permission_revision=permission_revision+1"));
        cache.invalidateAfterCommit("permission:"+systemId+":"+tenantId);
    }
    private Map<String,Object> readField(UnModuleField field) {
        Map<String,Object> value=new LinkedHashMap<>(); value.put("fieldName",field.getFieldName());
        value.put("fieldType",field.getFieldType()); value.put("required",field.getRequiredFlag());
        value.put("config",fieldRules.rules(field)); return value;
    }
    private ActionView view(Definition definition) {
        return new ActionView(definition.menu().getId(),definition.menu().getCode(),definition.menu().getName(),
                Boolean.TRUE.equals(definition.action().getEnabledFlag()),read(definition.action().getConfigJson()));
    }
    private <T> List<T> copy(List<T> value) {
        if (value==null) return List.of();
        if (value.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("动作配置列表不能包含空项");
        return List.copyOf(value);
    }
    private String write(Settings value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalArgumentException("动作配置无法保存",ex); } }
    private Settings read(String value) {
        try { var raw=json.readValue(value,Settings.class); return new Settings(raw.name(),raw.type(),raw.position(),copy(raw.fieldIds()),copy(raw.conditions()),raw.targetModuleId(),copy(raw.mappings())); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("动作配置损坏",ex); }
    }
    private void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,message); }
}
