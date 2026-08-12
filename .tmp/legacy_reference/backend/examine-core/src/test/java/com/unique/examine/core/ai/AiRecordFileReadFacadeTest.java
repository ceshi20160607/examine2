package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordFileReadFacadeTest {
    private static final Instant CREATED =
            Instant.parse("2026-08-04T09:00:00Z");

    @Test
    void resultIsBoundedMetadataOnlyAndDefensivelyCopied() {
        var file = new AiRecordFileReadFacade.File(
                "7", "contract.pdf", "application/pdf", 4096,
                "30", CREATED, CREATED.plusSeconds(1));
        var items = new ArrayList<>(List.of(file));
        var result = new AiRecordFileReadFacade.Result(
                "work_order", "40", 1,
                "/systems/10/workbench?module=work_order&mode=view&record=40",
                items);
        items.clear();

        assertThat(result.items()).containsExactly(file);
        assertThat(result.items().getFirst())
                .extracting(AiRecordFileReadFacade.File::originalName,
                        AiRecordFileReadFacade.File::mediaType,
                        AiRecordFileReadFacade.File::size)
                .containsExactly("contract.pdf", "application/pdf", 4096L);
    }

    @Test
    void rejectsInvalidLimitsSizesAndTimestampOrder() {
        assertThatThrownBy(() -> request(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> request(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new AiRecordFileReadFacade.File(
                "7", "contract.pdf", "application/pdf", -1,
                "30", CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> new AiRecordFileReadFacade.File(
                "7", "contract.pdf", "application/pdf", 1,
                "30", CREATED, CREATED.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timestamps");
    }

    private static AiRecordFileReadFacade.Request request(int limit) {
        return new AiRecordFileReadFacade.Request(
                1, 10, 20, 30,
                Set.of("system.runtime.access", "module.work_order.view"),
                "work_order", "40", limit);
    }
}
