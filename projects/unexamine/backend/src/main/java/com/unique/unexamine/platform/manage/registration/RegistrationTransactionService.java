package com.unique.unexamine.platform.manage.registration;

import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.entity.PlatformAccountCredential;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.entity.PlatformDefinition;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.platform.base.service.PlatformAccountCredentialBaseService;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.platform.manage.foundation.PlatformDefinitionManager;
import com.unique.unexamine.authentication.manage.AuthenticationService;
import com.unique.unexamine.authentication.manage.Pbkdf2PasswordHasher;
import com.unique.unexamine.authentication.manage.SessionTokens;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.authorization.manage.DataScopeExpression;
import com.unique.unexamine.authorization.manage.DataScopeTerm;
import com.unique.unexamine.authorization.manage.PermissionGrant;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.system.manage.SystemBootstrapResult;
import com.unique.unexamine.system.manage.SystemBootstrapService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.NullNode;

@Service
public class RegistrationTransactionService {
    private final PlatformAccountBaseService accountService;
    private final PlatformAccountCredentialBaseService credentialService;
    private final PlatformMemberBaseService platformMemberService;
    private final SystemBootstrapService systemBootstrapService;
    private final Pbkdf2PasswordHasher passwordHasher;
    private final AuthenticationService authenticationService;
    private final AuditRecorder auditRecorder;
    private final PlatformDefinitionManager platformDefinitionManager;

    public RegistrationTransactionService(
            PlatformAccountBaseService accountService,
            PlatformAccountCredentialBaseService credentialService,
            PlatformMemberBaseService platformMemberService,
            SystemBootstrapService systemBootstrapService,
            Pbkdf2PasswordHasher passwordHasher,
            AuthenticationService authenticationService,
            AuditRecorder auditRecorder,
            PlatformDefinitionManager platformDefinitionManager) {
        this.accountService = accountService;
        this.credentialService = credentialService;
        this.platformMemberService = platformMemberService;
        this.systemBootstrapService = systemBootstrapService;
        this.passwordHasher = passwordHasher;
        this.authenticationService = authenticationService;
        this.auditRecorder = auditRecorder;
        this.platformDefinitionManager = platformDefinitionManager;
    }

    /** 创建统一平台账号；仅在用户明确填写系统名称时初始化业务系统。 */
    @Transactional
    public RegistrationResult create(RegistrationRequest request, String traceId) {
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().strip();
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

        if (request.systemName() == null || request.systemName().isBlank()) {
            if (request.systemCode() != null && !request.systemCode().isBlank()) {
                throw new DomainException("SYSTEM_NAME_REQUIRED", "填写系统标识时也需要系统名称",
                        HttpStatus.BAD_REQUEST);
            }
            SessionTokens platformTokens = authenticationService.createPlatformSession(account.getId(), "NONE");
            auditRecorder.record(traceId, account.getId(), null, null, null,
                    "ACCOUNT_REGISTERED", "PLATFORM_ACCOUNT", account.getId().toString(), "SUCCESS",
                    Map.of("registrationMode", "PLATFORM_ONLY"));
            return new RegistrationResult(account.getId(), null, null, null, null, null, null,
                    null, platformTokens);
        }

        SystemBootstrapResult initialized = systemBootstrapService.bootstrap(
                platform.getId(), account.getId(), displayName, request.systemName(), request.systemCode());
        var system = initialized.system();
        var tenant = initialized.tenant();
        var member = initialized.member();
        var tenantMember = initialized.tenantMember();

        SessionTokens platformTokens = authenticationService.createPlatformSession(account.getId(), "NONE");
        SessionTokens systemTokens = authenticationService.createSystemSession(
                account.getId(), system.getId(), tenant.getId(), member.getId(), tenantMember.getId(),
                new ResolvedPermissions(
                        List.of(),
                        List.of(new PermissionGrant("*", "*", "*", List.of())),
                        Map.of("*:*:*", new DataScopeExpression("ALL",
                                List.of(new DataScopeTerm("ALL", NullNode.getInstance(), List.of()))))),
                "NONE");

        auditRecorder.record(traceId, account.getId(), system.getId(), tenant.getId(), member.getId(),
                "ACCOUNT_REGISTER_SYSTEM_CREATED", "SYSTEM", system.getId().toString(), "SUCCESS",
                Map.of("tenantMode", "SINGLE", "mainTenant", true,
                        "rootDepartmentId", initialized.rootDepartment().getId()));

        return new RegistrationResult(account.getId(), system.getId(), system.getCode(), system.getName(), tenant.getId(),
                tenant.getName(), system.getTenantMode(), systemTokens, platformTokens);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
