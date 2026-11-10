package com.unique.unexamine.sys.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysSystemConfig;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysSystemConfigService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.DepartmentRequest;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.DepartmentView;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.MemberSearch;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.MemberView;
import com.unique.unexamine.sys.manage.dto.SystemDirectoryDto.RoleView;
import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统租户组织目录管理。
 *
 * <p>部门、成员租户关系和角色是权限及审批候选人的主数据。保存后立即生效，
 * 变动通过系统操作日志记录，并递增权限配置版本使授权缓存失效。</p>
 */
@Service
public class SystemDirectoryService {
    private final SystemManageService systems;
    private final IUnSysDepartmentService departments;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysMemberService members;
    private final IUnPlatAccountService accounts;
    private final IUnSysRoleService roles;
    private final IUnSysSystemConfigService configs;
    private final IUnSysLogOptService logs;
    private final ObjectMapper json;

    public SystemDirectoryService(SystemManageService systems, IUnSysDepartmentService departments,
            IUnSysTenantMemberService tenantMembers, IUnSysMemberService members,
            IUnPlatAccountService accounts, IUnSysRoleService roles, IUnSysSystemConfigService configs,
            IUnSysLogOptService logs, ObjectMapper json) {
        this.systems = systems;
        this.departments = departments;
        this.tenantMembers = tenantMembers;
        this.members = members;
        this.accounts = accounts;
        this.roles = roles;
        this.configs = configs;
        this.logs = logs;
        this.json = json;
    }

    /** 查询当前租户的有效部门，供成员、字段权限和审批节点配置使用。 */
    public List<DepartmentView> departments(String token, Long systemId, Long tenantId) {
        var context = systems.context(token, systemId, tenantId);
        return departments.list(new LambdaQueryWrapper<UnSysDepartment>()
                        .eq(UnSysDepartment::getSystemId, context.systemId())
                        .eq(UnSysDepartment::getTenantId, context.tenantId())
                        .eq(UnSysDepartment::getStatus, "ACTIVE")
                        .orderByAsc(UnSysDepartment::getSortNo)
                        .orderByAsc(UnSysDepartment::getId))
                .stream().map(this::departmentView).toList();
    }

