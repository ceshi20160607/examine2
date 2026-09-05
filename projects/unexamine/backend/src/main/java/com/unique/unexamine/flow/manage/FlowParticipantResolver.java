package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.OrganizationRelationRepository;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FlowParticipantResolver {
    private static final Set<String> POLICY_TYPES = Set.of(
            "PERSON", "ROLE", "DEPARTMENT", "RECORD_OWNER", "MANAGER",
            "DIRECT_MANAGER", "INITIATOR_MANAGER", "DEPARTMENT_MANAGER", "PERSON_FIELD",
            "SUBORDINATES", "INITIATOR", "STARTER_SELECTED", "PREVIOUS_HANDLER");

    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemMemberBaseService memberService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final SystemRoleBaseService roleService;
    private final SystemDepartmentBaseService departmentService;
    private final BusinessRecordBaseService recordService;
    private final OrganizationRelationRepository relationRepository;

    public FlowParticipantResolver(
            SystemTenantMemberBaseService tenantMemberService,
            SystemMemberBaseService memberService,
            SystemMemberRoleBaseService memberRoleService,
            SystemRoleBaseService roleService,
            SystemDepartmentBaseService departmentService,
            BusinessRecordBaseService recordService,
            OrganizationRelationRepository relationRepository) {
        this.tenantMemberService = tenantMemberService;
        this.memberService = memberService;
        this.memberRoleService = memberRoleService;
        this.roleService = roleService;
        this.departmentService = departmentService;
        this.recordService = recordService;
        this.relationRepository = relationRepository;
    }

    public Long currentTenantMemberId(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) return null;
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, context.systemId())
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, context.memberId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().map(SystemTenantMember::getId).findFirst().orElse(null);
    }

    public Long tenantMemberIdForAccount(Long systemId, Long tenantId, Long accountId) {
        if (systemId == null || tenantId == null || accountId == null) return null;
        SystemMember member = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, accountId)
                        .eq(SystemMember::getStatus, "ACTIVE"))
                .stream().findFirst().orElse(null);
        if (member == null) return null;
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getSystemId, systemId)
                        .eq(SystemTenantMember::getTenantId, tenantId)
                        .eq(SystemTenantMember::getSystemMemberId, member.getId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().map(SystemTenantMember::getId).findFirst().orElse(null);
    }

    public ResolvedPerson requirePerson(AuthenticatedContext context, Long tenantMemberId) {
        if (context.systemId() == null || context.tenantId() == null || tenantMemberId == null) {
            throw invalid("FLOW_PARTICIPANT_INVALID", "请选择当前系统中的有效成员");
        }
        return activePeople(context).stream().filter(person -> Objects.equals(person.tenantMemberId(), tenantMemberId))
                .findFirst().orElseThrow(() -> invalid("FLOW_PARTICIPANT_INVALID", "所选成员不属于当前系统工作空间"));
    }

    public List<ResolvedPerson> resolve(
            AuthenticatedContext context,
            Map<String, Object> policy,
            FlowInstance instance,
            Map<String, Object> variables,
            Long previousHandlerTenantMemberId) {
        if (context.systemId() == null) return List.of();
        String type = string(policy.get("type")).toUpperCase();
        List<ResolvedPerson> people = activePeople(context);
        Map<Long, ResolvedPerson> byTenantMember = people.stream().collect(
                LinkedHashMap::new, (map, person) -> map.put(person.tenantMemberId(), person), Map::putAll);
        LinkedHashSet<Long> resolved = new LinkedHashSet<>();
        switch (type) {
            case "PERSON" -> resolved.addAll(numbers(policy.get("tenantMemberIds")));
            case "ROLE" -> {
                Set<Long> roleIds = new LinkedHashSet<>(numbers(policy.get("roleIds")));
                memberRoleService.selectList(Wrappers.<SystemMemberRole>lambdaQuery()
                                .eq(SystemMemberRole::getTenantId, context.tenantId()))
                        .stream().filter(link -> roleIds.contains(link.getRoleId()))
                        .map(SystemMemberRole::getTenantMemberId).forEach(resolved::add);
            }
            case "DEPARTMENT" -> {
                Set<Long> departmentIds = descendantDepartmentIds(context,
                        new LinkedHashSet<>(numbers(policy.get("departmentIds"))),
                        Boolean.TRUE.equals(policy.get("includeDescendants")));
                people.stream().filter(person -> departmentIds.contains(person.departmentId()))
                        .map(ResolvedPerson::tenantMemberId).forEach(resolved::add);
            }
            case "DEPARTMENT_MANAGER" -> {
                Set<Long> departmentIds = new LinkedHashSet<>(numbers(policy.get("departmentIds")));
                Map<Long, OrganizationRelationRepository.MemberRelation> relations =
                        relationRepository.byTenant(context.tenantId());
                people.stream().filter(person -> departmentIds.contains(person.departmentId()))
                        .filter(person -> {
                            OrganizationRelationRepository.MemberRelation relation = relations.get(person.tenantMemberId());
                            ResolvedPerson manager = relation == null ? null : byTenantMember.get(relation.managerTenantMemberId());
                            return manager == null || !Objects.equals(manager.departmentId(), person.departmentId());
                        }).map(ResolvedPerson::tenantMemberId).forEach(resolved::add);
            }
            case "RECORD_OWNER" -> recordOwner(context, instance).ifPresent(resolved::add);
            case "MANAGER", "DIRECT_MANAGER", "INITIATOR_MANAGER" -> {
                Long source = sourceMemberId(context, instance, variables, previousHandlerTenantMemberId, policy);
                OrganizationRelationRepository.MemberRelation relation = relationRepository.byTenant(context.tenantId()).get(source);
                if (relation != null && relation.managerTenantMemberId() != null) resolved.add(relation.managerTenantMemberId());
            }
            case "SUBORDINATES" -> {
                Long source = sourceMemberId(context, instance, variables, previousHandlerTenantMemberId, policy);
                Map<Long, OrganizationRelationRepository.MemberRelation> relations = relationRepository.byTenant(context.tenantId());
                boolean changed;
                resolved.add(source);
                do {
                    changed = false;
                    for (OrganizationRelationRepository.MemberRelation relation : relations.values()) {
                        if (relation.managerTenantMemberId() != null && resolved.contains(relation.managerTenantMemberId())
                                && resolved.add(relation.tenantMemberId())) changed = true;
                    }
                } while (changed);
                resolved.remove(source);
            }
            case "INITIATOR" -> {
                if (instance != null && instance.getStartedByTenantMemberId() != null) {
                    resolved.add(instance.getStartedByTenantMemberId());
                } else {
                    Long current = currentTenantMemberId(context);
                    if (current != null) resolved.add(current);
                }
            }
            case "STARTER_SELECTED" -> {
                Long selected = asLong(variables.get(string(policy.getOrDefault(
                        "variableKey", "selectedApproverTenantMemberId"))));
                if (selected != null) resolved.add(selected);
            }
            case "PERSON_FIELD" -> {
                String fieldCode = string(policy.get("fieldCode"));
                Object value = variables.get(fieldCode);
                if (value == null && instance != null) value = readBusinessSnapshot(instance).get(fieldCode);
                if (value instanceof List<?> list) list.stream().map(this::asLong)
                        .filter(Objects::nonNull).forEach(resolved::add);
                else {
                    Long selected = asLong(value);
                    if (selected != null) resolved.add(selected);
                }
            }
            case "PREVIOUS_HANDLER" -> {
                if (previousHandlerTenantMemberId != null) resolved.add(previousHandlerTenantMemberId);
            }
            default -> {
                return List.of();
            }
        }
        return resolved.stream().map(byTenantMember::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(ResolvedPerson::displayName)).toList();
    }

    public String validatePolicy(AuthenticatedContext context, Map<String, Object> policy) {
        String type = string(policy.get("type")).toUpperCase();
        if (!POLICY_TYPES.contains(type)) return "审批人来源必须选择人员、角色、部门、记录负责人、上下级或流程上下文";
        if (context.systemId() == null) return type.equals("PERSON") ? "平台 Flow 人员策略暂不支持系统成员" : null;
        if ("PERSON".equals(type)) {
            List<Long> ids = numbers(policy.get("tenantMemberIds"));
            if (ids.isEmpty()) return "请选择至少一名审批人";
            if (ids.stream().anyMatch(id -> !isActivePerson(context, id))) return "审批人包含已离开当前工作空间的成员";
        }
        if ("ROLE".equals(type)) {
            List<Long> ids = numbers(policy.get("roleIds"));
            if (ids.isEmpty()) return "请选择至少一个审批角色";
            long active = roleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                            .eq(SystemRole::getTenantId, context.tenantId()).eq(SystemRole::getStatus, "ACTIVE"))
                    .stream().filter(role -> ids.contains(role.getId())).count();
            if (active != ids.size()) return "审批角色包含无效或未发布角色";
        }
        if ("DEPARTMENT".equals(type)) {
            List<Long> ids = numbers(policy.get("departmentIds"));
            if (ids.isEmpty()) return "请选择至少一个审批部门";
            long active = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                            .eq(SystemDepartment::getTenantId, context.tenantId()).eq(SystemDepartment::getStatus, "ACTIVE"))
                    .stream().filter(department -> ids.contains(department.getId())).count();
            if (active != ids.size()) return "审批部门包含无效部门";
        }
        if ("DEPARTMENT_MANAGER".equals(type) && numbers(policy.get("departmentIds")).isEmpty()) {
            return "请选择需要解析负责人的部门";
        }
        if ("PERSON_FIELD".equals(type) && string(policy.get("fieldCode")).isBlank()) {
            return "请选择用于解析审批人的人员字段";
        }
        return null;
    }

    private List<ResolvedPerson> activePeople(AuthenticatedContext context) {
        List<SystemTenantMember> memberships = tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                .eq(SystemTenantMember::getSystemId, context.systemId())
                .eq(SystemTenantMember::getTenantId, context.tenantId())
                .eq(SystemTenantMember::getStatus, "ACTIVE"));
        Map<Long, SystemMember> members = memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, context.systemId()).eq(SystemMember::getStatus, "ACTIVE"))
                .stream().collect(LinkedHashMap::new, (map, member) -> map.put(member.getId(), member), Map::putAll);
        Map<Long, SystemDepartment> departments = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                        .eq(SystemDepartment::getTenantId, context.tenantId()).eq(SystemDepartment::getStatus, "ACTIVE"))
                .stream().collect(LinkedHashMap::new, (map, department) -> map.put(department.getId(), department), Map::putAll);
        Map<Long, OrganizationRelationRepository.MemberRelation> relations = relationRepository.byTenant(context.tenantId());
        List<ResolvedPerson> result = new ArrayList<>();
        for (SystemTenantMember membership : memberships) {
            SystemMember member = members.get(membership.getSystemMemberId());
            if (member == null) continue;
            SystemDepartment department = departments.get(membership.getDepartmentId());
            OrganizationRelationRepository.MemberRelation relation = relations.get(membership.getId());
            result.add(new ResolvedPerson(membership.getId(), membership.getSystemMemberId(), member.getAccountId(),
                    member.getDisplayName(), membership.getDepartmentId(), department == null ? null : department.getName(),
                    relation == null ? null : relation.positionTitle()));
        }
        return result;
    }

    private boolean isActivePerson(AuthenticatedContext context, Long tenantMemberId) {
        return activePeople(context).stream().anyMatch(person -> Objects.equals(person.tenantMemberId(), tenantMemberId));
    }

    private java.util.Optional<Long> recordOwner(AuthenticatedContext context, FlowInstance instance) {
        if (instance == null || instance.getBusinessType() == null || instance.getBusinessType().isBlank()) {
            return java.util.Optional.empty();
        }
        Long recordId = asLong(instance.getBusinessId());
        BusinessRecord record = recordId == null ? null : recordService.selectById(recordId);
        if (record == null || !Objects.equals(record.getSystemId(), context.systemId())
                || !Objects.equals(record.getTenantId(), context.tenantId()) || record.getOwnerMemberId() == null) {
            return java.util.Optional.empty();
        }
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getTenantId, context.tenantId())
                        .eq(SystemTenantMember::getSystemMemberId, record.getOwnerMemberId())
                        .eq(SystemTenantMember::getStatus, "ACTIVE"))
                .stream().map(SystemTenantMember::getId).findFirst();
    }

    private Long sourceMemberId(AuthenticatedContext context, FlowInstance instance, Map<String, Object> variables,
                                Long previousHandlerTenantMemberId, Map<String, Object> policy) {
        String source = string(policy.getOrDefault("source", "INITIATOR")).toUpperCase();
        if ("RECORD_OWNER".equals(source)) return recordOwner(context, instance).orElse(null);
        if ("PREVIOUS_HANDLER".equals(source)) return previousHandlerTenantMemberId;
        if ("VARIABLE".equals(source)) return asLong(variables.get(string(policy.get("variableKey"))));
        if (instance != null && instance.getStartedByTenantMemberId() != null) return instance.getStartedByTenantMemberId();
        return currentTenantMemberId(context);
    }

    private Set<Long> descendantDepartmentIds(AuthenticatedContext context, Set<Long> roots, boolean descendants) {
        if (!descendants) return roots;
        List<SystemDepartment> departments = departmentService.selectList(Wrappers.<SystemDepartment>lambdaQuery()
                .eq(SystemDepartment::getTenantId, context.tenantId()).eq(SystemDepartment::getStatus, "ACTIVE"));
        boolean changed;
        do {
            changed = false;
            for (SystemDepartment department : departments) {
                if (department.getParentId() != null && roots.contains(department.getParentId())
                        && roots.add(department.getId())) changed = true;
            }
        } while (changed);
        return roots;
    }

    private List<Long> numbers(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(this::asLong).filter(Objects::nonNull).distinct().toList();
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return null; }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private Map<String, Object> readBusinessSnapshot(FlowInstance instance) {
        if (instance.getBusinessSnapshotJson() == null || instance.getBusinessSnapshotJson().isBlank()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    instance.getBusinessSnapshotJson(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record ResolvedPerson(Long tenantMemberId, Long systemMemberId, Long accountId,
                                 String displayName, Long departmentId, String departmentName,
                                 String positionTitle) {
    }
}
