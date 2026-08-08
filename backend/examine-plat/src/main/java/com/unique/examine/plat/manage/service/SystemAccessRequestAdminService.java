package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.AccessRequest;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.Member;
import com.unique.examine.plat.base.entity.MemberRole;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.mapper.PlatAccessRequestMapper;
import com.unique.examine.plat.base.mapper.PlatAccountMapper;
import com.unique.examine.plat.base.mapper.PlatMemberMapper;
import com.unique.examine.plat.base.mapper.PlatMemberRoleMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatSystemMapper;
import com.unique.examine.plat.base.mapper.PlatTenantMapper;
import com.unique.examine.plat.manage.dto.SystemAdminRequests;
import com.unique.examine.plat.manage.vo.SystemAdminViews;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class SystemAccessRequestAdminService {
    private final PlatAccessRequestMapper accessRequestMapper;
    private final PlatAccountMapper accountMapper;
    private final PlatSystemMapper systemMapper;
    private final PlatTenantMapper tenantMapper;
    private final PlatRoleMapper roleMapper;
    private final PlatMemberMapper memberMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final PlatMemberRoleMapper memberRoleMapper;
    private final IdService idService;
    private final AuthzEpochService epochService;
    private final SystemAdminScopeSupport scopeSupport;
    private final SystemAdminMutationSupport mutationSupport;

    public SystemAccessRequestAdminService(
            PlatAccessRequestMapper accessRequestMapper,
            PlatAccountMapper accountMapper,
            PlatSystemMapper systemMapper,
            PlatTenantMapper tenantMapper,
            PlatRoleMapper roleMapper,
            PlatMemberMapper memberMapper,
            PlatMemberTenantMapper memberTenantMapper,
            PlatMemberRoleMapper memberRoleMapper,
            IdService idService,
            AuthzEpochService epochService,
            SystemAdminScopeSupport scopeSupport,
            SystemAdminMutationSupport mutationSupport
    ) {
        this.accessRequestMapper = accessRequestMapper;
        this.accountMapper = accountMapper;
        this.systemMapper = systemMapper;
        this.tenantMapper = tenantMapper;
        this.roleMapper = roleMapper;
        this.memberMapper = memberMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.idService = idService;
        this.epochService = epochService;
        this.scopeSupport = scopeSupport;
        this.mutationSupport = mutationSupport;
    }

    public SystemAdminViews.Page<SystemAdminViews.AccessRequest> accessRequests(
            AuthenticatedSession session,
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        requireSystem(systemId);
        var systemWide = scopeSupport.systemWide(session, systemId, "system.access.review");
        var accountIds = matchingAccountIds(keyword);
        var query = Wrappers.<AccessRequest>lambdaQuery()
                .eq(AccessRequest::getSystemId, systemId)
                .eq(!systemWide, AccessRequest::getTargetTenantId, scopeSupport.tenantId(session))
                .eq(status != null && !status.isBlank(), AccessRequest::getStatus, status)
                .in(accountIds != null && !accountIds.isEmpty(), AccessRequest::getAccountId, accountIds)
                .eq(accountIds != null && accountIds.isEmpty(), AccessRequest::getId, -1L)
                .orderByAsc(AccessRequest::getStatus)
                .orderByDesc(AccessRequest::getCreatedAt);
        var result = accessRequestMapper.selectPage(new Page<>(page(page), size(size)), query);
        var items = result.getRecords().stream().map(this::accessRequestView).toList();
        return new SystemAdminViews.Page<>(
                items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal()
        );
    }

    @Transactional
    public SystemAdminViews.AccessRequest approve(
            AuthenticatedSession session,
            long systemId,
            long requestId,
            SystemAdminRequests.ReviewAccessRequest request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutationSupport.idempotent(
                systemId + ":access-request:" + requestId + ":approve",
                idempotencyKey,
                request,
                SystemAdminViews.AccessRequest.class,
                () -> approveNow(session, systemId, requestId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.AccessRequest approveNow(
            AuthenticatedSession session,
            long systemId,
            long requestId,
            SystemAdminRequests.ReviewAccessRequest command,
            String idempotencyKey,
            ClientRequest client
    ) {
        var accessRequest = lockSubmittedRequest(systemId, requestId, command.version());
        var system = requireSystem(systemId);
        if (!Set.of("ACTIVE", "INITIALIZING").contains(system.getStatus())) {
            throw SystemAdminMutationSupport.invalidState("系统当前状态不允许批准访问申请");
        }
        var account = requireActiveAccount(accessRequest.getAccountId());
        var tenantIds = resolveTenantIds(systemId, accessRequest, command.tenantIds());
        requireReviewScope(session, systemId, tenantIds);
        var roles = resolveRoles(systemId, accessRequest, command.roleIds(), tenantIds);
        var before = accessRequestView(accessRequest);
        var now = LocalDateTime.now();
        var member = createOrRestoreMember(
                systemId, account, tenantIds.iterator().next(), session.accountId(), now
        );
        grantTenants(systemId, member.getId(), tenantIds, session.accountId(), now);
        grantRoles(systemId, member.getId(), roles, session.accountId(), now);

        var updated = accessRequestMapper.update(null, Wrappers.<AccessRequest>lambdaUpdate()
                .eq(AccessRequest::getId, requestId)
                .eq(AccessRequest::getSystemId, systemId)
                .eq(AccessRequest::getStatus, "SUBMITTED")
                .eq(AccessRequest::getVersion, SystemAdminMutationSupport.version(command.version()))
                .set(AccessRequest::getStatus, "APPROVED")
                .set(AccessRequest::getReviewedAt, now)
                .set(AccessRequest::getReviewedBy, session.accountId())
                .set(AccessRequest::getDecisionReason, command.reason().trim())
                .set(AccessRequest::getResolvedMemberId, member.getId())
                .set(AccessRequest::getUpdatedAt, now)
                .set(AccessRequest::getUpdatedBy, session.accountId())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        applyReviewState(accessRequest, "APPROVED", command.reason(), member.getId(), session.accountId(), now);
        epochService.bumpSystem(systemId, session.accountId());
        var after = accessRequestView(accessRequest);
        mutationSupport.success(
                session, systemId, "ACCESS_REQUEST", after.id(), "SYSTEM_ACCESS_REQUEST_APPROVE",
                before, after, client
        );
        var payload = new LinkedHashMap<String, Object>();
        payload.put("requestId", after.id());
        payload.put("memberId", Long.toString(member.getId()));
        payload.put("tenantIds", tenantIds.stream().map(String::valueOf).toList());
        payload.put("roleIds", roles.stream().map(role -> Long.toString(role.getId())).toList());
        mutationSupport.outbox(
                session, systemId, "ACCESS_REQUEST", after.id(), "SYSTEM_ACCESS_REQUEST_APPROVED",
                payload, idempotencyKey, client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.AccessRequest reject(
            AuthenticatedSession session,
            long systemId,
            long requestId,
            SystemAdminRequests.ReviewAccessRequest request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutationSupport.idempotent(
                systemId + ":access-request:" + requestId + ":reject",
                idempotencyKey,
                request,
                SystemAdminViews.AccessRequest.class,
                () -> rejectNow(session, systemId, requestId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.AccessRequest rejectNow(
            AuthenticatedSession session,
            long systemId,
            long requestId,
            SystemAdminRequests.ReviewAccessRequest command,
            String idempotencyKey,
            ClientRequest client
    ) {
        var accessRequest = lockSubmittedRequest(systemId, requestId, command.version());
        if (!scopeSupport.systemWide(session, systemId, "system.access.review")) {
            if (accessRequest.getTargetTenantId() == null
                    || !Objects.equals(accessRequest.getTargetTenantId(), scopeSupport.tenantId(session))) {
                throw SystemAdminMutationSupport.notFound();
            }
        }
        var before = accessRequestView(accessRequest);
        var now = LocalDateTime.now();
        var updated = accessRequestMapper.update(null, Wrappers.<AccessRequest>lambdaUpdate()
                .eq(AccessRequest::getId, requestId)
                .eq(AccessRequest::getSystemId, systemId)
                .eq(AccessRequest::getStatus, "SUBMITTED")
                .eq(AccessRequest::getVersion, SystemAdminMutationSupport.version(command.version()))
                .set(AccessRequest::getStatus, "REJECTED")
                .set(AccessRequest::getReviewedAt, now)
                .set(AccessRequest::getReviewedBy, session.accountId())
                .set(AccessRequest::getDecisionReason, command.reason().trim())
                .set(AccessRequest::getResolvedMemberId, null)
                .set(AccessRequest::getUpdatedAt, now)
                .set(AccessRequest::getUpdatedBy, session.accountId())
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        applyReviewState(accessRequest, "REJECTED", command.reason(), null, session.accountId(), now);
        var after = accessRequestView(accessRequest);
        mutationSupport.success(
                session, systemId, "ACCESS_REQUEST", after.id(), "SYSTEM_ACCESS_REQUEST_REJECT",
                before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "ACCESS_REQUEST", after.id(), "SYSTEM_ACCESS_REQUEST_REJECTED",
                Map.of("requestId", after.id(), "reason", command.reason().trim()),
                idempotencyKey, client
        );
        return after;
    }

    private AccessRequest lockSubmittedRequest(long systemId, long requestId, String version) {
        var accessRequest = accessRequestMapper.selectOne(Wrappers.<AccessRequest>lambdaQuery()
                .eq(AccessRequest::getId, requestId)
                .eq(AccessRequest::getSystemId, systemId)
                .last("FOR UPDATE"));
        if (accessRequest == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        if (accessRequest.getVersion() != SystemAdminMutationSupport.version(version)) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        if (!"SUBMITTED".equals(accessRequest.getStatus())) {
            throw SystemAdminMutationSupport.invalidState("访问申请已经进入终态，不能重复审核");
        }
        if (accessRequest.getExpiresAt() != null && !accessRequest.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw SystemAdminMutationSupport.invalidState("访问申请已过期");
        }
        return accessRequest;
    }

    private LinkedHashSet<Long> resolveTenantIds(
            long systemId,
            AccessRequest accessRequest,
            List<String> values
    ) {
        var ids = parseIds(values, "tenantIds");
        if (ids.isEmpty() && accessRequest.getTargetTenantId() != null) {
            ids.add(accessRequest.getTargetTenantId());
        }
        if (ids.isEmpty()) {
            var defaultTenant = tenantMapper.selectOne(Wrappers.<Tenant>lambdaQuery()
                    .eq(Tenant::getSystemId, systemId)
                    .eq(Tenant::getIsDefault, true)
                    .eq(Tenant::getStatus, "ACTIVE")
                    .isNull(Tenant::getDeletedAt));
            if (defaultTenant == null) {
                throw SystemAdminMutationSupport.invalidState("系统没有可用默认租户");
            }
            ids.add(defaultTenant.getId());
        }
        if (accessRequest.getTargetTenantId() != null && !ids.contains(accessRequest.getTargetTenantId())) {
            throw new BusinessException(
                    "ACCESS_REVIEW_SCOPE_MISMATCH", "批准范围必须包含申请的目标租户", HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        var count = tenantMapper.selectCount(Wrappers.<Tenant>lambdaQuery()
                .eq(Tenant::getSystemId, systemId)
                .eq(Tenant::getStatus, "ACTIVE")
                .isNull(Tenant::getDeletedAt)
                .in(Tenant::getId, ids));
        if (count != ids.size()) {
            throw new BusinessException(
                    "ACCESS_REVIEW_INVALID_TENANT", "授权租户不存在、已停用或不属于当前系统",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        return ids;
    }

    private List<Role> resolveRoles(
            long systemId,
            AccessRequest accessRequest,
            List<String> values,
            Set<Long> tenantIds
    ) {
        var roleIds = parseIds(values, "roleIds");
        if (roleIds.isEmpty() && accessRequest.getRequestedRoleId() != null) {
            roleIds.add(accessRequest.getRequestedRoleId());
        }
        if (roleIds.isEmpty()) {
            throw new BusinessException(
                    "ACCESS_REVIEW_ROLE_REQUIRED", "批准访问申请时至少需要分配一个角色",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        if (accessRequest.getRequestedRoleId() != null && !roleIds.contains(accessRequest.getRequestedRoleId())) {
            throw new BusinessException(
                    "ACCESS_REVIEW_SCOPE_MISMATCH", "批准范围必须包含申请的目标角色",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        var roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .eq(Role::getStatus, "ACTIVE")
                .isNull(Role::getDeletedAt)
                .in(Role::getId, roleIds));
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(
                    "ACCESS_REVIEW_INVALID_ROLE", "授权角色不存在、已停用或不属于当前系统",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        for (var role : roles) {
            if (role.getTenantId() != null && !tenantIds.contains(role.getTenantId())) {
                throw new BusinessException(
                        "ACCESS_REVIEW_ROLE_TENANT_MISMATCH", "租户角色只能授予已选择该租户的成员",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
        }
        var order = new ArrayList<>(roleIds);
        var byId = roles.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        return order.stream().map(byId::get).toList();
    }

    private void requireReviewScope(AuthenticatedSession session, long systemId, Set<Long> tenantIds) {
        if (scopeSupport.systemWide(session, systemId, "system.access.review")) {
            return;
        }
        var currentTenantId = scopeSupport.tenantId(session);
        if (tenantIds.size() != 1 || !tenantIds.contains(currentTenantId)) {
            throw new BusinessException(
                    "PERMISSION_DENIED", "当前租户管理员只能批准本租户访问",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private Member createOrRestoreMember(
            long systemId,
            Account account,
            long defaultTenantId,
            long actorAccountId,
            LocalDateTime now
    ) {
        var member = memberMapper.selectOne(Wrappers.<Member>lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getAccountId, account.getId()));
        if (member == null) {
            member = new Member();
            member.setId(idService.nextId());
            member.setSystemId(systemId);
            member.setAccountId(account.getId());
            member.setMemberCode("member_" + account.getId());
            member.setDisplayName(account.getDisplayName());
            member.setDefaultTenantId(defaultTenantId);
            member.setStatus("ACTIVE");
            member.setJoinedAt(now);
            member.setCreatedAt(now);
            member.setCreatedBy(actorAccountId);
            member.setUpdatedAt(now);
            member.setUpdatedBy(actorAccountId);
            member.setVersion(0L);
            memberMapper.insert(member);
            return member;
        }
        var memberVersion = member.getVersion();
        if (memberMapper.update(null, Wrappers.<Member>lambdaUpdate()
                .eq(Member::getId, member.getId())
                .eq(Member::getSystemId, systemId)
                .eq(Member::getVersion, memberVersion)
                .set(Member::getDefaultTenantId, defaultTenantId)
                .set(Member::getStatus, "ACTIVE")
                .set(Member::getDeletedAt, null)
                .set(Member::getUpdatedAt, now)
                .set(Member::getUpdatedBy, actorAccountId)
                .setSql("version = version + 1")) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        member.setDefaultTenantId(defaultTenantId);
        member.setStatus("ACTIVE");
        member.setDeletedAt(null);
        member.setUpdatedAt(now);
        member.setUpdatedBy(actorAccountId);
        member.setVersion(memberVersion + 1);
        return member;
    }

    private void grantTenants(
            long systemId,
            long memberId,
            Set<Long> tenantIds,
            long actorAccountId,
            LocalDateTime now
    ) {
        var existing = memberTenantMapper.selectList(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getMemberId, memberId)
                .in(MemberTenant::getTenantId, tenantIds));
        var byTenant = existing.stream().collect(Collectors.toMap(MemberTenant::getTenantId, Function.identity()));
        for (var tenantId : tenantIds) {
            var access = byTenant.get(tenantId);
            if (access == null) {
                access = new MemberTenant();
                access.setId(idService.nextId());
                access.setSystemId(systemId);
                access.setMemberId(memberId);
                access.setTenantId(tenantId);
                access.setStatus("ACTIVE");
                access.setGrantedAt(now);
                access.setGrantedBy(actorAccountId);
                access.setCreatedAt(now);
                access.setCreatedBy(actorAccountId);
                access.setUpdatedAt(now);
                access.setUpdatedBy(actorAccountId);
                access.setVersion(0L);
                memberTenantMapper.insert(access);
                continue;
            }
            var accessVersion = access.getVersion();
            if (memberTenantMapper.update(null, Wrappers.<MemberTenant>lambdaUpdate()
                    .eq(MemberTenant::getId, access.getId())
                    .eq(MemberTenant::getSystemId, systemId)
                    .eq(MemberTenant::getMemberId, memberId)
                    .eq(MemberTenant::getVersion, accessVersion)
                    .set(MemberTenant::getStatus, "ACTIVE")
                    .set(MemberTenant::getGrantedAt, now)
                    .set(MemberTenant::getGrantedBy, actorAccountId)
                    .set(MemberTenant::getExpiresAt, null)
                    .set(MemberTenant::getDeletedAt, null)
                    .set(MemberTenant::getDeletedBy, null)
                    .set(MemberTenant::getUpdatedAt, now)
                    .set(MemberTenant::getUpdatedBy, actorAccountId)
                    .setSql("version = version + 1")) != 1) {
                throw SystemAdminMutationSupport.versionConflict();
            }
        }
    }

    private void grantRoles(
            long systemId,
            long memberId,
            List<Role> roles,
            long actorAccountId,
            LocalDateTime now
    ) {
        var existing = memberRoleMapper.selectList(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, memberId)
                .in(MemberRole::getRoleId, roles.stream().map(Role::getId).toList()));
        var byKey = existing.stream().collect(Collectors.toMap(
                edge -> roleKey(edge.getRoleId(), edge.getTenantId()), Function.identity()
        ));
        for (var role : roles) {
            var edge = byKey.get(roleKey(role.getId(), role.getTenantId()));
            if (edge == null) {
                edge = new MemberRole();
                edge.setId(idService.nextId());
                edge.setSystemId(systemId);
                edge.setMemberId(memberId);
                edge.setRoleId(role.getId());
                edge.setTenantId(role.getTenantId());
                edge.setValidFrom(now);
                edge.setCreatedAt(now);
                edge.setCreatedBy(actorAccountId);
                memberRoleMapper.insert(edge);
            } else {
                edge.setValidFrom(now);
                edge.setValidUntil(null);
                memberRoleMapper.updateById(edge);
            }
        }
    }

    private void applyReviewState(
            AccessRequest accessRequest,
            String status,
            String reason,
            Long memberId,
            long actorAccountId,
            LocalDateTime now
    ) {
        accessRequest.setStatus(status);
        accessRequest.setReviewedAt(now);
        accessRequest.setReviewedBy(actorAccountId);
        accessRequest.setDecisionReason(reason.trim());
        accessRequest.setResolvedMemberId(memberId);
        accessRequest.setUpdatedAt(now);
        accessRequest.setUpdatedBy(actorAccountId);
        accessRequest.setVersion(accessRequest.getVersion() + 1);
    }

    private SystemAdminViews.AccessRequest accessRequestView(AccessRequest request) {
        var account = accountMapper.selectById(request.getAccountId());
        var system = systemMapper.selectById(request.getSystemId());
        var tenant = request.getTargetTenantId() == null
                ? null : tenantMapper.selectById(request.getTargetTenantId());
        return new SystemAdminViews.AccessRequest(
                Long.toString(request.getId()),
                Long.toString(request.getSystemId()),
                system == null ? null : system.getName(),
                Long.toString(request.getAccountId()),
                account == null ? null : account.getDisplayName(),
                string(request.getTargetTenantId()),
                tenant == null ? null : tenant.getName(),
                request.getRequestReason(),
                request.getStatus(),
                request.getDecisionReason(),
                request.getCreatedAt(),
                request.getReviewedAt(),
                Long.toString(request.getVersion())
        );
    }

    private List<Long> matchingAccountIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return accountMapper.selectList(Wrappers.<Account>lambdaQuery()
                        .isNull(Account::getDeletedAt)
                        .and(query -> query.like(Account::getDisplayName, keyword.trim())
                                .or().like(Account::getUsername, keyword.trim())
                                .or().like(Account::getEmail, keyword.trim())))
                .stream().map(Account::getId).toList();
    }

    private com.unique.examine.plat.base.entity.System requireSystem(long systemId) {
        var system = systemMapper.selectById(systemId);
        if (system == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return system;
    }

    private Account requireActiveAccount(long accountId) {
        var account = accountMapper.selectOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getId, accountId)
                .eq(Account::getStatus, "ACTIVE")
                .isNull(Account::getDeletedAt));
        if (account == null) {
            throw SystemAdminMutationSupport.invalidState("申请账号当前不可用");
        }
        return account;
    }

    private static LinkedHashSet<Long> parseIds(List<String> values, String field) {
        var result = new LinkedHashSet<Long>();
        if (values == null) {
            return result;
        }
        for (var value : values) {
            result.add(SystemAdminMutationSupport.id(value, field));
        }
        return result;
    }

    private static String roleKey(long roleId, Long tenantId) {
        return roleId + ":" + (tenantId == null ? 0 : tenantId);
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static int page(int value) {
        return Math.max(value, 1);
    }

    private static int size(int value) {
        return Math.min(Math.max(value, 1), 100);
    }
}
