package com.unique.examine.ai.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordQueryPlanParserTest {
    private final AiRecordQueryPlanParser parser = new AiRecordQueryPlanParser();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyAnAuthorizedBoundedRecordQueryAndProducesOwnerFragment() throws Exception {
        var plan = parser.parse("""
                {
                  "operation":"RECORD_QUERY",
                  "moduleCode":"orders",
                  "filter":{"kind":"PREDICATE","fieldCode":"status","operator":"EQ","value":"OPEN"},
                  "sort":[{"fieldCode":"amount","direction":"DESC","nulls":"LAST"}],
                  "outputFields":["status","amount"],
                  "limit":7
                }
                """, policy());

        assertThat(plan.moduleCode()).isEqualTo("orders");
        assertThat(plan.outputFields()).containsExactly("status", "amount");
        assertThat(plan.limit()).isEqualTo(7);
        var fragment = json.readTree(plan.canonicalQueryJson());
        assertThat(fragment.fieldNames()).toIterable()
                .containsExactly("columns", "filter", "q", "recordScope", "sort");
        assertThat(fragment.path("recordScope").textValue()).isEqualTo("active");
        assertThat(fragment.has("page")).isFalse();
        assertThat(fragment.has("size")).isFalse();
    }

    @Test
    void rejectsUnknownDuplicateWriteSqlUrlAndUnauthorizedFields() {
        rejects("""
                {"operation":"RECORD_QUERY","moduleCode":"orders","filter":null,
                 "sort":[],"outputFields":["status"],"limit":1,"page":1}
                """);
        rejects("""
                {"operation":"RECORD_QUERY","operation":"RECORD_QUERY","moduleCode":"orders",
                 "filter":null,"sort":[],"outputFields":["status"],"limit":1}
                """);
        rejects("""
                {"operation":"UPDATE","moduleCode":"orders","filter":null,
                 "sort":[],"outputFields":["status"],"limit":1}
                """);
        rejects("""
                {"operation":"RECORD_QUERY","moduleCode":"orders",
                 "filter":{"kind":"PREDICATE","fieldCode":"status","operator":"EQ","value":"select * from x"},
                 "sort":[],"outputFields":["status"],"limit":1}
                """);
        rejects("""
                {"operation":"RECORD_QUERY","moduleCode":"orders",
                 "filter":{"kind":"PREDICATE","fieldCode":"status","operator":"EQ","value":"https://example.invalid"},
                 "sort":[],"outputFields":["status"],"limit":1}
                """);
        rejects("""
                {"operation":"RECORD_QUERY","moduleCode":"orders","filter":null,
                 "sort":[],"outputFields":["secret"],"limit":1}
                """);
    }

    @Test
    void rejectsProviderAttemptsToExceedPolicyRowLimit() {
        rejects("""
                {"operation":"RECORD_QUERY","moduleCode":"orders","filter":null,
                 "sort":[],"outputFields":["status"],"limit":11}
                """);
    }

    private void rejects(String value) {
        assertThatThrownBy(() -> parser.parse(value, policy()))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_PLAN_INVALID");
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-read",
                Set.of("orders"), Map.of("orders", Set.of("status", "amount")),
                Set.of("RECORD_QUERY"), 10, true,
                AiPolicy.RedactionMode.STRICT, "v1", "a".repeat(64),
                Instant.parse("2026-08-04T00:00:00Z"), 7);
    }
}
