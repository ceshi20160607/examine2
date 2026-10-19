package com.unique.unexamine.manage.system;

import com.unique.unexamine.identity.base.entity.UnSysMember;
import com.unique.unexamine.identity.base.entity.UnSysMemberRole;
import com.unique.unexamine.identity.base.entity.UnSysRole;
import com.unique.unexamine.identity.base.entity.UnSysSystem;
import com.unique.unexamine.identity.base.entity.UnSysSystemConfig;
import com.unique.unexamine.identity.base.entity.UnSysTenant;
import com.unique.unexamine.identity.base.entity.UnSysTenantMember;
import com.unique.unexamine.identity.base.mapper.UnPlatMemberMapper;
import com.unique.unexamine.identity.base.mapper.UnSysMemberMapper;
import com.unique.unexamine.identity.base.mapper.UnSysMemberRoleMapper;
import com.unique.unexamine.identity.base.mapper.UnSysRoleMapper;
import com.unique.unexamine.identity.base.mapper.UnSysSystemConfigMapper;
import com.unique.unexamine.identity.base.mapper.UnSysSystemMapper;
import com.unique.unexamine.identity.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.identity.base.mapper.UnSysTenantMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 平台管理员建立业务系统、租户和首位管理员的入口。 */
@RestController
@RequestMapping("/api/platform")
public class SystemProvisionController {
    private final UnSysSystemMapper systems;
    private final UnPlatMemberMapper platformMembers;
    private final UnSysSystemConfigMapper configs;
    private final UnSysTenantMapper tenants;
    private final UnSysMemberMapper members;
    private final UnSysTenantMemberMapper tenantMembers;
    private final UnSysRoleMapper roles;
    private final UnSysMemberRoleMapper memberRoles;
    private final SessionAccess access;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SystemProvisionController(UnSysSystemMapper systems, UnPlatMemberMapper platformMembers,
                                     UnSysSystemConfigMapper configs,
                                     UnSysTenantMapper tenants, UnSysMemberMapper members,
                                     UnSysTenantMemberMapper tenantMembers, UnSysRoleMapper roles,
                                     UnSysMemberRoleMapper memberRoles, SessionAccess access) {
        this.systems = systems;
        this.platformMembers = platformMembers;
        this.configs = configs;
        this.tenants = tenants;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.roles = roles;
        this.memberRoles = memberRoles;
        this.access = access;
    }

    /** 业务系统列表仅暴露明确的排序列。 */
    @GetMapping("/systems")
    public IPage<UnSysSystem> systems(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(defaultValue = "createTime") String sort,
                                     HttpServletRequest request) {
        access.platformAdmin(request);
        LambdaQueryWrapper<UnSysSystem> query = new LambdaQueryWrapper<>();
        switch (sort) {
            case "systemCode" -> query.orderByAsc(UnSysSystem::getSystemCode);
            case "systemName" -> query.orderByAsc(UnSysSystem::getSystemName);
            case "createTime" -> query.orderByDesc(UnSysSystem::getCreateTime);
            default -> throw bad("不支持的系统排序字段");
        }
        return systems.selectPage(paging(page, size), query);
    }

    /** 平台创建系统，系统编码作为登录入口标识。 */
    @PostMapping("/systems")
    public UnSysSystem createSystem(@Valid @RequestBody SystemInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        ensureSystemCode(input.systemCode(), null);
        UnSysSystem system = new UnSysSystem();
        fillSystem(system, input);
        system.setStatus((byte) 1);
        systems.insert(system);
        return system;
    }

    /** 修改系统基本资料不改变其已有租户和成员。 */
    @PutMapping("/systems/{id}")
    public UnSysSystem updateSystem(@PathVariable long id, @Valid @RequestBody SystemInput input,
                                    HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysSystem system = system(id);
        ensureSystemCode(input.systemCode(), id);
        fillSystem(system, input);
        systems.updateById(system);
        return system;
    }

    /** 停用系统后登录和租户访问都应失效。 */
    @PutMapping("/systems/{id}/status")
    public UnSysSystem systemStatus(@PathVariable long id, @RequestBody StatusInput input,
                                    HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysSystem system = system(id);
        system.setStatus(status(input.status()));
        systems.updateById(system);
        return system;
    }

    /** 查询系统配置，不暴露其他业务系统的数据。 */
    @GetMapping("/systems/{systemId}/config")
    public java.util.List<UnSysSystemConfig> configs(@PathVariable long systemId, HttpServletRequest request) {
        access.platformAdmin(request);
        system(systemId);
        return configs.selectList(new LambdaQueryWrapper<UnSysSystemConfig>()
                .eq(UnSysSystemConfig::getSystemId, systemId).orderByAsc(UnSysSystemConfig::getConfigKey));
    }

