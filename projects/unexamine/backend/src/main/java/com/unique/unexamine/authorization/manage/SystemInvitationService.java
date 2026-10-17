package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.TokenFactory;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemDepartment;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberInvitation;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemDepartmentBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberInvitationBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 管理员发出邀请；持令牌的平台账号接受后才建立显式系统及租户成员关系。 */
@Service
public class SystemInvitationService {
    private final SystemMemberInvitationBaseService invitationService;
    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemDepartmentBaseService departmentService;
    private final SystemRoleBaseService roleService;
    private final PlatformAccountBaseService accountService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final TenantMemberQuotaService tenantMemberQuotaService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final OrganizationRelationRepository relationRepository;
    private final TokenFactory tokenFactory;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;

    public SystemInvitationService(SystemMemberInvitationBaseService invitationService,
                                   SystemDefinitionBaseService systemService,
                                   SystemTenantBaseService tenantService,
                                   SystemDepartmentBaseService departmentService,
                                   SystemRoleBaseService roleService,
                                   PlatformAccountBaseService accountService,
                                   SystemMemberBaseService memberService,
                                   SystemTenantMemberBaseService tenantMemberService,
                                   TenantMemberQuotaService tenantMemberQuotaService,
                                   SystemMemberRoleBaseService memberRoleService,
                                   OrganizationRelationRepository relationRepository,
                                   TokenFactory tokenFactory, ObjectMapper objectMapper,
                                   AuditRecorder auditRecorder) {
        this.invitationService = invitationService;
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.departmentService = departmentService;
        this.roleService = roleService;
        this.accountService = accountService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.tenantMemberQuotaService = tenantMemberQuotaService;
        this.memberRoleService = memberRoleService;
        this.relationRepository = relationRepository;
        this.tokenFactory = tokenFactory;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public List<SystemInvitationModels.InvitationView> list(AuthenticatedContext context) {
        requireSystemAdminContext(context);
        return invitationService.selectList(Wrappers.<SystemMemberInvitation>lambdaQuery()
                        .eq(SystemMemberInvitation::getSystemId, context.systemId())
                        .eq(SystemMemberInvitation::getTenantId, context.tenantId())
                        .orderByDesc(SystemMemberInvitation::getId))
                .stream().map(this::view).toList();
    }

    /** 仅保存随机令牌摘要；管理员须当次复制邀请链接，后续列表不泄露令牌。 */
    @Transactional
    public SystemInvitationModels.CreatedInvitation create(AuthenticatedContext context,
                                                            SystemInvitationModels.CreateRequest request,
                                                            String traceId) {
        requireSystemAdminContext(context);
        activeScope(context.systemId(), context.tenantId());
        activeDepartment(context.systemId(), context.tenantId(), request.departmentId());
        List<Long> roleIds = validatedRoles(context.systemId(), context.tenantId(), request.roleIds());
        PlatformAccount target = null;
        if (request.targetAccount() != null && !request.targetAccount().isBlank()) {
            String value = request.targetAccount().strip();
            target = accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                            .and(query -> query.eq(PlatformAccount::getUsername, value)
                                    .or().eq(PlatformAccount::getEmail, value)
                                    .or().eq(PlatformAccount::getMobile, value)))
                    .stream().findFirst().orElseThrow(() -> failure("ACCOUNT_NOT_FOUND", "未找到指定平台账号", HttpStatus.NOT_FOUND));
            if (!"ACTIVE".equals(target.getStatus())) throw failure("ACCOUNT_NOT_ACTIVE", "指定账号已停用", HttpStatus.CONFLICT);
            SystemMember existing = findMember(context.systemId(), target.getId());
            if (existing != null && findTenantMember(context.tenantId(), existing.getId()) != null) {
                throw failure("SYSTEM_MEMBER_EXISTS", "该账号已在当前工作空间，直接维护成员即可", HttpStatus.CONFLICT);
            }
        }
        String token = tokenFactory.create();
        SystemMemberInvitation invitation = new SystemMemberInvitation();
        invitation.setSystemId(context.systemId());
        invitation.setTenantId(context.tenantId());
        invitation.setTargetAccountId(target == null ? null : target.getId());
        invitation.setTokenHash(tokenFactory.hash(token));
        invitation.setDepartmentId(request.departmentId());
        invitation.setRoleIdsJson(json(roleIds));
        invitation.setEmployeeNumber(blankToNull(request.employeeNumber()));
        invitation.setPositionTitle(blankToNull(request.positionTitle()));
        invitation.setStatus("PENDING");
        invitation.setExpireTime(LocalDateTime.now().plusDays(7));
        invitation.setCreatedByAccountId(context.accountId());
        invitationService.insert(invitation);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_MEMBER_INVITED", "SYSTEM_MEMBER_INVITATION", invitation.getId().toString(), "SUCCESS",
                Map.of("targetAccountId", target == null ? "" : target.getId().toString(), "roleIds", roleIds));
        return new SystemInvitationModels.CreatedInvitation(view(invitationService.selectById(invitation.getId())), token);
    }

    @Transactional
    public SystemInvitationModels.InvitationView revoke(AuthenticatedContext context, Long invitationId,
                                                         SystemInvitationModels.RevokeRequest request, String traceId) {
        requireSystemAdminContext(context);
        SystemMemberInvitation invitation = invitationService.selectById(invitationId);
        if (invitation == null || !context.systemId().equals(invitation.getSystemId())
                || !context.tenantId().equals(invitation.getTenantId())) {
            throw failure("INVITATION_NOT_FOUND", "未找到当前工作空间的邀请", HttpStatus.NOT_FOUND);
        }
        if (!"PENDING".equals(invitation.getStatus())) throw failure("INVITATION_NOT_PENDING", "邀请已结束", HttpStatus.CONFLICT);
        if (!request.expectedVersion().equals(invitation.getVersion())) throw conflict();
        invitation.setStatus("REVOKED");
        if (invitationService.updateById(invitation) != 1) throw conflict();
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "SYSTEM_MEMBER_INVITATION_REVOKED", "SYSTEM_MEMBER_INVITATION", invitationId.toString(), "SUCCESS", Map.of());
        return view(invitationService.selectById(invitationId));
    }

    @Transactional(readOnly = true)
    public SystemInvitationModels.InvitationView preview(AuthenticatedContext context, String token) {
        SystemMemberInvitation invitation = byToken(token);
        requireRecipient(context, invitation);
        return view(invitation);
    }

    /** 先消费令牌，再在同一事务中创建成员映射；任何后续保存失败都会回滚消费。 */
    @Transactional
    public SystemInvitationModels.AcceptedInvitation accept(AuthenticatedContext context, String token, String traceId) {
        SystemMemberInvitation invitation = byToken(token);
        requireRecipient(context, invitation);
        if (!"PENDING".equals(invitation.getStatus())) throw failure("INVITATION_NOT_PENDING", "邀请已结束", HttpStatus.CONFLICT);
        if (!invitation.getExpireTime().isAfter(LocalDateTime.now())) {
            throw failure("INVITATION_EXPIRED", "邀请已过期，请联系管理员重新发出", HttpStatus.CONFLICT);
        }
        activeScope(invitation.getSystemId(), invitation.getTenantId());
        activeDepartment(invitation.getSystemId(), invitation.getTenantId(), invitation.getDepartmentId());
        List<Long> roleIds = validatedRoles(invitation.getSystemId(), invitation.getTenantId(), roles(invitation.getRoleIdsJson()));
        PlatformAccount account = accountService.selectById(context.accountId());
        if (account == null || !"ACTIVE".equals(account.getStatus())) throw failure("ACCOUNT_NOT_ACTIVE", "账号不可接受邀请", HttpStatus.FORBIDDEN);
        SystemMember member = findMember(invitation.getSystemId(), account.getId());
        if (member != null && !"ACTIVE".equals(member.getStatus())) {
            throw failure("SYSTEM_MEMBER_DISABLED", "系统成员已停用，请联系管理员", HttpStatus.CONFLICT);
        }
        if (member != null && findTenantMember(invitation.getTenantId(), member.getId()) != null) {
            throw failure("SYSTEM_MEMBER_EXISTS", "你已经是当前工作空间成员", HttpStatus.CONFLICT);
        }
        // 接受邀请才真正增加租户有效成员，超过硬限制时邀请仍保持待接受。
        tenantMemberQuotaService.checkBeforeActivate(invitation.getSystemId(), invitation.getTenantId());
        invitation.setStatus("ACCEPTED");
        invitation.setAcceptedAccountId(account.getId());
        invitation.setAcceptTime(LocalDateTime.now());
        if (invitationService.updateById(invitation) != 1) throw conflict();
        if (member == null) {
            member = new SystemMember();
            member.setSystemId(invitation.getSystemId());
            member.setAccountId(account.getId());
            member.setDisplayName(account.getDisplayName());
            member.setEmployeeNumber(invitation.getEmployeeNumber());
            member.setStatus("ACTIVE");
            member.setJoinTime(LocalDateTime.now());
            memberService.insert(member);
        } else if (invitation.getEmployeeNumber() != null && member.getEmployeeNumber() == null) {
            member.setEmployeeNumber(invitation.getEmployeeNumber());
            memberService.updateById(member);
        }
        SystemTenantMember tenantMember = new SystemTenantMember();
        tenantMember.setSystemId(invitation.getSystemId());
        tenantMember.setTenantId(invitation.getTenantId());
        tenantMember.setSystemMemberId(member.getId());
        tenantMember.setDepartmentId(invitation.getDepartmentId());
        tenantMember.setTenantAdmin(false);
        tenantMember.setStatus("ACTIVE");
        tenantMemberService.insert(tenantMember);
        for (Long roleId : roleIds) {
            SystemMemberRole memberRole = new SystemMemberRole();
            memberRole.setTenantId(invitation.getTenantId());
            memberRole.setTenantMemberId(tenantMember.getId());
            memberRole.setRoleId(roleId);
            memberRoleService.insert(memberRole);
        }
        relationRepository.upsert(invitation.getSystemId(), invitation.getTenantId(), tenantMember.getId(),
                null, invitation.getPositionTitle());
        auditRecorder.record(traceId, account.getId(), invitation.getSystemId(), invitation.getTenantId(), member.getId(),
                "SYSTEM_MEMBER_INVITATION_ACCEPTED", "SYSTEM_MEMBER_INVITATION", invitation.getId().toString(), "SUCCESS",
                Map.of("tenantMemberId", tenantMember.getId(), "roleIds", roleIds));
        return new SystemInvitationModels.AcceptedInvitation(invitation.getSystemId(), invitation.getTenantId(),
                member.getId(), tenantMember.getId());
    }

    private SystemMemberInvitation byToken(String token) {
        if (token == null || token.isBlank() || token.length() > 256) {
            throw failure("INVITATION_NOT_FOUND", "邀请链接无效", HttpStatus.NOT_FOUND);
        }
        return invitationService.selectList(Wrappers.<SystemMemberInvitation>lambdaQuery()
                        .eq(SystemMemberInvitation::getTokenHash, tokenFactory.hash(token)))
                .stream().findFirst().orElseThrow(() -> failure("INVITATION_NOT_FOUND", "邀请链接无效", HttpStatus.NOT_FOUND));
    }

    private void requireRecipient(AuthenticatedContext context, SystemMemberInvitation invitation) {
        if (context == null || context.accountId() == null) throw failure("AUTHENTICATION_REQUIRED", "请先登录平台账号", HttpStatus.UNAUTHORIZED);
        if (invitation.getTargetAccountId() != null && !invitation.getTargetAccountId().equals(context.accountId())) {
            throw failure("INVITATION_ACCOUNT_MISMATCH", "该邀请属于另一平台账号", HttpStatus.FORBIDDEN);
        }
    }

    private void requireSystemAdminContext(AuthenticatedContext context) {
        if (context == null || context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw failure("SYSTEM_CONTEXT_REQUIRED", "请先进入目标系统", HttpStatus.FORBIDDEN);
        }
    }

    private void activeScope(Long systemId, Long tenantId) {
        SystemDefinition system = systemService.selectById(systemId);
        SystemTenant tenant = tenantService.selectById(tenantId);
        if (system == null || !"ACTIVE".equals(system.getStatus()) || tenant == null
                || !systemId.equals(tenant.getSystemId()) || !"ACTIVE".equals(tenant.getStatus())) {
            throw failure("INVITATION_SCOPE_UNAVAILABLE", "目标系统或工作空间已停用", HttpStatus.CONFLICT);
        }
    }

    private SystemDepartment activeDepartment(Long systemId, Long tenantId, Long departmentId) {
        SystemDepartment department = departmentService.selectById(departmentId);
        if (department == null || !systemId.equals(department.getSystemId())
                || !tenantId.equals(department.getTenantId()) || !"ACTIVE".equals(department.getStatus())) {
            throw failure("DEPARTMENT_NOT_FOUND", "目标部门不存在或已停用", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        return department;
    }

    private List<Long> validatedRoles(Long systemId, Long tenantId, List<Long> requested) {
        if (requested == null || requested.contains(null)) {
            throw failure("ROLE_NOT_ACTIVE", "邀请角色无效", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<Long> roleIds = requested.stream().distinct().sorted().toList();
        for (Long roleId : roleIds) {
            SystemRole role = roleId == null ? null : roleService.selectById(roleId);
            if (role == null || !systemId.equals(role.getSystemId()) || !tenantId.equals(role.getTenantId())
                    || !"ACTIVE".equals(role.getStatus())) {
                throw failure("ROLE_NOT_ACTIVE", "邀请角色不存在或尚未发布", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        return roleIds;
    }

    private SystemMember findMember(Long systemId, Long accountId) {
        return memberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, accountId))
                .stream().findFirst().orElse(null);
    }

    private SystemTenantMember findTenantMember(Long tenantId, Long memberId) {
        return tenantMemberService.selectList(Wrappers.<SystemTenantMember>lambdaQuery()
                        .eq(SystemTenantMember::getTenantId, tenantId)
                        .eq(SystemTenantMember::getSystemMemberId, memberId))
                .stream().findFirst().orElse(null);
    }

    private SystemInvitationModels.InvitationView view(SystemMemberInvitation invitation) {
        SystemDefinition system = systemService.selectById(invitation.getSystemId());
        SystemTenant tenant = tenantService.selectById(invitation.getTenantId());
        SystemDepartment department = departmentService.selectById(invitation.getDepartmentId());
        PlatformAccount target = invitation.getTargetAccountId() == null ? null : accountService.selectById(invitation.getTargetAccountId());
        String status = "PENDING".equals(invitation.getStatus()) && !invitation.getExpireTime().isAfter(LocalDateTime.now())
                ? "EXPIRED" : invitation.getStatus();
        return new SystemInvitationModels.InvitationView(invitation.getId(), invitation.getSystemId(),
                system == null ? "已停用系统" : system.getName(), invitation.getTenantId(),
                tenant == null ? "已停用工作空间" : tenant.getName(),
                target == null ? null : target.getUsername(), invitation.getDepartmentId(),
                department == null ? "已删除部门" : department.getName(), roles(invitation.getRoleIdsJson()),
                invitation.getEmployeeNumber(), invitation.getPositionTitle(), status,
                invitation.getExpireTime(), invitation.getCreateTime(), invitation.getVersion());
    }

    private String json(List<Long> roleIds) {
        try { return objectMapper.writeValueAsString(roleIds); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("邀请角色序列化失败", exception); }
    }

    private List<Long> roles(String value) {
        try { return objectMapper.readValue(value, new TypeReference<List<Long>>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("邀请角色读取失败", exception); }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private DomainException conflict() { return failure("CONCURRENT_MODIFICATION", "邀请已被其他操作修改，请刷新", HttpStatus.CONFLICT); }
    private DomainException failure(String code, String message, HttpStatus status) { return new DomainException(code, message, status); }
}
