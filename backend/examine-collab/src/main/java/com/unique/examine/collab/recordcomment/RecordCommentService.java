package com.unique.examine.collab.recordcomment;

import com.unique.examine.collab.recordteam.RecordTeamKey;
import com.unique.examine.collab.recordteam.RecordTeamRepository;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RecordCommentService {
    private final RecordCommentRepository repository;
    private final RuntimeRecordAccessFacade recordAccess;
    private final RecordTeamRepository recordTeams;
    private final RuntimeActiveMemberFacade activeMembers;
    private final ResultNotificationFacade notifications;

    public RecordCommentService(
            RecordCommentRepository repository,
            RuntimeRecordAccessFacade recordAccess,
            RecordTeamRepository recordTeams,
            RuntimeActiveMemberFacade activeMembers,
            ResultNotificationFacade notifications
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.recordAccess = Objects.requireNonNull(recordAccess, "recordAccess");
        this.recordTeams = Objects.requireNonNull(recordTeams, "recordTeams");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    public RecordCommentService(
            RecordCommentRepository repository,
            RuntimeRecordAccessFacade recordAccess
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.recordAccess = Objects.requireNonNull(recordAccess, "recordAccess");
        this.recordTeams = null;
        this.activeMembers = null;
        this.notifications = null;
    }

    public RecordCommentPage page(RecordCommentActor actor, int page, int size) {
        var context = resolve(actor);
        return repository.findPage(context.key(), page, size);
    }

    @Transactional
    public RecordCommentCreation create(
            RecordCommentActor actor,
            String body,
            String parentCommentId,
            String idempotencyKey,
            List<String> mentionedMemberIds
    ) {
        var context = resolve(actor);
        var mentions = validateMentions(actor, context, mentionedMemberIds);
        var creation = repository.create(new RecordCommentCreate(
                context.key(),
                parentCommentId,
                context.memberId(),
                body,
                idempotencyKey,
                mentions));
        if (creation.created() && !creation.mentionedMemberIds().isEmpty()) {
            notifyAfterCommit(actor, creation.comment());
        }
        return creation;
    }

    public RecordCommentCreation create(
            RecordCommentActor actor,
            String body,
            String parentCommentId,
            String idempotencyKey
    ) {
        return create(actor, body, parentCommentId, idempotencyKey, List.of());
    }

    public RecordComment update(
            RecordCommentActor actor,
            String commentId,
            String body,
            Long version
    ) {
        var context = resolve(actor);
        var current = requireComment(context.key(), commentId);
        authorizeMutation(actor, current);
        return repository.update(
                context.key(),
                current.commentId(),
                requireVersion(version),
                body,
                context.memberId());
    }

    public RecordComment delete(
            RecordCommentActor actor,
            String commentId,
            Long version
    ) {
        var context = resolve(actor);
        var current = requireComment(context.key(), commentId);
        authorizeMutation(actor, current);
        return repository.tombstone(
                context.key(),
                current.commentId(),
                requireVersion(version),
                context.memberId());
    }

    private ResolvedContext resolve(RecordCommentActor actor) {
        Objects.requireNonNull(actor, "actor");
        var access = recordAccess.requireView(actor.accessRequest());
        if (!access.allowComments()) {
            throw RecordCommentException.forbidden(
                    "RECORD_COMMENTS_DISABLED",
                    "comments are disabled for this module");
        }
        return new ResolvedContext(
                new RecordCommentKey(
                        Long.toString(actor.systemId()),
                        Long.toString(actor.tenantId()),
                        access.recordId()),
                actor.memberIdString());
    }

    private List<String> validateMentions(
            RecordCommentActor actor,
            ResolvedContext context,
            List<String> requested
    ) {
        if (requested == null || requested.isEmpty()) return List.of();
        if (recordTeams == null || activeMembers == null || notifications == null) {
            throw new IllegalStateException("Record comment mention dependencies are unavailable");
        }
        var normalized = RecordCommentCreate.normalizeMentionIds(requested);
        if (normalized.contains(context.memberId())) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_MENTION_SELF_FORBIDDEN",
                    "a comment cannot mention its author");
        }
        var teamKey = new RecordTeamKey(context.key().systemId(), context.key().tenantId(), context.key().recordId());
        var teams = recordTeams.lockAll(List.of(teamKey));
        if (teams.size() != 1) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_MENTION_TEAM_REQUIRED",
                    "record team must be initialized before mentioning members");
        }
        var team = teams.getFirst();
        for (var memberId : normalized) {
            if (team.member(memberId).isEmpty()) {
                throw RecordCommentException.badRequest(
                        "RECORD_COMMENT_MENTION_MEMBER_NOT_ON_TEAM",
                        "mentioned member does not belong to the record team");
            }
            if (activeMembers.lockActiveMember(actor.systemId(), actor.tenantId(), Long.parseLong(memberId))
                    .isEmpty()) {
                throw RecordCommentException.badRequest(
                        "RECORD_COMMENT_MENTION_MEMBER_INACTIVE",
                        "mentioned member is not active in the current tenant");
            }
        }
        return normalized;
    }

    private void notifyAfterCommit(RecordCommentActor actor, RecordComment comment) {
        Runnable dispatch = () -> comment.mentionedMemberIds().forEach(memberId -> {
            try {
                notifications.dispatch(mentionCommand(actor, comment, Long.parseLong(memberId)));
            } catch (RuntimeException ignored) {
                // Command construction and delivery cannot change an already committed comment.
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private ResultNotificationFacade.Command mentionCommand(
            RecordCommentActor actor,
            RecordComment comment,
            long recipientMemberId
    ) {
        var commentId = comment.commentId();
        var recordId = comment.key().recordId();
        var targetPath = "/systems/" + actor.systemId() + "/workbench?module="
                + URLEncoder.encode(actor.moduleCode(), StandardCharsets.UTF_8)
                + "&record=" + recordId + "&mode=detail&panel=comments&comment=" + commentId;
        return new ResultNotificationFacade.Command(
                actor.systemId(), actor.tenantId(), actor.memberId(), recipientMemberId,
                "RECORD_COMMENT_MENTIONED", Map.of(
                        "moduleCode", actor.moduleCode(),
                        "recordId", recordId,
                        "commentExcerpt", excerpt(comment.body())),
                new AggregateRef("RECORD_COMMENT", commentId), targetPath,
                "record-comment-mention:" + commentId + ":" + recipientMemberId);
    }

    private static String excerpt(String body) {
        if (body == null) return "";
        var codePoints = body.codePoints().limit(160).toArray();
        return new String(codePoints, 0, codePoints.length);
    }

    private RecordComment requireComment(RecordCommentKey key, String commentId) {
        return repository.find(key, commentId).orElseThrow(() -> RecordCommentException.notFound(
                "RECORD_COMMENT_NOT_FOUND",
                "comment does not exist in this record"));
    }

    private static void authorizeMutation(RecordCommentActor actor, RecordComment comment) {
        if (comment.deleted()) {
            throw RecordCommentException.conflict(
                    "RECORD_COMMENT_DELETED",
                    "deleted comments cannot be changed");
        }
        if (!comment.authorMemberId().equals(actor.memberIdString()) && !actor.canManage()) {
            throw RecordCommentException.forbidden(
                    "RECORD_COMMENT_PERMISSION_DENIED",
                    "only the author or a module manager may change this comment");
        }
    }

    private static long requireVersion(Long version) {
        if (version == null || version < 1) {
            throw RecordCommentException.badRequest(
                    "RECORD_COMMENT_VERSION_INVALID",
                    "version must be positive");
        }
        return version;
    }

    private record ResolvedContext(RecordCommentKey key, String memberId) {
    }
}
