package com.unique.unexamine.plat.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatRole;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.base.service.IUnPlatRoleService;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.DepartmentRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.DepartmentView;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberRequest;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberSearch;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.MemberView;
import com.unique.unexamine.plat.manage.dto.PlatformDirectoryDto.RoleView;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台组织目录管理；保存后立即生效并留下平台操作审计。 */
@Service
public class PlatformDirectoryManageService {
    private final AuthManageService auth;
    private final IUnPlatDepartmentService departments;
    private final IUnPlatMemberService members;
    private final IUnPlatAccountService accounts;
    private final IUnPlatRoleService roles;
    private final IUnPlatLogOptService logs;
    private final ObjectMapper json;

    /** 注入平台管理员、目录基础服务和审计日志。 */
    public PlatformDirectoryManageService(AuthManageService auth, IUnPlatDepartmentService departments,
                                          IUnPlatMemberService members, IUnPlatAccountService accounts,
                                          IUnPlatRoleService roles, IUnPlatLogOptService logs,
                                          ObjectMapper json) {
        this.auth = auth;
        this.departments = departments;
        this.members = members;
        this.accounts = accounts;
        this.roles = roles;
        this.logs = logs;
        this.json = json;
    }

    /** 查询平台有效部门，供平台应用范围和成员维护使用。 */
    public List<DepartmentView> departments(String token) {
        auth.requireAccount(token);
        return departments.list(new LambdaQueryWrapper<UnPlatDepartment>()
                        .eq(UnPlatDepartment::getStatus, "ACTIVE")
                        .orderByAsc(UnPlatDepartment::getSortNo)
                        .orderByAsc(UnPlatDepartment::getId))
                .stream().map(this::departmentView).toList();
    }

    /** 保存平台部门并校验父级、编码及组织树闭环。 */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentView saveDepartment(String token, DepartmentRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        if (request == null || request.code() == null || request.name() == null
                || request.name().isBlank() || request.name().length() > 100
                || request.sortNo() != null && request.sortNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "部门内容不能为空");
        String code = normalizeCode(request.code());
        if (request.id() != null && request.id().equals(request.parentId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "部门不能以自身作为上级");
        if (request.parentId() != null) activeDepartment(request.parentId());
        ensureNoCycle(request.id(), request.parentId());
        long duplicate = departments.count(new LambdaQueryWrapper<UnPlatDepartment>()
                .eq(UnPlatDepartment::getCode, code)
                .ne(request.id() != null, UnPlatDepartment::getId, request.id()));
        if (duplicate > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门编码已存在");
        UnPlatDepartment row = request.id() == null ? new UnPlatDepartment() : departments.queryById(request.id());
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "部门不存在");
        if (request.id() != null && !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门已停用");
        if (request.id() == null) row.setCreateTime(LocalDateTime.now());
        row.setParentId(request.parentId());
        row.setCode(code);
        row.setName(request.name().trim());
        row.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        row.setStatus("ACTIVE");
        row.setUpdateTime(LocalDateTime.now());
        try {
            departments.addOrUpdate(row);
        } catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门编码已存在");
        }
        audit(operator.getId(), request.id() == null ? "PLATFORM_DEPARTMENT_CREATE" : "PLATFORM_DEPARTMENT_UPDATE",
                row.getId(), Map.of("code", row.getCode(), "name", row.getName()));
        return departmentView(row);
    }

    /** 停用平台部门；下级部门或仍在使用的成员必须先处理。 */
    @Transactional(rollbackFor = Exception.class)
    public DepartmentView disableDepartment(String token, Long departmentId) {
        var operator = auth.requirePlatformAdmin(token);
        UnPlatDepartment row = activeDepartment(departmentId);
        long children = departments.count(new LambdaQueryWrapper<UnPlatDepartment>()
                .eq(UnPlatDepartment::getParentId, departmentId)
                .eq(UnPlatDepartment::getStatus, "ACTIVE"));
        if (children > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "请先停用下级部门");
        long memberCount = members.count(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getDepartmentId, departmentId)
                .eq(UnPlatMember::getStatus, "ACTIVE"));
        if (memberCount > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "请先调整部门成员");
        row.setStatus("DISABLED");
        row.setUpdateTime(LocalDateTime.now());
        departments.addOrUpdate(row);
        audit(operator.getId(), "PLATFORM_DEPARTMENT_DISABLE", row.getId(), Map.of());
        return departmentView(row);
    }

