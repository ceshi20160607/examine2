package com.unique.examine.collab.recordcomment;

import java.util.List;

public final class RecordCommentApi {
    private RecordCommentApi() {
    }

    public record CreateRequest(String body, String parentCommentId, List<String> mentionedMemberIds) {
        public CreateRequest {
            mentionedMemberIds = mentionedMemberIds == null ? List.of() : List.copyOf(mentionedMemberIds);
        }
    }

    public record UpdateRequest(String body, Long version) {
    }

    public record DeleteRequest(Long version) {
    }

    public record CommentResponse(
            String commentId,
            String recordId,
            String parentCommentId,
            String authorMemberId,
            String body,
            boolean deleted,
            long version,
            String createdAt,
            String updatedAt,
            List<String> mentionedMemberIds,
            boolean canEdit,
            boolean canDelete
    ) {
        static CommentResponse from(RecordComment comment, RecordCommentActor actor) {
            var mutable = !comment.deleted()
                    && (comment.authorMemberId().equals(actor.memberIdString())
                    || actor.canManage());
            return new CommentResponse(
                    comment.commentId(),
                    comment.key().recordId(),
                    comment.parentCommentId(),
                    comment.authorMemberId(),
                    comment.deleted() ? null : comment.body(),
                    comment.deleted(),
                    comment.version(),
                    comment.createdAt().toString(),
                    comment.updatedAt().toString(),
                    comment.mentionedMemberIds(),
                    mutable,
                    mutable);
        }
    }

    public record PageResponse(
            List<CommentResponse> items,
            int page,
            int size,
            long total
    ) {
        public PageResponse {
            items = List.copyOf(items);
        }

        static PageResponse from(RecordCommentPage page, RecordCommentActor actor) {
            return new PageResponse(
                    page.items().stream()
                            .map(comment -> CommentResponse.from(comment, actor))
                            .toList(),
                    page.page(),
                    page.size(),
                    page.total());
        }
    }
}
