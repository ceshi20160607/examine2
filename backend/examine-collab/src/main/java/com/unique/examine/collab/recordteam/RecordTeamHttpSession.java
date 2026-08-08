package com.unique.examine.collab.recordteam;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;
import java.util.Set;

record RecordTeamHttpSession(
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions
) {
    static RecordTeamHttpSession require(
            Object value,
            long requestedSystemId
    ) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException("AUTH_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != requestedSystemId) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "当前系统上下文与请求不一致",
                    HttpStatus.FORBIDDEN);
        }
        if (session.tenantId() == null) {
            throw new BusinessException(
                    "CONTEXT_TENANT_REQUIRED",
                    "当前请求缺少租户上下文",
                    HttpStatus.FORBIDDEN);
        }
        if (session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_MEMBER_REQUIRED",
                    "当前请求缺少成员上下文",
                    HttpStatus.FORBIDDEN);
        }

        var permissions = session.permissions() == null
                ? Set.<String>of()
                : Set.copyOf(session.permissions());
        if (!permissions.contains("system.runtime.access")) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "当前成员没有系统运行端访问权限",
                    HttpStatus.FORBIDDEN);
        }
        return new RecordTeamHttpSession(
                requestedSystemId,
                session.tenantId(),
                session.memberId(),
                permissions);
    }

    RecordTeamKey key(long recordId) {
        return new RecordTeamKey(
                Long.toString(systemId),
                Long.toString(tenantId),
                Long.toString(recordId));
    }

    RecordTeamActor actor(String moduleCode) {
        var capabilities = EnumSet.noneOf(RecordTeamCapability.class);
        if (permissions.contains(permission(moduleCode, "update"))) {
            capabilities.add(RecordTeamCapability.MANAGE_MEMBERS);
        }
        if (permissions.contains(permission(moduleCode, "action.transfer"))) {
            capabilities.add(RecordTeamCapability.TRANSFER_OWNERSHIP);
        }
        return new RecordTeamActor(Long.toString(memberId), capabilities);
    }

    void requireView(String moduleCode) {
        if (!permissions.contains(permission(moduleCode, "view"))) {
            throw RecordTeamException.forbidden(
                    "Actor " + memberId + " cannot view record teams in module " + moduleCode);
        }
    }

    private static String permission(String moduleCode, String suffix) {
        if (moduleCode == null || !moduleCode.matches("[a-z][a-z0-9_]{0,63}")) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_MODULE_CODE_INVALID",
                    "moduleCode is invalid");
        }
        return "module." + moduleCode + "." + suffix;
    }
}
