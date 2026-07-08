package com.unique.examine.plat.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves current account-member binding for system backend APIs.
 */
@Component
public class SystemMemberContextResolver {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String CURRENT_BINDING_PREFIX = "unexamine:context:current-binding:";
    private static final Duration CURRENT_BINDING_TTL = Duration.ofHours(8);

    private final CurrentAccountProvider currentAccountProvider;
    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final StringRedisTemplate redisTemplate;

    public SystemMemberContextResolver(CurrentAccountProvider currentAccountProvider,
                                       PlatAccountMemberBindingBaseService bindingBaseService,
                                       PlatRoleMemberBaseService roleMemberBaseService,
                                       PlatRoleBaseService roleBaseService,
                                       StringRedisTemplate redisTemplate) {
        this.currentAccountProvider = currentAccountProvider;
        this.bindingBaseService = bindingBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.roleBaseService = roleBaseService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Resolve current system context from route system id and active switch context.
     *
     * @param systemId route system id
     * @return resolved context
     */
    public SystemMemberContext resolve(String systemId) {
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
        return new SystemMemberContext(account.getId(), binding.getSystemId(), binding.getTenantId(),
                binding.getSystemMemberId());
    }

    /**
     * Resolve current system context and require a system administrator role.
     *
     * @param systemId route system id
     * @return resolved administrator context
     */
    public SystemMemberContext requireSystemAdmin(String systemId) {
        SystemMemberContext context = resolve(systemId);
        if (!hasSystemAdminRole(context)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED,
                    "当前成员没有系统后台管理权限");
        }
        return context;
    }

    /**
     * Parse route ids.
     *
     * @param value raw id
     * @param message invalid message
     * @return parsed id
     */
    public Long parseRequiredId(String value, String message) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private boolean hasSystemAdminRole(SystemMemberContext context) {
        List<Long> roleIds = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                        .eq(PlatRoleMember::getSystemId, context.systemId())
                        .eq(PlatRoleMember::getTenantId, context.tenantId())
                        .eq(PlatRoleMember::getSystemMemberId, context.systemMemberId()))
                .stream()
                .map(PlatRoleMember::getRoleId)
                .filter(Objects::nonNull)
                .toList();
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleBaseService.count(new LambdaQueryWrapper<PlatRole>()
                .in(PlatRole::getId, roleIds)
                .eq(PlatRole::getScope, SCOPE_SYSTEM)
                .eq(PlatRole::getSystemId, context.systemId())
                .eq(PlatRole::getTenantId, context.tenantId())
                .eq(PlatRole::getDeleted, DELETED_NO)
                .and(wrapper -> wrapper.eq(PlatRole::getRoleCode, "SYSTEM_SUPER_ADMIN")
                        .or().eq(PlatRole::getRoleCode, "SYSTEM_ADMIN")
                        .or().eq(PlatRole::getRoleType, "SYSTEM_SUPER_ADMIN")
                        .or().eq(PlatRole::getRoleType, "SYSTEM_ADMIN"))) > 0;
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

    /**
     * Current system member context.
     *
     * @param accountId account id
     * @param systemId system id
     * @param tenantId tenant id
     * @param systemMemberId system member id
     */
    public record SystemMemberContext(Long accountId, Long systemId, Long tenantId, Long systemMemberId) {
    }
}