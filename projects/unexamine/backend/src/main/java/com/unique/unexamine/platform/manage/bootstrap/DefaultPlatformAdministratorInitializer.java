package com.unique.unexamine.platform.manage.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformMemberRole;
import com.unique.unexamine.platform.base.entity.PlatformRole;
import com.unique.unexamine.platform.base.entity.PlatformRolePermission;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRoleBaseService;
import com.unique.unexamine.platform.base.service.PlatformRolePermissionBaseService;
import com.unique.unexamine.authentication.manage.Pbkdf2PasswordHasher;
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
    private final Pbkdf2PasswordHasher passwordHasher;
    private final String username;
    private final String initialPassword;

    public DefaultPlatformAdministratorInitializer(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            PlatformMemberBaseService memberService,
            PlatformRoleBaseService roleService,
            PlatformMemberRoleBaseService memberRoleService,
            PlatformRolePermissionBaseService permissionService,
            AuditEventBaseService auditService,
            Pbkdf2PasswordHasher passwordHasher,
            @Value("${app.bootstrap.default-admin.username:admin}") String username,
            @Value("${app.bootstrap.default-admin.initial-password:123123aa}") String initialPassword) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.memberService = memberService;
        this.roleService = roleService;
        this.memberRoleService = memberRoleService;
        this.permissionService = permissionService;
        this.auditService = auditService;
        this.passwordHasher = passwordHasher;
        this.username = username;
        this.initialPassword = initialPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!findAccounts(username).isEmpty()) {
            return;
        }

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
        member.setAccountId(account.getId());
        member.setStatus("ACTIVE");
        memberService.insert(member);

        PlatformRole role = findOrCreateSuperAdministratorRole();

        PlatformMemberRole memberRole = new PlatformMemberRole();
        memberRole.setMemberId(member.getId());
        memberRole.setRoleId(role.getId());
        memberRoleService.insert(memberRole);

        ensureAllPlatformPermission(role.getId());

        AuditEvent audit = new AuditEvent();
        audit.setTraceId("bootstrap-" + UUID.randomUUID().toString().replace("-", ""));
        audit.setActorAccountId(account.getId());
        audit.setEventCode("PLATFORM_DEFAULT_ADMIN_CREATED");
        audit.setObjectType("PLATFORM_ACCOUNT");
        audit.setObjectId(account.getId().toString());
        audit.setResultCode("SUCCESS");
        audit.setDetailJson("{\"source\":\"application-bootstrap\"}");
        auditService.insert(audit);
    }

    private List<PlatformAccount> findAccounts(String value) {
        return accountService.selectList(Wrappers.<PlatformAccount>lambdaQuery()
                .eq(PlatformAccount::getUsername, value));
    }

    private PlatformRole findOrCreateSuperAdministratorRole() {
        List<PlatformRole> existing = roleService.selectList(Wrappers.<PlatformRole>lambdaQuery()
                .eq(PlatformRole::getCode, SUPER_ADMIN_ROLE_CODE));
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        PlatformRole role = new PlatformRole();
        role.setCode(SUPER_ADMIN_ROLE_CODE);
        role.setName("平台超级管理员");
        role.setStatus("ACTIVE");
        roleService.insert(role);
        return role;
    }

    private void ensureAllPlatformPermission(Long roleId) {
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
        permission.setRoleId(roleId);
        permission.setResourceType("PLATFORM");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permissionService.insert(permission);
    }
}
