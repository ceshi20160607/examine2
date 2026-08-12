package com.unique.examine.collab.recordcomment.ai;

import com.unique.examine.collab.recordcomment.RecordComment;
import com.unique.examine.collab.recordcomment.RecordCommentActor;
import com.unique.examine.collab.recordcomment.RecordCommentConfiguration;
import com.unique.examine.collab.recordcomment.RecordCommentCreate;
import com.unique.examine.collab.recordcomment.RecordCommentCreation;
import com.unique.examine.collab.recordcomment.RecordCommentKey;
import com.unique.examine.collab.recordcomment.RecordCommentPage;
import com.unique.examine.collab.recordcomment.RecordCommentRepository;
import com.unique.examine.collab.recordcomment.RecordCommentService;
import com.unique.examine.core.ai.AiRecordCommentReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.runtime.RuntimeRecordAccessFacade;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordCommentReadAdapterTest {
    private static final Set<String> VIEW = Set.of(
            "system.runtime.access", "module.work_order.view");
    private static final Instant CREATED =
            Instant.parse("2026-08-04T09:00:00Z");

    @Test
    void delegatesFirstBoundedPageWithCanonicalActorAndMapsSafeDtos() {
        var actor = new AtomicReference<RecordCommentActor>();
        var pageNumber = new AtomicInteger();
        var pageSize = new AtomicInteger();
        var adapter = new AiRecordCommentReadAdapter((seenActor, page, size) -> {
            actor.set(seenActor);
            pageNumber.set(page);
            pageSize.set(size);
            return page();
        });

        var result = adapter.query(request(2));

        assertThat(actor.get()).isEqualTo(new RecordCommentActor(
                10, 20, 30, VIEW, "work_order", 40));
        assertThat(pageNumber).hasValue(1);
        assertThat(pageSize).hasValue(2);
        assertThat(result.moduleCode()).isEqualTo("work_order");
        assertThat(result.recordId()).isEqualTo("40");
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.route()).isEqualTo(
                "/systems/10/workbench?module=work_order&mode=view&record=40");
        assertThat(result.items()).containsExactly(
                new AiRecordCommentReadFacade.Comment(
                        "1", null, "30", "Visible", false, 1,
                        CREATED, CREATED, List.of("31", "32")),
                new AiRecordCommentReadFacade.Comment(
                        "2", "1", "31", null, true, 2,
                        CREATED, CREATED.plusSeconds(1), List.of()));
    }

    @Test
    void preservesCanonicalOwnerFailuresAndDeclaresReadOnlyTransaction() throws Exception {
        var denied = new BusinessException(
                "PERMISSION_DENIED", "hidden", HttpStatus.FORBIDDEN);
        var adapter = new AiRecordCommentReadAdapter((actor, page, size) -> {
            throw denied;
        });

        assertThatThrownBy(() -> adapter.query(request(1))).isSameAs(denied);
        assertThat(AiRecordCommentReadAdapter.class.getMethod(
                        "query", AiRecordCommentReadFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void configurationPublishesFacadeBackedByTheExistingOwnerService() {
        var access = new AtomicReference<
                RuntimeRecordAccessFacade.RuntimeRecordAccessRequest>();
        var repository = new PageRepository();
        var service = new RecordCommentService(repository, request -> {
            access.set(request);
            return new RuntimeRecordAccessFacade.RuntimeRecordAccess("40", 3, true);
        });
        var facade = new RecordCommentConfiguration()
                .aiRecordCommentReadFacade(service);

        var result = facade.query(request(2));

        assertThat(facade).isInstanceOf(AiRecordCommentReadAdapter.class);
        assertThat(access.get()).isEqualTo(
                new RuntimeRecordAccessFacade.RuntimeRecordAccessRequest(
                        10, 20, 30, VIEW, "work_order", 40));
        assertThat(repository.key).isEqualTo(
                new RecordCommentKey("10", "20", "40"));
        assertThat(repository.page).isEqualTo(1);
        assertThat(repository.size).isEqualTo(2);
        assertThat(result.items()).isEmpty();
    }

    private static AiRecordCommentReadFacade.Request request(int limit) {
        return new AiRecordCommentReadFacade.Request(
                1, 10, 20, 30, VIEW, "work_order", "40", limit);
    }

    private static RecordCommentPage page() {
        var key = new RecordCommentKey("10", "20", "40");
        return new RecordCommentPage(List.of(
                new RecordComment(
                        "1", key, null, "30", "Visible", false, 1,
                        CREATED, CREATED, List.of("31", "32")),
                new RecordComment(
                        "2", key, "1", "31", null, true, 2,
                        CREATED, CREATED.plusSeconds(1), List.of())),
                1, 2, 3);
    }

    private static final class PageRepository implements RecordCommentRepository {
        private RecordCommentKey key;
        private int page;
        private int size;

        @Override
        public RecordCommentPage findPage(
                RecordCommentKey key, int page, int size
        ) {
            this.key = key;
            this.page = page;
            this.size = size;
            return new RecordCommentPage(List.of(), page, size, 0);
        }

        @Override
        public Optional<RecordComment> find(
                RecordCommentKey key, String commentId
        ) {
            return Optional.empty();
        }

        @Override
        public RecordCommentCreation create(RecordCommentCreate command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordComment update(
                RecordCommentKey key, String commentId, long expectedVersion,
                String body, String actorMemberId
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RecordComment tombstone(
                RecordCommentKey key, String commentId, long expectedVersion,
                String actorMemberId
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
