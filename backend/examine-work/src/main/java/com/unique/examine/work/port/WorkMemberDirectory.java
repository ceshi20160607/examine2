package com.unique.examine.work.port;

@FunctionalInterface
public interface WorkMemberDirectory {
    boolean isActiveMember(long systemId, long tenantId, long memberId);
}