    /** 按系统和配置键保存基础设置。 */
    @PutMapping("/systems/{systemId}/config/{key}")
    public UnSysSystemConfig saveConfig(@PathVariable long systemId, @PathVariable String key,
                                        @RequestBody ConfigInput input, HttpServletRequest request) {
        access.platformAdmin(request);
        system(systemId);
        if (key.isBlank() || key.length() > 100) throw bad("配置键无效");
        UnSysSystemConfig item = configs.selectOne(new LambdaQueryWrapper<UnSysSystemConfig>()
                .eq(UnSysSystemConfig::getSystemId, systemId).eq(UnSysSystemConfig::getConfigKey, key));
        if (item == null) {
            item = new UnSysSystemConfig();
            item.setSystemId(systemId);
            item.setConfigKey(key);
            item.setConfigValue(input.value());
            item.setDescription(input.description());
            configs.insert(item);
        } else {
            item.setConfigValue(input.value());
            item.setDescription(input.description());
            configs.updateById(item);
        }
        return item;
    }

    /** 在指定系统内查询租户，不能跨系统复用租户编码。 */
    @GetMapping("/systems/{systemId}/tenants")
    public IPage<UnSysTenant> tenants(@PathVariable long systemId,
                                     @RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(defaultValue = "createTime") String sort,
                                     HttpServletRequest request) {
        access.platformAdmin(request);
        system(systemId);
        LambdaQueryWrapper<UnSysTenant> query = new LambdaQueryWrapper<UnSysTenant>()
                .eq(UnSysTenant::getSystemId, systemId);
        switch (sort) {
            case "tenantCode" -> query.orderByAsc(UnSysTenant::getTenantCode);
            case "tenantName" -> query.orderByAsc(UnSysTenant::getTenantName);
            case "createTime" -> query.orderByDesc(UnSysTenant::getCreateTime);
            default -> throw bad("不支持的租户排序字段");
        }
        return tenants.selectPage(paging(page, size), query);
    }

    /** 创建租户时建立其管理员角色，首位加入的成员将被授予该角色。 */
    @PostMapping("/systems/{systemId}/tenants")
    @Transactional
    public UnSysTenant createTenant(@PathVariable long systemId, @Valid @RequestBody TenantInput input,
                                    HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysSystem system = system(systemId);
        if (!Byte.valueOf((byte) 1).equals(system.getStatus())) throw bad("系统已停用");
        ensureTenantCode(systemId, input.tenantCode(), null);
        UnSysTenant tenant = new UnSysTenant();
        tenant.setSystemId(systemId);
        fillTenant(tenant, input);
        tenant.setStatus((byte) 1);
        tenants.insert(tenant);
        UnSysRole admin = new UnSysRole();
        admin.setTenantId(tenant.getId());
        admin.setRoleCode("tenant_admin");
        admin.setRoleName("租户管理员");
        admin.setRoleType((byte) 0);
        admin.setStatus((byte) 1);
        roles.insert(admin);
        return tenant;
    }

    /** 编辑租户时验证编码在所属系统内唯一。 */
    @PutMapping("/tenants/{id}")
    public UnSysTenant updateTenant(@PathVariable long id, @Valid @RequestBody TenantInput input,
                                    HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysTenant tenant = tenant(id);
        ensureTenantCode(tenant.getSystemId(), input.tenantCode(), id);
        fillTenant(tenant, input);
        tenants.updateById(tenant);
        return tenant;
    }

    /** 停用租户保留既有成员和授权，访问校验应立即拒绝。 */
    @PutMapping("/tenants/{id}/status")
    public UnSysTenant tenantStatus(@PathVariable long id, @RequestBody StatusInput input,
                                    HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysTenant tenant = tenant(id);
        tenant.setStatus(status(input.status()));
        tenants.updateById(tenant);
        return tenant;
    }

