package com.unique.examine.collab.recordcomment;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Set;

record RecordCommentHttpSession(
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions
) {
    static RecordCommentHttpSession require(Object value, long requestedSystemId) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException(
                    "AUTH_REQUIRED",
                    "Authentication is required",
                    HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != requestedSystemId) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "The authenticated system context does not match the request",
                    HttpStatus.FORBIDDEN);
        }
        if (session.tenantId() == null) {
            throw new BusinessException(
                    "CONTEXT_TENANT_REQUIRED",
                    "The authenticated tenant context is required",
                    HttpStatus.FORBIDDEN);
        }
        if (session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_MEMBER_REQUIRED",
                    "The authenticated member context is required",
                    HttpStatus.FORBIDDEN);
        }
        return new RecordCommentHttpSession(
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                session.permissions() == null ? Set.of() : Set.copyOf(session.permissions()));
    }

    RecordCommentActor actor(String moduleCode, long recordId) {
        if (moduleCode == null || !moduleCode.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_MODULE_CODE_INVALID",
                    "moduleCode is invalid");
        }
        return new RecordCommentActor(
                systemId,
                tenantId,
                memberId,
                permissions,
                moduleCode,
                recordId);
    }
}
