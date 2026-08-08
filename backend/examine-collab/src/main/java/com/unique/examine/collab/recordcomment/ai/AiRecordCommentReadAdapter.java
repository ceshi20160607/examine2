package com.unique.examine.collab.recordcomment.ai;

import com.unique.examine.collab.recordcomment.RecordCommentActor;
import com.unique.examine.collab.recordcomment.RecordCommentPage;
import com.unique.examine.collab.recordcomment.RecordCommentService;
import com.unique.examine.core.ai.AiRecordCommentReadFacade;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Collab-owned, read-only projection of the canonical record-comment page. */
public class AiRecordCommentReadAdapter
        implements AiRecordCommentReadFacade {
    private static final int FIRST_PAGE = 1;

    private final CommentPageReader comments;

    public AiRecordCommentReadAdapter(RecordCommentService comments) {
        this(Objects.requireNonNull(comments, "comments")::page);
    }

    AiRecordCommentReadAdapter(CommentPageReader comments) {
        this.comments = Objects.requireNonNull(comments, "comments");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        var actor = new RecordCommentActor(
                request.systemId(),
                request.tenantId(),
                request.memberId(),
                request.effectivePermissions(),
                request.moduleCode(),
                Long.parseLong(request.recordId()));
        var page = comments.read(actor, FIRST_PAGE, request.limit());
        return new Result(
                request.moduleCode(),
                request.recordId(),
                page.total(),
                route(request),
                page.items().stream()
                        .map(value -> new Comment(
                                value.commentId(),
                                value.parentCommentId(),
                                value.authorMemberId(),
                                value.deleted() ? null : value.body(),
                                value.deleted(),
                                value.version(),
                                value.createdAt(),
                                value.updatedAt(),
                                value.mentionedMemberIds()))
                        .toList());
    }

    private static String route(Request request) {
        return "/systems/" + request.systemId()
                + "/workbench?module=" + request.moduleCode()
                + "&mode=view&record=" + request.recordId();
    }

    @FunctionalInterface
    interface CommentPageReader {
        RecordCommentPage read(RecordCommentActor actor, int page, int size);
    }
}
