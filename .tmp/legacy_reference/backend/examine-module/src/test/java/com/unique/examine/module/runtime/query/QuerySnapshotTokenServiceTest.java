package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuerySnapshotTokenServiceTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-07-27T01:00:00Z");
    private static final String CANONICAL_QUERY = """
            {"columns":["summary"],"filter":null,"page":1,"q":"测试 query","recordScope":"active",\
            "schemaVersionId":"41","size":50,"sort":[],"viewId":null}""";

    @Test
    void issuesASelfContainedVerifiableSnapshotBoundToEveryFrozenContextDimension() {
        var service = service(ISSUED_AT);

        var token = service.issue(
                11,
                22,
                33,
                "work_order",
                "31",
                "41",
                CANONICAL_QUERY);
        var snapshot = service.verify(
                token,
                11,
                22,
                33,
                "work_order",
                "31",
                "41");

        assertThat(snapshot.canonicalQuery()).isEqualTo(CANONICAL_QUERY);
        assertThat(snapshot.expiresAtEpochSecond()).isEqualTo(ISSUED_AT.plusSeconds(900).getEpochSecond());

        assertInvalid(() -> service.verify(token, 12, 22, 33, "work_order", "31", "41"));
        assertInvalid(() -> service.verify(token, 11, 23, 33, "work_order", "31", "41"));
        assertInvalid(() -> service.verify(token, 11, 22, 34, "work_order", "31", "41"));
        assertInvalid(() -> service.verify(token, 11, 22, 33, "incident", "31", "41"));
        assertInvalid(() -> service.verify(token, 11, 22, 33, "work_order", "32", "41"));
        assertInvalid(() -> service.verify(token, 11, 22, 33, "work_order", "31", "42"));
    }

    @Test
    void rejectsTamperingAndExpirationWithOneNonLeakingErrorContract() {
        var service = service(ISSUED_AT);
        var token = service.issue(11, 22, 33, "work_order", "31", "41", CANONICAL_QUERY);
        var replacement = token.charAt(0) == 'A' ? 'B' : 'A';
        var tampered = replacement + token.substring(1);

        assertInvalid(() -> service.verify(tampered, 11, 22, 33, "work_order", "31", "41"));

        var expiredVerifier = service(ISSUED_AT.plusSeconds(901));
        assertInvalid(() -> expiredVerifier.verify(
                token,
                11,
                22,
                33,
                "work_order",
                "31",
                "41"));
    }

    private static QuerySnapshotTokenService service(Instant now) {
        var key = new byte[32];
        Arrays.fill(key, (byte) 7);
        return new QuerySnapshotTokenService(
                new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC),
                key);
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.code()).isEqualTo("QUERY_SNAPSHOT_INVALID");
                            assertThat(exception.getMessage()).doesNotContain("11", "22", "33", "work_order");
                        });
    }
}
