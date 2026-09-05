package com.unique.unexamine.work.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels;
import com.unique.unexamine.authorization.manage.SystemPeopleDirectoryService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Translates business-facing member selections into the legacy account keys used by old storage columns.
 * System APIs use tenant member ids; account ids remain an internal compatibility key only.
 */
@Service
public class WorkParticipantResolver {
    private final SystemPeopleDirectoryService peopleDirectoryService;
    private final PlatformAccountBaseService accountService;

    public WorkParticipantResolver(
            SystemPeopleDirectoryService peopleDirectoryService,
            PlatformAccountBaseService accountService) {
        this.peopleDirectoryService = peopleDirectoryService;
        this.accountService = accountService;
    }

    public ResolvedPerson current(AuthenticatedContext context) {
        return require(context, null, context.accountId());
    }

    public ResolvedPerson require(
            AuthenticatedContext context, Long tenantMemberId, Long compatibilityAccountId) {
        if (context.systemId() != null) {
            SystemPeopleDirectoryModels.Person person = peopleDirectoryService.directory(context, null).people().stream()
                    .filter(item -> tenantMemberId != null
                            ? Objects.equals(item.tenantMemberId(), tenantMemberId)
                            : Objects.equals(item.accountId(), compatibilityAccountId))
                    .findFirst().orElseThrow(() -> invalid("TASK_ASSIGNEE_CONTEXT_INVALID",
                            "所选成员不属于当前系统租户或已停用"));
            return from(person);
        }
        Long accountId = compatibilityAccountId != null ? compatibilityAccountId : tenantMemberId;
        PlatformAccount account = accountId == null ? null : accountService.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw invalid("TASK_ASSIGNEE_INVALID", "所选平台成员不存在或不可用");
        }
        String name = account.getDisplayName() == null || account.getDisplayName().isBlank()
                ? account.getUsername() : account.getDisplayName();
        return new ResolvedPerson(null, account.getId(), name, null, null);
    }

    public ResolvedPerson find(AuthenticatedContext context, Long tenantMemberId, Long accountId) {
        try {
            return require(context, tenantMemberId, accountId);
        } catch (DomainException ignored) {
            return null;
        }
    }

    public Long currentTenantMemberId(AuthenticatedContext context) {
        return current(context).tenantMemberId();
    }

    private ResolvedPerson from(SystemPeopleDirectoryModels.Person person) {
        return new ResolvedPerson(person.tenantMemberId(), person.accountId(), person.displayName(),
                person.departmentName(), person.positionTitle());
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record ResolvedPerson(
            Long tenantMemberId,
            Long accountId,
            String displayName,
            String departmentName,
            String positionTitle) {
    }
}
