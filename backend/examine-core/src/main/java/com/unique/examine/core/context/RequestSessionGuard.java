package com.unique.examine.core.context;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

public final class RequestSessionGuard {
    private RequestSessionGuard() {
    }

    public static RequestSession requireSystem(Object value, long systemId, String permission) {
        if (!(value instanceof RequestSession session)) {
            throw new BusinessException("AUTH_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (session.contextType() != ContextType.SYSTEM
                || session.systemId() == null
                || session.systemId() != systemId) {
            throw new BusinessException(
                    "CONTEXT_SYSTEM_MISMATCH",
                    "当前系统上下文与请求不一致",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!session.permissions().contains(permission)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "当前身份没有执行此操作的权限",
                    HttpStatus.FORBIDDEN
            );
        }
        return session;
    }
}
