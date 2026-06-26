package com.unique.examine.plat.manage.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountBaseService;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.tenant.TenantModels.TenantSaveRequest;
import com.unique.examine.plat.manage.tenant.TenantModels.TenantVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Tenant management service backed by persisted tenant and member-binding tables.
 */
@Service
public class TenantService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SYSTEM_SUPER_ADMIN = "SYSTEM_SUPER_ADMIN";

    private final SystemMemberContextResolver contextResolver;
    private final PlatTenantBaseService tenantBaseService;
    private final PlatAccountBaseService accountBaseService;
    private final PlatMemberBaseService memberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatAccountMemberBindingBaseService bindingBaseService;

    public TenantService(SystemMemberContextResolver contextResolver,
                         PlatTenantBaseService tenantBaseService,
                         PlatAccountBaseService accountBaseService,
                         PlatMemberBaseService memberBaseService,
                         PlatRoleBaseService roleBaseService,
                         PlatRoleMemberBaseService roleMemberBaseService,
                         PlatAccountMemberBindingBaseService bindingBaseService) {
        this.contextResolver = contextResolver;
        this.tenantBaseService = tenantBaseService;
        this.accountBaseService = accountBaseService;
        this.memberBaseService = memberBaseService;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.bindingBaseService = bindingBaseService;
    }

    /**
     * List tenants under a system.
     *
     * @param systemId system id
     * @return tenants
     */
    public List<TenantVO> list(String systemId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        return tenantBaseService.list(new LambdaQueryWrapper<PlatTenant>()
                        .eq(PlatTenant::getSystemId, context.systemId())
                        .eq(PlatTenant::getDeleted, DELETED_NO)
                        .orderByAsc(PlatTenant::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * Create a tenant and make the creator its system super admin.
     *
     * @param systemId system id
     * @param request save request
     * @return tenant
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantVO create(String systemId, TenantSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        requireText(Objects.isNull(request) ? null : request.tenantCode(), "Tenant code is required.");
        requireText(request.tenantName(), "Tenant name is required.");
        String tenantCode = normalizedCode(request.tenantCode());
        if (tenantBaseService.count(new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getSystemId, context.systemId())
                .eq(PlatTenant::getTenantCode, tenantCode)
                .eq(PlatTenant::getDeleted, DELETED_NO)) > 0) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Tenant code already exists.");
        }

        LocalDateTime now = LocalDateTime.now();
        PlatTenant tenant = new PlatTenant();
        tenant.setSystemId(context.systemId());
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(request.tenantName());
        tenant.setDomain(blankToNull(request.domain()));
        tenant.setStatus(Objects.isNull(request.status()) ? ENABLED : request.status());
        tenant.setCreatedAt(now);
        tenant.setUpdatedAt(now);
        tenant.setDeleted(DELETED_NO);
        tenantBaseService.saveEntity(tenant);
        ensureCreatorBinding(context, tenant, now);
        return toVO(tenant);
    }

    /**
     * Update a tenant.
     *
     * @param systemId system id
     * @param tenantId tenant id
     * @param request save request
     * @return tenant
     */
    @Transactional(rollbackFor = Exception.class)
    public TenantVO update(String systemId, String tenantId, TenantSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatTenant tenant = requireTenant(context.systemId(), tenantId);
        if (Objects.nonNull(request) && StringUtils.hasText(request.tenantCode())) {
            String tenantCode = normalizedCode(request.tenantCode());
            if (!tenantCode.equals(tenant.getTenantCode()) && tenantBaseService.count(new LambdaQueryWrapper<PlatTenant>()
                    .eq(PlatTenant::getSystemId, context.systemId())
                    .eq(PlatTenant::getTenantCode, tenantCode)
                    .eq(PlatTenant::getDeleted, DELETED_NO)) > 0) {
                throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Tenant code already exists.");
            }
            tenant.setTenantCode(tenantCode);
        }
        if (Objects.nonNull(request) && StringUtils.hasText(request.tenantName())) {
            tenant.setTenantName(request.tenantName());
        }
        if (Objects.nonNull(request)) {
            tenant.setDomain(blankToNull(request.domain()));
            tenant.setStatus(Objects.isNull(request.status()) ? tenant.getStatus() : request.status());
        }
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantBaseService.updateById(tenant);
        return toVO(tenant);
    }

    /**
     * Ensure the tenant creator can switch into the new tenant and configure it.
     *
     * @param context current system member context
     * @param tenant created tenant
     * @param now operation time
     */
    private void ensureCreatorBinding(SystemMemberContext context, PlatTenant tenant, LocalDateTime now) {
        PlatAccount account = accountBaseService.getById(context.accountId());
        if (Objects.isNull(account)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED, "Current account does not exist.");
        }
        PlatAccountMemberBinding existingBinding = bindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getAccountId, context.accountId())
                        .eq(PlatAccountMemberBinding::getSystemId, context.systemId())
                        .eq(PlatAccountMemberBinding::getTenantId, tenant.getId())
                        .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                        .last("LIMIT 1"), false);
        if (Objects.nonNull(existingBinding)) {
            return;
        }

        PlatMember member = new PlatMember();
        member.setSystemId(context.systemId());
        member.setTenantId(tenant.getId());
        member.setMemberName(account.getAccountName());
        member.setEmployeeNo("SA" + account.getId());
        member.setMobile(account.getMobile());
        member.setEmail(account.getEmail());
        member.setStatus(ENABLED);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        member.setDeleted(DELETED_NO);
        memberBaseService.saveEntity(member);

        PlatRole role = ensureSystemSuperAdminRole(context.systemId(), tenant.getId(), now);
        PlatRoleMember roleMember = new PlatRoleMember();
        roleMember.setRoleId(role.getId());
        roleMember.setAccountId(context.accountId());
        roleMember.setSystemMemberId(member.getId());
        roleMember.setSystemId(context.systemId());
        roleMember.setTenantId(tenant.getId());
        roleMember.setCreatedAt(now);
        roleMemberBaseService.saveEntity(roleMember);

        PlatAccountMemberBinding binding = new PlatAccountMemberBinding();
        binding.setAccountId(context.accountId());
        binding.setSystemId(context.systemId());
        binding.setTenantId(tenant.getId());
        binding.setSystemMemberId(member.getId());
        binding.setBindingStatus(ENABLED);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        bindingBaseService.saveEntity(binding);
    }

    /**
     * Create or reuse the built-in system super admin role for a tenant.
     *
     * @param systemId system id
     * @param tenantId tenant id
     * @param now operation time
     * @return role
     */
    private PlatRole ensureSystemSuperAdminRole(Long systemId, Long tenantId, LocalDateTime now) {
        PlatRole role = roleBaseService.getOne(new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getScope, SCOPE_SYSTEM)
                .eq(PlatRole::getSystemId, systemId)
                .eq(PlatRole::getTenantId, tenantId)
                .eq(PlatRole::getRoleCode, SYSTEM_SUPER_ADMIN)
                .eq(PlatRole::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(role)) {
            return role;
        }
        role = new PlatRole();
        role.setScope(SCOPE_SYSTEM);
        role.setSystemId(systemId);
        role.setTenantId(tenantId);
        role.setRoleCode(SYSTEM_SUPER_ADMIN);
        role.setRoleName("System Super Admin");
        role.setRoleType(SYSTEM_SUPER_ADMIN);
        role.setBuiltin(ENABLED);
        role.setStatus(ENABLED);
        role.setDescription("Built-in system super administrator for this tenant.");
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleted(DELETED_NO);
        roleBaseService.saveEntity(role);
        return role;
    }

    private PlatTenant requireTenant(Long systemId, String tenantId) {
        Long id = contextResolver.parseRequiredId(tenantId, "Tenant id is invalid.");
        PlatTenant tenant = tenantBaseService.getOne(new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getId, id)
                .eq(PlatTenant::getSystemId, systemId)
                .eq(PlatTenant::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(tenant)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Tenant does not exist.");
        }
        return tenant;
    }

    private TenantVO toVO(PlatTenant tenant) {
        return new TenantVO(String.valueOf(tenant.getId()), String.valueOf(tenant.getSystemId()),
                tenant.getTenantCode(), tenant.getTenantName(), tenant.getDomain(), tenant.getStatus(),
                tenant.getUpdatedAt());
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String normalizedCode(String value) {
        return value.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
