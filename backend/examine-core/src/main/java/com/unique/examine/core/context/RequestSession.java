package com.unique.examine.core.context;

import java.util.Set;

public interface RequestSession {
    String REQUEST_ATTRIBUTE = "com.unique.examine.authenticatedSession";

    long sessionId();

    long accountId();

    ContextType contextType();

    Long systemId();

    Long tenantId();

    Long memberId();

    long permissionVersion();

    Set<String> permissions();
}
