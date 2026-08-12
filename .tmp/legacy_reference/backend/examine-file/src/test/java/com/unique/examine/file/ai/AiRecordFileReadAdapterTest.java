package com.unique.examine.file.ai;

import com.unique.examine.core.ai.AiRecordFileReadFacade;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.domain.FileAsset;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.FileReference;
import com.unique.examine.file.domain.RuntimeRecordFile;
import com.unique.examine.file.domain.RuntimeRecordFileActor;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordFileReadAdapterTest {
    private static final Set<String> PERMISSIONS = Set.of(
            "system.runtime.access", "module.work_order.view", "file.read");

    @Test
    void delegatesToCanonicalPageAndProjectsSafeMetadataOnly() throws Exception {
        var actor = new AtomicReference<RuntimeRecordFileActor>();
        var pageNumber = new AtomicInteger();
        var pageSize = new AtomicInteger();
        var adapter = new AiRecordFileReadAdapter((seenActor, seenPage, seenSize) -> {
            actor.set(seenActor);
            pageNumber.set(seenPage);
            pageSize.set(seenSize);
            return page();
        });

        var result = adapter.query(request());

        assertThat(actor.get()).isEqualTo(new RuntimeRecordFileActor(
                11, 13, 17, PERMISSIONS, "work_order", 23));
        assertThat(pageNumber).hasValue(1);
        assertThat(pageSize).hasValue(10);
        assertThat(result.route()).isEqualTo(
                "/systems/11/workbench?module=work_order&mode=view&record=23");
        assertThat(result.items()).containsExactly(new AiRecordFileReadFacade.File(
                "41", "safe.pdf", "application/pdf", 12, "17",
                Instant.parse("2026-08-04T10:00:00Z"),
                Instant.parse("2026-08-04T10:01:00Z")));
        assertThat(result.toString())
                .doesNotContain("private/object/key", "0123456789abcdef");
        assertThat(AiRecordFileReadAdapter.class.getMethod(
                        "query", AiRecordFileReadFacade.Request.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void propagatesCanonicalOwnerDenialWithoutFallback() {
        var denied = new FileDomainException("FILE_FORBIDDEN", "file revoked");
        var adapter = new AiRecordFileReadAdapter(
                (actor, page, size) -> { throw denied; });

        assertThatThrownBy(() -> adapter.query(request()))
                .isInstanceOfSatisfying(BusinessException.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo("FILE_FORBIDDEN"));
    }

    private static AiRecordFileReadFacade.Request request() {
        return new AiRecordFileReadFacade.Request(
                5, 11, 13, 17, PERMISSIONS, "work_order", "23", 10);
    }

    private static RuntimeRecordFilePage page() {
        var target = new AggregateRef("RUNTIME_RECORD", "23");
        var reference = new FileReference(
                target, 19, Instant.parse("2026-08-04T10:01:00Z"));
        var asset = new FileAsset(
                41, 11, 13, 17, "private/object/key", "safe.pdf",
                "application/pdf", 12,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-04T10:00:00Z"),
                Map.of(target, reference), 3);
        return new RuntimeRecordFilePage(
                List.of(new RuntimeRecordFile(asset, reference)), 1, 10, 1);
    }
}
