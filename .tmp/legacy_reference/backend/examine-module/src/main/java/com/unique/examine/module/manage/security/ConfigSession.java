package com.unique.examine.module.manage.security;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import org.springframework.http.HttpStatus;

import java.util.Set;

public record ConfigSession(long accountId, long systemId, long memberId, Long tenantId, Set<String> permissions) {
    public static final String REQUEST_ATTRIBUTE = RequestSession.REQUEST_ATTRIBUTE;

    public ConfigSession {
        permissions = Set.copyOf(permissions);
    }

    public static ConfigSession require(Object value, long requestedSystemId) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException("AUTH_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM || session.systemId() == null
                || session.systemId() != requestedSystemId || session.memberId() == null) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH", "当前系统成员上下文与请求不一致", HttpStatus.FORBIDDEN
            );
        }
        for (var permission : ConfigPermissions.REQUIRED_NOW) {
            if (!session.permissions().contains(permission)) {
                throw new BusinessException(
                        "PERMISSION_DENIED", "当前身份没有管理模块配置的权限", HttpStatus.FORBIDDEN
                );
            }
        }
        return new ConfigSession(
                session.accountId(), session.systemId(), session.memberId(),
                session.tenantId(), session.permissions()
        );
    }
}
