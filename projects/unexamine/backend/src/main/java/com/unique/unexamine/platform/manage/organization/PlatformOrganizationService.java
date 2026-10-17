package com.unique.unexamine.platform.manage.organization;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformDepartment;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformDepartmentBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 平台组织管理使用独立的平台部门和成员表，不触碰系统租户组织。 */
@Service
public class PlatformOrganizationService {
    private final PlatformDepartmentBaseService departments;
    private final PlatformMemberBaseService members;
    private final PlatformAccountBaseService accounts;
    private final AuditRecorder audit;

    public PlatformOrganizationService(PlatformDepartmentBaseService departments, PlatformMemberBaseService members,
                                       PlatformAccountBaseService accounts, AuditRecorder audit) {
        this.departments = departments;
        this.members = members;
        this.accounts = accounts;
        this.audit = audit;
    }

    /** 页面只接收当前平台的部门和成员，账号联系方式与凭据不在该视图中返回。 */
    @Transactional(readOnly = true)
    public Overview overview(AuthenticatedContext context) {
        requirePlatform(context);
        List<DepartmentView> departmentViews = departments.selectList(Wrappers.<PlatformDepartment>lambdaQuery()
                        .eq(PlatformDepartment::getPlatformId, context.platformId()))
                .stream().sorted(Comparator.comparing(PlatformDepartment::getPathCode)
                        .thenComparing(PlatformDepartment::getSortOrder)
                        .thenComparing(PlatformDepartment::getId))
                .map(this::view).toList();
        List<PlatformMember> memberRows = members.selectList(Wrappers.<PlatformMember>lambdaQuery()
                .eq(PlatformMember::getPlatformId, context.platformId()).orderByAsc(PlatformMember::getId));
        List<Long> accountIds = memberRows.stream().map(PlatformMember::getAccountId).distinct().toList();
        Map<Long, PlatformAccount> accountById = accountIds.isEmpty() ? Map.of()
                : accounts.selectList(Wrappers.<PlatformAccount>lambdaQuery().in(PlatformAccount::getId, accountIds))
                .stream().collect(Collectors.toMap(PlatformAccount::getId, Function.identity()));
        List<MemberView> memberViews = memberRows.stream().map(member -> {
            PlatformAccount account = accountById.get(member.getAccountId());
            return new MemberView(member.getId(), member.getAccountId(),
                    account == null ? "已移除账号" : account.getDisplayName(),
                    account == null ? "" : account.getUsername(), member.getDepartmentId(),
                    member.getTitle(), member.getStatus(), member.getVersion());
        }).toList();
        return new Overview(departmentViews, memberViews);
    }