    /** 系统成员列表不返回密码摘要。 */
    @GetMapping("/systems/{systemId}/members")
    public IPage<MemberView> members(@PathVariable long systemId,
                                     @RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(defaultValue = "createTime") String sort,
                                     HttpServletRequest request) {
        access.platformAdmin(request);
        system(systemId);
        LambdaQueryWrapper<UnSysMember> query = new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId);
        switch (sort) {
            case "account" -> query.orderByAsc(UnSysMember::getAccount);
            case "displayName" -> query.orderByAsc(UnSysMember::getDisplayName);
            case "createTime" -> query.orderByDesc(UnSysMember::getCreateTime);
            default -> throw bad("不支持的系统成员排序字段");
        }
        return members.selectPage(paging(page, size), query).convert(this::memberView);
    }

    /** 建立可登录的系统成员，密码只保存 BCrypt 摘要。 */
    @PostMapping("/systems/{systemId}/members")
    public MemberView createMember(@PathVariable long systemId, @Valid @RequestBody MemberInput input,
                                   HttpServletRequest request) {
        access.platformAdmin(request);
        system(systemId);
        if (input.password().length() < 12) throw bad("密码至少12位");
        ensureMemberAccount(systemId, input.account(), null);
        UnSysMember member = new UnSysMember();
        member.setSystemId(systemId);
        fillMember(member, input);
        member.setPasswordHash(encoder.encode(input.password()));
        member.setStatus((byte) 1);
        members.insert(member);
        return memberView(member);
    }

    /** 修改成员公开资料，不允许通过请求覆盖系统归属或密码摘要。 */
    @PutMapping("/system-members/{id}")
    public MemberView updateMember(@PathVariable long id, @Valid @RequestBody MemberProfileInput input,
                                   HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysMember member = member(id);
        member.setDisplayName(input.displayName().trim());
        member.setRealName(input.realName());
        member.setMobile(input.mobile());
        member.setEmail(input.email());
        members.updateById(member);
        return memberView(member);
    }

    /** 系统成员停用后，其所有租户身份随会话检查失效。 */
    @PutMapping("/system-members/{id}/status")
    public MemberView memberStatus(@PathVariable long id, @RequestBody StatusInput input,
                                   HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysMember member = member(id);
        member.setStatus(status(input.status()));
        members.updateById(member);
        return memberView(member);
    }

    /** 平台管理员重置系统成员密码。 */
    @PutMapping("/system-members/{id}/password")
    public void resetPassword(@PathVariable long id, @Valid @RequestBody PasswordInput input,
                              HttpServletRequest request) {
        access.platformAdmin(request);
        if (input.password().length() < 12) throw bad("密码至少12位");
        UnSysMember member = member(id);
        member.setPasswordHash(encoder.encode(input.password()));
        members.updateById(member);
    }

    /** 租户成员列表必须以租户主键限定。 */
    @GetMapping("/tenants/{tenantId}/members")
    public IPage<UnSysTenantMember> tenantMembers(@PathVariable long tenantId,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "20") long size,
                                                  @RequestParam(defaultValue = "createTime") String sort,
                                                  HttpServletRequest request) {
        access.platformAdmin(request);
        tenant(tenantId);
        LambdaQueryWrapper<UnSysTenantMember> query = new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId);
        switch (sort) {
            case "memberNo" -> query.orderByAsc(UnSysTenantMember::getMemberNo);
            case "joinTime" -> query.orderByDesc(UnSysTenantMember::getJoinTime);
            case "createTime" -> query.orderByDesc(UnSysTenantMember::getCreateTime);
            default -> throw bad("不支持的租户成员排序字段");
        }
        return tenantMembers.selectPage(paging(page, size), query);
    }

    /** 只允许同一系统成员加入租户；首位成员获租户管理员角色。 */
    @PostMapping("/tenants/{tenantId}/members")
    @Transactional
    public UnSysTenantMember addTenantMember(@PathVariable long tenantId, @Valid @RequestBody TenantMemberInput input,
                                             HttpServletRequest request) {
        access.platformAdmin(request);
        UnSysTenant tenant = tenant(tenantId);
        UnSysMember member = member(input.memberId());
        if (!tenant.getSystemId().equals(member.getSystemId())) throw bad("成员与租户不属于同一系统");
        if (!Byte.valueOf((byte) 1).equals(tenant.getStatus())
                || !Byte.valueOf((byte) 1).equals(member.getStatus())) throw bad("租户或成员已停用");
        if (tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberId, member.getId())) > 0)
            throw conflict("成员已加入该租户");
        if (input.memberNo() != null && !input.memberNo().isBlank()
                && tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberNo, input.memberNo())) > 0)
            throw conflict("租户成员编号已存在");
        Long count = tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId));
        UnSysTenantMember link = new UnSysTenantMember();
        link.setTenantId(tenantId);
        link.setMemberId(member.getId());
        link.setMemberNo(input.memberNo());
        link.setPositionName(input.positionName());
        link.setStatus((byte) 1);
        tenantMembers.insert(link);
        if (count == 0) {
            UnSysRole admin = roles.selectOne(new LambdaQueryWrapper<UnSysRole>()
                    .eq(UnSysRole::getTenantId, tenantId).eq(UnSysRole::getRoleCode, "tenant_admin"));
            if (admin == null) throw new IllegalStateException("租户管理员角色缺失");
            UnSysMemberRole grant = new UnSysMemberRole();
            grant.setTenantMemberId(link.getId());
            grant.setRoleId(admin.getId());
            memberRoles.insert(grant);
        }
        return link;
    }

    private void fillSystem(UnSysSystem item, SystemInput input) {
        if (input.ownerPlatMemberId() != null && platformMembers.selectById(input.ownerPlatMemberId()) == null)
            throw bad("系统负责人不是有效的平台成员");
        item.setSystemCode(input.systemCode().trim());
        item.setSystemName(input.systemName().trim());
        item.setDescription(input.description());
        item.setOwnerPlatMemberId(input.ownerPlatMemberId());
    }

    private void fillTenant(UnSysTenant item, TenantInput input) {
        item.setTenantCode(input.tenantCode().trim());
        item.setTenantName(input.tenantName().trim());
        item.setContactName(input.contactName());
        item.setContactMobile(input.contactMobile());
    }

    private void fillMember(UnSysMember item, MemberInput input) {
        item.setAccount(input.account().trim());
        item.setDisplayName(input.displayName().trim());
        item.setRealName(input.realName());
        item.setMobile(input.mobile());
        item.setEmail(input.email());
    }

    private void ensureSystemCode(String code, Long exceptId) {
        LambdaQueryWrapper<UnSysSystem> query = new LambdaQueryWrapper<UnSysSystem>()
                .eq(UnSysSystem::getSystemCode, code.trim());
        if (exceptId != null) query.ne(UnSysSystem::getId, exceptId);
        if (systems.selectCount(query) > 0) throw conflict("系统编码已存在");
    }

    private void ensureTenantCode(long systemId, String code, Long exceptId) {
        LambdaQueryWrapper<UnSysTenant> query = new LambdaQueryWrapper<UnSysTenant>()
                .eq(UnSysTenant::getSystemId, systemId).eq(UnSysTenant::getTenantCode, code.trim());
        if (exceptId != null) query.ne(UnSysTenant::getId, exceptId);
        if (tenants.selectCount(query) > 0) throw conflict("租户编码已存在");
    }

    private void ensureMemberAccount(long systemId, String account, Long exceptId) {
        LambdaQueryWrapper<UnSysMember> query = new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId).eq(UnSysMember::getAccount, account.trim());
        if (exceptId != null) query.ne(UnSysMember::getId, exceptId);
        if (members.selectCount(query) > 0) throw conflict("系统登录账号已存在");
    }

    private UnSysSystem system(long id) {
        UnSysSystem item = systems.selectById(id);
        if (item == null) throw missing("系统不存在");
        return item;
    }

    private UnSysTenant tenant(long id) {
        UnSysTenant item = tenants.selectById(id);
        if (item == null) throw missing("租户不存在");
        return item;
    }

    private UnSysMember member(long id) {
        UnSysMember item = members.selectById(id);
        if (item == null) throw missing("系统成员不存在");
        return item;
    }

    private MemberView memberView(UnSysMember item) {
        return new MemberView(item.getId(), item.getSystemId(), item.getAccount(), item.getDisplayName(),
                item.getRealName(), item.getMobile(), item.getEmail(), item.getStatus());
    }

    private byte status(byte value) {
        if (value != 0 && value != 1) throw bad("状态只能是0或1");
        return value;
    }

    private <T> Page<T> paging(long page, long size) {
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        return new Page<>(page, size);
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
    private ResponseStatusException missing(String text) { return new ResponseStatusException(HttpStatus.NOT_FOUND, text); }

    /** 系统资料输入不能指定主键和状态。 */
    public record SystemInput(@NotBlank @Size(max = 64) String systemCode,
                              @NotBlank @Size(max = 100) String systemName,
                              String description, Long ownerPlatMemberId) {}
    public record TenantInput(@NotBlank @Size(max = 64) String tenantCode,
                              @NotBlank @Size(max = 100) String tenantName,
                              String contactName, String contactMobile) {}
    public record MemberInput(@NotBlank @Size(max = 64) String account, @NotBlank String password,
                              @NotBlank @Size(max = 100) String displayName,
                              String realName, String mobile, String email) {}
    public record MemberProfileInput(@NotBlank @Size(max = 100) String displayName,
                                     String realName, String mobile, String email) {}
    public record TenantMemberInput(long memberId, String memberNo, String positionName) {}
    public record MemberView(Long id, Long systemId, String account, String displayName,
                             String realName, String mobile, String email, Byte status) {}
    public record StatusInput(byte status) {}
    public record PasswordInput(@NotBlank String password) {}
    public record ConfigInput(String value, String description) {}
}
