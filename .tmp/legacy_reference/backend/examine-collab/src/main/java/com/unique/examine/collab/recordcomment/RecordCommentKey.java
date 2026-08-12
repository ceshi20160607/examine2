package com.unique.examine.collab.recordcomment;

public record RecordCommentKey(String systemId, String tenantId, String recordId) {
    public RecordCommentKey {
        systemId = requireId(systemId, "systemId");
        tenantId = requireId(tenantId, "tenantId");
        recordId = requireId(recordId, "recordId");
    }

    private static String requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_CONTEXT_REQUIRED",
                    field + " is required");
        }
        return value.trim();
    }
}
