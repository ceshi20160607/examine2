package com.unique.examine.plat.vnext.manage.registration;

import com.unique.examine.core.base.entity.Idempotency;
import com.unique.examine.plat.vnext.manage.auth.ClientRequest;
import com.unique.examine.plat.vnext.manage.auth.IssuedSession;
import com.unique.examine.plat.vnext.manage.auth.VNextSecurityAuditService;
import com.unique.examine.plat.vnext.manage.auth.VNextSystemSessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/** Thin transaction orchestrator that owns only the frozen P1 registration order. */
@Service
public class VNextRegistrationTransaction {
    private final RegistrationIdempotencyStore idempotencyStore;
    private final RegistrationIdentityService identityService;
    private final RegistrationSystemMembershipService systemMembershipService;
    private final RegistrationAuthorizationService authorizationService;
    private final VNextSystemSessionService sessionService;
    private final VNextSecurityAuditService securityAuditService;
    private final RegistrationFailureProbe failureProbe;

    VNextRegistrationTransaction(
            RegistrationIdempotencyStore idempotencyStore,
            RegistrationIdentityService identityService,
            RegistrationSystemMembershipService systemMembershipService,
            RegistrationAuthorizationService authorizationService,
            VNextSystemSessionService sessionService,
            VNextSecurityAuditService securityAuditService,
            RegistrationFailureProbe failureProbe
    ) {
        this.idempotencyStore = idempotencyStore;
        this.identityService = identityService;
        this.systemMembershipService = systemMembershipService;
        this.authorizationService = authorizationService;
        this.sessionService = sessionService;
        this.securityAuditService = securityAuditService;
        this.failureProbe = failureProbe;
    }

    @Transactional
    public IssuedSession create(RegistrationCommand command, ClientRequest client) {
        var now = LocalDateTime.now();
        var idempotency = idempotencyStore.begin(command, now);
        passed(RegistrationStep.IDEMPOTENCY);

        if (identityService.find(command.usernameNormalized()) != null) {
            throw RegistrationErrors.usernameConflict();
        }
        if (systemMembershipService.findSystem(command.systemCode()) != null) {
            throw RegistrationErrors.systemCodeConflict();
        }
        passed(RegistrationStep.AVAILABILITY);

        var account = identityService.create(command, now);
        passed(RegistrationStep.IDENTITY);
        var system = systemMembershipService.createSystem(command, account.getId(), now);
        passed(RegistrationStep.SYSTEM);
        var tenant = systemMembershipService.createTenant(system.getId(), account.getId(), now);
        passed(RegistrationStep.TENANT);
        var member = systemMembershipService.createMembership(command, account, system, tenant, now);
        passed(RegistrationStep.MEMBERSHIP);

        var dataScope = authorizationService.createDataScope(account.getId(), system.getId(), now);
        passed(RegistrationStep.DATA_SCOPE);
        var role = authorizationService.createRole(
                account.getId(), system.getId(), dataScope.getId(), now
        );
        passed(RegistrationStep.ROLE);
        authorizationService.createPermissions(account.getId(), system.getId(), role.getId(), now);
        passed(RegistrationStep.PERMISSIONS);
        authorizationService.createMemberRole(
                account.getId(), system.getId(), member.getId(), role.getId(), now
        );
        passed(RegistrationStep.MEMBER_ROLE);

        var issue = issue(account, system, tenant, member, role, dataScope, now);
        securityAuditService.record(
                "REGISTER_FIRST_SYSTEM", account.getId(), null, system.getId(), tenant.getId(),
                "SUCCESS", null, client, now
        );
        passed(RegistrationStep.SECURITY_AUDIT);

        systemMembershipService.activate(system, account.getId(), now);
        passed(RegistrationStep.SYSTEM_ACTIVE);
        var receipt = receipt(account.getId(), system.getId(), tenant.getId(), member.getId(),
                role.getId(), dataScope.getId(), issue);
        var issued = sessionService.complete(account, issue, system.getId());
        idempotencyStore.complete(
                idempotency, new StoredRegistration(receipt, issued.accountContext()), now
        );
        passed(RegistrationStep.IDEMPOTENCY_COMPLETE);
        return issued;
    }

    @Transactional
    public IssuedSession replay(Idempotency idempotency, StoredRegistration stored, ClientRequest client) {
        var now = LocalDateTime.now();
        var previous = stored.receipt();
        var account = identityService.requireActive(previous.accountId());
        var systemGraph = systemMembershipService.loadActive(previous);
        var authorization = authorizationService.loadActive(previous);

        sessionService.revoke(
                previous.contextSessionId(), previous.refreshTokenId(), now
        );
        var issue = issue(
                account, systemGraph.system(), systemGraph.tenant(), systemGraph.member(),
                authorization.role(), authorization.dataScope(), now
        );
        var receipt = receipt(
                previous.accountId(), previous.systemId(), previous.tenantId(), previous.memberId(),
                previous.roleId(), previous.dataScopeId(), issue
        );
        var issued = sessionService.complete(account, issue, previous.systemId());
        idempotencyStore.complete(
                idempotency, new StoredRegistration(receipt, issued.accountContext()), now
        );
        securityAuditService.record(
                "REGISTER_FIRST_SYSTEM_REPLAY", account.getId(), null, previous.systemId(),
                previous.tenantId(), "SUCCESS", null, client, now
        );
        return issued;
    }

    private VNextSystemSessionService.SystemSessionIssue issue(
            com.unique.examine.plat.vnext.base.entity.Account account,
            com.unique.examine.plat.vnext.base.entity.System system,
            com.unique.examine.plat.vnext.base.entity.Tenant tenant,
            com.unique.examine.plat.vnext.base.entity.Member member,
            com.unique.examine.plat.vnext.base.entity.Role role,
            com.unique.examine.plat.vnext.base.entity.DataScope dataScope,
            LocalDateTime now
    ) {
        return sessionService.issue(
                account, system, tenant, member,
                Set.copyOf(RegistrationAuthorizationService.PERMISSIONS),
                authorizationService.roleSnapshot(role),
                authorizationService.dataScopeSnapshot(role, dataScope),
                now,
                stage -> passed(RegistrationStep.valueOf(stage.name()))
        );
    }

    private static RegistrationReceipt receipt(
            long accountId, long systemId, long tenantId, long memberId,
            long roleId, long dataScopeId, VNextSystemSessionService.SystemSessionIssue issue
    ) {
        return new RegistrationReceipt(
                accountId, systemId, tenantId, memberId, roleId, dataScopeId,
                issue.context().getId(), issue.refresh().getId()
        );
    }

    private void passed(RegistrationStep step) {
        failureProbe.afterStep(step);
    }
}