    /** 分页查询平台成员，支持账号、员工编号、部门和状态筛选。 */
    public Page<MemberView> members(String token, MemberSearch search) {
        auth.requirePlatformAdmin(token);
        MemberSearch criteria = search == null ? new MemberSearch() : search;
        var where = new LambdaQueryWrapper<UnPlatMember>().orderByDesc(UnPlatMember::getId);
        if (criteria.getDepartmentId() != null) where.eq(UnPlatMember::getDepartmentId, criteria.getDepartmentId());
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank())
            where.eq(UnPlatMember::getStatus, normalizeStatus(criteria.getStatus()));
        String keyword = criteria.getKeyword() == null ? null : criteria.getKeyword().trim();
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length() > 100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "成员搜索内容过长");
            List<Long> accountIds = accounts.list(new LambdaQueryWrapper<UnPlatAccount>()
                    .and(q -> q.like(UnPlatAccount::getUsername, keyword)
                            .or().like(UnPlatAccount::getDisplayName, keyword)))
                    .stream().map(UnPlatAccount::getId).toList();
            where.and(q -> {
                if (!accountIds.isEmpty()) q.in(UnPlatMember::getAccountId, accountIds).or();
                q.like(UnPlatMember::getEmployeeNo, keyword);
            });
        }
        Page<UnPlatMember> result = members.page(criteria.parse(), where);
        return new Page<MemberView>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(result.getRecords().stream().map(this::memberView).toList());
    }

    /** 保存账号与平台成员关系；同一账号只允许有一个有效平台成员。 */
    @Transactional(rollbackFor = Exception.class)
    public MemberView saveMember(String token, MemberRequest request) {
        var operator = auth.requirePlatformAdmin(token);
        if (request == null || request.accountId() == null || request.accountId() < 1
                || request.departmentId() != null && request.departmentId() < 1
                || request.employeeNo() != null && request.employeeNo().length() > 80)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "成员内容不能为空");
        UnPlatAccount account = accounts.queryById(request.accountId());
        if (account == null || !"ACTIVE".equals(account.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台账号不存在或已停用");
        if (request.departmentId() != null) activeDepartment(request.departmentId());
        String status = normalizeStatus(request.status());
        long duplicate = members.count(new LambdaQueryWrapper<UnPlatMember>()
                .eq(UnPlatMember::getAccountId, request.accountId())
                .ne(request.id() != null, UnPlatMember::getId, request.id()));
        if (duplicate > 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "账号已存在平台成员关系");
        UnPlatMember row = request.id() == null ? new UnPlatMember() : members.queryById(request.id());
        if (row == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台成员不存在");
        if (request.id() == null) row.setCreateTime(LocalDateTime.now());
        row.setAccountId(request.accountId());
        row.setDepartmentId(request.departmentId());
        row.setEmployeeNo(request.employeeNo() == null ? null : request.employeeNo().trim());
        row.setStatus(status);
        row.setUpdateTime(LocalDateTime.now());
        members.addOrUpdate(row);
        audit(operator.getId(), request.id() == null ? "PLATFORM_MEMBER_CREATE" : "PLATFORM_MEMBER_UPDATE",
                row.getId(), Map.of("accountId", row.getAccountId(), "status", row.getStatus()));
        return memberView(row);
    }

    /** 查询平台角色摘要；角色菜单与数据范围由平台权限配置继续维护。 */
    public List<RoleView> roles(String token) {
        auth.requirePlatformAdmin(token);
        return roles.list(new LambdaQueryWrapper<UnPlatRole>().orderByAsc(UnPlatRole::getId))
                .stream().map(row -> new RoleView(row.getId(), row.getCode(), row.getName(), row.getStatus(),
                        row.getDescription(), row.getUpdateTime())).toList();
    }

    private UnPlatDepartment activeDepartment(Long id) {
        UnPlatDepartment row = departments.queryById(id);
        if (row == null || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "部门不存在或已停用");
        return row;
    }

    /** 沿父级链检查闭环，避免平台应用范围和成员组织解析进入死循环。 */
    private void ensureNoCycle(Long id, Long parentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null) {
            if (id != null && id.equals(cursor))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门上级不能是自身或下级");
            if (!visited.add(cursor) || visited.size() > 1000)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "部门层级存在循环");
            UnPlatDepartment parent = departments.queryById(cursor);
            if (parent == null) break;
            cursor = parent.getParentId();
        }
    }

    private DepartmentView departmentView(UnPlatDepartment row) {
        return new DepartmentView(row.getId(), row.getParentId(), row.getCode(), row.getName(),
                row.getSortNo(), row.getStatus(), row.getUpdateTime());
    }

    private MemberView memberView(UnPlatMember row) {
        UnPlatAccount account = accounts.queryById(row.getAccountId());
        return new MemberView(row.getId(), row.getAccountId(), account == null ? null : account.getUsername(),
                account == null ? null : account.getDisplayName(), row.getDepartmentId(), row.getEmployeeNo(),
                row.getStatus(), row.getUpdateTime());
    }

    private String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9_-]{1,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "部门编码只能使用字母、数字、下划线和横线");
        return code;
    }

    private String normalizeStatus(String value) {
        String status = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "DISABLED").contains(status))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "状态无效");
        return status;
    }

    private void audit(Long accountId, String action, Long objectId, Object detail) {
        UnPlatLogOpt log = new UnPlatLogOpt();
        log.setAccountId(accountId);
        log.setActionCode(action);
        log.setObjectType("PLATFORM_DIRECTORY");
        log.setObjectId(objectId);
        log.setCreateTime(LocalDateTime.now());
        try {
            log.setDetailJson(json.writeValueAsString(detail == null ? Map.of() : detail));
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "平台组织审计内容无法保存", ex);
        }
        logs.addOrUpdate(log);
    }
}
