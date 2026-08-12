package com.unique.examine.plat.vnext.manage.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.manage.security.PasswordService;
import com.unique.examine.plat.vnext.base.entity.Account;
import com.unique.examine.plat.vnext.base.entity.AccountRole;
import com.unique.examine.plat.vnext.base.entity.Credential;
import com.unique.examine.plat.vnext.base.entity.Permission;
import com.unique.examine.plat.vnext.base.entity.Role;
import com.unique.examine.plat.vnext.base.entity.RolePermission;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatAccountService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatCredentialService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatPermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRolePermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRoleService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@Profile("vnext")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(name = "examine.foundation.vnext.enabled", havingValue = "true")
@EnableConfigurationProperties(DefaultAdminBootstrapProperties.class)
public class DefaultAdminBootstrap implements ApplicationRunner {
    static final String PLATFORM_SCOPE = "PLATFORM";
    static final long PLATFORM_SCOPE_KEY = 0L;
    static final String ROOT_ROLE_CODE = "platform_root";
    private static final List<PermissionDefinition> P1_PLATFORM_PERMISSIONS = List.of(
            new PermissionDefinition("platform.runtime.access", "Access platform workspace", "SHELL"),
            new PermissionDefinition("platform.admin.access", "Access platform administration", "SHELL"),
            new PermissionDefinition("platform.system.manage", "Manage platform systems", "ACTION")
    );

    private final DefaultAdminBootstrapProperties properties;
    private final IVNextPlatAccountService accountService;
    private final IVNextPlatCredentialService credentialService;
    private final IVNextPlatRoleService roleService;
    private final IVNextPlatPermissionService permissionService;
    private final IVNextPlatRolePermissionService rolePermissionService;
    private final IVNextPlatAccountRoleService accountRoleService;
    private final IdService idService;
    private final PasswordService passwordService;

    public DefaultAdminBootstrap(
            DefaultAdminBootstrapProperties properties,
            IVNextPlatAccountService accountService,
            IVNextPlatCredentialService credentialService,
            IVNextPlatRoleService roleService,
            IVNextPlatPermissionService permissionService,
            IVNextPlatRolePermissionService rolePermissionService,
            IVNextPlatAccountRoleService accountRoleService,
            IdService idService,
            PasswordService passwordService
    ) {
        this.properties = properties;
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.rolePermissionService = rolePermissionService;
        this.accountRoleService = accountRoleService;
        this.idService = idService;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var username = properties.requiredUsername();
        var normalizedUsername = normalize(username);
        var now = LocalDateTime.now();
        var account = accountService.getOne(Wrappers.<Account>lambdaQuery()
                .eq(Account::getUsernameNormalized, normalizedUsername));
        if (account == null) {
            account = createAccount(username, normalizedUsername, now);
        } else if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalStateException(
                    "FOUNDATION_BOOTSTRAP_ADMIN_UNAVAILABLE: configured administrator exists but is not active"
            );
        }

