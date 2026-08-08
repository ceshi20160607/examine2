package com.unique.examine.event.port;

@FunctionalInterface
public interface MessageRecipientDirectory {
    boolean isActiveMember(long systemId, long tenantId, long memberId);
}
