package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatMemberRole;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberRoleService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 平台审批的角色、部门及显式上级账号解析器，系统审批由系统组织解析器负责。 */
@Component
public class PlatformExamineCandidateResolver implements ExamineCandidateResolver {
    private final IUnPlatAccountService accounts;
    private final IUnPlatMemberService members;
    private final IUnPlatMemberRoleService memberRoles;
    private final IUnPlatRoleService roles;
    private final IUnPlatDepartmentService departments;

    public PlatformExamineCandidateResolver(IUnPlatAccountService accounts, IUnPlatMemberService members,
                                            IUnPlatMemberRoleService memberRoles, IUnPlatRoleService roles,
                                            IUnPlatDepartmentService departments) {
        this.accounts = accounts;
        this.members = members;
        this.memberRoles = memberRoles;
        this.roles = roles;
        this.departments = departments;
    }

    @Override
    public Set<Long> resolve(JsonNode node, ExamineCandidateContext context) {
        if (context == null || context.systemId() != null || context.tenantId() != null) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        collect(result, node);
        collect(result, node.path("config"));
        return result;
    }

    private void collect(Set<Long> result, JsonNode source) {
        if (source == null || source.isMissingNode()) return;
        JsonNode approvers = source.get("approvers");
        if (approvers == null || !approvers.isArray()) return;
        for (JsonNode rule : approvers) {
            String type = rule.path("type").asText(rule.path("subjectType").asText(""))
                    .trim().toUpperCase(Locale.ROOT);
            Long id = id(rule);
            switch (type) {
                case "ROLE" -> addRole(result, id);
                case "DEPARTMENT", "DEPT" -> addDepartment(result, id);
                case "SUPERVISOR" -> addAccount(result, id);
                default -> { }
            }
        }
    }

    private void addRole(Set<Long> result, Long roleId) {
        if (roleId == null) return;
        UnPlatRole role = roles.queryById(roleId);
        if (role == null || !"ACTIVE".equals(role.getStatus())) return;
        for (UnPlatMemberRole link : memberRoles.list(new LambdaQueryWrapper<UnPlatMemberRole>()
                .eq(UnPlatMemberRole::getRoleId, roleId))) {
            UnPlatMember member = members.queryById(link.getMemberId());
            if (member != null && "ACTIVE".equals(member.getStatus())) addAccount(result, member.getAccountId());
        }
    }

    private void addDepartment(Set<Long> result, Long departmentId) {
        if (departmentId == null) return;
        UnPlatDepartment department = departments.queryById(departmentId);
        if (department == null || !"ACTIVE".equals(department.getStatus())) return;
        for (UnPlatMember member : members.list(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getDepartmentId, departmentId)
                .eq(UnPlatMember::getStatus, "ACTIVE"))) addAccount(result, member.getAccountId());
    }

    private void addAccount(Set<Long> result, Long accountId) {
        if (accountId == null) return;
        var account = accounts.queryById(accountId);
        if (account != null && "ACTIVE".equals(account.getStatus())) result.add(accountId);
    }

    private Long id(JsonNode rule) {
        for (String key : List.of("subjectId", "roleId", "departmentId", "accountId", "supervisorAccountId")) {
            JsonNode value = rule.get(key);
            if (value != null && value.canConvertToLong() && value.asLong() > 0) return value.asLong();
        }
        return null;
    }
}
