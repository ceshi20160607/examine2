package com.unique.examine.collab.recordcomment;

import java.time.Instant;
import java.util.List;

public record RecordComment(
        String commentId,
        RecordCommentKey key,
        String parentCommentId,
        String authorMemberId,
        String body,
        boolean deleted,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<String> mentionedMemberIds
) {
    public static final int MAX_BODY_CHARACTERS = 4000;

    public RecordComment {
        commentId = requireId(commentId, "commentId");
        if (key == null) {
            throw persistenceInvalid("key is required");
        }
        parentCommentId = optionalId(parentCommentId, "parentCommentId");
        authorMemberId = requireId(authorMemberId, "authorMemberId");
        if (version < 1) {
            throw persistenceInvalid("version must be positive");
        }
        if (createdAt == null || updatedAt == null) {
            throw persistenceInvalid("timestamps are required");
        }
        mentionedMemberIds = RecordCommentCreate.normalizeMentionIds(mentionedMemberIds);
        if (deleted) {
            if (body != null) {
                throw persistenceInvalid("a deleted comment cannot retain its body");
            }
        } else {
            body = normalizeBody(body);
        }
    }

    public RecordComment(
            String commentId,
            RecordCommentKey key,
            String parentCommentId,
            String authorMemberId,
            String body,
            boolean deleted,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(commentId, key, parentCommentId, authorMemberId, body, deleted,
                version, createdAt, updatedAt, List.of());
    }

    RecordComment withMentionedMemberIds(List<String> memberIds) {
        return new RecordComment(commentId, key, parentCommentId, authorMemberId, body,
                deleted, version, createdAt, updatedAt, memberIds);
    }

    static String normalizeBody(String value) {
        if (value == null) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_BODY_REQUIRED",
                    "body is required");
        }
        var normalized = value.trim();
        var length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > MAX_BODY_CHARACTERS) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_BODY_INVALID",
                    "body must contain 1.." + MAX_BODY_CHARACTERS + " characters after trimming");
        }
        return normalized;
    }

    static String requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw persistenceInvalid(field + " is required");
        }
        return value.trim();
    }

    private static String optionalId(String value, String field) {
        return value == null ? null : requireId(value, field);
    }

    private static RecordCommentException persistenceInvalid(String message) {
        return RecordCommentException.conflict(
                "RECORD_COMMENT_PERSISTENCE_INVALID",
                message);
    }
}
