package com.unique.examine.flow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.domain.TriggerCondition;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

public final class TriggerConditionMatcher {
    private final ObjectMapper json;

    public TriggerConditionMatcher(ObjectMapper json) {
        this.json = Objects.requireNonNull(json, "json");
    }

    public boolean matches(TriggerBinding binding, Map<String, String> recordValuesJson) {
        Objects.requireNonNull(binding, "binding");
        return matches(binding.conditions(), recordValuesJson);
    }

    public boolean matches(
            List<TriggerCondition> conditions,
            Map<String, String> recordValuesJson
    ) {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(recordValuesJson, "recordValuesJson");
        return conditions.stream()
                .allMatch(condition -> matches(condition, recordValuesJson.get(condition.fieldCode())));
    }

    private boolean matches(TriggerCondition condition, String actualJson) {
        final JsonNode actual;
        try {
            actual = actualJson == null ? null : json.readTree(actualJson);
        } catch (JsonProcessingException exception) {
            return false;
        }
        return switch (condition.operator()) {
            case EMPTY -> empty(actual);
            case NOT_EMPTY -> !empty(actual);
            case EQ -> structuralEquals(actual, expected(condition));
            case NE -> !structuralEquals(actual, expected(condition));
            case GT -> compareNumbers(actual, expected(condition), comparison -> comparison > 0);
            case GTE -> compareNumbers(actual, expected(condition), comparison -> comparison >= 0);
            case LT -> compareNumbers(actual, expected(condition), comparison -> comparison < 0);
            case LTE -> compareNumbers(actual, expected(condition), comparison -> comparison <= 0);
        };
    }

    private JsonNode expected(TriggerCondition condition) {
        try {
            return json.readTree(condition.valueJson());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published trigger condition JSON is invalid", exception);
        }
    }

    private static boolean structuralEquals(JsonNode actual, JsonNode expected) {
        return actual != null && actual.equals(expected);
    }

    private static boolean compareNumbers(
            JsonNode actual,
            JsonNode expected,
            IntPredicate comparison
    ) {
        if (actual == null || expected == null || !actual.isNumber() || !expected.isNumber()) {
            return false;
        }
        return comparison.test(actual.decimalValue().compareTo(expected.decimalValue()));
    }

    private static boolean empty(JsonNode value) {
        return value == null
                || value.isNull()
                || (value.isTextual() && value.textValue().isEmpty())
                || (value.isArray() && value.isEmpty())
                || (value.isObject() && value.isEmpty());
    }
}
