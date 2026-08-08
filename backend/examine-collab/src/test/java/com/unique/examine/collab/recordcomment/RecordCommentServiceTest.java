package com.unique.examine.collab.recordcomment;

import com.unique.examine.collab.recordteam.InMemoryRecordTeamRepository;
import com.unique.examine.collab.recordteam.RecordTeam;
import com.unique.examine.collab.recordteam.RecordTeamKey;
import com.unique.examine.collab.recordteam.RecordTeamMember;
import com.unique.examine.collab.recordteam.RecordTeamRole;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordCommentServiceTest {
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access",
            "module.work_order.view");
    private static final Set<String> MANAGE = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.update");
    private static final RecordCommentKey CANONICAL_KEY =
            new RecordCommentKey("1", "2", "900");

    private InMemoryRecordCommentRepository repository;
    private AtomicReference<RuntimeRecordAccessFacade.RuntimeRecordAccessRequest> accessRequest;
    private RecordCommentService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordCommentRepository();
        accessRequest = new AtomicReference<>();
        service = new RecordCommentService(repository, request -> {
            accessRequest.set(request);
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess("900", 7, true);
        });
    }

    @Test
    void delegatesEveryReadToCanonicalViewScopeAndUsesTheCanonicalRecordId() {
        repository.create(new RecordCommentCreate(
                CANONICAL_KEY, null, "10", "visible", "seed"));

        var page = service.page(actor(10, VIEW), 1, 20);

        assertThat(page.items()).singleElement()
                .satisfies(comment -> assertThat(comment.key()).isEqualTo(CANONICAL_KEY));
        assertThat(accessRequest.get()).isEqualTo(
                new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                        1, 2, 10, VIEW, "work_order", 44));
    }

    @Test
    void createsIdempotentlyAndEnforcesOneLevelReplies() {
        var first = service.create(actor(10, VIEW), "  root  ", null, "request-1");
        var replay = service.create(actor(10, VIEW), "root", null, "request-1");
        var reply = service.create(
                actor(11, VIEW), "reply", first.comment().commentId(), "request-2");

        assertThat(first.created()).isTrue();
        assertThat(first.comment().body()).isEqualTo("root");
        assertThat(replay.created()).isFalse();
        assertThat(replay.comment().commentId()).isEqualTo(first.comment().commentId());
        assertThat(reply.comment().parentCommentId()).isEqualTo(first.comment().commentId());
        assertCode(
                () -> service.create(
                        actor(12, VIEW),
                        "nested",
                        reply.comment().commentId(),
                        "request-3"),
                "RECORD_COMMENT_REPLY_DEPTH_EXCEEDED");
    }

    @Test
    void authorCanMutateOwnCommentAndCasRejectsStaleVersions() {
        var comment = service.create(
                actor(10, VIEW), "original", null, "request-1").comment();

        var changed = service.update(actor(10, VIEW), comment.commentId(), "changed", 1L);
        assertThat(changed.body()).isEqualTo("changed");
        assertThat(changed.version()).isEqualTo(2);

        assertCode(
                () -> service.update(actor(10, VIEW), comment.commentId(), "stale", 1L),
                "RECORD_COMMENT_VERSION_CONFLICT");

        var deleted = service.delete(actor(10, VIEW), comment.commentId(), 2L);
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.body()).isNull();
        assertThat(deleted.version()).isEqualTo(3);
        assertCode(
                () -> service.delete(actor(10, VIEW), comment.commentId(), 3L),
                "RECORD_COMMENT_DELETED");
    }

    @Test
    void anotherAuthorNeedsModuleUpdateAndDeletedResponsesAreNeverMutable() {
        var comment = service.create(
                actor(10, VIEW), "original", null, "request-1").comment();

        assertCode(
                () -> service.update(actor(11, VIEW), comment.commentId(), "denied", 1L),
                "RECORD_COMMENT_PERMISSION_DENIED");

        var changed = service.update(actor(11, MANAGE), comment.commentId(), "managed", 1L);
        var activeResponse = RecordCommentApi.CommentResponse.from(changed, actor(11, MANAGE));
        assertThat(activeResponse.canEdit()).isTrue();
        assertThat(activeResponse.canDelete()).isTrue();

        var deleted = service.delete(actor(11, MANAGE), comment.commentId(), 2L);
        var deletedResponse = RecordCommentApi.CommentResponse.from(deleted, actor(11, MANAGE));
        assertThat(deletedResponse.body()).isNull();
        assertThat(deletedResponse.canEdit()).isFalse();
        assertThat(deletedResponse.canDelete()).isFalse();
    }

    @Test
    void disabledCommentsFailBeforeTheRepositoryIsReadOrWritten() {
        var disabled = new RecordCommentService(
                repository,
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("900", 1, false));

        assertCode(
                () -> disabled.page(actor(10, VIEW), 1, 20),
                "RECORD_COMMENTS_DISABLED");
        assertCode(
                () -> disabled.create(actor(10, VIEW), "blocked", null, "request-1"),
                "RECORD_COMMENTS_DISABLED");
        assertThat(repository.findPage(CANONICAL_KEY, 1, 20).total()).isZero();
    }

    @Test
    void canonicalNotFoundAndPermissionErrorsArePreserved() {
        var notFound = new BusinessException(
                "RECORD_NOT_FOUND",
                "missing or outside VIEW scope",
                HttpStatus.NOT_FOUND);
        var denied = new BusinessException(
                "PERMISSION_DENIED",
                "module VIEW is required",
                HttpStatus.FORBIDDEN);

        var missingService = new RecordCommentService(repository, request -> {
            throw notFound;
        });
        var deniedService = new RecordCommentService(repository, request -> {
            throw denied;
        });

        assertThatThrownBy(() -> missingService.page(actor(10, VIEW), 1, 20))
                .isSameAs(notFound);
        assertThatThrownBy(() -> deniedService.page(actor(10, Set.of()), 1, 20))
                .isSameAs(denied);
    }

    @Test
    void validatesBodyVersionPageAndIdempotencyInputsWithStableCodes() {
        var comment = service.create(
                actor(10, VIEW), "original", null, "request-1").comment();

        assertCode(
                () -> service.create(actor(10, VIEW), " ", null, "request-2"),
                "RECORD_COMMENT_BODY_INVALID");
        assertCode(
                () -> service.create(actor(10, VIEW), "body", null, null),
                "RECORD_COMMENT_IDEMPOTENCY_KEY_REQUIRED");
        assertCode(
                () -> service.update(actor(10, VIEW), comment.commentId(), "body", null),
                "RECORD_COMMENT_VERSION_INVALID");
        assertCode(
                () -> service.page(actor(10, VIEW), 1, 101),
                "RECORD_COMMENT_SIZE_INVALID");
    }

    @Test
    void validatesPersistsAndDeduplicatesStructuredTeamMentions() {
        var teams = new InMemoryRecordTeamRepository();
        teams.create(RecordTeam.restore(new RecordTeamKey("1", "2", "900"), List.of(
                new RecordTeamMember("10", RecordTeamRole.OWNER),
                new RecordTeamMember("11", RecordTeamRole.COLLABORATOR),
                new RecordTeamMember("12", RecordTeamRole.VIEWER)), 1));
        var deliveries = new ArrayList<ResultNotificationFacade.Command>();
        RuntimeActiveMemberFacade activeMembers = (systemId, tenantId, memberId) ->
                Set.of(10L, 11L, 12L).contains(memberId)
                        ? java.util.Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                        : java.util.Optional.empty();
        ResultNotificationFacade notifications = command -> {
            deliveries.add(command);
            if (command.recipientMemberId() == 11) throw new IllegalStateException("temporary event failure");
            return new ResultNotificationFacade.DeliveryReceipt(100 + command.recipientMemberId(),
                    100 + command.recipientMemberId(), "DELIVERED", false);
        };
        var mentioned = new RecordCommentService(repository,
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("900", 7, true),
                teams, activeMembers, notifications);

        var created = mentioned.create(actor(10, VIEW), "  请共同复核  ", null,
                "mention-1", List.of("12", "11"));
        assertThat(created.mentionedMemberIds()).containsExactly("11", "12");
        assertThat(created.comment().mentionedMemberIds()).containsExactly("11", "12");
        assertThat(deliveries).hasSize(2).allSatisfy(command -> {
            assertThat(command.templateCode()).isEqualTo("RECORD_COMMENT_MENTIONED");
            assertThat(command.targetPath()).contains("record=900", "panel=comments",
                    "comment=" + created.comment().commentId());
            assertThat(command.dedupeKey()).startsWith(
                    "record-comment-mention:" + created.comment().commentId() + ":");
        });

        var replay = mentioned.create(actor(10, VIEW), "请共同复核", null,
                "mention-1", List.of("11", "12"));
        assertThat(replay.created()).isFalse();
        assertThat(deliveries).hasSize(2);
        assertCode(() -> mentioned.create(actor(10, VIEW), "请共同复核", null,
                "mention-1", List.of("11")), "RECORD_COMMENT_IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void rejectsSelfNonTeamInactiveAndDuplicateMentionsWithoutACommentWrite() {
        var teams = new InMemoryRecordTeamRepository();
        teams.create(RecordTeam.restore(new RecordTeamKey("1", "2", "900"), List.of(
                new RecordTeamMember("10", RecordTeamRole.OWNER),
                new RecordTeamMember("11", RecordTeamRole.COLLABORATOR),
                new RecordTeamMember("12", RecordTeamRole.VIEWER)), 1));
        RuntimeActiveMemberFacade activeMembers = (systemId, tenantId, memberId) -> memberId == 11
                ? java.util.Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null))
                : java.util.Optional.empty();
        var mentioned = new RecordCommentService(repository,
                request -> new RuntimeRecordAccessFacade.RuntimeRecordAccess("900", 7, true),
                teams, activeMembers,
                command -> new ResultNotificationFacade.DeliveryReceipt(1, 1L, "DELIVERED", false));

        assertCode(() -> mentioned.create(actor(10, VIEW), "self", null, "mention-self", List.of("10")),
                "RECORD_COMMENT_MENTION_SELF_FORBIDDEN");
        assertCode(() -> mentioned.create(actor(10, VIEW), "outside", null, "mention-out", List.of("99")),
                "RECORD_COMMENT_MENTION_MEMBER_NOT_ON_TEAM");
        assertCode(() -> mentioned.create(actor(10, VIEW), "inactive", null, "mention-inactive", List.of("12")),
                "RECORD_COMMENT_MENTION_MEMBER_INACTIVE");
        assertCode(() -> mentioned.create(actor(10, VIEW), "duplicate", null, "mention-dup", List.of("11", "11")),
                "RECORD_COMMENT_MENTION_DUPLICATE");
        assertThat(repository.findPage(CANONICAL_KEY, 1, 20).total()).isZero();
    }

    private static RecordCommentActor actor(long memberId, Set<String> permissions) {
        return new RecordCommentActor(1, 2, memberId, permissions, "work_order", 44);
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
