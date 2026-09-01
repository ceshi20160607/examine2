package com.unique.unexamine.platform.manage.registration;

import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformDefinition;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.SystemTenantMember;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.SystemTenantMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.platform.manage.foundation.PlatformDefinitionManager;
import com.unique.unexamine.authentication.manage.AuthenticationService;
import com.unique.unexamine.authentication.manage.Pbkdf2PasswordHasher;
import com.unique.unexamine.authentication.manage.SessionTokens;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.NullNode;

@Service
public class RegistrationTransactionService {
    private final PlatformAccountBaseService accountService;
    private final PlatformAccountCredentialBaseService credentialService;
    private final PlatformMemberBaseService platformMemberService;
    private final SystemDefinitionBaseService systemService;
    private final SystemTenantBaseService tenantService;
    private final SystemMemberBaseService memberService;
    private final SystemTenantMemberBaseService tenantMemberService;
    private final SystemRoleBaseService roleService;
    private final SystemMemberRoleBaseService memberRoleService;
    private final SystemRolePermissionBaseService rolePermissionService;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final AuthenticationService authenticationService;
    private final AuditRecorder auditRecorder;
    private final PlatformDefinitionManager platformDefinitionManager;

    public RegistrationTransactionService(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            PlatformMemberBaseService platformMemberService,
            SystemDefinitionBaseService systemService,
            SystemTenantBaseService tenantService,
            SystemMemberBaseService memberService,
            SystemTenantMemberBaseService tenantMemberService,
            SystemRoleBaseService roleService,
            SystemMemberRoleBaseService memberRoleService,
            SystemRolePermissionBaseService rolePermissionService,
            Pbkdf2PasswordHasher passwordHasher,
            AuthenticationService authenticationService,
            AuditRecorder auditRecorder,
            PlatformDefinitionManager platformDefinitionManager) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.platformMemberService = platformMemberService;
        this.systemService = systemService;
        this.tenantService = tenantService;
        this.memberService = memberService;
        this.tenantMemberService = tenantMemberService;
        this.roleService = roleService;
        this.memberRoleService = memberRoleService;
        this.rolePermissionService = rolePermissionService;
        this.passwordHasher = passwordHasher;
        this.authenticationService = authenticationService;
        this.auditRecorder = auditRecorder;
        this.platformDefinitionManager = platformDefinitionManager;
    }

    @Transactional
    public RegistrationResult create(RegistrationRequest request, String traceId) {
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        String systemCode = request.systemCode().strip().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().strip();
        String systemName = request.systemName().strip();
        PlatformDefinition platform = platformDefinitionManager.requireDefaultPlatform();

        PlatformAccount account = new PlatformAccount();
        account.setUsername(username);
        account.setEmail(blankToNull(request.email()));
        account.setDisplayName(displayName);
        account.setStatus("ACTIVE");
        accountService.insert(account);

        PlatformAccountCredential credential = new PlatformAccountCredential();
        credential.setAccountId(account.getId());
        credential.setPasswordHash(passwordHasher.hash(request.password().toCharArray()));
        credential.setCredentialVersion(1);
        credentialService.insert(credential);

        PlatformMember platformMember = new PlatformMember();
        platformMember.setPlatformId(platform.getId());
        platformMember.setAccountId(account.getId());
        platformMember.setStatus("ACTIVE");
        platformMemberService.insert(platformMember);

        SystemDefinition system = new SystemDefinition();
        system.setPlatformId(platform.getId());
        system.setCode(systemCode);
        system.setName(systemName);
        system.setCreatorAccountId(account.getId());
        system.setTenantMode("SINGLE");
        system.setStatus("ACTIVE");
        system.setDeleted(false);
        systemService.insert(system);

        SystemTenant tenant = new SystemTenant();
        tenant.setSystemId(system.getId());
        tenant.setCode("main");
        tenant.setName("默认主租户");
        tenant.setMain(true);
        tenant.setMainMarker("MAIN");
        tenant.setCreatorAccountId(account.getId());
        tenant.setStatus("ACTIVE");
        tenantService.insert(tenant);

        SystemMember member = new SystemMember();
        member.setSystemId(system.getId());
        member.setAccountId(account.getId());
        member.setDisplayName(displayName);
        member.setStatus("ACTIVE");
        memberService.insert(member);

        SystemTenantMember tenantMember = new SystemTenantMember();
        tenantMember.setSystemId(system.getId());
        tenantMember.setTenantId(tenant.getId());
        tenantMember.setSystemMemberId(member.getId());
        tenantMember.setTenantAdmin(true);
        tenantMember.setStatus("ACTIVE");
        tenantMemberService.insert(tenantMember);

        SystemRole role = new SystemRole();
        role.setSystemId(system.getId());
        role.setTenantId(tenant.getId());
        role.setCode("SYSTEM_SUPER_ADMIN");
        role.setName("系统超级管理员");
        role.setStatus("ACTIVE");
        roleService.insert(role);

        SystemMemberRole memberRole = new SystemMemberRole();
        memberRole.setTenantId(tenant.getId());
        memberRole.setTenantMemberId(tenantMember.getId());
        memberRole.setRoleId(role.getId());
        memberRoleService.insert(memberRole);

        SystemRolePermission permission = new SystemRolePermission();
        permission.setSystemId(system.getId());
        permission.setTenantId(tenant.getId());
        permission.setRoleId(role.getId());
        permission.setResourceType("*");
        permission.setResourceCode("*");
        permission.setActionCode("*");
        permission.setDataScopeType("ALL");
        rolePermissionService.insert(permission);

        SessionTokens tokens = authenticationService.createSystemSession(
                account.getId(), system.getId(), tenant.getId(), member.getId(), tenantMember.getId(),
                new ResolvedPermissions(
                        List.of(role.getId()),
                        List.of(new PermissionGrant("*", "*", "*", List.of(role.getId()))),
                        Map.of("*:*:*", new DataScopeExpression("ALL",
                                List.of(new DataScopeTerm("ALL", NullNode.getInstance(), List.of(role.getId())))))),
                "NONE");

        auditRecorder.record(traceId, account.getId(), system.getId(), tenant.getId(), member.getId(),
                "ACCOUNT_REGISTER_SYSTEM_CREATED", "SYSTEM", system.getId().toString(), "SUCCESS",
                Map.of("tenantMode", "SINGLE", "mainTenant", true));

        return new RegistrationResult(account.getId(), system.getId(), systemCode, systemName, tenant.getId(),
                tenant.getName(), system.getTenantMode(), tokens);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
