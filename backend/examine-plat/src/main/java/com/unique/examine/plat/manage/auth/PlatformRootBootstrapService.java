package com.unique.examine.plat.manage.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatAccountBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Bootstraps the built-in platform root account for a real deployment.
 */
@Service
public class PlatformRootBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformRootBootstrapService.class);

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_PLATFORM = "PLATFORM";
    private static final String PLATFORM_ROOT_ROLE_CODE = "PLATFORM_ROOT";
    private static final String LEGACY_PLATFORM_ROOT_ROLE_CODE = "platform_admin_root";
    private static final String PLATFORM_ROOT_ROLE_TYPE = "PLATFORM_ROOT";

    private final PlatAccountBaseService accountBaseService;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String accountName;
    private final String initialPassword;
    private final boolean resetPassword;

    public PlatformRootBootstrapService(PlatAccountBaseService accountBaseService,
                                        PlatRoleBaseService roleBaseService,
                                        PlatRoleMemberBaseService roleMemberBaseService,
                                        PasswordEncoder passwordEncoder,
                                        @Value("${unexamine.bootstrap.platform-root.enabled:true}") boolean enabled,
                                        @Value("${unexamine.bootstrap.platform-root.account-name:admin}") String accountName,
                                        @Value("${unexamine.bootstrap.platform-root.initial-password:123123aa}") String initialPassword,
                                        @Value("${unexamine.bootstrap.platform-root.reset-password:false}") boolean resetPassword) {
        this.accountBaseService = accountBaseService;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.accountName = accountName;
        this.initialPassword = initialPassword;
        this.resetPassword = resetPassword;
    }

    /**
     * Ensure the platform root account exists after the application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(rollbackFor = Exception.class)
    public void ensurePlatformRoot(ApplicationReadyEvent event) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(accountName) || !StringUtils.hasText(initialPassword)) {
            LOGGER.warn("Skip platform root bootstrap because account name or initial password is empty.");
            return;
        }
        try {
            PlatAccount rootAccount = ensureRootAccount();
            PlatRole rootRole = ensureRootRole();
            ensureRootRoleMember(rootRole.getId(), rootAccount.getId());
            LOGGER.info("Platform root bootstrap checked for account '{}'.", accountName);
        } catch (DataAccessException ex) {
            LOGGER.warn("Skip platform root bootstrap because database schema is not ready: {}", ex.getMessage());
        }
    }

    /**
     * Create or repair the built-in platform root account.
     *
     * @return root account
     */
    private PlatAccount ensureRootAccount() {
        LocalDateTime now = LocalDateTime.now();
        PlatAccount account = accountBaseService.getOne(new LambdaQueryWrapper<PlatAccount>()
                .eq(PlatAccount::getAccountName, accountName)
                .eq(PlatAccount::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(account)) {
            account = new PlatAccount();
            account.setAccountName(accountName);
            account.setPasswordHash(passwordEncoder.encode(initialPassword));
            account.setStatus(ENABLED);
            account.setCreatedAt(now);
            account.setUpdatedAt(now);
            account.setDeleted(DELETED_NO);
            accountBaseService.saveEntity(account);
            return account;
        }

        boolean changed = false;
        if (!Objects.equals(account.getStatus(), ENABLED)) {
            account.setStatus(ENABLED);
            changed = true;
        }
        if (!StringUtils.hasText(account.getPasswordHash()) || resetPassword) {
            account.setPasswordHash(passwordEncoder.encode(initialPassword));
            changed = true;
        }
        if (changed) {
            account.setUpdatedAt(now);
            accountBaseService.updateById(account);
        }
        return account;
    }

    /**
     * Create or repair the built-in platform root role.
     *
     * @return root role
     */
    private PlatRole ensureRootRole() {
        LocalDateTime now = LocalDateTime.now();
        List<PlatRole> candidates = roleBaseService.list(new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getScope, SCOPE_PLATFORM)
                .eq(PlatRole::getDeleted, DELETED_NO)
                .and(wrapper -> wrapper.eq(PlatRole::getRoleCode, PLATFORM_ROOT_ROLE_CODE)
                        .or().eq(PlatRole::getRoleCode, LEGACY_PLATFORM_ROOT_ROLE_CODE))
                .orderByAsc(PlatRole::getId));
        PlatRole role = candidates.stream()
                .filter(item -> PLATFORM_ROOT_ROLE_CODE.equals(item.getRoleCode()))
                .findFirst()
                .orElse(candidates.isEmpty() ? null : candidates.get(0));
        if (Objects.isNull(role)) {
            role = new PlatRole();
            role.setScope(SCOPE_PLATFORM);
            role.setRoleCode(PLATFORM_ROOT_ROLE_CODE);
            role.setCreatedAt(now);
            role.setDeleted(DELETED_NO);
        }
        role.setSystemId(null);
        role.setTenantId(null);
        role.setRoleCode(PLATFORM_ROOT_ROLE_CODE);
        role.setRoleName("Platform Root");
        role.setRoleType(PLATFORM_ROOT_ROLE_TYPE);
        role.setBuiltin(ENABLED);
        role.setStatus(ENABLED);
        role.setDescription("Built-in platform super administrator.");
        role.setUpdatedAt(now);
        if (Objects.isNull(role.getId())) {
            roleBaseService.saveEntity(role);
        } else {
            roleBaseService.updateById(role);
        }
        return role;
    }

    /**
     * Bind the platform root role to the platform root account.
     *
     * @param roleId root role id
     * @param accountId root account id
     */
    private void ensureRootRoleMember(Long roleId, Long accountId) {
        long count = roleMemberBaseService.count(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getRoleId, roleId)
                .eq(PlatRoleMember::getAccountId, accountId)
                .isNull(PlatRoleMember::getSystemMemberId)
                .isNull(PlatRoleMember::getSystemId)
                .isNull(PlatRoleMember::getTenantId));
        if (count > 0) {
            return;
        }
        PlatRoleMember roleMember = new PlatRoleMember();
        roleMember.setRoleId(roleId);
        roleMember.setAccountId(accountId);
        roleMember.setCreatedAt(LocalDateTime.now());
        roleMemberBaseService.saveEntity(roleMember);
    }
}
