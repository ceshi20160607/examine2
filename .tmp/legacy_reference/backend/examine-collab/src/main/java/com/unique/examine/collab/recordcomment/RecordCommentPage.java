package com.unique.examine.collab.recordcomment;

import java.util.List;

public record RecordCommentPage(
        List<RecordComment> items,
        int page,
        int size,
        long total
) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public RecordCommentPage {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || size > MAX_SIZE || total < 0) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_PERSISTENCE_INVALID",
                    "persisted page metadata is invalid");
        }
    }

    static void validate(int page, int size) {
        if (page < 1) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_PAGE_INVALID",
                    "page must be at least 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_SIZE_INVALID",
                    "size must be within 1.." + MAX_SIZE);
        }
    }
}
