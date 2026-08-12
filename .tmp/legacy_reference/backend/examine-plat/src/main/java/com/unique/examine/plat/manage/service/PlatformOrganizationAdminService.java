package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.Department;
import com.unique.examine.plat.base.entity.DepartmentClosure;
import com.unique.examine.plat.base.entity.MemberDepartment;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatAccountRoleMapper;
import com.unique.examine.plat.base.mapper.PlatDepartmentClosureMapper;
import com.unique.examine.plat.base.mapper.PlatDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatMemberDepartmentMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.AccountUpdate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.AccountView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.DepartmentCreate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.DepartmentUpdate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.DepartmentView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformOrganizationAdminService {
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "DISABLED", "LOCKED");
    private static final Set<String> DEPARTMENT_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final PlatAccountMapper accountMapper;
    private final PlatDepartmentMapper departmentMapper;
    private final PlatDepartmentClosureMapper closureMapper;
    private final PlatMemberDepartmentMapper memberDepartmentMapper;
    private final PlatAccountRoleMapper accountRoleMapper;
    private final PlatRoleMapper roleMapper;
    private final AuthzEpochService epochService;
    private final IdService idService;
    private final PlatformMutationSupport mutations;

    public PlatformOrganizationAdminService(
            PlatAccountMapper accountMapper,
            PlatDepartmentMapper departmentMapper,
            PlatDepartmentClosureMapper closureMapper,
            PlatMemberDepartmentMapper memberDepartmentMapper,
            PlatAccountRoleMapper accountRoleMapper,
            PlatRoleMapper roleMapper,
            AuthzEpochService epochService,
            IdService idService,
            PlatformMutationSupport mutations
    ) {
        this.accountMapper = accountMapper;
        this.departmentMapper = departmentMapper;
        this.closureMapper = closureMapper;
        this.memberDepartmentMapper = memberDepartmentMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.roleMapper = roleMapper;
        this.epochService = epochService;
        this.idService = idService;
        this.mutations = mutations;
    }

    public PageResult<AccountView> listAccounts(
            Integer pageValue,
            Integer sizeValue,
            String keyword,
            String status
    ) {
        var page = PlatformMutationSupport.page(pageValue);
        var size = PlatformMutationSupport.size(sizeValue);
        var normalizedStatus = normalizeStatus(status, ACCOUNT_STATUSES);
        var query = Wrappers.<Account>lambdaQuery()
                .eq(normalizedStatus != null, Account::getStatus, normalizedStatus)
                .and(keyword != null && !keyword.isBlank(), value -> value
                        .like(Account::getUsername, keyword.trim())
                        .or()
                        .like(Account::getDisplayName, keyword.trim())
                        .or()
                        .like(Account::getEmail, keyword.trim()))
                .orderByDesc(Account::getCreatedAt)
                .orderByDesc(Account::getId);
        var result = accountMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::accountView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public AccountView updateAccount(
            AuthenticatedSession session,
            String accountId,
            AccountUpdate request,
            ClientRequest client
    ) {
        var account = requireAccount(PlatformMutationSupport.id(accountId));
        requireVersion(account.getVersion(), request.version());
        var before = accountView(account);
        var departmentIds = request.departmentIds() == null ? null : parseIds(request.departmentIds());
        var roleIds = request.roleIds() == null ? null : parseIds(request.roleIds());
        if (departmentIds != null) {
            requirePlatformDepartments(departmentIds);
        }
        if (roleIds != null) {
            requirePlatformRoles(roleIds);
        }
        var requestedStatus = request.status() == null
                ? account.getStatus()
                : normalizeStatus(request.status(), ACCOUNT_STATUSES);
        requireRootContinuity(account, requestedStatus, roleIds, LocalDateTime.now());

        var statusChanged = false;
        if (request.displayName() != null) {
            account.setDisplayName(PlatformMutationSupport.required(request.displayName(), "displayName", 120));
        }
        if (request.email() != null) {
            var email = nullableValue(request.email(), "email", 254);
            account.setEmail(email);
            account.setEmailNormalized(email == null ? null : normalize(email));
        }
        if (request.mobile() != null) {
            account.setPhone(nullableValue(request.mobile(), "mobile", 32));
        }
        if (request.status() != null) {
            statusChanged = !Objects.equals(account.getStatus(), requestedStatus);
            account.setStatus(requestedStatus);
        }
        account.setUpdatedAt(LocalDateTime.now());
        account.setUpdatedBy(session.accountId());
        if (accountMapper.updateById(account) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        clearNullableAccountFields(account.getId(), request);

        var departmentChanged = departmentIds != null
                && !new LinkedHashSet<>(before.departmentIds()).equals(stringIds(departmentIds));
        var roleChanged = roleIds != null
                && !new LinkedHashSet<>(before.roleIds()).equals(stringIds(roleIds));
        var now = LocalDateTime.now();
        if (departmentChanged) {
            replaceDepartments(account.getId(), departmentIds, session.accountId(), now);
        }
        if (roleChanged) {
            replaceRoles(account.getId(), roleIds, session.accountId(), now);
        }
        if (statusChanged || departmentChanged || roleChanged) {
            epochService.bumpPlatform(session.accountId());
        }

        var after = accountView(requireAccount(account.getId()));
        mutations.success(
                session, client, "PLATFORM_ACCOUNT", after.id(), "PLATFORM_ACCOUNT_UPDATED",
                before, after, null
        );
        return after;
    }

    public PageResult<DepartmentView> listDepartments(
            Integer pageValue,
            Integer sizeValue,
            String keyword,
            String status
    ) {
        var page = PlatformMutationSupport.page(pageValue);
        var size = PlatformMutationSupport.size(sizeValue);
        var normalizedStatus = normalizeStatus(status, DEPARTMENT_STATUSES);
        var query = Wrappers.<Department>lambdaQuery()
                .eq(Department::getScopeType, "PLATFORM")
                .eq(Department::getScopeKey, 0L)
                .eq(normalizedStatus != null, Department::getStatus, normalizedStatus)
                .and(keyword != null && !keyword.isBlank(), value -> value
                        .like(Department::getDepartmentCode, keyword.trim())
                        .or()
                        .like(Department::getName, keyword.trim()))
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId);
        var result = departmentMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::departmentView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public DepartmentView createDepartment(
            AuthenticatedSession session,
            DepartmentCreate request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutations.idempotent(
                session.accountId(),
                "DEPARTMENT_CREATE",
                "new",
                idempotencyKey,
                request,
                DepartmentView.class,
                () -> createDepartmentOnce(session, request, idempotencyKey, client)
        );
    }

    @Transactional
    public DepartmentView updateDepartment(
            AuthenticatedSession session,
            String departmentId,
            DepartmentUpdate request,
            ClientRequest client
    ) {
        var department = requirePlatformDepartment(PlatformMutationSupport.id(departmentId));
        requireVersion(department.getVersion(), request.version());
        var before = departmentView(department);
        var parentId = optionalId(request.parentId());
        if (parentId != null) {
            requirePlatformDepartment(parentId);
            if (parentId.equals(department.getId()) || isDescendant(department.getId(), parentId)) {
                throw new com.unique.examine.core.error.BusinessException(
                        "STATE_TRANSITION_INVALID",
                        "部门不能移动到自身或其子部门下",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
        }

        var parentChanged = !Objects.equals(department.getParentId(), parentId);
        department.setName(PlatformMutationSupport.required(request.name(), "name", 160));
        department.setStatus(normalizeStatus(request.status(), DEPARTMENT_STATUSES));
        department.setParentId(parentId);
        department.setUpdatedAt(LocalDateTime.now());
        department.setUpdatedBy(session.accountId());
        if (departmentMapper.updateById(department) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        if (parentId == null) {
            departmentMapper.update(null, Wrappers.<Department>lambdaUpdate()
                    .eq(Department::getId, department.getId())
                    .eq(Department::getScopeType, "PLATFORM")
                    .eq(Department::getScopeKey, 0L)
                    .set(Department::getParentId, null));
        }
        if (parentChanged) {
            moveClosure(department.getId(), parentId, session.accountId());
        }
        epochService.bumpPlatform(session.accountId());
        var after = departmentView(requirePlatformDepartment(department.getId()));
        mutations.success(
                session, client, "PLATFORM_DEPARTMENT", after.id(), "PLATFORM_DEPARTMENT_UPDATED",
                before, after, null
        );
        return after;
    }

    private DepartmentView createDepartmentOnce(
            AuthenticatedSession session,
            DepartmentCreate request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var code = normalizeCode(request.code());
        var name = PlatformMutationSupport.required(request.name(), "name", 160);
        var parentId = optionalId(request.parentId());
        if (parentId != null) {
            requirePlatformDepartment(parentId);
        }
        if (departmentMapper.selectCount(Wrappers.<Department>lambdaQuery()
                .eq(Department::getScopeType, "PLATFORM")
                .eq(Department::getScopeKey, 0L)
                .eq(Department::getDepartmentCode, code)) > 0) {
            throw new com.unique.examine.core.error.BusinessException(
                    "DATA_CONFLICT", "部门编码已存在", HttpStatus.CONFLICT
            );
        }

        var now = LocalDateTime.now();
        var department = new Department();
        department.setId(idService.nextId());
        department.setScopeType("PLATFORM");
        department.setScopeKey(0L);
        department.setParentId(parentId);
        department.setDepartmentCode(code);
        department.setName(name);
        department.setSortOrder(0);
        department.setStatus("ACTIVE");
        department.setCreatedAt(now);
        department.setCreatedBy(session.accountId());
        department.setUpdatedAt(now);
        department.setUpdatedBy(session.accountId());
        department.setVersion(0L);
        departmentMapper.insert(department);
        insertClosure(department.getId(), department.getId(), 0, session.accountId(), now);
        if (parentId != null) {
            var ancestors = closureMapper.selectList(Wrappers.<DepartmentClosure>lambdaQuery()
                    .eq(DepartmentClosure::getScopeType, "PLATFORM")
                    .eq(DepartmentClosure::getScopeKey, 0L)
                    .eq(DepartmentClosure::getDescendantId, parentId));
            for (var ancestor : ancestors) {
                insertClosure(
                        ancestor.getAncestorId(), department.getId(), ancestor.getDepth() + 1,
                        session.accountId(), now
                );
            }
        }
        epochService.bumpPlatform(session.accountId());
        var after = departmentView(department);
        mutations.success(
                session, client, "PLATFORM_DEPARTMENT", after.id(), "PLATFORM_DEPARTMENT_CREATED",
                null, after, idempotencyKey
        );
        return after;
    }

    private void moveClosure(long departmentId, Long parentId, long actorAccountId) {
        var subtree = closureMapper.selectList(Wrappers.<DepartmentClosure>lambdaQuery()
                .eq(DepartmentClosure::getScopeType, "PLATFORM")
                .eq(DepartmentClosure::getScopeKey, 0L)
                .eq(DepartmentClosure::getAncestorId, departmentId));
        var oldAncestors = closureMapper.selectList(Wrappers.<DepartmentClosure>lambdaQuery()
                .eq(DepartmentClosure::getScopeType, "PLATFORM")
                .eq(DepartmentClosure::getScopeKey, 0L)
                .eq(DepartmentClosure::getDescendantId, departmentId)
                .ne(DepartmentClosure::getAncestorId, departmentId));
        if (!oldAncestors.isEmpty() && !subtree.isEmpty()) {
            closureMapper.delete(Wrappers.<DepartmentClosure>lambdaQuery()
                    .eq(DepartmentClosure::getScopeType, "PLATFORM")
                    .eq(DepartmentClosure::getScopeKey, 0L)
                    .in(DepartmentClosure::getAncestorId,
                            oldAncestors.stream().map(DepartmentClosure::getAncestorId).toList())
                    .in(DepartmentClosure::getDescendantId,
                            subtree.stream().map(DepartmentClosure::getDescendantId).toList()));
        }
        if (parentId == null) {
            return;
        }

        var newAncestors = closureMapper.selectList(Wrappers.<DepartmentClosure>lambdaQuery()
                .eq(DepartmentClosure::getScopeType, "PLATFORM")
                .eq(DepartmentClosure::getScopeKey, 0L)
                .eq(DepartmentClosure::getDescendantId, parentId));
        var now = LocalDateTime.now();
        for (var ancestor : newAncestors) {
            for (var descendant : subtree) {
                insertClosure(
                        ancestor.getAncestorId(),
                        descendant.getDescendantId(),
                        ancestor.getDepth() + 1 + descendant.getDepth(),
                        actorAccountId,
                        now
                );
            }
        }
    }

    private boolean isDescendant(long ancestorId, long possibleDescendantId) {
        return closureMapper.selectCount(Wrappers.<DepartmentClosure>lambdaQuery()
                .eq(DepartmentClosure::getScopeType, "PLATFORM")
                .eq(DepartmentClosure::getScopeKey, 0L)
                .eq(DepartmentClosure::getAncestorId, ancestorId)
                .eq(DepartmentClosure::getDescendantId, possibleDescendantId)) > 0;
    }

    private void insertClosure(long ancestorId, long descendantId, int depth, long actorAccountId, LocalDateTime now) {
        var closure = new DepartmentClosure();
        closure.setId(idService.nextId());
        closure.setScopeType("PLATFORM");
        closure.setScopeKey(0L);
        closure.setAncestorId(ancestorId);
        closure.setDescendantId(descendantId);
        closure.setDepth(depth);
        closure.setCreatedAt(now);
        closure.setCreatedBy(actorAccountId);
        closureMapper.insert(closure);
    }

    private void replaceDepartments(long accountId, LinkedHashSet<Long> departmentIds, long actorAccountId, LocalDateTime now) {
        memberDepartmentMapper.update(null, Wrappers.<MemberDepartment>lambdaUpdate()
                .eq(MemberDepartment::getScopeType, "PLATFORM")
                .eq(MemberDepartment::getScopeKey, 0L)
                .eq(MemberDepartment::getAccountId, accountId)
                .set(MemberDepartment::getDeletedAt, now)
                .set(MemberDepartment::getDeletedBy, actorAccountId)
                .set(MemberDepartment::getUpdatedAt, now)
                .set(MemberDepartment::getUpdatedBy, actorAccountId));
        var primary = true;
        for (var departmentId : departmentIds) {
            var binding = new MemberDepartment();
            binding.setId(idService.nextId());
            binding.setScopeType("PLATFORM");
            binding.setScopeKey(0L);
            binding.setAccountId(accountId);
            binding.setDepartmentId(departmentId);
            binding.setIsPrimary(primary);
            binding.setCreatedAt(now);
            binding.setCreatedBy(actorAccountId);
            binding.setUpdatedAt(now);
            binding.setUpdatedBy(actorAccountId);
            binding.setVersion(0L);
            memberDepartmentMapper.insert(binding);
            primary = false;
        }
    }

    private void replaceRoles(long accountId, LinkedHashSet<Long> roleIds, long actorAccountId, LocalDateTime now) {
        var existing = accountRoleMapper.selectList(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getScopeType, "PLATFORM")
                .eq(AccountRole::getScopeKey, 0L)
                .eq(AccountRole::getAccountId, accountId));
        var byRole = existing.stream().collect(Collectors.toMap(AccountRole::getRoleId, Function.identity()));
        for (var binding : existing) {
            if (!roleIds.contains(binding.getRoleId()) && isActive(binding, now)) {
                accountRoleMapper.update(null, Wrappers.<AccountRole>lambdaUpdate()
                        .eq(AccountRole::getId, binding.getId())
                        .set(AccountRole::getValidUntil, now));
            }
        }
        for (var roleId : roleIds) {
            var binding = byRole.get(roleId);
            if (binding == null) {
                binding = new AccountRole();
                binding.setId(idService.nextId());
                binding.setScopeType("PLATFORM");
                binding.setScopeKey(0L);
                binding.setAccountId(accountId);
                binding.setRoleId(roleId);
                binding.setValidFrom(now);
                binding.setCreatedAt(now);
                binding.setCreatedBy(actorAccountId);
                accountRoleMapper.insert(binding);
            } else if (!isActive(binding, now)) {
                accountRoleMapper.update(null, Wrappers.<AccountRole>lambdaUpdate()
                        .eq(AccountRole::getId, binding.getId())
                        .set(AccountRole::getValidFrom, now)
                        .set(AccountRole::getValidUntil, null));
            }
        }
    }

    private void requireRootContinuity(
            Account target,
            String requestedStatus,
            LinkedHashSet<Long> requestedRoleIds,
            LocalDateTime now
    ) {
        var rootRoleIds = roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                        .eq(Role::getScopeType, "PLATFORM")
                        .eq(Role::getScopeKey, 0L)
                        .eq(Role::getRoleType, "ROOT")
                        .eq(Role::getStatus, "ACTIVE"))
                .stream()
                .map(Role::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (rootRoleIds.isEmpty()) {
            throw new BusinessException(
                    "LAST_PLATFORM_ROOT_REQUIRED",
                    "平台必须保留至少一个有效 Root 角色",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }

        var targetBindings = accountRoleMapper.selectList(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getScopeType, "PLATFORM")
                .eq(AccountRole::getScopeKey, 0L)
                .eq(AccountRole::getAccountId, target.getId())
                .in(AccountRole::getRoleId, rootRoleIds));
        var targetIsActiveRoot = "ACTIVE".equals(target.getStatus())
                && target.getDeletedAt() == null
                && targetBindings.stream().anyMatch(binding -> isActive(binding, now));
        if (!targetIsActiveRoot) {
            return;
        }

        var targetKeepsRoot = "ACTIVE".equals(requestedStatus)
                && (requestedRoleIds == null || requestedRoleIds.stream().anyMatch(rootRoleIds::contains));
        if (targetKeepsRoot) {
            return;
        }

        var otherBindings = accountRoleMapper.selectList(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getScopeType, "PLATFORM")
                .eq(AccountRole::getScopeKey, 0L)
                .ne(AccountRole::getAccountId, target.getId())
                .in(AccountRole::getRoleId, rootRoleIds)
                .le(AccountRole::getValidFrom, now)
                .and(query -> query.isNull(AccountRole::getValidUntil).or().gt(AccountRole::getValidUntil, now)));
        var otherAccountIds = otherBindings.stream().map(AccountRole::getAccountId).distinct().toList();
        var hasOtherActiveRoot = !otherAccountIds.isEmpty()
                && accountMapper.selectByIds(otherAccountIds).stream().anyMatch(account ->
                        "ACTIVE".equals(account.getStatus()) && account.getDeletedAt() == null
                );
        if (!hasOtherActiveRoot) {
            throw new BusinessException(
                    "LAST_PLATFORM_ROOT_REQUIRED",
                    "不能停用或移除最后一个有效平台 Root",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private AccountView accountView(Account account) {
        var departmentIds = memberDepartmentMapper.selectList(Wrappers.<MemberDepartment>lambdaQuery()
                        .eq(MemberDepartment::getScopeType, "PLATFORM")
                        .eq(MemberDepartment::getScopeKey, 0L)
                        .eq(MemberDepartment::getAccountId, account.getId())
                        .orderByDesc(MemberDepartment::getIsPrimary)
                        .orderByAsc(MemberDepartment::getCreatedAt))
                .stream()
                .map(MemberDepartment::getDepartmentId)
                .map(String::valueOf)
                .toList();
        var now = LocalDateTime.now();
        var roleIds = accountRoleMapper.selectList(Wrappers.<AccountRole>lambdaQuery()
                        .eq(AccountRole::getScopeType, "PLATFORM")
                        .eq(AccountRole::getScopeKey, 0L)
                        .eq(AccountRole::getAccountId, account.getId())
                        .le(AccountRole::getValidFrom, now)
                        .and(value -> value.isNull(AccountRole::getValidUntil).or().gt(AccountRole::getValidUntil, now))
                        .orderByAsc(AccountRole::getRoleId))
                .stream()
                .map(AccountRole::getRoleId)
                .map(String::valueOf)
                .toList();
        return new AccountView(
                Long.toString(account.getId()),
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                account.getPhone(),
                account.getStatus(),
                departmentIds,
                roleIds,
                Long.toString(account.getVersion())
        );
    }

    private DepartmentView departmentView(Department department) {
        var memberCount = memberDepartmentMapper.selectCount(Wrappers.<MemberDepartment>lambdaQuery()
                .eq(MemberDepartment::getScopeType, "PLATFORM")
                .eq(MemberDepartment::getScopeKey, 0L)
                .eq(MemberDepartment::getDepartmentId, department.getId()));
        return new DepartmentView(
                Long.toString(department.getId()),
                department.getParentId() == null ? null : Long.toString(department.getParentId()),
                department.getName(),
                department.getDepartmentCode(),
                department.getStatus(),
                memberCount,
                Long.toString(department.getVersion())
        );
    }

    private Account requireAccount(long id) {
        var account = accountMapper.selectById(id);
        if (account == null) {
            throw PlatformMutationSupport.notFound("账号");
        }
        return account;
    }

    private Department requirePlatformDepartment(long id) {
        var department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery()
                .eq(Department::getScopeType, "PLATFORM")
                .eq(Department::getScopeKey, 0L)
                .eq(Department::getId, id));
        if (department == null) {
            throw PlatformMutationSupport.notFound("部门");
        }
        return department;
    }

    private void requirePlatformDepartments(Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        var count = departmentMapper.selectCount(Wrappers.<Department>lambdaQuery()
                .eq(Department::getScopeType, "PLATFORM")
                .eq(Department::getScopeKey, 0L)
                .eq(Department::getStatus, "ACTIVE")
                .in(Department::getId, ids));
        if (count != ids.size()) {
            throw PlatformMutationSupport.notFound("部门");
        }
    }

    private void requirePlatformRoles(Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        var count = roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "PLATFORM")
                .eq(Role::getScopeKey, 0L)
                .eq(Role::getStatus, "ACTIVE")
                .in(Role::getId, ids));
        if (count != ids.size()) {
            throw PlatformMutationSupport.notFound("角色");
        }
    }

    private void clearNullableAccountFields(long accountId, AccountUpdate request) {
        if (request.email() != null && request.email().isBlank()) {
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                    .eq(Account::getId, accountId)
                    .set(Account::getEmail, null)
                    .set(Account::getEmailNormalized, null));
        }
        if (request.mobile() != null && request.mobile().isBlank()) {
            accountMapper.update(null, Wrappers.<Account>lambdaUpdate()
                    .eq(Account::getId, accountId)
                    .set(Account::getPhone, null));
        }
    }

    private static boolean isActive(AccountRole binding, LocalDateTime now) {
        return !binding.getValidFrom().isAfter(now)
                && (binding.getValidUntil() == null || binding.getValidUntil().isAfter(now));
    }

    private static LinkedHashSet<Long> parseIds(List<String> values) {
        var result = new LinkedHashSet<Long>();
        for (var value : values) {
            result.add(PlatformMutationSupport.id(value));
        }
        return result;
    }

    private static LinkedHashSet<String> stringIds(Set<Long> values) {
        return values.stream().map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Long optionalId(String value) {
        return value == null || value.isBlank() ? null : PlatformMutationSupport.id(value);
    }

    private static String nullableValue(String value, String field, int maxLength) {
        var normalized = PlatformMutationSupport.optional(value, field, maxLength);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static String normalizeCode(String value) {
        var code = normalize(PlatformMutationSupport.required(value, "code", 64));
        if (!code.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw PlatformMutationSupport.validation("code 格式不合法");
        }
        return code;
    }

    private static String normalizeStatus(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var status = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(status)) {
            throw PlatformMutationSupport.validation("status 不合法");
        }
        return status;
    }

    private static void requireVersion(long actual, String requested) {
        if (actual != PlatformMutationSupport.version(requested)) {
            throw PlatformMutationSupport.versionConflict();
        }
    }
}
