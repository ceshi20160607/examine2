package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysMemberRoleService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 系统范围的角色、部门和固定上级审批人解析器。 */
@Component
public class SystemExamineCandidateResolver implements ExamineCandidateResolver {
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysRoleService roles;
    private final IUnSysDepartmentService departments;
    private final IUnPlatAccountService accounts;

    public SystemExamineCandidateResolver(IUnSysMemberService members,
                                          IUnSysTenantMemberService tenantMembers,
                                          IUnSysMemberRoleService memberRoles,
                                          IUnSysRoleService roles,
                                          IUnSysDepartmentService departments,
                                          IUnPlatAccountService accounts) {
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.memberRoles = memberRoles;
        this.roles = roles;
        this.departments = departments;
        this.accounts = accounts;
    }

    @Override
    public Set<Long> resolve(JsonNode node, ExamineCandidateContext context) {
        Set<Long> result = new LinkedHashSet<>();
        // 平台审批没有系统租户成员范围，只允许通用解析器处理固定账号。
        if (context == null || context.systemId() == null || context.tenantId() == null) return result;
        JsonNode config = node.path("config");
        addRule(result, node, context);
        addRule(result, config, context);
        JsonNode approvers = node.get("approvers");
        if (approvers != null && approvers.isArray()) approvers.forEach(item -> addRule(result, item, context));
        JsonNode configuredApprovers = config.get("approvers");
        if (configuredApprovers != null && configuredApprovers.isArray())
            configuredApprovers.forEach(item -> addRule(result, item, context));
        return result;
    }

    private void addRule(Set<Long> result, JsonNode rule, ExamineCandidateContext context) {
        if (rule == null || rule.isMissingNode() || rule.isNull()) return;
        addArray(result, rule.get("roleIds"), "ROLE", context);
        addArray(result, rule.get("departmentIds"), "DEPARTMENT", context);
        addArray(result, rule.get("accountIds"), "ACCOUNT", context);
        addArray(result, rule.get("candidateAccountIds"), "ACCOUNT", context);
        String type = rule.path("approverType").asText(rule.path("subjectType").asText(rule.path("type").asText("")))
                .toUpperCase(Locale.ROOT);
        Long subjectId = longValue(rule, "approverId", "subjectId", "roleId", "departmentId", "accountId");
        if (type.isBlank() && (rule.has("accountId") || rule.has("id"))) type = "ACCOUNT";
        switch (type) {
            case "ACCOUNT", "PERSON", "USER", "FIXED", "SPECIFIED" -> addAccount(result, subjectId, context);
            case "ROLE" -> addRole(result, subjectId, context);
            case "DEPARTMENT", "DEPT" -> addDepartment(result, subjectId, context);
            case "DIRECT_SUPERVISOR", "SUPERVISOR", "MANAGER" ->
                    addSupervisor(result, longValue(rule, "supervisorAccountId", "accountId"), context);
            default -> { }
        }
    }

    private void addArray(Set<Long> result, JsonNode values, String type, ExamineCandidateContext context) {
        if (values == null || !values.isArray()) return;
        values.forEach(value -> {
            if (!value.canConvertToLong()) return;
            if ("ROLE".equals(type)) addRole(result, value.asLong(), context);
            else if ("DEPARTMENT".equals(type)) addDepartment(result, value.asLong(), context);
            else addAccount(result, value.asLong(), context);
        });
    }

    private void addAccount(Set<Long> result, Long accountId, ExamineCandidateContext context) {
        if (accountId == null) return;
        var account = accounts.queryById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) return;
        UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, context.systemId())
                .eq(UnSysMember::getAccountId, accountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (member == null) return;
        UnSysTenantMember tenantMember = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, context.systemId())
                .eq(UnSysTenantMember::getTenantId, context.tenantId())
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
        if (tenantMember != null) result.add(accountId);
    }

    private void addRole(Set<Long> result, Long roleId, ExamineCandidateContext context) {
        if (roleId == null) return;
        UnSysRole role = roles.getById(roleId);
        if (role == null || !context.systemId().equals(role.getSystemId())
                || !context.tenantId().equals(role.getTenantId()) || !"ACTIVE".equals(role.getStatus())) return;
        for (UnSysMemberRole link : memberRoles.list(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getRoleId, roleId))) {
            UnSysTenantMember tenantMember = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getSystemId, context.systemId())
                    .eq(UnSysTenantMember::getId, link.getTenantMemberId())
                    .eq(UnSysTenantMember::getTenantId, context.tenantId())
                    .eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
            if (tenantMember == null) continue;
            UnSysMember member = members.getById(tenantMember.getMemberId());
            if (member != null && context.systemId().equals(member.getSystemId()) && "ACTIVE".equals(member.getStatus()))
                addAccount(result, member.getAccountId(), context);
        }
    }

    private void addDepartment(Set<Long> result, Long departmentId, ExamineCandidateContext context) {
        if (departmentId == null) return;
        UnSysDepartment department = departments.getById(departmentId);
        if (department == null || !context.systemId().equals(department.getSystemId())
                || !context.tenantId().equals(department.getTenantId()) || !"ACTIVE".equals(department.getStatus())) return;
        for (UnSysTenantMember tenantMember : tenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, context.systemId())
                .eq(UnSysTenantMember::getTenantId, context.tenantId())
                .eq(UnSysTenantMember::getDepartmentId, departmentId)
                .eq(UnSysTenantMember::getStatus, "ACTIVE"))) {
            UnSysMember member = members.getById(tenantMember.getMemberId());
            if (member != null && context.systemId().equals(member.getSystemId()) && "ACTIVE".equals(member.getStatus()))
                addAccount(result, member.getAccountId(), context);
        }
    }

    /** 先使用节点显式指定的上级，否则从发起人的当前租户成员关系读取直系上级。 */
    private void addSupervisor(Set<Long> result, Long configuredAccountId, ExamineCandidateContext context) {
        if (configuredAccountId != null) {
            addAccount(result, configuredAccountId, context);
            return;
        }
        if (context.initiatorAccountId() == null) return;
        UnSysMember initiator = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, context.systemId())
                .eq(UnSysMember::getAccountId, context.initiatorAccountId())
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (initiator == null) return;
        UnSysTenantMember membership = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, context.systemId())
                .eq(UnSysTenantMember::getTenantId, context.tenantId())
                .eq(UnSysTenantMember::getMemberId, initiator.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
        if (membership != null) addAccount(result, membership.getSupervisorAccountId(), context);
    }

    private Long longValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.canConvertToLong()) return value.asLong();
        }
        return null;
    }
}
