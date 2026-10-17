package com.unique.unexamine.system.manage;

import com.unique.unexamine.platform.manage.AccountSessionService;
import com.unique.unexamine.system.base.entity.RoleMenuGrant;
import com.unique.unexamine.system.base.entity.SystemRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统所属人的主租户角色、菜单授权与已有账号邀请入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}")
public class SystemMembershipController {
    private final AccountSessionService sessions;
    private final SystemMembershipService memberships;

    public SystemMembershipController(AccountSessionService sessions, SystemMembershipService memberships) {
        this.sessions = sessions;
        this.memberships = memberships;
    }

    /** 列出本系统主租户角色。 */
    @GetMapping("/roles")
    public List<RoleView> roles(@PathVariable Long systemId, HttpServletRequest request) {
        return memberships.roles(accountId(request), systemId).stream().map(RoleView::from).toList();
    }

    /** 创建自定义成员角色。 */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleView createRole(@PathVariable Long systemId, @Valid @RequestBody RoleRequest body, HttpServletRequest request) {
        return RoleView.from(memberships.createRole(accountId(request), systemId, body.code(), body.name()));
    }

    /** 列出指定角色的模块菜单权限。 */
    @GetMapping("/roles/{roleId}/grants")
    public List<SystemMembershipService.GrantView> grants(@PathVariable Long systemId, @PathVariable Long roleId,
                                                            HttpServletRequest request) {
        return memberships.grants(accountId(request), systemId, roleId);
    }

    /** 保存指定角色对已发布模块列表菜单的基础动作权限。 */
    @PostMapping("/roles/{roleId}/grants")
    public SystemMembershipService.GrantView grant(@PathVariable Long systemId, @PathVariable Long roleId,
                                                    @Valid @RequestBody GrantRequest body, HttpServletRequest request) {
        RoleMenuGrant saved = memberships.grant(accountId(request), systemId, roleId, body.moduleId(),
                body.canView(), body.canCreate(), body.canUpdate(), body.canDelete(), body.canExport());
        return new SystemMembershipService.GrantView(body.moduleId(), saved.getCanView(), saved.getCanCreate(),
                saved.getCanUpdate(), saved.getCanDelete(), saved.getCanExport());
    }

    /** 列出当前系统主租户的已加入成员。 */
    @GetMapping("/members")
    public List<SystemMembershipService.MemberView> members(@PathVariable Long systemId, HttpServletRequest request) {
        return memberships.members(accountId(request), systemId);
    }

    /** 将已注册平台账号加入系统主租户并赋予指定角色。 */
    @PostMapping("/members/invite")
    @ResponseStatus(HttpStatus.CREATED)
    public SystemMembershipService.MemberView invite(@PathVariable Long systemId, @Valid @RequestBody InviteRequest body,
                                                      HttpServletRequest request) {
        memberships.invite(accountId(request), systemId, body.username(), body.roleId());
        return memberships.members(accountId(request), systemId).stream()
                .filter(member -> member.username().equals(body.username())).findFirst().orElseThrow();
    }

    private Long accountId(HttpServletRequest request) { return sessions.requireAccount(request).getId(); }

    /** 租户内稳定的角色编码与展示名称。 */
    public record RoleRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,39}") String code,
                              @NotBlank @Size(max = 120) String name) {}
    /** 用户名指向已注册平台账号；角色必须属于当前系统主租户。 */
    public record InviteRequest(@NotBlank String username, @NotNull Long roleId) {}
    /** 基础菜单动作权限不包含尚未实施的数据范围规则。 */
    public record GrantRequest(@NotNull Long moduleId, boolean canView, boolean canCreate,
                               boolean canUpdate, boolean canDelete, boolean canExport) {}
    /** 系统后台角色字段。 */
    public record RoleView(Long id, String code, String name) {
        static RoleView from(SystemRole role) { return new RoleView(role.getId(), role.getCode(), role.getName()); }
    }
}
