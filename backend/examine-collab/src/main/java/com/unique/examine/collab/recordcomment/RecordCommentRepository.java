package com.unique.examine.collab.recordcomment;

import java.util.Optional;

public interface RecordCommentRepository {
    RecordCommentPage findPage(RecordCommentKey key, int page, int size);

    Optional<RecordComment> find(RecordCommentKey key, String commentId);

    RecordCommentCreation create(RecordCommentCreate command);

    RecordComment update(
            RecordCommentKey key,
            String commentId,
            long expectedVersion,
            String body,
            String actorMemberId);

    RecordComment tombstone(
            RecordCommentKey key,
            String commentId,
            long expectedVersion,
            String actorMemberId);
}
