package com.unique.examine.module.runtime.history;

import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordHistoryServiceTest {
    private static final Set<String> HISTORY_PERMISSIONS = Set.of(
            "system.runtime.access",
            "module.work_order.view",
            "module.work_order.history.read");

    @Test
    void appliesCurrentFieldProjectionAndKeepsSensitiveEntriesMasked() {
        var repository = new InMemoryRecordHistoryRepository();
        repository.append(new RecordHistoryAppend(
                101,
                1,
                2,
                3,
                7,
                "RECORD_UPDATED",
                10L,
                LocalDateTime.parse("2026-07-25T12:00:00"),
                List.of(
                        diff("$title", "Old", "New", false),
                        diff("visible", "before", "after", false),
                        diff("hidden", "secret-before", "secret-after", false),
                        diff("identity", "********0001", "********0002", true))));
        var service = new RecordHistoryService(
                repository,
                (session, moduleCode, recordId) -> Set.of("visible", "identity"));

        var page = service.page(session(HISTORY_PERMISSIONS), "work_order", 3, 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(history -> {
            assertThat(history.historyId()).isEqualTo("101");
            assertThat(history.actorMemberId()).isEqualTo("10");
            assertThat(history.diff()).extracting(RecordHistoryDiff::fieldCode)
                    .containsExactly("$title", "visible", "identity");
            assertThat(history.diff().get(2).masked()).isTrue();
            assertThat(history.diff().get(2).beforeValue().asText()).isEqualTo("********0001");
            assertThat(history.diff().get(2).afterValue().asText()).isEqualTo("********0002");
        });
    }

    @Test
    void requiresAllThreePermissionsBeforeResolvingRecordScope() {
        var repository = new InMemoryRecordHistoryRepository();
        var accessRead = new AtomicBoolean();
        var service = new RecordHistoryService(repository, (session, moduleCode, recordId) -> {
            accessRead.set(true);
            return Set.of();
        });

        assertThatThrownBy(() -> service.page(
                session(Set.of("system.runtime.access", "module.work_order.view")),
                "work_order",
                3,
                1,
                20))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("PERMISSION_DENIED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        assertThat(accessRead).isFalse();
        assertThat(repository.pageReads()).isZero();
    }

    @Test
    void preservesCanonicalNotFoundBeforeReadingHistoryPersistence() {
        var repository = new InMemoryRecordHistoryRepository();
        var notFound = new BusinessException(
                "RECORD_NOT_FOUND",
                "Record is missing or outside VIEW scope",
                HttpStatus.NOT_FOUND);
        var service = new RecordHistoryService(repository, (session, moduleCode, recordId) -> {
            throw notFound;
        });

        assertThatThrownBy(() -> service.page(
                session(HISTORY_PERMISSIONS),
                "work_order",
                3,
                1,
                20)).isSameAs(notFound);
        assertThat(repository.pageReads()).isZero();
    }

    @Test
    void validatesStablePageBounds() {
        var service = new RecordHistoryService(
                new InMemoryRecordHistoryRepository(),
                (session, moduleCode, recordId) -> Set.of());

        assertCode(
                () -> service.page(session(HISTORY_PERMISSIONS), "work_order", 3, 0, 20),
                "RECORD_HISTORY_PAGE_INVALID");
        assertCode(
                () -> service.page(session(HISTORY_PERMISSIONS), "work_order", 3, 1, 101),
                "RECORD_HISTORY_SIZE_INVALID");
    }

    @Test
    void repositoryContractUsesDescendingStableOrder() {
        assertThat(JdbcRecordHistoryRepository.PAGE_SQL)
                .contains("ORDER BY occurred_at DESC,history_id DESC")
                .contains("LIMIT ? OFFSET ?");
    }

    private static RecordHistoryDiff diff(
            String code,
            String before,
            String after,
            boolean masked
    ) {
        return new RecordHistoryDiff(
                code,
                TextNode.valueOf(before),
                TextNode.valueOf(after),
                masked);
    }

    private static RuntimeSession session(Set<String> permissions) {
        return new RuntimeSession(9, 1, 10, 2L, permissions);
    }

    private static void assertCode(Runnable operation, String code) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }
}
