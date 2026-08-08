package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.api.FlowPermissions;
import com.unique.examine.flow.security.FlowSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

/** Live authorization and member-fact checks for Flow definition drafts. */
@Component
public class FlowAiDefinitionDraftContextReader {
    private final EffectivePermissionFacade authorization;
    private final RuntimeActiveMemberFacade activeMembers;

    public FlowAiDefinitionDraftContextReader(
            EffectivePermissionFacade authorization,
            RuntimeActiveMemberFacade activeMembers) {
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
        this.activeMembers = Objects.requireNonNull(
                activeMembers, "activeMembers");
    }

    @Transactional
    public FlowSession validate(
            Access access, AiFlowDefinitionDraftFacade.Draft draft) {
        var session = authorize(access);
        validateFacts(session, draft);
        return session;
    }

    @Transactional(readOnly = true)
    public FlowSession authorize(Access access) {
        Objects.requireNonNull(access, "access");
        var live = authorization.evaluateSystem(
                access.systemId(), access.tenantId(), access.memberId());
        if (!live.permissions().contains(
                FlowPermissions.DEFINITION_MANAGE)) {
            throw new BusinessException(
                    "AI_FLOW_PERMISSION_DENIED",
                    "Live flow.definition.manage permission is required",
                    HttpStatus.FORBIDDEN);
        }
        if (live.epoch() != access.authorizationEpoch()
                || !live.permissions().equals(access.effectivePermissions())) {
            throw new BusinessException(
                    "AI_FLOW_AUTHORIZATION_STALE",
                    "Flow authorization changed before draft confirmation",
                    HttpStatus.CONFLICT);
        }
        return new FlowSession(
                access.accountId(), access.systemId(), access.tenantId(),
                access.memberId(), live.permissions());
    }

    @Transactional
    public void validateFacts(
            FlowSession session, AiFlowDefinitionDraftFacade.Draft draft) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(draft, "draft");
        requireActive(session, session.memberId(), "owner");
        for (var value : draft.approverMemberIds()) {
            requireActive(session, Long.parseLong(value), "approver");
        }
    }

    private void requireActive(
            FlowSession session, long memberId, String role) {
        var active = activeMembers.lockActiveMember(
                        session.systemId(), session.tenantId(), memberId)
                .filter(member -> member.memberId() == memberId);
        if (active.isEmpty()) {
            throw new BusinessException(
                    "AI_FLOW_MEMBER_UNAVAILABLE",
                    "The proposed Flow " + role
                            + " member is no longer active",
                    HttpStatus.CONFLICT);
        }
    }

    public record Access(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            AiFlowDefinitionDraftFacade.Operation operation) {
        public Access {
            if (accountId <= 0 || systemId <= 0 || tenantId <= 0
                    || memberId <= 0 || authorizationEpoch <= 0) {
                throw new IllegalArgumentException(
                        "Flow AI draft access identity is invalid");
            }
            effectivePermissions = Set.copyOf(Objects.requireNonNull(
                    effectivePermissions, "effectivePermissions"));
            operation = Objects.requireNonNull(operation, "operation");
        }
    }
}
