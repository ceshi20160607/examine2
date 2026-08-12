package com.unique.examine.collab.recordcomment;

import java.util.List;

public record RecordCommentCreation(boolean created, RecordComment comment, List<String> mentionedMemberIds) {
    public RecordCommentCreation {
        if (comment == null) {
            throw new NullPointerException("comment");
        }
        mentionedMemberIds = RecordCommentCreate.normalizeMentionIds(mentionedMemberIds);
    }

    static RecordCommentCreation created(RecordComment comment) {
        return new RecordCommentCreation(true, comment, comment.mentionedMemberIds());
    }

    static RecordCommentCreation existing(RecordComment comment) {
        return new RecordCommentCreation(false, comment, comment.mentionedMemberIds());
    }
}
