package com.unique.examine.module.runtime.recent;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecentRequestParserTest {
    private final RecentRequestParser parser = new RecentRequestParser();

    @Test
    void acceptsOnlyCanonicalTouchShape() {
        assertThat(parser.touch("""
                {"moduleCode":"work_order","recordId":"123"}
                """)).isEqualTo(new RecentRequestParser.TouchRequest("work_order", 123L));
    }

    @Test
    void rejectsClientMetadataUnknownFieldsAndDuplicateKeys() {
        assertInvalid("""
                {"moduleCode":"work_order","recordId":"123","status":"ACTIVE"}
                """);
        assertInvalid("""
                {"moduleCode":"work_order","recordId":"123","recordId":"456"}
                """);
        assertInvalid("""
                {"moduleCode":"work_order"}
                """);
    }

    @Test
    void rejectsNonStringNonPositiveAndOverflowRecordIds() {
        assertInvalid("""
                {"moduleCode":"work_order","recordId":123}
                """);
        assertInvalid("""
                {"moduleCode":"work_order","recordId":"0"}
                """);
        assertInvalid("""
                {"moduleCode":"work_order","recordId":"9999999999999999999"}
                """);
    }

    @Test
    void freezesPageAndSizeBounds() {
        assertThat(parser.page(1, 100))
                .isEqualTo(new RecentRequestParser.PageRequest(1, 100));
        assertThat(parser.page(1_000_000, 1))
                .isEqualTo(new RecentRequestParser.PageRequest(1_000_000, 1));
        assertThat(catchThrowableOfType(() -> parser.page(0, 20), BusinessException.class).code())
                .isEqualTo("RECENT_RECORD_INVALID");
        assertThat(catchThrowableOfType(() -> parser.page(1, 101), BusinessException.class).code())
                .isEqualTo("RECENT_RECORD_INVALID");
    }

    private void assertInvalid(String body) {
        var exception = catchThrowableOfType(() -> parser.touch(body), BusinessException.class);
        assertThat(exception.code()).isEqualTo("RECENT_RECORD_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }
}
