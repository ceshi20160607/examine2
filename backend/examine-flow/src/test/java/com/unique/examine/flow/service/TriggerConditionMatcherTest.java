package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TriggerConditionMatcherTest {
    private final TriggerConditionMatcher target = new TriggerConditionMatcher(new ObjectMapper());

    @Test
    void emptyConditionListMatchesEverySnapshot() {
        assertThat(target.matches(binding(List.of()), Map.of())).isTrue();
    }

    @Test
    void combinesConditionsWithAllAndUsesStructuralJsonEquality() {
        var binding = binding(List.of(
                condition("payload", TriggerCondition.Operator.EQ, "{\"a\":1,\"b\":[true]}"),
                condition("status", TriggerCondition.Operator.NE, "\"closed\"")
        ));

        assertThat(target.matches(
                binding,
                Map.of("payload", "{\"b\":[true],\"a\":1}", "status", "\"open\"")
        )).isTrue();
        assertThat(target.matches(
                binding,
                Map.of("payload", "{\"a\":1,\"b\":[false]}", "status", "\"open\"")
        )).isFalse();
    }

    @Test
    void ordersOnlyJsonNumbersWithoutCoercingStringsOrBooleans() {
        assertThat(target.matches(
                binding(List.of(condition("amount", TriggerCondition.Operator.GT, "99.5"))),
                Map.of("amount", "100")
        )).isTrue();
        assertThat(target.matches(
                binding(List.of(condition("amount", TriggerCondition.Operator.GTE, "100"))),
                Map.of("amount", "100.0")
        )).isTrue();
        assertThat(target.matches(
                binding(List.of(condition("amount", TriggerCondition.Operator.LT, "101"))),
                Map.of("amount", "100")
        )).isTrue();
        assertThat(target.matches(
                binding(List.of(condition("amount", TriggerCondition.Operator.LTE, "100"))),
                Map.of("amount", "100")
        )).isTrue();
        assertThat(target.matches(
                binding(List.of(condition("amount", TriggerCondition.Operator.GT, "99"))),
                Map.of("amount", "\"100\"")
        )).isFalse();
    }

    @Test
    void emptyMatchesMissingNullAndEmptyJsonContainersButNotWhitespaceText() {
        var empty = binding(List.of(condition("value", TriggerCondition.Operator.EMPTY, null)));

        assertThat(target.matches(empty, Map.of())).isTrue();
        assertThat(target.matches(empty, Map.of("value", "null"))).isTrue();
        assertThat(target.matches(empty, Map.of("value", "\"\""))).isTrue();
        assertThat(target.matches(empty, Map.of("value", "[]"))).isTrue();
        assertThat(target.matches(empty, Map.of("value", "{}"))).isTrue();
        assertThat(target.matches(empty, Map.of("value", "\" \""))).isFalse();
        assertThat(target.matches(empty, Map.of("value", "[1]"))).isFalse();
    }

    @Test
    void notEmptyIsTheExactInverseOfEmpty() {
        var notEmpty = binding(List.of(condition(
                "value",
                TriggerCondition.Operator.NOT_EMPTY,
                null
        )));

        assertThat(target.matches(notEmpty, Map.of())).isFalse();
        assertThat(target.matches(notEmpty, Map.of("value", "{}"))).isFalse();
        assertThat(target.matches(notEmpty, Map.of("value", "false"))).isTrue();
    }

    private static TriggerBinding binding(List<TriggerCondition> conditions) {
        return new TriggerBinding(
                "purchase_order",
                TriggerBinding.Event.RECORD_ACTIVATED,
                100,
                false,
                conditions
        );
    }

    private static TriggerCondition condition(
            String fieldCode,
            TriggerCondition.Operator operator,
            String valueJson
    ) {
        return new TriggerCondition(fieldCode, operator, valueJson);
    }
}