    /** 保存部门并校验父级仍属于当前租户，避免跨租户组织树。 */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentView saveDepartment(String token, Long systemId, Long tenantId,
                                         DepartmentRequest request) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        if (request == null || request.code() == null || request.name() == null
                || request.name().isBlank() || request.name().length() > 100
                || request.sortNo() != null && request.sortNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "部门内容不能为空");
        String code = normalizeCode(request.code());
        if (request.id() != null && request.id().equals(request.parentId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "部门不能以自身作为上级");
        if (request.parentId() != null) activeDepartment(context.systemId(), context.tenantId(), request.parentId());
        ensureNoCycle(context.systemId(), context.tenantId(), request.id(), request.parentId());
        long duplicate = departments.count(new LambdaQueryWrapper<UnSysDepartment>()
                .eq(UnSysDepartment::getSystemId, context.systemId())
                .eq(UnSysDepartment::getTenantId, context.tenantId())
                .eq(UnSysDepartment::getCode, code)
                .ne(request.id() != null, UnSysDepartment::getId, request.id()));
        if (duplicate > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门编码已存在");
        UnSysDepartment row = request.id() == null ? new UnSysDepartment() : departments.getById(request.id());
        if (request.id() != null && (row == null || !context.systemId().equals(row.getSystemId())
                || !context.tenantId().equals(row.getTenantId())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "部门不存在");
        if (request.id() == null) {
            row.setSystemId(context.systemId());
            row.setTenantId(context.tenantId());
            row.setCreateTime(LocalDateTime.now());
        } else if (!"ACTIVE".equals(row.getStatus())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门已停用");
        }
        row.setParentId(request.parentId());
        row.setCode(code);
        row.setName(request.name().trim());
        row.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        row.setStatus("ACTIVE");
        row.setUpdateTime(LocalDateTime.now());
        departments.addOrUpdate(row);
        audit(context, request.id() == null ? "DEPARTMENT_CREATE" : "DEPARTMENT_UPDATE", row.getId(),
                Map.of("parentId", row.getParentId() == null ? "" : row.getParentId(), "code", row.getCode()));
        changed(context.systemId());
        return departmentView(row);
    }

    /** 停用部门；仍有成员或子部门时保留关系但阻止再次使用。 */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentView disableDepartment(String token, Long systemId, Long tenantId, Long departmentId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        UnSysDepartment row = activeDepartment(context.systemId(), context.tenantId(), departmentId);
        long childCount = departments.count(new LambdaQueryWrapper<UnSysDepartment>()
                .eq(UnSysDepartment::getSystemId, context.systemId())
                .eq(UnSysDepartment::getTenantId, context.tenantId())
                .eq(UnSysDepartment::getParentId, departmentId)
                .eq(UnSysDepartment::getStatus, "ACTIVE"));
        if (childCount > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "请先停用下级部门");
        row.setStatus("DISABLED");
        row.setUpdateTime(LocalDateTime.now());
        departments.addOrUpdate(row);
        audit(context, "DEPARTMENT_DISABLE", row.getId(), Map.of());
        changed(context.systemId());
        return departmentView(row);
    }

    /** 查询当前租户的成员与部门关系，支持按部门、状态和账号显示名筛选。 */
    public Page<MemberView> members(String token, Long systemId, Long tenantId, MemberSearch search) {
        var context = systems.context(token, systemId, tenantId);
        MemberSearch criteria = search == null ? new MemberSearch() : search;
        LambdaQueryWrapper<UnSysTenantMember> where = new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, context.systemId())
                .eq(UnSysTenantMember::getTenantId, context.tenantId())
                .orderByDesc(UnSysTenantMember::getId);
        if (criteria.getDepartmentId() != null) where.eq(UnSysTenantMember::getDepartmentId, criteria.getDepartmentId());
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = criteria.getStatus().trim().toUpperCase(Locale.ROOT);
            if (!List.of("ACTIVE", "DISABLED").contains(status))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "成员状态无效");
            where.eq(UnSysTenantMember::getStatus, status);
        }
        String keyword = criteria.getKeyword() == null ? null : criteria.getKeyword().trim();
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "成员搜索内容过长");
            List<Long> accountIds = accountsByKeyword(keyword);
            List<Long> memberIds = members.list(new LambdaQueryWrapper<UnSysMember>()
                            .eq(UnSysMember::getSystemId, context.systemId())
                            .and(q -> q.in(!accountIds.isEmpty(), UnSysMember::getAccountId, accountIds)
                                    .or().like(UnSysMember::getEmployeeNo, keyword)))
                    .stream().map(UnSysMember::getId).toList();
            if (memberIds.isEmpty()) return new Page<MemberView>(criteria.parse().getCurrent(), criteria.parse().getSize(), 0);
            where.in(UnSysTenantMember::getMemberId, memberIds);
        }
        Page<UnSysTenantMember> result = tenantMembers.page(criteria.parse(), where);
        List<MemberView> views = result.getRecords().stream().map(this::memberView).toList();
        return new Page<MemberView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(views);
    }

    /** 查询当前租户角色，供成员角色和模块权限配置使用。 */
    public List<RoleView> roles(String token, Long systemId, Long tenantId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        return roles.list(new LambdaQueryWrapper<UnSysRole>()
                        .eq(UnSysRole::getSystemId, context.systemId())
                        .eq(UnSysRole::getTenantId, context.tenantId())
                        .orderByAsc(UnSysRole::getId))
                .stream().map(row -> new RoleView(row.getId(), row.getCode(), row.getName(),
                        row.getDescription(), row.getStatus(), row.getUpdateTime())).toList();
    }

    private UnSysDepartment activeDepartment(Long systemId, Long tenantId, Long id) {
        UnSysDepartment row = departments.getById(id);
        if (row == null || !systemId.equals(row.getSystemId()) || !tenantId.equals(row.getTenantId())
                || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "部门不存在或已停用");
        return row;
    }

    /** 沿父级链检查闭环，避免部门树损坏后影响下属范围和审批候选人解析。 */
    private void ensureNoCycle(Long systemId, Long tenantId, Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (id != null && id.equals(cursor))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门上级不能是自身或下级");
            if (!visited.add(cursor) || visited.size() > 1000)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门层级存在循环");
            UnSysDepartment parent = departments.getById(cursor);
            if (parent == null || !systemId.equals(parent.getSystemId()) || !tenantId.equals(parent.getTenantId())) break;
            cursor = parent.getParentId();
        }
    }

    private DepartmentView departmentView(UnSysDepartment row) {
        return new DepartmentView(row.getId(), row.getParentId(), row.getCode(), row.getName(),
                row.getSortNo(), row.getStatus(), row.getUpdateTime());
    }

    private MemberView memberView(UnSysTenantMember link) {
        UnSysMember member = members.getById(link.getMemberId());
        return new MemberView(link.getId(), link.getMemberId(), member == null ? null : member.getAccountId(),
                member == null ? null : member.getDisplayName(), member == null ? null : member.getEmployeeNo(),
                link.getDepartmentId(), link.getSupervisorAccountId(), link.getIsAdmin(), link.getStatus(),
                link.getJoinTime());
    }

    private List<Long> accountsByKeyword(String keyword) {
        return accounts.list(new LambdaQueryWrapper<UnPlatAccount>()
                        .and(q -> q.like(UnPlatAccount::getUsername, keyword)
                                .or().like(UnPlatAccount::getDisplayName, keyword)))
                .stream().map(UnPlatAccount::getId).toList();
    }

    private String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9_-]{1,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "部门编码只能使用字母、数字、下划线和横线");
        return code;
    }

    private void audit(SystemManageDto.SystemContext context, String action, Long objectId, Object detail) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId());
        log.setActionCode(action);
        log.setObjectType("SYSTEM_DIRECTORY");
        log.setObjectId(objectId);
        try {
            log.setDetailJson(json.writeValueAsString(detail == null ? Map.of() : detail));
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "组织目录审计内容无法保存", ex);
        }
        logs.addOrUpdate(log);
    }

    private void changed(Long systemId) {
        configs.update(new LambdaUpdateWrapper<UnSysSystemConfig>()
                .eq(UnSysSystemConfig::getSystemId, systemId)
                .setSql("permission_revision = permission_revision + 1"));
    }
}