        ensureCredential(account.getId(), now);
        var role = ensureRootRole(account.getId(), now);
        for (var definition : P1_PLATFORM_PERMISSIONS) {
            var permission = ensurePermission(definition, account.getId(), now);
            ensureRolePermission(role.getId(), permission.getId(), account.getId(), now);
        }
        ensureAccountRole(account.getId(), role.getId(), now);
    }

    private Account createAccount(String username, String normalizedUsername, LocalDateTime now) {
        var accountId = idService.nextId();
        var account = new Account();
        account.setId(accountId);
        account.setAccountCode("ACC_" + accountId);
        account.setUsername(username);
        account.setUsernameNormalized(normalizedUsername);
        account.setDisplayName(properties.effectiveDisplayName());
        account.setLocale("zh-CN");
        account.setTimeZone("Asia/Shanghai");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        account.setCreatedBy(accountId);
        account.setUpdatedAt(now);
        account.setUpdatedBy(accountId);
        account.setVersion(0L);
        save(accountService.save(account), "account");
        return account;
    }

    private void ensureCredential(long accountId, LocalDateTime now) {
        var existing = credentialService.getOne(Wrappers.<Credential>lambdaQuery()
                .eq(Credential::getAccountId, accountId)
                .eq(Credential::getCredentialType, "PASSWORD"));
        if (existing != null) {
            return;
        }
        var password = passwordService.hash(properties.requiredPassword());
        var credential = new Credential();
        credential.setId(idService.nextId());
        credential.setAccountId(accountId);
        credential.setCredentialType("PASSWORD");
        credential.setPasswordHash(password.encoded());
        credential.setPasswordAlgorithm(password.algorithm());
        credential.setPasswordParameters(password.parameters());
        credential.setFailedAttempts(0);
        credential.setPasswordChangedAt(now);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credential.setVersion(0L);
        save(credentialService.save(credential), "credential");
    }

    private Role ensureRootRole(long actorAccountId, LocalDateTime now) {
        var existing = roleService.getOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, PLATFORM_SCOPE)
                .eq(Role::getScopeKey, PLATFORM_SCOPE_KEY)
                .eq(Role::getRoleCode, ROOT_ROLE_CODE));
        if (existing != null) {
            if (!"ACTIVE".equals(existing.getStatus()) || !"ROOT".equals(existing.getRoleType())) {
                throw unavailable("root role");
            }
            return existing;
        }
        var role = new Role();
        role.setId(idService.nextId());
        role.setScopeType(PLATFORM_SCOPE);
        role.setScopeKey(PLATFORM_SCOPE_KEY);
        role.setRoleCode(ROOT_ROLE_CODE);
        role.setName("Platform Super Administrator");
        role.setRoleType("ROOT");
        role.setStatus("ACTIVE");
        role.setPermissionVersion(1L);
        role.setIsBuiltin(true);
        role.setPublishedVersion(1L);
        role.setCreatedAt(now);
        role.setCreatedBy(actorAccountId);
        role.setUpdatedAt(now);
        role.setUpdatedBy(actorAccountId);
        role.setVersion(0L);
        save(roleService.save(role), "role");
        return role;
    }

    private Permission ensurePermission(
            PermissionDefinition definition,
            long actorAccountId,
            LocalDateTime now
    ) {
        var existing = permissionService.getOne(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, PLATFORM_SCOPE)
                .eq(Permission::getScopeKey, PLATFORM_SCOPE_KEY)
                .eq(Permission::getPermissionCode, definition.code()));
        if (existing != null) {
            if (!"ACTIVE".equals(existing.getStatus())) {
                throw unavailable("permission " + definition.code());
            }
            return existing;
        }
        var permission = new Permission();
        permission.setId(idService.nextId());
        permission.setScopeType(PLATFORM_SCOPE);
        permission.setScopeKey(PLATFORM_SCOPE_KEY);
        permission.setPermissionCode(definition.code());
        permission.setName(definition.name());
        permission.setResourceType(definition.resourceType());
        permission.setStatus("ACTIVE");
        permission.setCreatedAt(now);
        permission.setCreatedBy(actorAccountId);
        permission.setUpdatedAt(now);
        permission.setUpdatedBy(actorAccountId);
        permission.setVersion(0L);
        save(permissionService.save(permission), "permission");
        return permission;
    }

    private void ensureRolePermission(long roleId, long permissionId, long actorAccountId, LocalDateTime now) {
        var existing = rolePermissionService.getOne(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getPermissionId, permissionId));
        if (existing != null) {
            if (!"ALLOW".equals(existing.getEffect())) {
                throw unavailable("role permission");
            }
            return;
        }
        var link = new RolePermission();
        link.setId(idService.nextId());
        link.setScopeType(PLATFORM_SCOPE);
        link.setScopeKey(PLATFORM_SCOPE_KEY);
        link.setRoleId(roleId);
        link.setPermissionId(permissionId);
        link.setEffect("ALLOW");
        link.setCreatedAt(now);
        link.setCreatedBy(actorAccountId);
        save(rolePermissionService.save(link), "role permission");
    }

    private void ensureAccountRole(long accountId, long roleId, LocalDateTime now) {
        var existing = accountRoleService.getOne(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getAccountId, accountId)
                .eq(AccountRole::getRoleId, roleId));
        if (existing != null) {
            if (existing.getValidFrom().isAfter(now)
                    || existing.getValidUntil() != null && !existing.getValidUntil().isAfter(now)) {
                throw unavailable("account role");
            }
            return;
        }
        var link = new AccountRole();
        link.setId(idService.nextId());
        link.setScopeType(PLATFORM_SCOPE);
        link.setScopeKey(PLATFORM_SCOPE_KEY);
        link.setAccountId(accountId);
        link.setRoleId(roleId);
        link.setValidFrom(now);
        link.setCreatedAt(now);
        link.setCreatedBy(accountId);
        save(accountRoleService.save(link), "account role");
    }

    private static void save(boolean saved, String recordType) {
        if (!saved) {
            throw new IllegalStateException("FOUNDATION_BOOTSTRAP_WRITE_FAILED: could not create " + recordType);
        }
    }

    private static IllegalStateException unavailable(String recordType) {
        return new IllegalStateException(
                "FOUNDATION_BOOTSTRAP_ADMIN_UNAVAILABLE: existing " + recordType + " is not active"
        );
    }

    static String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private record PermissionDefinition(String code, String name, String resourceType) {
    }
}
