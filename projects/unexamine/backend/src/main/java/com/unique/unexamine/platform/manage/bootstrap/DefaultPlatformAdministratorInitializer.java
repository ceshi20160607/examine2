package com.unique.unexamine.platform.manage.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformMemberRole;
import com.unique.unexamine.platform.base.entity.PlatformRole;
import com.unique.unexamine.platform.base.entity.PlatformRolePermission;
import com.unique.unexamine.platform.base.entity.PlatformDefinition;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRolePermissionBaseService;
import com.unique.unexamine.authentication.manage.Pbkdf2PasswordHasher;
import com.unique.unexamine.platform.manage.foundation.PlatformDefinitionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "app.bootstrap.default-admin", name = "enabled", matchIfMissing = true)
public class DefaultPlatformAdministratorInitializer implements ApplicationRunner {
    static final String SUPER_ADMIN_ROLE_CODE = "PLATFORM_SUPER_ADMIN";

    private final PlatformAccountBaseService accountService;
    private final PlatformAccountCredentialBaseService credentialService;
    private final PlatformMemberBaseService memberService;
    private final PlatformRoleBaseService roleService;
    private final PlatformMemberRoleBaseService memberRoleService;
    private final PlatformRolePermissionBaseService permissionService;
    private final AuditEventBaseService auditService;
    private final PlatformDefinitionManager platformDefinitionManager;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final String username;
    private final String initialPassword;
    private final String deploymentKey;

    public DefaultPlatformAdministratorInitializer(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            PlatformMemberBaseService memberService,
            PlatformRoleBaseService roleService,
            PlatformMemberRoleBaseService memberRoleService,
            PlatformRolePermissionBaseService permissionService,
            AuditEventBaseService auditService,
            PlatformDefinitionManager platformDefinitionManager,
            Pbkdf2PasswordHasher passwordHasher,
            @Value("${app.bootstrap.default-admin.username:admin}") String username,
            @Value("${app.bootstrap.default-admin.initial-password:123123aa}") String initialPassword,
            @Value("${app.bootstrap.default-admin.deployment-key:}") String deploymentKey) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.memberService = memberService;
        this.roleService = roleService;
        this.memberRoleService = memberRoleService;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.platformDefinitionManager = platformDefinitionManager;
        this.passwordHasher = passwordHasher;
        this.username = username;
        this.initialPassword = initialPassword;
        this.deploymentKey = deploymentKey;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        PlatformDefinition platform = platformDefinitionManager.requireDefaultPlatform();
        if (!findAccounts(username).isEmpty()) {
            return;
        }
        validateDeploymentKey(deploymentKey);

        PlatformAccount account = new PlatformAccount();
        account.setUsername(username);
        account.setDisplayName("平台超级管理员");
        account.setStatus("ACTIVE");
        accountService.insert(account);

        PlatformAccountCredential credential = new PlatformAccountCredential();
        credential.setAccountId(account.getId());
        credential.setPasswordHash(passwordHasher.hash(initialPassword.toCharArray()));
        credential.setCredentialVersion(1);
        credentialService.insert(credential);

        PlatformMember member = new PlatformMember();
        member.setPlatformId(platform.getId());
        member.setAccountId(account.getId());
        member.setStatus("ACTIVE");
        memberService.insert(member);

        PlatformRole role = findOrCreateSuperAdministratorRole(platform.getId());

        PlatformMemberRole memberRole = new PlatformMemberRole();
        memberRole.setPlatformId(platform.getId());
        memberRole.setMemberId(member.getId());
        memberRole.setRoleId(role.getId());
        memberRoleService.insert(memberRole);

        ensureAllPlatformPermission(platform.getId(), role.getId());

        AuditEvent audit = new AuditEvent();
        audit.setTraceId("bootstrap-" + UUID.randomUUID().toString().replace("-", ""));
        audit.setContextType("PLATFORM");
        audit.setActorAccountId(account.getId());
        audit.setPlatformId(platform.getId());
        audit.setEventCategory("SECURITY");
        audit.setEventCode("PLATFORM_DEFAULT_ADMIN_CREATED");
        audit.setObjectType("PLATFORM_ACCOUNT");
        audit.setObjectId(account.getId().toString());
        audit.setResultCode("SUCCESS");
        audit.setDetailJson("{\"source\":\"application-bootstrap\"}");
        auditService.insert(audit);
    }

    static void validateDeploymentKey(String value) {
        if (value == null || value.isBlank() || value.length() < 24) {
            throw new IllegalStateException(
                    "Default platform administrator initialization requires a deployment key of at least 24 characters");
        }
    }

    private List<PlatformAccount> findAccounts(String value) {
        return accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                .eq(PlatformAccount::getUsername, value));
    }

    private PlatformRole findOrCreateSuperAdministratorRole(Long platformId) {
        List<PlatformRole> existing = roleService.selectList(Wrappers.<PlatformRole>lambdaQuery()
                .eq(PlatformRole::getPlatformId, platformId)
                .eq(PlatformRole::getCode, SUPER_ADMIN_ROLE_CODE));
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        PlatformRole role = new PlatformRole();
        role.setPlatformId(platformId);
        role.setCode(SUPER_ADMIN_ROLE_CODE);
        role.setName("平台超级管理员");
        role.setStatus("ACTIVE");
        roleService.insert(role);
        return role;
    }

    private void ensureAllPlatformPermission(Long platformId, Long roleId) {
        List<PlatformRolePermission> existing = permissionService.selectList(
                Wrappers.<PlatformRolePermission>lambdaQuery()
                        .eq(PlatformRolePermission::getRoleId, roleId)
                        .eq(PlatformRolePermission::getResourceType, "PLATFORM")
                        .eq(PlatformRolePermission::getResourceCode, "*")
                        .eq(PlatformRolePermission::getActionCode, "*"));
        if (!existing.isEmpty()) {
            return;
        }
        PlatformRolePermission permission = new PlatformRolePermission();
        permission.setPlatformId(platformId);
        permission.setRoleId(roleId);
        permission.setResourceType("PLATFORM");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permissionService.insert(permission);
    }
}
