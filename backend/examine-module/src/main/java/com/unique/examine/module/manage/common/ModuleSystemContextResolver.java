package com.unique.examine.module.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatTenant;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatTenantBaseService;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import java.time.Duration;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves the current member context used by module configuration and runtime APIs.
 */
@Component
public class ModuleSystemContextResolver {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String CURRENT_BINDING_PREFIX = "unexamine:context:current-binding:";
    private static final Duration CURRENT_BINDING_TTL = Duration.ofHours(8);

    private final CurrentAccountProvider currentAccountProvider;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatTenantBaseService tenantBaseService;
    private final StringRedisTemplate redisTemplate;

    public ModuleSystemContextResolver(CurrentAccountProvider currentAccountProvider,
                                       PlatAccountMemberBindingBaseService bindingBaseService,
                                       PlatTenantBaseService tenantBaseService,
                                       StringRedisTemplate redisTemplate) {
        this.currentAccountProvider = currentAccountProvider;
        this.bindingBaseService = bindingBaseService;
        this.tenantBaseService = tenantBaseService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Resolve account, system, tenant and member ids from the active switch context.
     *
     * @param systemId system id from route
     * @return persisted context
     */
    public ModuleSystemContext resolve(String systemId) {
        Long resolvedSystemId = parseRequiredId(systemId, "Invalid system id.");
        PlatAccount account = currentAccountProvider.currentAccount();
        PlatAccountMemberBinding binding = currentBinding(account.getId(), resolvedSystemId);
        if (Objects.isNull(binding)) {
            binding = bindingBaseService.getOne(
                    new LambdaQueryWrapper<PlatAccountMemberBinding>()
                            .eq(PlatAccountMemberBinding::getAccountId, account.getId())
                            .eq(PlatAccountMemberBinding::getSystemId, resolvedSystemId)
                            .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                            .orderByAsc(PlatAccountMemberBinding::getId)
                            .last("LIMIT 1"), false);
        }
        if (Objects.isNull(binding)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED,
                    "Account has no member binding for the target system.");
        }
        return new ModuleSystemContext(account.getId(), binding.getSystemId(), binding.getTenantId(),
                binding.getSystemMemberId(), permissionSnapshotId(binding), permissionVersion(binding));
    }

    /**
     * Resolve a tenant for system-level configuration creation when a member context is not required.
     *
     * @param systemId system id from route
     * @return default tenant id
     */
    public Long defaultTenantId(String systemId) {
        Long resolvedSystemId = parseRequiredId(systemId, "Invalid system id.");
        PlatTenant tenant = tenantBaseService.getOne(new LambdaQueryWrapper<PlatTenant>()
                .eq(PlatTenant::getSystemId, resolvedSystemId)
                .eq(PlatTenant::getStatus, ENABLED)
                .eq(PlatTenant::getDeleted, DELETED_NO)
                .orderByAsc(PlatTenant::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(tenant)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "System has no enabled tenant.");
        }
        return tenant.getId();
    }

    /**
     * Parse a route id and fail with a business exception when it is invalid.
     *
     * @param value raw route value
     * @param message error message
     * @return parsed id
     */
    public Long parseRequiredId(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private PlatAccountMemberBinding currentBinding(Long accountId, Long systemId) {
        String value = redisTemplate.opsForValue().get(CURRENT_BINDING_PREFIX + accountId);
        Long bindingId = parseOptionalId(value);
        if (Objects.isNull(bindingId)) {
            return null;
        }
        PlatAccountMemberBinding binding = bindingBaseService.getById(bindingId);
        if (Objects.isNull(binding) || !Objects.equals(binding.getAccountId(), accountId)
                || !Objects.equals(binding.getSystemId(), systemId)
                || !Objects.equals(binding.getBindingStatus(), ENABLED)) {
            redisTemplate.delete(CURRENT_BINDING_PREFIX + accountId);
            return null;
        }
        redisTemplate.expire(CURRENT_BINDING_PREFIX + accountId, CURRENT_BINDING_TTL);
        return binding;
    }

    private Long parseOptionalId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String permissionSnapshotId(PlatAccountMemberBinding binding) {
        return "eps_" + binding.getSystemId() + "_" + binding.getTenantId() + "_"
                + binding.getSystemMemberId();
    }

    private String permissionVersion(PlatAccountMemberBinding binding) {
        return "perm_live_" + binding.getSystemId() + "_" + binding.getTenantId() + "_"
                + binding.getSystemMemberId();
    }

    /**
     * Current module API identity context.
     *
     * @param accountId account id
     * @param systemId system id
     * @param tenantId tenant id
     * @param systemMemberId system member id
     * @param permissionSnapshotId permission snapshot id
     * @param permissionVersion permission version
     */
    public record ModuleSystemContext(Long accountId, Long systemId, Long tenantId, Long systemMemberId,
                                      String permissionSnapshotId, String permissionVersion) {
    }
}