    /** 部门编码由服务端生成，管理员只需填写名称和可选上级部门。 */
    @Transactional
    public DepartmentView createDepartment(AuthenticatedContext context, DepartmentInput input, String traceId) {
        requirePlatform(context);
        PlatformDepartment parent = input.parentId() == null ? null : requireActiveDepartment(context, input.parentId());
        PlatformDepartment department = new PlatformDepartment();
        department.setPlatformId(context.platformId());
        department.setParentId(parent == null ? null : parent.getId());
        department.setCode("department_" + UUID.randomUUID().toString().replace("-", ""));
        department.setName(input.name().strip());
        department.setPathCode((parent == null ? "" : parent.getPathCode() + "/") + department.getCode());
        department.setSortOrder(input.sortOrder() == null ? 0 : input.sortOrder());
        department.setStatus("ACTIVE");
        department.setVersion(0);
        departments.insert(department);
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_DEPARTMENT_CREATED",
                "PLATFORM_DEPARTMENT", department.getId().toString(), "SUCCESS",
                Map.of("name", department.getName(), "parentId", parent == null ? 0L : parent.getId()));
        return view(department);
    }

    /** 更名不改变部门编码、路径和已有业务引用。 */
    @Transactional
    public DepartmentView renameDepartment(AuthenticatedContext context, Long id, RenameDepartmentInput input,
                                           String traceId) {
        requirePlatform(context);
        PlatformDepartment department = requireActiveDepartment(context, id);
        if (!department.getVersion().equals(input.expectedVersion())) throw versionConflict();
        department.setName(input.name().strip());
        department.setSortOrder(input.sortOrder() == null ? department.getSortOrder() : input.sortOrder());
        if (departments.updateById(department) != 1) throw versionConflict();
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_DEPARTMENT_RENAMED",
                "PLATFORM_DEPARTMENT", id.toString(), "SUCCESS", Map.of("name", department.getName()));
        return view(department);
    }

    /** 成员调入有效平台部门前校验范围；角色关联保持不变。 */
    @Transactional
    public MemberView assignMember(AuthenticatedContext context, Long memberId, MemberAssignment input,
                                   String traceId) {
        requirePlatform(context);
        PlatformMember member = members.selectById(memberId);
        if (member == null || !context.platformId().equals(member.getPlatformId())) {
            throw new DomainException("PLATFORM_MEMBER_NOT_FOUND", "当前平台不存在该成员", HttpStatus.NOT_FOUND);
        }
        if (!member.getVersion().equals(input.expectedVersion())) throw versionConflict();
        if (input.departmentId() != null) requireActiveDepartment(context, input.departmentId());
        String title = input.title() == null ? null : input.title().strip();
        int updated = members.update(Wrappers.<PlatformMember>lambdaUpdate()
                .eq(PlatformMember::getId, memberId)
                .eq(PlatformMember::getPlatformId, context.platformId())
                .eq(PlatformMember::getVersion, input.expectedVersion())
                .set(PlatformMember::getDepartmentId, input.departmentId())
                .set(PlatformMember::getTitle, title == null || title.isEmpty() ? null : title)
                .setSql("version = version + 1"));
        if (updated != 1) throw versionConflict();
        audit.record(traceId, context.accountId(), null, null, null, "PLATFORM_MEMBER_DEPARTMENT_ASSIGNED",
                "PLATFORM_MEMBER", memberId.toString(), "SUCCESS",
                Map.of("departmentId", input.departmentId() == null ? 0L : input.departmentId()));
        PlatformAccount account = accounts.selectById(member.getAccountId());
        return new MemberView(memberId, member.getAccountId(),
                account == null ? "已移除账号" : account.getDisplayName(),
                account == null ? "" : account.getUsername(),
                input.departmentId(), title, member.getStatus(), input.expectedVersion() + 1);
    }

    private PlatformDepartment requireActiveDepartment(AuthenticatedContext context, Long id) {
        PlatformDepartment department = departments.selectById(id);
        if (department == null || !context.platformId().equals(department.getPlatformId())
                || !"ACTIVE".equals(department.getStatus())) {
            throw new DomainException("PLATFORM_DEPARTMENT_NOT_FOUND", "当前平台没有可用的目标部门", HttpStatus.NOT_FOUND);
        }
        return department;
    }

    private void requirePlatform(AuthenticatedContext context) {
        if (context.platformId() == null || context.systemId() != null) {
            throw new DomainException("PLATFORM_CONTEXT_REQUIRED", "请先进入平台", HttpStatus.CONFLICT);
        }
    }

    private DomainException versionConflict() {
        return new DomainException("PLATFORM_ORGANIZATION_VERSION_CONFLICT", "组织数据已变化，请刷新后重试", HttpStatus.CONFLICT);
    }

    private DepartmentView view(PlatformDepartment value) {
        return new DepartmentView(value.getId(), value.getParentId(), value.getName(), value.getPathCode(),
                value.getSortOrder(), value.getStatus(), value.getVersion());
    }

    public record Overview(List<DepartmentView> departments, List<MemberView> members) {}
    public record DepartmentView(Long id, Long parentId, String name, String pathCode, Integer sortOrder,
                                 String status, Integer version) {}
    public record MemberView(Long id, Long accountId, String displayName, String username, Long departmentId,
                             String title, String status, Integer version) {}
    public record DepartmentInput(@NotBlank @Size(max = 200) String name, Long parentId, Integer sortOrder) {}
    public record RenameDepartmentInput(@NotBlank @Size(max = 200) String name, Integer sortOrder,
                                        @NotNull Integer expectedVersion) {}
    public record MemberAssignment(Long departmentId, @Size(max = 100) String title,
                                   @NotNull Integer expectedVersion) {}
}
