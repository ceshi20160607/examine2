package com.unique.unexamine.sys.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.manage.AuthManageService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysRoleMenu;
import com.unique.unexamine.sys.base.entity.UnSysSystem;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberRoleService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysMenuService;
import com.unique.unexamine.sys.base.service.IUnSysRoleMenuService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 系统创建、主租户映射及租户模式的业务编排，所有贴表写入复用 base Service。 */
@Service
public class SystemManageService {
    private final AuthManageService auth;
    private final IUnPlatAccountService accounts;
    private final IUnSysSystemService systems;
    private final IUnSysSystemConfigService configs;
    private final IUnSysTenantService tenants;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysRoleService roles;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysMenuService menus;
    private final IUnSysRoleMenuService roleMenus;
    private final IUnSysLogOptService logs;

    /** 创建系统所需的用户可见资料。 */
    public record CreateSystemRequest(@NotBlank @Size(max = 80) String code,
                                      @NotBlank @Size(max = 100) String name,
                                      @Size(max = 500) String description) {}

    /** 多租户模式配置，false 只暂停非主租户访问。 */
    public record TenantModeRequest(@NotNull Boolean enabled) {}

    /** 创建租户所需的基本资料。 */
    public record CreateTenantRequest(@NotBlank @Size(max = 80) String code,
                                      @NotBlank @Size(max = 100) String name) {}

    /** 系统列表与创建结果。 */
    public record SystemView(Long id, String code, String name, Long rootTenantId,
                             boolean multiTenantEnabled, boolean owner) {}

    /** 可进入的租户展示信息。 */
    public record TenantView(Long id, String code, String name, boolean root) {}

    /** 进入系统后重建的系统、租户和成员上下文。 */
    public record SystemContext(Long systemId, Long tenantId, Long memberId,
                                Long tenantMemberId, boolean owner, boolean tenantAdmin) {}

    /** 注入本业务链使用的已生成基础服务。 */
    public SystemManageService(AuthManageService auth, IUnPlatAccountService accounts,
            IUnSysSystemService systems, IUnSysSystemConfigService configs,
            IUnSysTenantService tenants, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnSysRoleService roles,
            IUnSysMemberRoleService memberRoles, IUnSysMenuService menus,
            IUnSysRoleMenuService roleMenus, IUnSysLogOptService logs) {
        this.auth = auth;
        this.accounts = accounts;
        this.systems = systems;
        this.configs = configs;
        this.tenants = tenants;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.roles = roles;
        this.memberRoles = memberRoles;
        this.menus = menus;
        this.roleMenus = roleMenus;
        this.logs = logs;
    }

    /** 创建系统时同步建立主租户、显式成员关系及初始后台角色菜单。 */
    @Transactional(rollbackFor = Exception.class)
    public SystemView create(String token, CreateSystemRequest request) {
        UnPlatAccount actor = auth.requireAccount(token);
        String code = normalizeCode(request.code());
        if (systems.count(new LambdaQueryWrapper<UnSysSystem>()
                .eq(UnSysSystem::getCode, code)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统编码已存在");
        }
        UnSysSystem system = new UnSysSystem();
        system.setCode(code);
        system.setName(request.name().trim());
        system.setDescription(request.description());
        system.setOwnerAccountId(actor.getId());
        system.setCreateAccountId(actor.getId());
        system.setStatus("ACTIVE");
        systems.addOrUpdate(system);

        UnSysTenant root = new UnSysTenant();
        root.setSystemId(system.getId());
        root.setCode("root");
        root.setName("主租户");
        root.setIsRoot(true);
        root.setStatus("ACTIVE");
        root.setCreateAccountId(actor.getId());
        tenants.addOrUpdate(root);
        system.setRootTenantId(root.getId());
        systems.addOrUpdate(system);

        UnSysSystemConfig config = new UnSysSystemConfig();
        config.setSystemId(system.getId());
        config.setMultiTenantEnabled(false);
        config.setUpdateAccountId(actor.getId());
        configs.addOrUpdate(config);

        UnSysMember member = new UnSysMember();
        member.setSystemId(system.getId());
        member.setAccountId(actor.getId());
        member.setDisplayName(actor.getDisplayName());
        member.setStatus("ACTIVE");
        members.addOrUpdate(member);
        linkTenantMember(system.getId(), root.getId(), member.getId(), true);

        UnSysRole adminRole = new UnSysRole();
        adminRole.setSystemId(system.getId());
        adminRole.setTenantId(root.getId());
        adminRole.setCode("SYSTEM_ADMIN");
        adminRole.setName("系统管理员");
        adminRole.setStatus("ACTIVE");
        roles.addOrUpdate(adminRole);
        UnSysMemberRole membership = new UnSysMemberRole();
        UnSysTenantMember tenantMember = tenantMember(root.getId(), member.getId());
        membership.setTenantMemberId(tenantMember.getId());
        membership.setRoleId(adminRole.getId());
        memberRoles.addOrUpdate(membership);
        for (String codeName : List.of("SYSTEM_BACKEND", "SYSTEM_MODULES")) {
            UnSysMenu menu = new UnSysMenu();
            menu.setSystemId(system.getId());
            menu.setTenantId(root.getId());
            menu.setCode(codeName);
            menu.setName("SYSTEM_BACKEND".equals(codeName) ? "系统后台" : "业务模块");
            menu.setStatus("ACTIVE");
            menus.addOrUpdate(menu);
            UnSysRoleMenu grant = new UnSysRoleMenu();
            grant.setRoleId(adminRole.getId());
            grant.setMenuId(menu.getId());
            grant.setActionCode("VIEW");
            roleMenus.addOrUpdate(grant);
        }
        if (actor.getDefaultSystemId() == null) {
            actor.setDefaultSystemId(system.getId());
            accounts.addOrUpdate(actor);
        }
        audit(system.getId(), root.getId(), actor.getId(), "SYSTEM_CREATE", system.getId(), null);
        return view(system, false, true);
    }

    /** 账号可见系统来自显式系统成员关系；系统所属人权限单独标记。 */
    public List<SystemView> list(String token) {
        UnPlatAccount actor = auth.requireAccount(token);
        return members.list(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getAccountId, actor.getId())
                .eq(UnSysMember::getStatus, "ACTIVE")).stream()
                .map(member -> systems.getById(member.getSystemId()))
                .filter(system -> system != null && "ACTIVE".equals(system.getStatus()))
                .map(system -> view(system, mode(system.getId()),
                        actor.getId().equals(system.getOwnerAccountId())))
                .toList();
    }

