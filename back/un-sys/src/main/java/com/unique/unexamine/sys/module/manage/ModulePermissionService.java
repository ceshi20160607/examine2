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
    private static final Set<String> ACTIONS=Set.of("VIEW","CREATE","EDIT","DELETE","TRANSFER","CONVERT","IMPORT","EXPORT","PRINT");
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
    private final IUnSysMenuService menus;
    private final IUnSysRoleMenuService roleMenus;
    private final IUnSysRoleDataService roleData;
    private final IUnModuleService modules;
    private final IUnModuleFieldService fields;
    private final IUnModuleTenantService tenantModules;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    /** null 字段集合表示全部，空集合表示没有；多个角色按正向授权并集。 */
    public record FieldAccess(Set<String> readable,Set<String> writable) {}
    /** 人员与部门使用明确的业务主键，不能写任意 SQL 条件。 */
    public record GrantRequest(boolean allowed,@NotBlank String scope,Set<Long> accountIds,
                               Set<Long> departmentIds,@NotNull @Valid FieldAccess fields) {}
    /** 角色规则的发布缓存载荷，不缓存业务记录或人员当前部门。 */
    public record Grant(String scope,Set<Long> accountIds,Set<Long> departmentIds,FieldAccess fields) {}
    /** 已合并管理上下文的规则及租户字段硬上限。 */
    public record Rules(List<Grant> grants,FieldAccess tenantFields) {}
    /** 一次鉴权得到的上下文和结果，业务端复用后过滤查询、布局及保存值。 */
    public record Access(SystemManageService.SystemContext context,Long accountId,PermissionPolicy policy) {}
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
            IUnSysMemberRoleService memberRoles,IUnSysMenuService menus,IUnSysRoleMenuService roleMenus,
            IUnSysRoleDataService roleData,IUnModuleService modules,IUnModuleFieldService fields,
            IUnModuleTenantService tenantModules,ConfigurationCache cache,ObjectMapper json) {
        this.systems=systems; this.systemRows=systemRows; this.configs=configs; this.accounts=accounts;
        this.members=members; this.tenantMembers=tenantMembers; this.departments=departments; this.roles=roles;
        this.memberRoles=memberRoles; this.menus=menus; this.roleMenus=roleMenus; this.roleData=roleData;
        this.modules=modules; this.fields=fields; this.tenantModules=tenantModules; this.cache=cache; this.json=json;
    }

    /** 登录、系统/租户/成员状态每次查验；权限缓存键同时携带数据库提交版本。 */
    public Access require(String token,Long systemId,Long tenantId,Long moduleId,String action) {
        var context=systems.context(token,systemId,tenantId);
        Long actor=systems.accountId(token,systemId);
        module(context,moduleId);
        if (!ACTIONS.contains(action)) throw new IllegalArgumentException("未知模块动作");
        var config=configs.getOne(new LambdaQueryWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,systemId));
        String key=config.getPermissionRevision()+":"+context.tenantMemberId()+":"+moduleId+":"+action;
        Rules rules=cache.get(namespace(systemId,context.tenantId()),key,Rules.class,()->load(context,moduleId,action));
        boolean admin=context.owner() || context.tenantAdmin();
        Set<String> readable=new HashSet<>(),writable=new HashSet<>(); Set<Long> owners=new HashSet<>();
        boolean allRead=admin,allWrite=admin,allData=admin;
        for (Grant grant:rules.grants()) {
            allData|="ALL".equals(grant.scope());
            owners.addAll(ownerIds(context,actor,grant));
            if (grant.fields().readable()==null) allRead=true; else readable.addAll(grant.fields().readable());
            if (grant.fields().writable()==null) allWrite=true; else writable.addAll(grant.fields().writable());
        }
        // 租户字段许可是角色并集之外的硬上限；系统所属人仍须遵守使用租户的字段限制。
        if (rules.tenantFields().readable()!=null) {
            if (allRead) { readable.clear(); readable.addAll(rules.tenantFields().readable()); }
            else readable.retainAll(rules.tenantFields().readable());
            allRead=false;
        }
        if (rules.tenantFields().writable()!=null) {
            if (allWrite) { writable.clear(); writable.addAll(rules.tenantFields().writable()); }
            else writable.retainAll(rules.tenantFields().writable());
            allWrite=false;
        }
        PermissionPolicy policy=new PermissionPolicy(admin || !rules.grants().isEmpty(),allData,owners,allRead,readable,allWrite,writable);
        policy.requireAction();
        return new Access(context,actor,policy);
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
        link.setDepartmentId(request.departmentId()); tenantMembers.addOrUpdate(link); changed(context);
        return link.getId();
    }

    /** 当前租户创建普通角色；角色名称不能提升为所属人或管理员身份。 */
    @Transactional(rollbackFor=Exception.class)
    public Long createRole(String token,Long systemId,Long tenantId,RoleRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        if (!request.code().matches("[a-zA-Z][a-zA-Z0-9_]{1,79}")) throw new IllegalArgumentException("角色编码无效");
        UnSysRole role=new UnSysRole(); role.setSystemId(systemId); role.setTenantId(context.tenantId());
        role.setCode(request.code()); role.setName(request.name().trim()); role.setStatus("ACTIVE");
        roles.addOrUpdate(role); changed(context); return role.getId();
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
        changed(context);
    }

    /** 授权包含菜单动作、负责人范围及字段规则；false 撤销该角色对应动作。 */
    @Transactional(rollbackFor=Exception.class)
    public void grant(String token,Long systemId,Long tenantId,Long roleId,Long moduleId,String action,GrantRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId); var module=module(context,moduleId); role(context,roleId);
        if (!ACTIONS.contains(action) || !SCOPES.contains(request.scope())) throw new IllegalArgumentException("动作或数据范围无效");
        validateFields(moduleId,request.fields());
        for (Long id:safe(request.departmentIds())) department(context,id);
        Set<Long> active=activeOwners(context,null);
        if (!active.containsAll(safe(request.accountIds()))) throw new IllegalArgumentException("指定人员不是当前有效租户成员");
        UnSysMenu menu=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,context.tenantId())
                .eq(UnSysMenu::getCode,"MODULE_"+moduleId));
        if (menu==null) { menu=new UnSysMenu(); menu.setSystemId(systemId); menu.setTenantId(context.tenantId());
            menu.setCode("MODULE_"+moduleId); menu.setName(module.getName()); menu.setStatus("ACTIVE"); menus.addOrUpdate(menu); }
        var dataQuery=new LambdaQueryWrapper<UnSysRoleData>().eq(UnSysRoleData::getRoleId,roleId)
                .eq(UnSysRoleData::getModuleId,moduleId).eq(UnSysRoleData::getActionCode,action);
        var menuQuery=new LambdaQueryWrapper<UnSysRoleMenu>().eq(UnSysRoleMenu::getRoleId,roleId)
                .eq(UnSysRoleMenu::getMenuId,menu.getId()).eq(UnSysRoleMenu::getActionCode,action);
        if (!request.allowed()) { roleData.remove(dataQuery); roleMenus.remove(menuQuery); }
        else {
            UnSysRoleData row=roleData.getOne(dataQuery); if (row==null) row=new UnSysRoleData();
            row.setRoleId(roleId); row.setModuleId(moduleId); row.setActionCode(action); row.setScopeCode(request.scope());
            row.setScopeConfig(write(new Grant(request.scope(),safe(request.accountIds()),safe(request.departmentIds()),request.fields())));
            roleData.addOrUpdate(row);
            if (roleMenus.count(menuQuery)==0) { UnSysRoleMenu link=new UnSysRoleMenu(); link.setRoleId(roleId);
                link.setMenuId(menu.getId()); link.setActionCode(action); roleMenus.addOrUpdate(link); }
        }
        changed(context);
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
        row.setConfigJson(write(config)); tenantModules.addOrUpdate(row); changed(context);
        cache.invalidateAfterCommit(namespace(systemId,targetTenantId));
    }

    private Rules load(SystemManageService.SystemContext context,Long moduleId,String action) {
        var tenant=tenantModules.getOne(new LambdaQueryWrapper<UnModuleTenant>().eq(UnModuleTenant::getTenantId,context.tenantId())
                .eq(UnModuleTenant::getModuleId,moduleId));
        if (tenant!=null && !"ACTIVE".equals(tenant.getStatus())) forbidden();
        Object configured=tenant==null?null:readMap(tenant.getConfigJson()).get("fieldPolicy");
        FieldAccess cap=configured==null?new FieldAccess(null,null):json.convertValue(configured,FieldAccess.class);
        List<Grant> grants=new ArrayList<>();
        var menu=menus.getOne(new LambdaQueryWrapper<UnSysMenu>().eq(UnSysMenu::getTenantId,context.tenantId())
                .eq(UnSysMenu::getCode,"MODULE_"+moduleId).eq(UnSysMenu::getStatus,"ACTIVE"));
        if (menu==null) return new Rules(grants,cap);
        for (var link:memberRoles.list(new LambdaQueryWrapper<UnSysMemberRole>().eq(UnSysMemberRole::getTenantMemberId,context.tenantMemberId()))) {
            var role=roles.getById(link.getRoleId());
            if (role==null || !context.systemId().equals(role.getSystemId()) || !context.tenantId().equals(role.getTenantId()) || !"ACTIVE".equals(role.getStatus())) continue;
            if (roleMenus.count(new LambdaQueryWrapper<UnSysRoleMenu>().eq(UnSysRoleMenu::getRoleId,role.getId())
                    .eq(UnSysRoleMenu::getMenuId,menu.getId()).eq(UnSysRoleMenu::getActionCode,action))==0) continue;
            var row=roleData.getOne(new LambdaQueryWrapper<UnSysRoleData>().eq(UnSysRoleData::getRoleId,role.getId())
                    .eq(UnSysRoleData::getModuleId,moduleId).eq(UnSysRoleData::getActionCode,action));
            if (row!=null) grants.add(read(row.getScopeConfig(),Grant.class));
        }
        return new Rules(grants,cap);
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
                || access.readable()!=null && (access.writable()==null || !access.readable().containsAll(access.writable())))
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
