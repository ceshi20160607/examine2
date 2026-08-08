package com.unique.examine.module.runtime.favorite;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FavoriteRequestParserTest {
    private final FavoriteRequestParser parser = new FavoriteRequestParser();

    @Test
    void acceptsExactModuleAndRecordShapes() {
        var module = parser.create("""
                {"type":"MODULE","moduleCode":"work_order"}
                """);
        var record = parser.create("""
                {"type":"RECORD","moduleCode":"work_order","recordId":"123"}
                """);

        assertThat(module).isEqualTo(new FavoriteRequestParser.CreateRequest(
                "MODULE", "work_order", null));
        assertThat(record).isEqualTo(new FavoriteRequestParser.CreateRequest(
                "RECORD", "work_order", 123L));
    }

    @Test
    void rejectsBranchMismatchUnknownFieldsAndDuplicateKeys() {
        assertInvalid("""
                {"type":"MODULE","moduleCode":"work_order","recordId":"123"}
                """);
        assertInvalid("""
                {"type":"RECORD","moduleCode":"work_order"}
                """);
        assertInvalid("""
                {"type":"MODULE","moduleCode":"work_order","moduleCode":"asset"}
                """);
    }

    @Test
    void rejectsNonStringNonPositiveAndOverflowRecordIds() {
        assertInvalid("""
                {"type":"RECORD","moduleCode":"work_order","recordId":123}
                """);
        assertInvalid("""
                {"type":"RECORD","moduleCode":"work_order","recordId":"0"}
                """);
        assertInvalid("""
                {"type":"RECORD","moduleCode":"work_order","recordId":"9999999999999999999"}
                """);
    }

    @Test
    void freezesPageAndDeleteVersionBounds() {
        assertThat(parser.page(1, 100))
                .isEqualTo(new FavoriteRequestParser.PageRequest(1, 100));
        assertThat(parser.delete("""
                {"expectedVersion":0}
                """).expectedVersion()).isZero();

        assertThat(catchThrowableOfType(() -> parser.page(0, 20), BusinessException.class).code())
                .isEqualTo("FAVORITE_INVALID");
        assertThat(catchThrowableOfType(() -> parser.page(1, 101), BusinessException.class).code())
                .isEqualTo("FAVORITE_INVALID");
        assertInvalidDelete("""
                {"expectedVersion":-1}
                """);
    }

    private void assertInvalid(String body) {
        var exception = catchThrowableOfType(() -> parser.create(body), BusinessException.class);
        assertThat(exception.code()).isEqualTo("FAVORITE_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }

    private void assertInvalidDelete(String body) {
        var exception = catchThrowableOfType(() -> parser.delete(body), BusinessException.class);
        assertThat(exception.code()).isEqualTo("FAVORITE_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }
}
