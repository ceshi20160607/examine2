package com.unique.examine.ai.plan;

import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecordMutationPlanParserTest {
    private final AiRecordMutationPlanParser parser =
            new AiRecordMutationPlanParser();

    @Test
    void parsesCreateAndProducesExactCanonicalOwnerCommand() {
        var plan = parser.parse("""
                {"operation":"RECORD_CREATE","moduleCode":"orders",
                 "recordId":null,"expectedVersion":null,"title":"Order A",
                 "values":{"status":"OPEN","amount":12.50},
                 "relations":[{"fieldCode":"customer","targets":[
                   {"targetRecordId":"91","targetExpectedVersion":2,"ordinal":0}]}],
                 "subtables":[],
                 "confidence":{"status":0.99,"amount":0.98,"customer":0.95},
                 "clarifications":[]}
                """, policy(), "501");

        assertThat(plan.operation()).isEqualTo(
                AiRecordMutationFacade.Operation.RECORD_CREATE);
        assertThat(plan.writable()).isTrue();
        assertThat(plan.fieldCodes()).containsExactly("status", "amount", "customer");
        assertThat(plan.canonicalOwnerCommandJson()).isEqualTo(
                "{\"expectedVersion\":null,\"recordId\":null,\"relations\":[{\"fieldCode\":\"customer\",\"targets\":[{\"ordinal\":0,\"targetExpectedVersion\":2,\"targetRecordId\":\"91\"}]}],\"schemaVersionId\":\"501\",\"subtables\":[],\"title\":\"Order A\",\"values\":{\"amount\":12.5,\"status\":\"OPEN\"}}");
        assertThat(plan.planHash()).hasSize(64);
    }

    @Test
    void clarificationOrLowConfidenceCannotBecomeWritableProposal() {
        var plan = parser.parse("""
                {"operation":"RECORD_UPDATE","moduleCode":"orders",
                 "recordId":"81","expectedVersion":4,"title":null,
                 "values":{"status":"CLOSED"},"relations":[],"subtables":[],
                 "confidence":{"status":0.79},
                 "clarifications":["Confirm the requested final status"]}
                """, policy(), "501");

        assertThat(plan.operation()).isEqualTo(
                AiRecordMutationFacade.Operation.RECORD_UPDATE);
        assertThat(plan.recordId()).isEqualTo("81");
        assertThat(plan.expectedVersion()).isEqualTo(4);
        assertThat(plan.writable()).isFalse();
        assertThat(plan.clarifications()).containsExactly(
                "Confirm the requested final status");
    }

    @Test
    void rejectsUnknownActionsFieldsDuplicateKeysAndMalformedNestedShapes() {
        assertInvalid("""
                {"operation":"RECORD_DELETE","moduleCode":"orders",
                 "recordId":"81","expectedVersion":4,"title":null,
                 "values":{},"relations":[],"subtables":[],
                 "confidence":{},"clarifications":[]}
                """);
        assertInvalid("""
                {"operation":"RECORD_CREATE","moduleCode":"orders",
                 "recordId":null,"expectedVersion":null,"title":null,
                 "values":{"hidden":"raw"},"relations":[],"subtables":[],
                 "confidence":{"hidden":1},"clarifications":[]}
                """);
        assertInvalid("""
                {"operation":"RECORD_CREATE","operation":"RECORD_UPDATE",
                 "moduleCode":"orders","recordId":null,"expectedVersion":null,
                 "title":null,"values":{},"relations":[],"subtables":[],
                 "confidence":{},"clarifications":[]}
                """);
        assertInvalid("""
                {"operation":"RECORD_CREATE","moduleCode":"orders",
                 "recordId":null,"expectedVersion":null,"title":null,
                 "values":{},"relations":[{"fieldCode":"customer","targets":[
                 {"targetRecordId":"91","targetExpectedVersion":1,"ordinal":0,
                  "unexpected":true}]}],"subtables":[],
                 "confidence":{"customer":1},"clarifications":[]}
                """);
    }

    private void assertInvalid(String value) {
        assertThatThrownBy(() -> parser.parse(value, policy(), "501"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_MUTATION_PLAN_INVALID");
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-write",
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE"),
                Map.of("orders", Set.of(
                        "status", "amount", "customer", "lines")),
                10, AiPolicy.ConfirmationMode.REQUIRED, 600, true,
                AiPolicy.RedactionMode.STRICT, "v2", "b".repeat(64),
                Instant.parse("2026-08-04T00:00:00Z"), 7);
    }
}
