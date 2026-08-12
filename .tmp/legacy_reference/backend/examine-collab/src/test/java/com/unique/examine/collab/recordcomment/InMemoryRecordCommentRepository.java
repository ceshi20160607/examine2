package com.unique.examine.collab.recordcomment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

final class InMemoryRecordCommentRepository implements RecordCommentRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Stored> comments = new LinkedHashMap<>();
    private final Map<String, String> idempotency = new LinkedHashMap<>();
    private long tick;

    @Override
    public synchronized RecordCommentPage findPage(RecordCommentKey key, int page, int size) {
        RecordCommentPage.validate(page, size);
        var all = comments.values().stream()
                .map(Stored::comment)
                .filter(comment -> comment.key().equals(key))
                .sorted(Comparator.comparing(RecordComment::createdAt)
                        .thenComparing(comment -> Long.parseLong(comment.commentId())))
                .toList();
        var from = Math.min((long) (page - 1) * size, all.size());
        var to = Math.min(from + size, all.size());
        return new RecordCommentPage(
                new ArrayList<>(all.subList((int) from, (int) to)),
                page,
                size,
                all.size());
    }

    @Override
    public synchronized Optional<RecordComment> find(RecordCommentKey key, String commentId) {
        var stored = comments.get(commentId);
        return stored == null || !stored.comment().key().equals(key)
                ? Optional.empty()
                : Optional.of(stored.comment());
    }

    @Override
    public synchronized RecordCommentCreation create(RecordCommentCreate command) {
        var idempotencyScope = command.key() + "|" + command.authorMemberId()
                + "|" + command.idempotencyKey();
        var existingId = idempotency.get(idempotencyScope);
        if (existingId != null) {
            var existing = comments.get(existingId);
            if (!existing.requestHash().equals(command.requestHash())) {
                throw RecordCommentException.conflict(
                        "RECORD_COMMENT_IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used for a different request");
            }
            return RecordCommentCreation.existing(existing.comment());
        }
        if (command.parentCommentId() != null) {
            var parent = find(command.key(), command.parentCommentId()).orElseThrow(
                    () -> RecordCommentException.notFound(
                            "RECORD_COMMENT_PARENT_NOT_FOUND",
                            "parent comment does not exist in this record"));
            if (parent.parentCommentId() != null) {
                throw RecordCommentException.badRequest(
                        "RECORD_COMMENT_REPLY_DEPTH_EXCEEDED",
                        "replies can only target a top-level comment");
            }
        }
        var now = nextInstant();
        var comment = new RecordComment(
                Long.toString(sequence.incrementAndGet()),
                command.key(),
                command.parentCommentId(),
                command.authorMemberId(),
                command.body(),
                false,
                1,
                now,
                now,
                command.mentionedMemberIds());
        comments.put(comment.commentId(), new Stored(comment, command.requestHash()));
        idempotency.put(idempotencyScope, comment.commentId());
        return RecordCommentCreation.created(comment);
    }

    @Override
    public synchronized RecordComment update(
            RecordCommentKey key,
            String commentId,
            long expectedVersion,
            String body,
            String actorMemberId
    ) {
        var current = requireMutable(key, commentId, expectedVersion);
        var changed = new RecordComment(
                current.commentId(),
                current.key(),
                current.parentCommentId(),
                current.authorMemberId(),
                body,
                false,
                current.version() + 1,
                current.createdAt(),
                nextInstant(),
                current.mentionedMemberIds());
        replace(changed);
        return changed;
    }

    @Override
    public synchronized RecordComment tombstone(
            RecordCommentKey key,
            String commentId,
            long expectedVersion,
            String actorMemberId
    ) {
        var current = requireMutable(key, commentId, expectedVersion);
        var changed = new RecordComment(
                current.commentId(),
                current.key(),
                current.parentCommentId(),
                current.authorMemberId(),
                null,
                true,
                current.version() + 1,
                current.createdAt(),
                nextInstant(),
                current.mentionedMemberIds());
        replace(changed);
        return changed;
    }

    private RecordComment requireMutable(
            RecordCommentKey key,
            String commentId,
            long expectedVersion
    ) {
        var current = find(key, commentId).orElseThrow(() -> RecordCommentException.notFound(
                "RECORD_COMMENT_NOT_FOUND",
                "comment does not exist in this record"));
        if (current.deleted()) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_DELETED",
                    "deleted comments cannot be changed");
        }
        if (current.version() != expectedVersion) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_VERSION_CONFLICT",
                    "comment changed concurrently");
        }
        return current;
    }

    private void replace(RecordComment comment) {
        var previous = comments.get(comment.commentId());
        comments.put(comment.commentId(), new Stored(comment, previous.requestHash()));
    }

    private Instant nextInstant() {
        return Instant.parse("2026-01-01T00:00:00Z").plusMillis(tick++);
    }

    private record Stored(RecordComment comment, String requestHash) {
    }
}
