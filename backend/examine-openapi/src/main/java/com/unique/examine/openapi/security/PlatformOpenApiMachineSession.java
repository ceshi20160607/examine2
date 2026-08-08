package com.unique.examine.openapi.security;

import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.context.RequestSession;
import java.util.Set;

public record PlatformOpenApiMachineSession(long sessionId,long accountId,
        long permissionVersion,Set<String> permissions) implements RequestSession {
    public PlatformOpenApiMachineSession { if(sessionId<=0||accountId<=0||permissionVersion<=0)throw new IllegalArgumentException("Invalid platform machine session");permissions=Set.copyOf(permissions); }
    public ContextType contextType(){return ContextType.PLATFORM;}
    public Long systemId(){return null;} public Long tenantId(){return null;} public Long memberId(){return null;}
}
