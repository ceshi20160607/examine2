package com.unique.unexamine.platform.manage.authorization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformMemberRole;
import com.unique.unexamine.platform.base.entity.PlatformRole;
import com.unique.unexamine.platform.base.entity.PlatformRolePermission;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRolePermissionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 平台角色只授予平台资源；系统及租户角色仍由各自后台管理。 */
@Service
public class PlatformAuthorizationService {
    /** 页面呈现业务能力，服务端仅接受这些已知动作，避免提交任意通配授权。 */
    private static final List<Capability> CAPABILITIES = List.of(
            new Capability("PLATFORM:ORGANIZATION:VIEW", "查看平台组织"),
            new Capability("PLATFORM:ORGANIZATION:MANAGE", "维护平台组织"),
            new Capability("PLATFORM:AUTHORIZATION:VIEW", "查看平台角色"),
            new Capability("PLATFORM:AUTHORIZATION:MANAGE", "维护平台角色"),
            new Capability("PLATFORM:AUDIT:VIEW", "查看平台审计"),
            new Capability("PLATFORM:AUDIT:VIEW_SENSITIVE", "查看敏感审计详情"),
            new Capability("PLATFORM:CONFIGURATION:MANAGE", "管理平台配置"),
            new Capability("PLATFORM:IDENTITY_PROVIDER:MANAGE", "管理统一认证"),
            new Capability("PLATFORM:AI:MANAGE", "管理平台智能助手"),
            new Capability("APPLICATION:PLATFORM:VIEW", "查看平台应用"),
            new Capability("APPLICATION:PLATFORM:MANAGE", "管理平台应用"),
            new Capability("FLOW:PLATFORM:DESIGN", "设计平台流程"),
            new Capability("FLOW:PLATFORM:PUBLISH", "发布平台流程"),
            new Capability("MESSAGE:PLATFORM:MANAGE", "管理平台消息"),
            new Capability("WORK:PLATFORM:VIEW_CONFIG", "查看平台工作配置"),
            new Capability("WORK:PLATFORM:CONFIGURE", "维护平台工作配置"),
            new Capability("WORK:PLATFORM:PUBLISH_CONFIG", "发布平台工作配置"));
    private static final Set<String> ALLOWED = CAPABILITIES.stream().map(Capability::key).collect(Collectors.toUnmodifiableSet());

    private final PlatformRoleBaseService roles;
    private final PlatformRolePermissionBaseService permissions;
    private final PlatformMemberRoleBaseService memberRoles;
    private final PlatformMemberBaseService members;
    private final PlatformAccountBaseService accounts;
    private final AuditRecorder audit;

    public PlatformAuthorizationService(PlatformRoleBaseService roles, PlatformRolePermissionBaseService permissions,
                                        PlatformMemberRoleBaseService memberRoles, PlatformMemberBaseService members,
                                        PlatformAccountBaseService accounts, AuditRecorder audit) {
        this.roles = roles;
        this.permissions = permissions;
        this.memberRoles = memberRoles;
        this.members = members;
        this.accounts = accounts;
        this.audit = audit;
    }

    /** 读取当前平台角色及成员绑定；只展示账号名称，不暴露联系信息。 */
    @Transactional(readOnly = true)
    public Overview overview(AuthenticatedContext context) {
        requirePlatform(context);
        List<PlatformRole> roleRows = roles.selectList(Wrappers.<PlatformRole>lambdaQuery()
                .eq(PlatformRole::getPlatformId, context.platformId()).orderByAsc(PlatformRole::getId));
        List<PlatformRolePermission> grants = permissions.selectList(Wrappers.<PlatformRolePermission>lambdaQuery()
                .eq(PlatformRolePermission::getPlatformId, context.platformId()));
        List<PlatformMemberRole> assignments = memberRoles.selectList(Wrappers.<PlatformMemberRole>lambdaQuery()
                .eq(PlatformMemberRole::getPlatformId, context.platformId()));
        List<PlatformMember> memberRows = members.selectList(Wrappers.<PlatformMember>lambdaQuery()
                .eq(PlatformMember::getPlatformId, context.platformId()).orderByAsc(PlatformMember::getId));
        List<Long> accountIds = memberRows.stream().map(PlatformMember::getAccountId).distinct().toList();
        Map<Long, PlatformAccount> accountById = accountIds.isEmpty() ? Map.of()
                : accounts.selectList(Wrappers.<PlatformAccount>lambdaQuery().in(PlatformAccount::getId, accountIds))
                .stream().collect(Collectors.toMap(PlatformAccount::getId, Function.identity()));
        List<RoleView> roleViews = roleRows.stream().map(role -> new RoleView(role.getId(), role.getName(),
                role.getDescription(), Boolean.TRUE.equals(role.getBuiltIn()) || "PLATFORM_SUPER_ADMIN".equals(role.getCode()),
                role.getStatus(), role.getVersion(), grants.stream().filter(grant -> role.getId().equals(grant.getRoleId()))
                .filter(grant -> "ALLOW".equals(grant.getEffect())).map(this::key).sorted().toList(),
                assignments.stream().filter(item -> role.getId().equals(item.getRoleId())).count())).toList();
        List<MemberView> memberViews = memberRows.stream().map(member -> {
            PlatformAccount account = accountById.get(member.getAccountId());
            return new MemberView(member.getId(), account == null ? "已移除账号" : account.getDisplayName(),
                    account == null ? "" : account.getUsername(), member.getStatus(), member.getVersion(),
                    assignments.stream().filter(item -> member.getId().equals(item.getMemberId()))
                            .map(PlatformMemberRole::getRoleId).sorted().toList());
        }).toList();
        return new Overview(CAPABILITIES, roleViews, memberViews);
    }

