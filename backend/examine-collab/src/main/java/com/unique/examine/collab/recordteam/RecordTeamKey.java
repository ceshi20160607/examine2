package com.unique.examine.collab.recordteam;

public record RecordTeamKey(String systemId, String tenantId, String recordId) {
    public RecordTeamKey {
        systemId = requireId(systemId, "systemId");
        tenantId = requireId(tenantId, "tenantId");
        recordId = requireId(recordId, "recordId");
    }

    private static String requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_CONTEXT_REQUIRED",
                    field + " is required");
        }
        return value.trim();
    }
}