    /** 当前租户的进入权限由显式租户成员映射和多租户开关共同决定。 */
    public SystemContext context(String token, Long systemId, Long requestedTenantId) {
        UnPlatAccount actor = auth.requireAccount(token);
        UnSysSystem system = activeSystem(systemId);
        UnSysMember member = member(systemId, actor.getId());
        Long tenantId = requestedTenantId == null ? system.getRootTenantId() : requestedTenantId;
        if (!mode(systemId) && !tenantId.equals(system.getRootTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "单租户模式只能进入主租户");
        }
        UnSysTenant tenant = tenants.queryById(tenantId);
        if (!systemId.equals(tenant.getSystemId()) || !"ACTIVE".equals(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "租户不可进入");
        }
        UnSysTenantMember link = tenantMember(tenantId, member.getId());
        return new SystemContext(systemId, tenantId, member.getId(), link.getId(),
                actor.getId().equals(system.getOwnerAccountId()), Boolean.TRUE.equals(link.getIsAdmin()));
    }

    /** 只列出当前账号可进入且当前模式允许显示的租户。 */
    public List<TenantView> tenants(String token, Long systemId) {
        UnPlatAccount actor = auth.requireAccount(token);
        UnSysSystem system = activeSystem(systemId);
        UnSysMember member = member(systemId, actor.getId());
        boolean multi = mode(systemId);
        return tenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE")).stream()
                .map(link -> tenants.getById(link.getTenantId()))
                .filter(tenant -> tenant != null && systemId.equals(tenant.getSystemId())
                        && "ACTIVE".equals(tenant.getStatus())
                        && (multi || tenant.getId().equals(system.getRootTenantId())))
                .map(tenant -> new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(),
                        Boolean.TRUE.equals(tenant.getIsRoot())))
                .toList();
    }

    /** 系统管理员直接切换租户模式；关闭仅改变生效范围，保留租户和业务数据。 */
    @Transactional(rollbackFor = Exception.class)
    public SystemView setTenantMode(String token, Long systemId, TenantModeRequest request) {
        UnPlatAccount actor = auth.requireAccount(token);
        UnSysSystem system = activeSystem(systemId);
        requireSystemAdmin(system, actor.getId());
        UnSysSystemConfig config = config(systemId);
        boolean before = Boolean.TRUE.equals(config.getMultiTenantEnabled());
        config.setMultiTenantEnabled(request.enabled());
        config.setUpdateAccountId(actor.getId());
        configs.addOrUpdate(config);
        audit(systemId, system.getRootTenantId(), actor.getId(), "TENANT_MODE_CHANGE", systemId,
                "{\"before\":" + before + ",\"after\":" + request.enabled() + "}");
        return view(system, request.enabled(), actor.getId().equals(system.getOwnerAccountId()));
    }

    /** 多租户开启后创建租户并把系统所属人显式加入该租户。 */
    @Transactional(rollbackFor = Exception.class)
    public TenantView createTenant(String token, Long systemId, CreateTenantRequest request) {
        UnPlatAccount actor = auth.requireAccount(token);
        UnSysSystem system = activeSystem(systemId);
        requireSystemAdmin(system, actor.getId());
        if (!mode(systemId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先开启多租户模式");
        }
        String code = normalizeCode(request.code());
        if ("root".equals(code) || tenants.count(new LambdaQueryWrapper<UnSysTenant>()
                .eq(UnSysTenant::getSystemId, systemId).eq(UnSysTenant::getCode, code)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "租户编码已存在");
        }
        UnSysTenant tenant = new UnSysTenant();
        tenant.setSystemId(systemId);
        tenant.setCode(code);
        tenant.setName(request.name().trim());
        tenant.setIsRoot(false);
        tenant.setStatus("ACTIVE");
        tenant.setCreateAccountId(actor.getId());
        tenants.addOrUpdate(tenant);
        UnSysMember owner = member(systemId, system.getOwnerAccountId());
        linkTenantMember(systemId, tenant.getId(), owner.getId(), true);
        audit(systemId, tenant.getId(), actor.getId(), "TENANT_CREATE", tenant.getId(), null);
        return new TenantView(tenant.getId(), tenant.getCode(), tenant.getName(), false);
    }

    /** 系统拥有者始终可管理；后续其他管理员以当前根租户管理授权判断。 */
    private void requireSystemAdmin(UnSysSystem system, Long accountId) {
        if (accountId.equals(system.getOwnerAccountId())) {
            return;
        }
        UnSysMember member = member(system.getId(), accountId);
        UnSysTenantMember rootMember = tenantMember(system.getRootTenantId(), member.getId());
        if (!Boolean.TRUE.equals(rootMember.getIsAdmin())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少系统管理权限");
        }
    }

    /** 校验系统状态和所属范围。 */
    private UnSysSystem activeSystem(Long systemId) {
        UnSysSystem system = systems.queryById(systemId);
        if (!"ACTIVE".equals(system.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "系统已停用");
        }
        return system;
    }

    /** 系统成员身份不能由平台账号直接替代。 */
    private UnSysMember member(Long systemId, Long accountId) {
        UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId)
                .eq(UnSysMember::getAccountId, accountId), false);
        if (member == null || !"ACTIVE".equals(member.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不是有效系统成员");
        }
        return member;
    }

    /** 租户成员关系必须显式存在且有效。 */
    private UnSysTenantMember tenantMember(Long tenantId, Long memberId) {
        UnSysTenantMember link = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, memberId), false);
        if (link == null || !"ACTIVE".equals(link.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不是有效租户成员");
        }
        return link;
    }

    /** 建立租户成员关系并记录管理身份。 */
    private void linkTenantMember(Long systemId, Long tenantId, Long memberId, boolean admin) {
        UnSysTenantMember link = new UnSysTenantMember();
        link.setSystemId(systemId);
        link.setTenantId(tenantId);
        link.setMemberId(memberId);
        link.setIsAdmin(admin);
        link.setStatus("ACTIVE");
        tenantMembers.addOrUpdate(link);
    }

    /** 查询系统唯一配置。 */
    private UnSysSystemConfig config(Long systemId) {
        UnSysSystemConfig config = configs.getOne(new LambdaQueryWrapper<UnSysSystemConfig>()
                .eq(UnSysSystemConfig::getSystemId, systemId), false);
        if (config == null) {
            throw new IllegalStateException("系统配置缺失");
        }
        return config;
    }

    /** 判断当前多租户模式。 */
    private boolean mode(Long systemId) {
        return Boolean.TRUE.equals(config(systemId).getMultiTenantEnabled());
    }

    /** 系统创建与租户配置变化必须留操作审计。 */
    private void audit(Long systemId, Long tenantId, Long actorId, String action, Long objectId,
                       String detailJson) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(systemId);
        log.setTenantId(tenantId);
        log.setAccountId(actorId);
        log.setActionCode(action);
        log.setObjectType("TENANT_CREATE".equals(action) ? "TENANT" : "SYSTEM");
        log.setObjectId(objectId);
        log.setDetailJson(detailJson);
        logs.addOrUpdate(log);
    }

    /** 系统编码用于登录入口，统一小写并限制可用字符。 */
    private String normalizeCode(String input) {
        String code = input.trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9-]{2,79}")) {
            throw new IllegalArgumentException("编码须以字母开头，使用字母、数字或横线");
        }
        return code;
    }

    /** 对外只返回系统当前状态和权限标记。 */
    private SystemView view(UnSysSystem system, boolean multi, boolean owner) {
        return new SystemView(system.getId(), system.getCode(), system.getName(),
                system.getRootTenantId(), multi, owner);
    }
}
