package com.unique.examine.openapi.api;

import com.unique.examine.core.context.ContextType;import com.unique.examine.core.context.RequestSession;
import com.unique.examine.core.error.BusinessException;import org.springframework.http.HttpStatus;
import java.util.Set;

public record PlatformOpenApiAdminSession(long accountId,Set<String> permissions){
    public static final String MANAGE="platform.openapi.application.manage";
    public PlatformOpenApiAdminSession{permissions=Set.copyOf(permissions);}
    public static PlatformOpenApiAdminSession require(Object value){if(!(value instanceof RequestSession s))throw new BusinessException("AUTH_REQUIRED","Authentication is required",HttpStatus.UNAUTHORIZED);if(s.contextType()!=ContextType.PLATFORM)throw new BusinessException("CONTEXT_PLATFORM_REQUIRED","Platform context is required",HttpStatus.FORBIDDEN);if(!s.permissions().contains(MANAGE))throw new BusinessException("PERMISSION_DENIED","Platform OpenAPI management permission is required",HttpStatus.FORBIDDEN);return new PlatformOpenApiAdminSession(s.accountId(),s.permissions());}
}
