package com.unique.examine.module.runtime.search;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class GlobalSearchRequestParserTest {
    private final GlobalSearchRequestParser parser = new GlobalSearchRequestParser();

    @Test
    void normalizesAndParsesAValidRequest() {
        var request = parser.parse("  ＰＵＭＰ  ", "12", "50");

        assertThat(request.q()).isEqualTo("pump");
        assertThat(request.page()).isEqualTo(12);
        assertThat(request.size()).isEqualTo(50);
    }

    @Test
    void rejectsMissingShortAndOversizedSearchTextWithTheStableError() {
        assertInvalid(() -> parser.parse(null, "1", "20"));
        assertInvalid(() -> parser.parse("x", "1", "20"));
        assertInvalid(() -> parser.parse("x".repeat(101), "1", "20"));
    }

    @Test
    void rejectsSearchTextContainingAnInvalidOneCharacterToken() {
        assertInvalid(() -> parser.parse("pump x", "1", "20"));
    }

    @Test
    void rejectsMalformedAndOutOfRangePaginationWithTheStableError() {
        assertInvalid(() -> parser.parse("pump", "1.5", "20"));
        assertInvalid(() -> parser.parse("pump", "0", "20"));
        assertInvalid(() -> parser.parse("pump", "1000001", "20"));
        assertInvalid(() -> parser.parse("pump", "1", "0"));
        assertInvalid(() -> parser.parse("pump", "1", "51"));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        var exception = catchThrowableOfType(call, BusinessException.class);

        assertThat(exception.code()).isEqualTo("GLOBAL_SEARCH_INVALID");
        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
