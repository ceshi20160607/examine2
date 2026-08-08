package com.unique.examine.collab.recordcomment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;

public record RecordCommentCreate(
        RecordCommentKey key,
        String parentCommentId,
        String authorMemberId,
        String body,
        String idempotencyKey,
        List<String> mentionedMemberIds
) {
    public static final int MAX_IDEMPOTENCY_KEY_CHARACTERS = 128;
    public static final int MAX_MENTIONED_MEMBERS = 20;

    public RecordCommentCreate {
        if (key == null) {
            throw new NullPointerException("key");
        }
        parentCommentId = normalizeOptionalId(parentCommentId);
        authorMemberId = RecordComment.requireId(authorMemberId, "authorMemberId");
        body = RecordComment.normalizeBody(body);
        idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        mentionedMemberIds = normalizeMentionIds(mentionedMemberIds);
    }

    public RecordCommentCreate(
            RecordCommentKey key,
            String parentCommentId,
            String authorMemberId,
            String body,
            String idempotencyKey
    ) {
        this(key, parentCommentId, authorMemberId, body, idempotencyKey, List.of());
    }

    String requestHash() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, parentCommentId == null ? "" : parentCommentId);
            update(digest, "\n");
            update(digest, body);
            for (var memberId : mentionedMemberIds) {
                update(digest, "\n@");
                update(digest, memberId);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static List<String> normalizeMentionIds(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > MAX_MENTIONED_MEMBERS) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_MENTION_LIMIT_EXCEEDED",
                    "a comment can mention at most " + MAX_MENTIONED_MEMBERS + " members");
        }
        var normalized = new TreeSet<Long>();
        for (var value : values) {
            if (value == null || !value.matches("^[1-9][0-9]{0,18}$")) {
                throw RecordCommentException.badRequest(
                        "RECORD_COMMENT_MENTION_MEMBER_INVALID",
                        "mentioned member ids must be positive integers");
            }
            final long memberId;
            try {
                memberId = Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw RecordCommentException.badRequest(
                        "RECORD_COMMENT_MENTION_MEMBER_INVALID",
                        "mentioned member ids must be positive integers");
            }
            if (!normalized.add(memberId)) {
                throw RecordCommentException.badRequest(
                        "RECORD_COMMENT_MENTION_DUPLICATE",
                        "mentioned member ids must be unique");
            }
        }
        return normalized.stream().map(value -> Long.toString(value)).toList();
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeOptionalId(String value) {
        return value == null ? null : RecordComment.requireId(value, "parentCommentId");
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required");
        }
        var normalized = value.trim();
        var length = normalized.codePointCount(0, normalized.length());
        if (length > MAX_IDEMPOTENCY_KEY_CHARACTERS) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key cannot exceed "
                            + MAX_IDEMPOTENCY_KEY_CHARACTERS + " characters");
        }
        return normalized;
    }
}
