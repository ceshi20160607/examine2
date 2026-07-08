package com.unique.examine.plat.manage.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatSystem;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatSystemBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import com.unique.examine.plat.manage.context.ContextModels.PermissionSnapshotSummary;
import com.unique.examine.plat.manage.context.ContextModels.SwitchOption;
import com.unique.examine.plat.manage.context.ContextModels.SystemSwitchContext;
import com.unique.examine.plat.manage.context.ContextModels.SystemSwitchRequest;
import com.unique.examine.plat.manage.context.ContextModels.TenantSwitchContext;
import com.unique.examine.plat.manage.context.ContextModels.TenantSwitchRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * System and tenant context service backed by account-member bindings.
 */
@Service
public class SystemContextService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String CURRENT_BINDING_PREFIX = "unexamine:context:current-binding:";
    private static final Duration CURRENT_BINDING_TTL = Duration.ofHours(8);

    private final CurrentAccountProvider currentAccountProvider;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatSystemBaseService systemBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final StringRedisTemplate redisTemplate;

    public SystemContextService(CurrentAccountProvider currentAccountProvider,
                                PlatAccountMemberBindingBaseService bindingBaseService,
                                PlatSystemBaseService systemBaseService,
                                PlatTenantBaseService tenantBaseService,
                                PlatRoleMemberBaseService roleMemberBaseService,
                                PlatRoleBaseService roleBaseService,
                                StringRedisTemplate redisTemplate) {
        this.currentAccountProvider = currentAccountProvider;
        this.bindingBaseService = bindingBaseService;
        this.systemBaseService = systemBaseService;
        this.tenantBaseService = tenantBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.roleBaseService = roleBaseService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Query systems the current account can switch into.
     *
     * @return switch options
     */
    public List<SwitchOption> options() {
        PlatAccount account = currentAccountProvider.currentAccount();
        return bindingBaseService.list(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getAccountId, account.getId())
                        .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED))
                .stream()
                .map(this::toSwitchOption)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Switch to a system only when the account has a system member mapping.
     *
     * @param request switch request
     * @return switch context
     */
    public SystemSwitchContext switchSystem(SystemSwitchRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        PlatAccountMemberBinding binding = resolveBinding(account.getId(), request.systemId(), request.tenantId());
        if (Objects.isNull(binding)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "账号没有目标系统的成员映射");
        }
        storeCurrentBinding(account.getId(), binding.getId());
        return toSystemContext(account.getId(), binding);
    }

    /**
     * Return current system context.
     *
     * @return system context
     */
    public SystemSwitchContext currentSystem() {
        PlatAccount account = currentAccountProvider.currentAccount();
        PlatAccountMemberBinding binding = currentBinding(account.getId());
        if (Objects.isNull(binding)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "账号没有可进入的系统");
        }
        return toSystemContext(account.getId(), binding);
    }

    /**
     * Switch tenant and refresh permission summary.
     *
     * @param systemId system id
     * @param request tenant switch request
     * @return tenant context
     */
    public TenantSwitchContext switchTenant(String systemId, TenantSwitchRequest request) {
        PlatAccount account = currentAccountProvider.currentAccount();
        PlatAccountMemberBinding binding = resolveBinding(account.getId(), systemId,
                request == null ? null : request.tenantId());
        if (Objects.isNull(binding)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "账号没有目标租户的成员映射");
        }
        PlatTenant tenant = tenantBaseService.getById(binding.getTenantId());
        if (Objects.isNull(tenant)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "租户不存在");
        }
        storeCurrentBinding(account.getId(), binding.getId());
        List<String> roleIds = roleCodes(binding);
        return new TenantSwitchContext(String.valueOf(binding.getSystemId()), String.valueOf(binding.getTenantId()),
                tenant.getTenantName(), roleIds, Map.of("type", "ALL"), true, null,
                permissionSummary(binding, roleIds));
    }

    private PlatAccountMemberBinding currentBinding(Long accountId) {
        PlatAccountMemberBinding stored = storedCurrentBinding(accountId);
        if (Objects.nonNull(stored)) {
            return stored;
        }
        return bindingBaseService.getOne(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getAccountId, accountId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                .orderByAsc(PlatAccountMemberBinding::getId)
                .last("LIMIT 1"), false);
    }

    private PlatAccountMemberBinding storedCurrentBinding(Long accountId) {
        String value = redisTemplate.opsForValue().get(CURRENT_BINDING_PREFIX + accountId);
        Long bindingId = parseLong(value);
        if (Objects.isNull(bindingId)) {
            return null;
        }
        PlatAccountMemberBinding binding = bindingBaseService.getById(bindingId);
        if (Objects.isNull(binding) || !Objects.equals(binding.getAccountId(), accountId)
                || !Objects.equals(binding.getBindingStatus(), ENABLED)) {
            redisTemplate.delete(CURRENT_BINDING_PREFIX + accountId);
            return null;
        }
        return binding;
    }

    private void storeCurrentBinding(Long accountId, Long bindingId) {
        redisTemplate.opsForValue().set(CURRENT_BINDING_PREFIX + accountId, String.valueOf(bindingId),
                CURRENT_BINDING_TTL);
    }

    private SwitchOption toSwitchOption(PlatAccountMemberBinding binding) {
        PlatSystem system = systemBaseService.getById(binding.getSystemId());
        PlatTenant tenant = tenantBaseService.getById(binding.getTenantId());
        if (Objects.isNull(system) || Objects.isNull(tenant) || Objects.equals(system.getDeleted(), 1)
                || Objects.equals(tenant.getDeleted(), 1)) {
            return null;
        }
        boolean switchable = Objects.equals(system.getStatus(), ENABLED)
                && Objects.equals(tenant.getStatus(), ENABLED);
        String disabledReason = switchable ? null : "系统或租户已停用";
        return new SwitchOption(String.valueOf(system.getId()), system.getSystemName(), String.valueOf(tenant.getId()),
                tenant.getTenantName(), switchable, disabledReason);
    }

    private SystemSwitchContext toSystemContext(Long accountId, PlatAccountMemberBinding binding) {
        PlatSystem system = systemBaseService.getById(binding.getSystemId());
        PlatTenant tenant = tenantBaseService.getById(binding.getTenantId());
        if (Objects.isNull(system) || Objects.isNull(tenant)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "系统或租户不存在");
        }
        List<String> roleIds = roleCodes(binding);
        return new SystemSwitchContext(String.valueOf(accountId), String.valueOf(binding.getId()),
                String.valueOf(system.getId()), system.getSystemCode(), system.getSystemName(),
                String.valueOf(tenant.getId()), String.valueOf(binding.getSystemMemberId()), roleIds,
                Map.of("type", "ALL"), permissionSummary(binding, roleIds),
                Map.of("scope", "system", "systemId", String.valueOf(system.getId()),
                        "tenantId", String.valueOf(tenant.getId())),
                LocalDateTime.now().plusHours(8));
    }

    private PlatAccountMemberBinding resolveBinding(Long accountId, String systemIdOrCode, String tenantIdOrCode) {
        PlatSystem system = resolveSystem(systemIdOrCode);
        PlatTenant tenant = resolveTenant(system, tenantIdOrCode);
        LambdaQueryWrapper<PlatAccountMemberBinding> wrapper = new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getAccountId, accountId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED);
        if (Objects.nonNull(system)) {
            wrapper.eq(PlatAccountMemberBinding::getSystemId, system.getId());
        }
        if (Objects.nonNull(tenant)) {
            wrapper.eq(PlatAccountMemberBinding::getTenantId, tenant.getId());
        }
        return bindingBaseService.getOne(wrapper.orderByAsc(PlatAccountMemberBinding::getId)
                .last("LIMIT 1"), false);
    }

    private PlatSystem resolveSystem(String systemIdOrCode) {
        if (!StringUtils.hasText(systemIdOrCode)) {
            return null;
        }
        Long id = parseLong(systemIdOrCode);
        LambdaQueryWrapper<PlatSystem> wrapper = new LambdaQueryWrapper<PlatSystem>()
                .eq(PlatSystem::getDeleted, DELETED_NO);
        if (Objects.nonNull(id)) {
            wrapper.eq(PlatSystem::getId, id);
        } else {
            wrapper.eq(PlatSystem::getSystemCode, systemIdOrCode);
        }
        return systemBaseService.getOne(wrapper.last("LIMIT 1"), false);
    }

    private PlatTenant resolveTenant(PlatSystem system, String tenantIdOrCode) {
        if (!StringUtils.hasText(tenantIdOrCode)) {
            return null;
        }
        Long id = parseLong(tenantIdOrCode);
        LambdaQueryWrapper<PlatTenant> wrapper = new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getDeleted, DELETED_NO);
        if (Objects.nonNull(system)) {
            wrapper.eq(PlatTenant::getSystemId, system.getId());
        }
        if (Objects.nonNull(id)) {
            wrapper.eq(PlatTenant::getId, id);
        } else {
            wrapper.eq(PlatTenant::getTenantCode, tenantIdOrCode);
        }
        return tenantBaseService.getOne(wrapper.last("LIMIT 1"), false);
    }

    private List<String> roleCodes(PlatAccountMemberBinding binding) {
        List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, binding.getSystemId())
                .eq(PlatRoleMember::getTenantId, binding.getTenantId())
                .eq(PlatRoleMember::getSystemMemberId, binding.getSystemMemberId()));
        Set<Long> ids = new LinkedHashSet<>();
        for (PlatRoleMember roleMember : roleMembers) {
            ids.add(roleMember.getRoleId());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        return roleBaseService.list(new LambdaQueryWrapper<PlatRole>()
                        .in(PlatRole::getId, ids)
                        .eq(PlatRole::getScope, SCOPE_SYSTEM)
                .eq(PlatRole::getStatus, ENABLED)
                .eq(PlatRole::getDeleted, DELETED_NO))
                .stream()
                .flatMap(role -> java.util.stream.Stream.of(String.valueOf(role.getId()), role.getRoleCode()))
                .distinct()
                .toList();
    }

    private PermissionSnapshotSummary permissionSummary(PlatAccountMemberBinding binding, List<String> roleIds) {
        String snapshotId = "eps_" + binding.getSystemId() + "_" + binding.getTenantId() + "_"
                + binding.getSystemMemberId();
        String version = roleIds.isEmpty() ? "perm_empty" : "perm_live_" + String.join("_", roleIds);
        return new PermissionSnapshotSummary(snapshotId, version, null);
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
