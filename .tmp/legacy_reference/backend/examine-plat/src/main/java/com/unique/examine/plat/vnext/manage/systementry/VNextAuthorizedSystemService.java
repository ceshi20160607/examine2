package com.unique.examine.plat.vnext.manage.systementry;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.plat.vnext.base.entity.ContextSession;
import com.unique.examine.plat.vnext.base.entity.Member;
import com.unique.examine.plat.vnext.base.entity.MemberRole;
import com.unique.examine.plat.vnext.base.entity.Role;
import com.unique.examine.plat.vnext.base.entity.System;
import com.unique.examine.plat.vnext.base.service.IVNextPlatContextSessionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatSystemService;
import com.unique.examine.plat.vnext.manage.auth.SystemSummary;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only authorized-system projection with the frozen five generated IService boundary. */
@Service
public class VNextAuthorizedSystemService {
    private final IVNextPlatMemberService memberService;
    private final IVNextPlatSystemService systemService;
    private final IVNextPlatMemberRoleService memberRoleService;
    private final IVNextPlatRoleService roleService;
    private final IVNextPlatContextSessionService contextSessionService;

    public VNextAuthorizedSystemService(
            IVNextPlatMemberService memberService,
            IVNextPlatSystemService systemService,
            IVNextPlatMemberRoleService memberRoleService,
            IVNextPlatRoleService roleService,
            IVNextPlatContextSessionService contextSessionService
    ) {
        this.memberService = memberService;
        this.systemService = systemService;
        this.memberRoleService = memberRoleService;
        this.roleService = roleService;
        this.contextSessionService = contextSessionService;
    }

    public List<SystemSummary> list(long accountId) {
        var now = LocalDateTime.now();
        var members = memberService.list(Wrappers.<Member>lambdaQuery()
                .eq(Member::getAccountId, accountId)
                .eq(Member::getStatus, "ACTIVE"));
        if (members.isEmpty()) return List.of();
        var systems = systems(members);
        if (systems.isEmpty()) return List.of();
        var rolesByMember = rolesByMember(members, systems, now);
        var recent = recent(accountId, systems.keySet());
        return members.stream()
                .filter(member -> systems.containsKey(member.getSystemId()))
                .map(member -> summary(member, systems.get(member.getSystemId()),
                        rolesByMember.getOrDefault(member.getId(), List.of()),
                        recent.get(member.getSystemId())))
                .sorted(summaryOrder())
                .toList();
    }

    private Map<Long, System> systems(List<Member> members) {
        var ids = members.stream().map(Member::getSystemId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return systemService.listByIds(ids).stream()
                .filter(system -> "ACTIVE".equals(system.getStatus()) || "DISABLED".equals(system.getStatus()))
                .collect(Collectors.toMap(System::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, List<Role>> rolesByMember(
            List<Member> members, Map<Long, System> systems, LocalDateTime now
    ) {
        var memberIds = members.stream().map(Member::getId).toList();
        var assignments = memberRoleService.list(Wrappers.<MemberRole>lambdaQuery()
                .in(MemberRole::getMemberId, memberIds)
                .le(MemberRole::getValidFrom, now)
                .and(query -> query.isNull(MemberRole::getValidUntil).or().gt(MemberRole::getValidUntil, now)));
        var roleIds = assignments.stream().map(MemberRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) return Map.of();
        var roles = roleService.listByIds(roleIds).stream()
                .filter(role -> "SYSTEM".equals(role.getScopeType()))
                .filter(role -> "ACTIVE".equals(role.getStatus()))
                .filter(role -> role.getPublishedVersion() != null && role.getPublishedVersion() > 0)
                .filter(role -> Objects.equals(role.getScopeKey(), role.getSystemId()))
                .filter(role -> systems.containsKey(role.getSystemId()))
                .collect(Collectors.toMap(Role::getId, Function.identity(), (left, right) -> left));
        var result = new LinkedHashMap<Long, List<Role>>();
        for (var member : members) {
            var values = assignments.stream()
                    .filter(link -> Objects.equals(link.getMemberId(), member.getId()))
                    .filter(link -> Objects.equals(link.getSystemId(), member.getSystemId()))
                    .map(link -> roles.get(link.getRoleId()))
                    .filter(Objects::nonNull)
                    .filter(role -> Objects.equals(role.getSystemId(), member.getSystemId()))
                    .distinct()
                    .sorted(Comparator.comparing(Role::getName).thenComparing(Role::getId))
                    .toList();
            result.put(member.getId(), values);
        }
        return result;
    }

    private Map<Long, LocalDateTime> recent(long accountId, java.util.Set<Long> systemIds) {
        return contextSessionService.list(Wrappers.<ContextSession>lambdaQuery()
                        .eq(ContextSession::getAccountId, accountId)
                        .eq(ContextSession::getContextType, "SYSTEM")
                        .in(ContextSession::getSystemId, systemIds)).stream()
                .filter(context -> context.getSystemId() != null && context.getLastSeenAt() != null)
                .collect(Collectors.toMap(
                        ContextSession::getSystemId, ContextSession::getLastSeenAt,
                        (left, right) -> left.isAfter(right) ? left : right
                ));
    }

    private static SystemSummary summary(
            Member member, System system, List<Role> roles, LocalDateTime recent
    ) {
        var roleNames = roles.stream().map(Role::getName).distinct().toList();
        return new SystemSummary(
                system.getId().toString(), system.getSystemCode(), system.getName(), system.getStatus(),
                member.getStatus(), id(member.getDefaultTenantId()), roleNames,
                recent == null ? null : recent.toString()
        );
    }

    private static Comparator<SystemSummary> summaryOrder() {
        return Comparator.comparing(
                        SystemSummary::recentEnteredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(SystemSummary::name)
                .thenComparing(SystemSummary::code)
                .thenComparingLong(summary -> Long.parseLong(summary.id()));
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }
}
