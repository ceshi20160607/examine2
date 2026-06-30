package com.unique.examine.plat.manage.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatSystem;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatSystemBaseService;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Backend guard for system administration APIs.
 */
@Component
public class SystemAccessGuard {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String CURRENT_BINDING_PREFIX = "unexamine:context:current-binding:";
    private static final Duration CURRENT_BINDING_TTL = Duration.ofHours(8);
    private static final Set<String> SYSTEM_ADMIN_ROLES = Set.of("SYSTEM_ADMIN", "SYSTEM_SUPER_ADMIN");

    private final PlatAccountMemberBindingBaseService bindingBaseService;
    private final PlatSystemBaseService systemBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final StringRedisTemplate redisTemplate;

    public SystemAccessGuard(PlatAccountMemberBindingBaseService bindingBaseService,
                             PlatSystemBaseService systemBaseService,
                             PlatRoleMemberBaseService roleMemberBaseService,
                             PlatRoleBaseService roleBaseService,
                             StringRedisTemplate redisTemplate) {
        this.bindingBaseService = bindingBaseService;
        this.systemBaseService = systemBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.roleBaseService = roleBaseService;
        this.redisTemplate = redisTemplate;
    }

    public boolean canManageSystem(Long accountId, String systemIdOrCode) {
        if (Objects.isNull(accountId) || !StringUtils.hasText(systemIdOrCode)) {
            return false;
        }
        PlatSystem system = resolveSystem(systemIdOrCode);
        if (Objects.isNull(system)) {
            return false;
        }
        PlatAccountMemberBinding binding = currentBinding(accountId);
        if (Objects.isNull(binding) || !Objects.equals(binding.getSystemId(), system.getId())) {
            binding = firstBinding(accountId, system.getId());
        }
        if (Objects.isNull(binding)) {
            return false;
        }
        return roleCodes(binding).stream().anyMatch(SYSTEM_ADMIN_ROLES::contains);
    }

    private PlatAccountMemberBinding currentBinding(Long accountId) {
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
        redisTemplate.expire(CURRENT_BINDING_PREFIX + accountId, CURRENT_BINDING_TTL);
        return binding;
    }

    private PlatAccountMemberBinding firstBinding(Long accountId, Long systemId) {
        return bindingBaseService.getOne(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getAccountId, accountId)
                .eq(PlatAccountMemberBinding::getSystemId, systemId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                .orderByAsc(PlatAccountMemberBinding::getId)
                .last("LIMIT 1"), false);
    }

    private PlatSystem resolveSystem(String systemIdOrCode) {
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

    private Set<String> roleCodes(PlatAccountMemberBinding binding) {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (PlatRoleMember roleMember : roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, binding.getSystemId())
                .eq(PlatRoleMember::getTenantId, binding.getTenantId())
                .eq(PlatRoleMember::getSystemMemberId, binding.getSystemMemberId()))) {
            if (Objects.nonNull(roleMember.getRoleId())) {
                roleIds.add(roleMember.getRoleId());
            }
        }
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (PlatRole role : roleBaseService.list(new LambdaQueryWrapper<PlatRole>()
                .in(PlatRole::getId, roleIds)
                .eq(PlatRole::getScope, SCOPE_SYSTEM)
                .eq(PlatRole::getStatus, ENABLED)
                .eq(PlatRole::getDeleted, DELETED_NO))) {
            if (StringUtils.hasText(role.getRoleCode())) {
                codes.add(role.getRoleCode());
            }
            if (StringUtils.hasText(role.getRoleType())) {
                codes.add(role.getRoleType());
            }
        }
        return codes;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
