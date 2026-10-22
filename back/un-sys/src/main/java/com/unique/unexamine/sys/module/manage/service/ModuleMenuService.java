package com.unique.unexamine.sys.module.manage.service;

import com.unique.unexamine.sys.module.manage.enums.DefaultModuleAction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.service.*;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 菜单是唯一权限身份，模块菜单负责展示，动作保存发布后的执行配置。 */
@Service
public class ModuleMenuService {
    private static final Set<String> POSITIONS=Set.of("MODULE_ENTRY","LIST_TOOLBAR","BATCH","ROW","DETAIL_HEADER");
    private static final Set<String> EXECUTABLE=Set.of("CREATE","EDIT","LIST","DETAIL","DELETE","TRANSFER","CONVERT","UPDATE_FIELDS");
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnSysSystemConfigService systemConfigs;
    private final IUnSysMenuService menus;
    private final IUnModuleService modules;
    private final IUnModuleMenuService bindings;
    private final IUnModuleActionService actions;
    private final IUnModuleFieldService fields;
    private final ModuleFieldService fieldRules;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    /** 条件字段使用稳定 ID，条件均满足才允许执行。 */
    public record Condition(@NotNull Long fieldId,@NotBlank String operator,Object value) {}
    /** 转化字段映射使用两端稳定字段 ID，不接受数据库列名。 */
    public record Mapping(@NotNull Long sourceFieldId,@NotNull Long targetFieldId) {}
    /** 执行配置的发布载荷；名称只在发布时同步到统一菜单。 */
    public record Settings(String name,String type,String position,List<Long> fieldIds,List<Condition> conditions,
                           Long targetModuleId,List<Mapping> mappings) {}
    /** menuId 为空时新建自定义菜单，默认菜单不能更换执行类型。 */
    public record SaveRequest(Long menuId,@NotBlank @Size(max=100) String name,@NotBlank String type,
                              @NotBlank String position,boolean enabled,List<Long> fieldIds,List<Condition> conditions,
                              Long targetModuleId,List<Mapping> mappings) {}
    /** 管理员读取草稿和发布内容，运行态只使用 published。 */
    public record ActionView(Long menuId,String code,String name,boolean enabled,boolean publishedEnabled,int version,Settings settings,Settings published) {}
    /** 同一次解析得到的菜单、绑定和动作，避免混用不同模块或租户身份。 */
    public record Definition(UnModule module,UnSysMenu menu,UnModuleMenu binding,UnModuleAction action,Settings settings) {}

    /** 注入生成的基础服务，菜单和动作的业务关联在本服务完成。 */
    public ModuleMenuService(SystemManageService systems,IUnSysSystemService systemRows,IUnSysSystemConfigService systemConfigs,
            IUnSysMenuService menus,IUnModuleService modules,IUnModuleMenuService bindings,IUnModuleActionService actions,
            IUnModuleFieldService fields,ModuleFieldService fieldRules,ConfigurationCache cache,ObjectMapper json) {
        this.systems=systems; this.systemRows=systemRows; this.systemConfigs=systemConfigs; this.menus=menus;
        this.modules=modules; this.bindings=bindings; this.actions=actions; this.fields=fields; this.fieldRules=fieldRules; this.cache=cache; this.json=json;
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

    /** 模块首次发布时让默认菜单具备运行态配置，不覆盖后续动作草稿。 */
    public void publishInitial(UnModule module) {
        for (var row:actions.list(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId,module.getId())
                .eq(UnModuleAction::getTenantId,module.getTenantId()).eq(UnModuleAction::getPublishedVersion,0))) {
            var menu=menus.queryById(row.getMenuId());
            if (Arrays.stream(DefaultModuleAction.values()).noneMatch(item->menu.getCode().equals("MODULE_"+module.getId()+"_"+item.name()))) continue;
            publishRows(menu,row,read(row.getConfigJson()));
        }
    }

