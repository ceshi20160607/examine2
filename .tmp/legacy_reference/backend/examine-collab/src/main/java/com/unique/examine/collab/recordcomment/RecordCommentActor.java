package com.unique.examine.collab.recordcomment;

import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;

import java.util.Set;

public record RecordCommentActor(
        long systemId,
        long tenantId,
        long memberId,
        Set<String> permissions,
        String moduleCode,
        long recordId
) {
    public RecordCommentActor {
        permissions = Set.copyOf(permissions);
    }

    RuntimeRecordAccessFacade.RuntimeRecordAccessRequest accessRequest() {
        return new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                systemId,
                tenantId,
                memberId,
                permissions,
                moduleCode,
                recordId);
    }

    String memberIdString() {
        return Long.toString(memberId);
    }

    boolean canManage() {
        return permissions.contains("module." + moduleCode + ".update");
    }
}