    /** 新角色默认没有授权，管理员需明确选择允许的业务动作。 */
    @Transactional
    public RoleView create(AuthenticatedContext context, RoleInput input, String traceId) {
        requirePlatform(context);
        PlatformRole role = new PlatformRole();
        role.setPlatformId(context.platformId());
        role.setCode("role_" + UUID.randomUUID().toString().replace("-", ""));
        role.setName(input.name().strip());
        role.setDescription(clean(input.description()));
        role.setBuiltIn(false);
        role.setStatus("ACTIVE");
        role.setVersion(0);
        roles.insert(role);
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_ROLE_CREATED",
                "PLATFORM_ROLE", role.getId().toString(), "SUCCESS", Map.of("name", role.getName()));
        return new RoleView(role.getId(), role.getName(), role.getDescription(), false, "ACTIVE", role.getVersion(), List.of(), 0);
    }

    /** 角色属性使用乐观锁；内置超级管理员不可由此入口改变。 */
    @Transactional
    public RoleView rename(AuthenticatedContext context, Long roleId, RenameInput input, String traceId) {
        PlatformRole role = editableRole(context, roleId);
        if (!role.getVersion().equals(input.expectedVersion())) throw versionConflict();
        role.setName(input.name().strip());
        role.setDescription(clean(input.description()));
        if (roles.updateById(role) != 1) throw versionConflict();
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_ROLE_RENAMED",
                "PLATFORM_ROLE", roleId.toString(), "SUCCESS", Map.of("name", role.getName()));
        return new RoleView(role.getId(), role.getName(), role.getDescription(), false, role.getStatus(),
                role.getVersion(), List.of(), 0);
    }

    /** 授权集合一次替换，未勾选的动作即撤回；每次请求会重新解析成员有效角色。 */
    @Transactional
    public void replaceGrants(AuthenticatedContext context, Long roleId, GrantsInput input, String traceId) {
        PlatformRole role = editableRole(context, roleId);
        if (!role.getVersion().equals(input.expectedVersion())) throw versionConflict();
        Set<String> requested = new LinkedHashSet<>(input.grants() == null ? List.of() : input.grants());
        if (requested.size() > CAPABILITIES.size() || !ALLOWED.containsAll(requested)) {
            throw new DomainException("PLATFORM_PERMISSION_INVALID", "包含当前平台不支持的授权动作", HttpStatus.BAD_REQUEST);
        }
        List<PlatformRolePermission> previous = permissions.selectList(Wrappers.<PlatformRolePermission>lambdaQuery()
                .eq(PlatformRolePermission::getPlatformId, context.platformId())
                .eq(PlatformRolePermission::getRoleId, roleId));
        // 现有但页面尚不识别的授权保留，避免用简化页面误删其他能力。
        for (PlatformRolePermission grant : previous) {
            if (ALLOWED.contains(key(grant)) && !requested.contains(key(grant))) permissions.deleteById(grant.getId());
        }
        Set<String> existing = previous.stream().map(this::key).collect(Collectors.toSet());
        for (String grant : requested) {
            if (existing.contains(grant)) continue;
            String[] parts = grant.split(":", 3);
            PlatformRolePermission row = new PlatformRolePermission();
            row.setPlatformId(context.platformId());
            row.setRoleId(roleId);
            row.setResourceType(parts[0]);
            row.setResourceCode(parts[1]);
            row.setActionCode(parts[2]);
            row.setDataScopeType("PLATFORM");
            row.setEffect("ALLOW");
            permissions.insert(row);
        }
        role.setVersion(role.getVersion() + 1);
        if (roles.update(Wrappers.<PlatformRole>lambdaUpdate().eq(PlatformRole::getId, roleId)
                .eq(PlatformRole::getVersion, input.expectedVersion()).set(PlatformRole::getVersion, role.getVersion())) != 1) {
            throw versionConflict();
        }
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_ROLE_GRANTS_REPLACED",
                "PLATFORM_ROLE", roleId.toString(), "SUCCESS", Map.of("grantCount", requested.size()));
    }

    /** 成员角色一次替换；禁止修改自己的角色，防止当前管理员意外失去恢复入口。 */
    @Transactional
    public void assignMember(AuthenticatedContext context, Long memberId, MemberRolesInput input, String traceId) {
        requirePlatform(context);
        PlatformMember member = members.selectById(memberId);
        if (member == null || !context.platformId().equals(member.getPlatformId())) {
            throw new DomainException("PLATFORM_MEMBER_NOT_FOUND", "当前平台不存在该成员", HttpStatus.NOT_FOUND);
        }
        if (context.accountId().equals(member.getAccountId())) {
            throw new DomainException("PLATFORM_SELF_ROLE_CHANGE_DENIED", "不能从此入口修改自己的平台角色", HttpStatus.CONFLICT);
        }
        if (!member.getVersion().equals(input.expectedVersion())) throw versionConflict();
        Set<Long> requested = new LinkedHashSet<>(input.roleIds() == null ? List.of() : input.roleIds());
        List<PlatformRole> selected = requested.isEmpty() ? List.of() : roles.selectList(Wrappers.<PlatformRole>lambdaQuery()
                .eq(PlatformRole::getPlatformId, context.platformId()).eq(PlatformRole::getStatus, "ACTIVE")
                .in(PlatformRole::getId, requested));
        if (selected.size() != requested.size() || selected.stream().anyMatch(role -> Boolean.TRUE.equals(role.getBuiltIn())
                || "PLATFORM_SUPER_ADMIN".equals(role.getCode()))) {
            throw new DomainException("PLATFORM_ROLE_INVALID", "只能分配当前平台的有效普通角色", HttpStatus.BAD_REQUEST);
        }
        List<PlatformMemberRole> previous = memberRoles.selectList(Wrappers.<PlatformMemberRole>lambdaQuery()
                .eq(PlatformMemberRole::getPlatformId, context.platformId()).eq(PlatformMemberRole::getMemberId, memberId));
        for (PlatformMemberRole row : previous) {
            if (requested.remove(row.getRoleId())) continue;
            // 内置角色不允许从页面撤销，避免破坏引导管理员账号。
            PlatformRole role = roles.selectById(row.getRoleId());
            if (role != null && (Boolean.TRUE.equals(role.getBuiltIn()) || "PLATFORM_SUPER_ADMIN".equals(role.getCode()))) {
                throw new DomainException("PLATFORM_BUILTIN_ROLE_PROTECTED", "内置管理员角色不能在此变更", HttpStatus.CONFLICT);
            }
            memberRoles.deleteById(row.getId());
        }
        for (Long roleId : requested) {
            PlatformMemberRole row = new PlatformMemberRole();
            row.setPlatformId(context.platformId());
            row.setMemberId(memberId);
            row.setRoleId(roleId);
            memberRoles.insert(row);
        }
        if (members.update(Wrappers.<PlatformMember>lambdaUpdate().eq(PlatformMember::getId, memberId)
                .eq(PlatformMember::getVersion, input.expectedVersion()).setSql("version = version + 1")) != 1) {
            throw versionConflict();
        }
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_MEMBER_ROLES_ASSIGNED",
                "PLATFORM_MEMBER", memberId.toString(), "SUCCESS", Map.of("roleCount", selected.size()));
    }

    private PlatformRole editableRole(AuthenticatedContext context, Long roleId) {
        requirePlatform(context);
        PlatformRole role = roles.selectById(roleId);
        if (role == null || !context.platformId().equals(role.getPlatformId())) {
            throw new DomainException("PLATFORM_ROLE_NOT_FOUND", "当前平台不存在该角色", HttpStatus.NOT_FOUND);
        }
        if (Boolean.TRUE.equals(role.getBuiltIn()) || "PLATFORM_SUPER_ADMIN".equals(role.getCode())) {
            throw new DomainException("PLATFORM_BUILTIN_ROLE_PROTECTED", "内置管理员角色不能在此变更", HttpStatus.CONFLICT);
        }
        return role;
    }

    private void requirePlatform(AuthenticatedContext context) {
        if (context.platformId() == null || context.systemId() != null) {
            throw new DomainException("PLATFORM_CONTEXT_REQUIRED", "请先进入平台", HttpStatus.CONFLICT);
        }
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private String key(PlatformRolePermission row) {
        return row.getResourceType() + ":" + row.getResourceCode() + ":" + row.getActionCode();
    }
    private DomainException versionConflict() {
        return new DomainException("PLATFORM_AUTHORIZATION_VERSION_CONFLICT", "权限数据已变化，请刷新后重试", HttpStatus.CONFLICT);
    }

    public record Capability(String key, String label) {}
    public record Overview(List<Capability> capabilities, List<RoleView> roles, List<MemberView> members) {}
    public record RoleView(Long id, String name, String description, boolean builtIn, String status,
                           Integer version, List<String> grants, long memberCount) {}
    public record MemberView(Long id, String displayName, String username, String status, Integer version,
                             List<Long> roleIds) {}
    public record RoleInput(@NotBlank @Size(max = 200) String name, @Size(max = 1000) String description) {}
    public record RenameInput(@NotBlank @Size(max = 200) String name, @Size(max = 1000) String description,
                              @NotNull Integer expectedVersion) {}
    public record GrantsInput(List<String> grants, @NotNull Integer expectedVersion) {}
    public record MemberRolesInput(List<Long> roleIds, @NotNull Integer expectedVersion) {}
}
