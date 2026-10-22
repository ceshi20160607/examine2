package com.unique.unexamine.sys.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.*;
import com.unique.unexamine.sys.base.service.*;
import com.unique.unexamine.sys.manage.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 系统模块授权统一入口；缓存角色规则，实时按负责人当前组织归属求数据范围。 */
@Service
public class ModulePermissionService {
    private static final Set<String> SCOPES=Set.of("ALL","SELF","DEPARTMENT","DEPARTMENT_SUBTREE","SELECTED_DEPARTMENTS","SELECTED_MEMBERS");
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnSysSystemConfigService configs;
    private final IUnPlatAccountService accounts;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysDepartmentService departments;
    private final IUnSysRoleService roles;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysRoleMenuService roleMenus;
    private final IUnSysRoleDataService roleData;
    private final IUnSysRoleFieldService roleFields;
    private final ModuleMenuService moduleMenus;
    private final IUnSysLogOptService auditLogs;
    private final IUnModuleService modules;
    private final IUnModuleFieldService fields;
    private final IUnModuleTenantService tenantModules;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    /** 租户字段上限，null 集合表示不额外限制，普通角色许可逐字段存储。 */
    public record FieldAccess(Set<String> readable,Set<String> writable,Set<String> exportable) {}
    /** 菜单功能授权与负责人范围，字段许可在独立接口配置。 */
    public record GrantRequest(boolean allowed,@NotBlank String scope,Set<Long> accountIds,Set<Long> departmentIds) {}
    /** 每个稳定字段 ID 的正向许可，不授权的标记不否决其他角色。 */
    public record FieldGrant(@NotNull Long fieldId,boolean readable,boolean writable,boolean exportable) {}
    /** 替换指定角色在当前模块的全部字段许可。 */
    public record FieldsRequest(@NotNull List<@Valid FieldGrant> fields) {}
    /** 数据范围规则不包含字段权限。 */
    public record Grant(String scope,Set<Long> accountIds,Set<Long> departmentIds) {}
    /** 可缓存的授权规则；人员当前部门不进入缓存。 */
    public record Rules(boolean menuAllowed,List<Grant> grants,Set<String> readable,Set<String> writable,
                         Set<String> exportable,FieldAccess tenantFields) {}
    /** 一次鉴权得到的菜单身份与数据、字段检查结果。 */
    public record Access(SystemManageService.SystemContext context,Long accountId,PermissionPolicy policy,
                          ModuleMenuService.Definition definition) {}
    /** 管理员添加或调整租户成员，不能提升管理身份。 */
    public record MemberRequest(@NotNull Long accountId,Long departmentId) {}
    /** 普通角色资料，代码不是系统管理身份。 */
    public record RoleRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=100) String name) {}
    /** 显式设置成员角色关系，撤销不删除其他角色。 */
    public record AssignRequest(@NotNull Long tenantMemberId,@NotNull Long roleId,boolean assigned) {}

    /** 所有读写均通过生成的基础 Service，Controller 不接触表查询。 */
    public ModulePermissionService(SystemManageService systems,IUnSysSystemService systemRows,
            IUnSysSystemConfigService configs,IUnPlatAccountService accounts,IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers,IUnSysDepartmentService departments,IUnSysRoleService roles,
            IUnSysMemberRoleService memberRoles,IUnSysRoleMenuService roleMenus,
            IUnSysRoleDataService roleData,IUnSysRoleFieldService roleFields,ModuleMenuService moduleMenus,IUnSysLogOptService auditLogs,IUnModuleService modules,IUnModuleFieldService fields,
            IUnModuleTenantService tenantModules,ConfigurationCache cache,ObjectMapper json) {
        this.systems=systems; this.systemRows=systemRows; this.configs=configs; this.accounts=accounts;
        this.members=members; this.tenantMembers=tenantMembers; this.departments=departments; this.roles=roles;
        this.memberRoles=memberRoles; this.roleMenus=roleMenus; this.roleData=roleData; this.roleFields=roleFields; this.moduleMenus=moduleMenus; this.auditLogs=auditLogs;
        this.modules=modules; this.fields=fields; this.tenantModules=tenantModules; this.cache=cache; this.json=json;
    }

    /** 基础接口先解析实际默认菜单，列表和详情使用不同身份。 */
    public Access require(String token,Long systemId,Long tenantId,Long moduleId,String operation) {
        var context=systems.context(token,systemId,tenantId);
        return resolve(token,context,moduleMenus.defaultDefinition(context,moduleId,operation));
    }

    /** 默认和自定义动作均使用同一个菜单鉴权入口，不依赖固定动作枚举。 */
    public Access requireMenu(String token,Long systemId,Long tenantId,Long moduleId,Long menuId) {
        var context=systems.context(token,systemId,tenantId);
        return resolve(token,context,moduleMenus.definition(context,moduleId,menuId,true));
    }

    private Access resolve(String token,SystemManageService.SystemContext context,ModuleMenuService.Definition definition) {
        Long actor=systems.accountId(token,context.systemId());
        var config=configs.getOne(new LambdaQueryWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,context.systemId()));
        String key=config.getPermissionRevision()+":"+context.tenantMemberId()+":"+definition.menu().getId();
        Rules rules=cache.get(namespace(context.systemId(),context.tenantId()),key,Rules.class,
                ()->load(context,definition.module().getId(),definition.menu().getId()));
        boolean admin=context.owner() || context.tenantAdmin();
        Set<Long> owners=new HashSet<>(); boolean allData=admin;
        for (Grant grant:rules.grants()) { allData|="ALL".equals(grant.scope()); owners.addAll(ownerIds(context,actor,grant)); }
        Set<String> readable=new HashSet<>(rules.readable()),writable=new HashSet<>(rules.writable()),exportable=new HashSet<>(rules.exportable());
        boolean allRead=admin,allWrite=admin,allExport=admin;
        if (rules.tenantFields().readable()!=null) {
            if (allRead) { readable.clear(); readable.addAll(rules.tenantFields().readable()); } else readable.retainAll(rules.tenantFields().readable()); allRead=false;
        }
        if (rules.tenantFields().writable()!=null) {
            if (allWrite) { writable.clear(); writable.addAll(rules.tenantFields().writable()); } else writable.retainAll(rules.tenantFields().writable()); allWrite=false;
        }
        if (rules.tenantFields().exportable()!=null) {
            if (allExport) { exportable.clear(); exportable.addAll(rules.tenantFields().exportable()); } else exportable.retainAll(rules.tenantFields().exportable()); allExport=false;
        }
        var policy=new PermissionPolicy(admin || rules.menuAllowed(),allData,owners,allRead,readable,allWrite,writable,allExport,exportable);
        policy.requireAction(); return new Access(context,actor,policy,definition);
    }

    /** 添加已注册平台账号到系统当前租户，不授予管理员或任何默认业务角色。 */
    @Transactional(rollbackFor=Exception.class)
    public Long addMember(String token,Long systemId,Long tenantId,MemberRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        if (!"ACTIVE".equals(accounts.queryById(request.accountId()).getStatus())) throw new IllegalArgumentException("账号已停用");
        if (request.departmentId()!=null) department(context,request.departmentId());
        UnSysMember member=members.getOne(new LambdaQueryWrapper<UnSysMember>().eq(UnSysMember::getSystemId,systemId)
                .eq(UnSysMember::getAccountId,request.accountId()));
        if (member==null) { member=new UnSysMember(); member.setSystemId(systemId); member.setAccountId(request.accountId());
            member.setStatus("ACTIVE"); members.addOrUpdate(member); }
        if (!"ACTIVE".equals(member.getStatus())) throw new IllegalArgumentException("系统成员已停用");
        UnSysTenantMember link=tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId,context.tenantId()).eq(UnSysTenantMember::getMemberId,member.getId()));
        if (link==null) { link=new UnSysTenantMember(); link.setSystemId(systemId); link.setTenantId(context.tenantId());
            link.setMemberId(member.getId()); link.setIsAdmin(false); link.setStatus("ACTIVE"); }
        if (!"ACTIVE".equals(link.getStatus())) throw new IllegalArgumentException("租户成员已停用");
        link.setDepartmentId(request.departmentId()); tenantMembers.addOrUpdate(link);
        tenantMembers.update(new LambdaUpdateWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getId,link.getId())
                .set(UnSysTenantMember::getDepartmentId,request.departmentId()));
        audit(token,context,"TENANT_MEMBER_SAVE","TENANT_MEMBER",link.getId(),request); changed(context);
        return link.getId();
    }

    /** 当前租户创建普通角色；角色名称不能提升为所属人或管理员身份。 */
    @Transactional(rollbackFor=Exception.class)
    public Long createRole(String token,Long systemId,Long tenantId,RoleRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        if (!request.code().matches("[a-zA-Z][a-zA-Z0-9_]{1,79}")) throw new IllegalArgumentException("角色编码无效");
        UnSysRole role=new UnSysRole(); role.setSystemId(systemId); role.setTenantId(context.tenantId());
        role.setCode(request.code()); role.setName(request.name().trim()); role.setStatus("ACTIVE");
        roles.addOrUpdate(role); audit(token,context,"ROLE_CREATE","ROLE",role.getId(),request); changed(context); return role.getId();
    }

    /** 人员角色赋予/撤销在同一事务中递增权限版本。 */
    @Transactional(rollbackFor=Exception.class)
    public void assign(String token,Long systemId,Long tenantId,AssignRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); role(context,request.roleId());
        var member=tenantMembers.queryById(request.tenantMemberId());
        if (!systemId.equals(member.getSystemId()) || !context.tenantId().equals(member.getTenantId())) forbidden();
        var query=new LambdaQueryWrapper<UnSysMemberRole>().eq(UnSysMemberRole::getTenantMemberId,member.getId())
                .eq(UnSysMemberRole::getRoleId,request.roleId());
        if (!request.assigned()) memberRoles.remove(query);
        else if (memberRoles.count(query)==0) { var link=new UnSysMemberRole(); link.setTenantMemberId(member.getId());
            link.setRoleId(request.roleId()); memberRoles.addOrUpdate(link); }
        audit(token,context,"ROLE_ASSIGNMENT_SAVE","ROLE",request.roleId(),request); changed(context);
    }

    /** 菜单授权只定义执行许可和负责人范围，不在此写入字段配置。 */
    @Transactional(rollbackFor=Exception.class)
    public void grant(String token,Long systemId,Long tenantId,Long roleId,Long moduleId,Long menuId,GrantRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); role(context,roleId);
        moduleMenus.definition(context,moduleId,menuId,false);
        if (!SCOPES.contains(request.scope())) throw new IllegalArgumentException("数据范围无效");
        for (Long id:safe(request.departmentIds())) department(context,id);
        if (!activeOwners(context,null).containsAll(safe(request.accountIds()))) throw new IllegalArgumentException("指定人员不是当前有效租户成员");
        var dataQuery=new LambdaQueryWrapper<UnSysRoleData>().eq(UnSysRoleData::getRoleId,roleId).eq(UnSysRoleData::getMenuId,menuId);
        var menuQuery=new LambdaQueryWrapper<UnSysRoleMenu>().eq(UnSysRoleMenu::getRoleId,roleId).eq(UnSysRoleMenu::getMenuId,menuId);
        if (!request.allowed()) { roleData.remove(dataQuery); roleMenus.remove(menuQuery); }
        else {
            var row=roleData.getOne(dataQuery); if (row==null) row=new UnSysRoleData();
            row.setRoleId(roleId); row.setMenuId(menuId); row.setScopeCode(request.scope());
            row.setScopeConfig(write(Map.of("accountIds",safe(request.accountIds()),"departmentIds",safe(request.departmentIds())))); roleData.addOrUpdate(row);
            if (roleMenus.count(menuQuery)==0) { var link=new UnSysRoleMenu(); link.setRoleId(roleId); link.setMenuId(menuId); roleMenus.addOrUpdate(link); }
        }
        audit(token,context,"ROLE_MENU_SAVE","ROLE",roleId,Map.of("menuId",menuId,"grant",request)); changed(context);
    }

    /** 独立配置角色字段；稳定字段主键和模块归属必须同时匹配。 */
    @Transactional(rollbackFor=Exception.class)
    public void saveFields(String token,Long systemId,Long tenantId,Long roleId,Long moduleId,FieldsRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); role(context,roleId); module(context,moduleId);
        Set<Long> allowed=new HashSet<>(); fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,moduleId)
                .eq(UnModuleField::getStatus,"PUBLISHED")).forEach(f->allowed.add(f.getId()));
        Set<Long> seen=new HashSet<>();
        for (var grant:request.fields()) if (grant==null || !allowed.contains(grant.fieldId()) || !seen.add(grant.fieldId())
                || (!grant.readable() && (grant.writable() || grant.exportable()))) throw new IllegalArgumentException("字段许可无效、重复或超出可读范围");
        roleFields.remove(new LambdaQueryWrapper<UnSysRoleField>().eq(UnSysRoleField::getRoleId,roleId).eq(UnSysRoleField::getModuleId,moduleId));
        for (var grant:request.fields()) {
            var row=new UnSysRoleField(); row.setRoleId(roleId); row.setModuleId(moduleId); row.setFieldId(grant.fieldId());
            row.setReadableFlag(grant.readable()); row.setWritableFlag(grant.writable()); row.setExportableFlag(grant.exportable()); roleFields.addOrUpdate(row);
        }
        audit(token,context,"ROLE_FIELD_SAVE","ROLE",roleId,Map.of("moduleId",moduleId,"fields",request.fields())); changed(context);
    }

    /** 后台读取当前角色模块字段配置，不从动作数据范围推断。 */
    public List<FieldGrant> configuredFields(String token,Long systemId,Long tenantId,Long roleId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); role(context,roleId); module(context,moduleId);
        return roleFields.list(new LambdaQueryWrapper<UnSysRoleField>().eq(UnSysRoleField::getRoleId,roleId).eq(UnSysRoleField::getModuleId,moduleId)).stream()
                .map(f->new FieldGrant(f.getFieldId(),Boolean.TRUE.equals(f.getReadableFlag()),Boolean.TRUE.equals(f.getWritableFlag()),Boolean.TRUE.equals(f.getExportableFlag()))).toList();
    }

    /** 主租户管理员设置某租户字段上限，子租户不能自行扩大主租户许可。 */
    @Transactional(rollbackFor=Exception.class)
    public void tenantFields(String token,Long systemId,Long targetTenantId,Long moduleId,FieldAccess access) {
        Long root=systemRows.queryById(systemId).getRootTenantId();
        var context=systems.requireAdminContext(token,systemId,root);
        var target=systems.context(token,systemId,targetTenantId); module(target,moduleId); validateFields(moduleId,access);
        var row=tenantModules.getOne(new LambdaQueryWrapper<UnModuleTenant>().eq(UnModuleTenant::getTenantId,targetTenantId)
                .eq(UnModuleTenant::getModuleId,moduleId));
        if (row==null) { row=new UnModuleTenant(); row.setSystemId(systemId); row.setTenantId(targetTenantId);
            row.setModuleId(moduleId); row.setStatus("ACTIVE"); }
        Map<String,Object> config=readMap(row.getConfigJson()); config.put("fieldPolicy",access);
        row.setConfigJson(write(config)); tenantModules.addOrUpdate(row); audit(token,context,"TENANT_FIELD_LIMIT_SAVE","MODULE",moduleId,Map.of("tenantId",targetTenantId,"fields",access)); changed(context);
        cache.invalidateAfterCommit(namespace(systemId,targetTenantId));
    }

    private Rules load(SystemManageService.SystemContext context,Long moduleId,Long menuId) {
        var tenant=tenantModules.getOne(new LambdaQueryWrapper<UnModuleTenant>().eq(UnModuleTenant::getTenantId,context.tenantId()).eq(UnModuleTenant::getModuleId,moduleId));
        if (tenant!=null && !"ACTIVE".equals(tenant.getStatus())) forbidden();
        Object configured=tenant==null?null:readMap(tenant.getConfigJson()).get("fieldPolicy");
        FieldAccess cap=configured==null?new FieldAccess(null,null,null):json.convertValue(configured,FieldAccess.class);
        List<Grant> grants=new ArrayList<>(); Set<String> readable=new HashSet<>(),writable=new HashSet<>(),exportable=new HashSet<>(); boolean menuAllowed=false;
        Map<Long,String> codes=new HashMap<>(); fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,moduleId)
                .eq(UnModuleField::getStatus,"PUBLISHED")).forEach(f->codes.put(f.getId(),f.getFieldCode()));
        for (var link:memberRoles.list(new LambdaQueryWrapper<UnSysMemberRole>().eq(UnSysMemberRole::getTenantMemberId,context.tenantMemberId()))) {
            var role=roles.getById(link.getRoleId());
            if (role==null || !context.systemId().equals(role.getSystemId()) || !context.tenantId().equals(role.getTenantId()) || !"ACTIVE".equals(role.getStatus())) continue;
            for (var field:roleFields.list(new LambdaQueryWrapper<UnSysRoleField>().eq(UnSysRoleField::getRoleId,role.getId()).eq(UnSysRoleField::getModuleId,moduleId))) {
                String code=codes.get(field.getFieldId()); if (code==null) continue;
                if (Boolean.TRUE.equals(field.getReadableFlag())) readable.add(code);
                if (Boolean.TRUE.equals(field.getWritableFlag())) writable.add(code);
                if (Boolean.TRUE.equals(field.getExportableFlag())) exportable.add(code);
            }
            if (roleMenus.count(new LambdaQueryWrapper<UnSysRoleMenu>().eq(UnSysRoleMenu::getRoleId,role.getId()).eq(UnSysRoleMenu::getMenuId,menuId))==0) continue;
            menuAllowed=true;
            var row=roleData.getOne(new LambdaQueryWrapper<UnSysRoleData>().eq(UnSysRoleData::getRoleId,role.getId()).eq(UnSysRoleData::getMenuId,menuId));
            if (row!=null) {
                var config=readMap(row.getScopeConfig());
                grants.add(new Grant(row.getScopeCode(),longSet(config.get("accountIds")),longSet(config.get("departmentIds"))));
            }
        }
        return new Rules(menuAllowed,grants,readable,writable,exportable,cap);
    }

    private Set<Long> longSet(Object value) {
        if (value==null) return Set.of();
        return json.convertValue(value,new com.fasterxml.jackson.core.type.TypeReference<Set<Long>>(){});
    }

    /** 当前成员部门实时求值；调岗后无需等待授权缓存过期。 */
    private Set<Long> ownerIds(SystemManageService.SystemContext context,Long actor,Grant grant) {
        switch (grant.scope()) {
            case "ALL": return Set.of();
            case "SELF": return Set.of(actor);
            case "SELECTED_MEMBERS": { Set<Long> ids=new HashSet<>(grant.accountIds()); ids.retainAll(activeOwners(context,null)); return ids; }
            case "DEPARTMENT", "DEPARTMENT_SUBTREE", "SELECTED_DEPARTMENTS": {
                Set<Long> selected=new HashSet<>(grant.departmentIds());
                if (!"SELECTED_DEPARTMENTS".equals(grant.scope())) {
                    Long dept=tenantMembers.queryById(context.tenantMemberId()).getDepartmentId();
                    selected.clear(); if (dept!=null) selected.add(dept);
                }
                List<UnSysDepartment> active=departments.list(new LambdaQueryWrapper<UnSysDepartment>()
                        .eq(UnSysDepartment::getTenantId,context.tenantId()).eq(UnSysDepartment::getStatus,"ACTIVE"));
                Set<Long> available=new HashSet<>(); active.forEach(d->available.add(d.getId())); selected.retainAll(available);
                if ("DEPARTMENT_SUBTREE".equals(grant.scope())) {
                    boolean grew; do { grew=false; for (var dept:active) if (selected.contains(dept.getParentId())) grew|=selected.add(dept.getId()); } while (grew);
                }
                return selected.isEmpty()?Set.of():activeOwners(context,selected);
            }
            default: throw new IllegalStateException("数据范围配置无效");
        }
    }

    private Set<Long> activeOwners(SystemManageService.SystemContext context,Set<Long> departmentIds) {
        Set<Long> result=new HashSet<>();
        for (var link:tenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>().eq(UnSysTenantMember::getTenantId,context.tenantId())
                .eq(UnSysTenantMember::getStatus,"ACTIVE").in(departmentIds!=null,UnSysTenantMember::getDepartmentId,departmentIds))) {
            var member=members.getById(link.getMemberId());
            if (member!=null && context.systemId().equals(member.getSystemId()) && "ACTIVE".equals(member.getStatus())) {
                var account=accounts.getById(member.getAccountId()); if (account!=null && "ACTIVE".equals(account.getStatus())) result.add(member.getAccountId());
            }
        }
        return result;
    }
    private void validateFields(Long moduleId,FieldAccess access) {
        if (access==null) throw new IllegalArgumentException("字段权限不能为空");
        Set<String> codes=new HashSet<>(); fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,moduleId)
                .eq(UnModuleField::getStatus,"PUBLISHED")).forEach(f->codes.add(f.getFieldCode()));
        if (access.readable()!=null && !codes.containsAll(access.readable()) || access.writable()!=null && !codes.containsAll(access.writable())
                || access.exportable()!=null && !codes.containsAll(access.exportable()))
            throw new IllegalArgumentException("字段权限超出发布字段或写入超出可读范围");
    }
    private UnModule module(SystemManageService.SystemContext context,Long id) {
        var module=modules.queryById(id); Long root=systemRows.queryById(context.systemId()).getRootTenantId();
        if (!context.systemId().equals(module.getSystemId()) || !List.of(context.tenantId(),root).contains(module.getTenantId())) forbidden();
        if (!"PUBLISHED".equals(module.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"模块尚未发布");
        return module;
    }
    private void role(SystemManageService.SystemContext context,Long id) {
        var role=roles.queryById(id); if (!context.systemId().equals(role.getSystemId()) || !context.tenantId().equals(role.getTenantId())) forbidden();
    }
    private void department(SystemManageService.SystemContext context,Long id) {
        var dept=departments.queryById(id);
        if (!context.systemId().equals(dept.getSystemId()) || !context.tenantId().equals(dept.getTenantId()) || !"ACTIVE".equals(dept.getStatus()))
            throw new IllegalArgumentException("部门不是当前租户的有效部门");
    }
    /** 授权配置与系统操作日志同事务，不能只更新缓存版本而缺少实际操作者。 */
    private void audit(String token,SystemManageService.SystemContext context,String operation,String objectType,Long objectId,Object detail) {
        var log=new UnSysLogOpt(); log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,context.systemId())); log.setActionCode(operation); log.setObjectType(objectType);
        log.setObjectId(objectId); log.setDetailJson(write(detail)); auditLogs.addOrUpdate(log);
    }
    private void changed(SystemManageService.SystemContext context) {
        configs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,context.systemId())
                .setSql("permission_revision = permission_revision + 1"));
        cache.invalidateAfterCommit(namespace(context.systemId(),context.tenantId()));
    }
    private String namespace(Long systemId,Long tenantId) { return "permission:"+systemId+":"+tenantId; }
    private <T> Set<T> safe(Set<T> values) { return values==null?Set.of():values; }
    private void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"缺少当前系统租户的管理权限"); }
    private String write(Object value) { try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("权限配置无法保存",ex); } }
    private <T> T read(String value,Class<T> type) { try { return json.readValue(value,type); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("权限配置损坏",ex); } }
    @SuppressWarnings("unchecked")
    private Map<String,Object> readMap(String value) { return value==null?new LinkedHashMap<>():read(value,LinkedHashMap.class); }
}