    /** 管理员查看模块当前租户自定义菜单和可继承的主租户菜单。 */
    public List<ActionView> configuration(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); module(context,moduleId,false);
        return definitions(context,moduleId).stream().map(this::view).toList();
    }

    /** 保存动作草稿，未发布内容不会影响原来的按钮名称和执行规则。 */
    @Transactional(rollbackFor=Exception.class)
    public ActionView save(String token,Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); var module=module(context,moduleId,false); lock(moduleId);
        Settings settings=new Settings(request.name().trim(),request.type().trim().toUpperCase(Locale.ROOT),request.position(),
                copy(request.fieldIds()),copy(request.conditions()),request.targetModuleId(),copy(request.mappings()));
        validate(context,moduleId,settings,false);
        if (request.enabled() && !EXECUTABLE.contains(settings.type())) throw new IllegalArgumentException("该动作执行能力尚未开放");
        if (request.menuId()==null) {
            if (!Set.of("UPDATE_FIELDS","TRANSFER","CONVERT").contains(settings.type())) throw new IllegalArgumentException("自定义动作执行类型无效");
            var parent=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,module.getTenantId()).eq(UnSysMenu::getCode,"MODULE_"+moduleId));
            return view(create(module,context.tenantId(),parent.getId(),"MODULE_"+moduleId+"_CUSTOM_"+UUID.randomUUID().toString().replace("-",""),settings,request.enabled(),100));
        }
        var definition=definition(context,moduleId,request.menuId(),false);
        if (!context.tenantId().equals(definition.action().getTenantId())) forbidden("不能修改其他租户定义的菜单动作");
        if (!definition.action().getExecutionType().equals(settings.type())) throw new IllegalArgumentException("已有菜单不能变更动作执行类型");
        var action=definition.action(); action.setConfigJson(write(settings)); action.setEnabledFlag(request.enabled());
        actions.addOrUpdate(action); return view(new Definition(module,definition.menu(),definition.binding(),action,settings));
    }

    /** 只在发布时更新权限版本；禁止把不合法的字段或跨租户映射投入运行。 */
    @Transactional(rollbackFor=Exception.class)
    public ActionView publish(String token,Long systemId,Long tenantId,Long moduleId,Long menuId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); lock(moduleId);
        var definition=definition(context,moduleId,menuId,false);
        if (!context.tenantId().equals(definition.action().getTenantId())) forbidden("不能发布其他租户定义的菜单动作");
        Settings settings=read(definition.action().getConfigJson()); validate(context,moduleId,settings,true);
        publishRows(definition.menu(),definition.action(),settings); changed(systemId,context.tenantId());
        return view(new Definition(definition.module(),definition.menu(),definition.binding(),definition.action(),settings));
    }

    /** 基础接口解析自己对应的菜单，具体授权仍以菜单 ID 完成。 */
    public Definition defaultDefinition(SystemManageService.SystemContext context,Long moduleId,String operation) {
        var module=module(context,moduleId,true);
        var menu=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,module.getTenantId())
                .eq(UnSysMenu::getCode,"MODULE_"+moduleId+"_"+operation));
        if (menu==null) forbidden("模块菜单不存在");
        return definition(context,moduleId,menu.getId(),true);
    }

    /** 解析菜单所属模块和租户，发布状态与启停每次检查，不从权限缓存猜测。 */
    public Definition definition(SystemManageService.SystemContext context,Long moduleId,Long menuId,boolean runtime) {
        var module=module(context,moduleId,runtime); var menu=menus.queryById(menuId);
        var binding=bindings.getOne(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId,moduleId).eq(UnModuleMenu::getMenuId,menuId));
        var action=actions.getOne(new LambdaQueryWrapper<UnModuleAction>().eq(UnModuleAction::getModuleId,moduleId).eq(UnModuleAction::getMenuId,menuId));
        if (binding==null || action==null || !context.systemId().equals(menu.getSystemId())
                || !binding.getTenantId().equals(menu.getTenantId()) || !action.getTenantId().equals(menu.getTenantId())
                || !List.of(context.tenantId(),module.getTenantId()).contains(menu.getTenantId())) forbidden("菜单不属于当前模块或租户");
        if (runtime) {
            checkActive(menu);
            if (!"PUBLISHED".equals(binding.getStatus()) || action.getPublishedVersion()==null || action.getPublishedVersion()<1
                    || !Boolean.TRUE.equals(action.getPublishedEnabledFlag())) forbidden("模块动作尚未发布或已停用");
        }
        return new Definition(module,menu,binding,action,read(runtime?action.getPublishedConfigJson():action.getConfigJson()));
    }

    /** 模块内候选操作仍需由调用方逐个鉴权后返回给登录人。 */
    public List<Definition> definitions(SystemManageService.SystemContext context,Long moduleId) {
        var module=module(context,moduleId,false);
        return bindings.list(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId,moduleId)
                .in(UnModuleMenu::getTenantId,new HashSet<>(List.of(context.tenantId(),module.getTenantId()))).orderByAsc(UnModuleMenu::getSortNo)).stream()
                .map(binding->definition(context,moduleId,binding.getMenuId(),false)).toList();
    }

    /** 字段和映射按发布字段 ID 查验，不随字段名称修改漂移。 */
    private void validate(SystemManageService.SystemContext context,Long moduleId,Settings settings,boolean publishing) {
        if (settings.name().isBlank() || settings.name().length()>100 || !POSITIONS.contains(settings.position())) throw new IllegalArgumentException("动作名称或展示位置无效");
        Set<String> known=new HashSet<>(EXECUTABLE); known.addAll(Set.of("IMPORT","EXPORT","PRINT"));
        if (!known.contains(settings.type())) throw new IllegalArgumentException("动作执行类型无效");
        var source=publishedFields(moduleId);
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
            module(context,settings.targetModuleId(),publishing);
            var target=publishedFields(settings.targetModuleId()); Set<Long> mapped=new HashSet<>();
            for (var mapping:settings.mappings()) if (mapping==null || !source.containsKey(mapping.sourceFieldId())
                    || !target.containsKey(mapping.targetFieldId()) || !mapped.add(mapping.targetFieldId())) throw new IllegalArgumentException("转化映射无效或目标字段重复");
            for (var mapping:settings.mappings()) {
                var from=source.get(mapping.sourceFieldId()); var to=target.get(mapping.targetFieldId());
                fieldRules.validateMapping(fieldRules.published(from.getFieldCode(),readField(from)),fieldRules.published(to.getFieldCode(),readField(to)));
            }
            if (publishing) for (var field:target.values()) {
                try {
                    var config=json.readTree(field.getPublishedConfigJson());
                    if (config.path("required").asBoolean() && !mapped.contains(field.getId())
                            && !config.path("config").hasNonNull("defaultValue")) throw new IllegalArgumentException("目标必填字段没有映射或默认值："+config.path("fieldName").asText());
                } catch (JsonProcessingException ex) { throw new IllegalStateException("目标字段发布配置损坏",ex); }
            }
        }
    }

    private Definition create(UnModule module,Long tenantId,Long parentId,String code,Settings settings,boolean enabled,int order) {
        var menu=new UnSysMenu(); menu.setSystemId(module.getSystemId()); menu.setTenantId(tenantId); menu.setParentId(parentId);
        menu.setCode(code); menu.setName(settings.name()); menu.setMenuType(Set.of("LIST","DETAIL").contains(settings.type())?"PAGE":"ACTION");
        menu.setSortNo(order); menu.setStatus("ACTIVE"); menus.addOrUpdate(menu);
        var binding=new UnModuleMenu(); binding.setSystemId(module.getSystemId()); binding.setTenantId(tenantId); binding.setModuleId(module.getId());
        binding.setMenuId(menu.getId()); binding.setDisplayPosition(settings.position()); binding.setSortNo(order); binding.setStatus("DRAFT"); bindings.addOrUpdate(binding);
        var action=new UnModuleAction(); action.setModuleId(module.getId()); action.setTenantId(tenantId); action.setMenuId(menu.getId());
        action.setExecutionType(settings.type()); action.setEnabledFlag(enabled); action.setConfigJson(write(settings)); action.setStatus("DRAFT"); actions.addOrUpdate(action);
        return new Definition(module,menu,binding,action,settings);
    }
    private void publishRows(UnSysMenu menu,UnModuleAction action,Settings settings) {
        menu.setName(settings.name()); menus.addOrUpdate(menu);
        var binding=bindings.getOne(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getMenuId,menu.getId()));
        binding.setDisplayPosition(settings.position()); binding.setStatus("PUBLISHED"); bindings.addOrUpdate(binding);
        action.setPublishedConfigJson(write(settings)); action.setPublishedEnabledFlag(action.getEnabledFlag()); action.setStatus("PUBLISHED");
        action.setPublishedVersion((action.getPublishedVersion()==null?0:action.getPublishedVersion())+1); actions.addOrUpdate(action);
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
    private Map<Long,UnModuleField> publishedFields(Long moduleId) {
        Map<Long,UnModuleField> result=new LinkedHashMap<>(); fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,moduleId).eq(UnModuleField::getStatus,"PUBLISHED")).forEach(f->result.put(f.getId(),f)); return result;
    }
    private UnModule module(SystemManageService.SystemContext context,Long moduleId,boolean published) {
        var module=modules.queryById(moduleId); Long root=systemRows.queryById(context.systemId()).getRootTenantId();
        if (!context.systemId().equals(module.getSystemId()) || !List.of(root,context.tenantId()).contains(module.getTenantId())) forbidden("模块不属于当前系统租户");
        if (published && !"PUBLISHED".equals(module.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"模块尚未发布"); return module;
    }
    private void lock(Long moduleId) { modules.getOne(new LambdaQueryWrapper<UnModule>().eq(UnModule::getId,moduleId).last("FOR UPDATE")); }
    private void changed(Long systemId,Long tenantId) {
        systemConfigs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,systemId).setSql("permission_revision=permission_revision+1"));
        cache.invalidateAfterCommit("permission:"+systemId+":"+tenantId);
    }
    private Map<String,Object> readField(UnModuleField field) {
        try { return json.readValue(field.getPublishedConfigJson(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("字段发布配置损坏",ex); }
    }
    private ActionView view(Definition d) { return new ActionView(d.menu().getId(),d.menu().getCode(),d.menu().getName(),Boolean.TRUE.equals(d.action().getEnabledFlag()),Boolean.TRUE.equals(d.action().getPublishedEnabledFlag()),
            d.action().getPublishedVersion()==null?0:d.action().getPublishedVersion(),read(d.action().getConfigJson()),
            d.action().getPublishedConfigJson()==null?null:read(d.action().getPublishedConfigJson())); }
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
