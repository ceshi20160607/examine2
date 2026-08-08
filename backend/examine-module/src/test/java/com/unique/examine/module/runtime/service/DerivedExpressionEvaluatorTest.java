package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static com.unique.examine.module.runtime.service.DerivedExpressionEvaluator.ResultSchema;
import static com.unique.examine.module.runtime.service.DerivedExpressionEvaluator.Value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DerivedExpressionEvaluatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DerivedExpressionEvaluator evaluator = new DerivedExpressionEvaluator();

    @Test
    void evaluatesSixTypedSchemasDeterministically() throws Exception {
        var fields = Map.of(
                1L, new Value(ResultSchema.DECIMAL, new BigDecimal("10.5")),
                2L, new Value(ResultSchema.STRING, "WO"),
                3L, new Value(ResultSchema.DATE, LocalDate.parse("2026-07-21")),
                4L, new Value(ResultSchema.BOOLEAN, true));

        assertThat(evaluate("""
                {"op":"DIVIDE","args":[{"fieldId":"1"},{"literalType":"INTEGER","value":2}]}
                """, fields)).isEqualTo(new Value(ResultSchema.DECIMAL, new BigDecimal("5.25")));
        assertThat(evaluate("""
                {"op":"DAYS_BETWEEN","args":[{"fieldId":"3"},{"literalType":"DATE","value":"2026-07-26"}]}
                """, fields)).isEqualTo(new Value(ResultSchema.INTEGER, new BigDecimal("5")));
        assertThat(evaluate("""
                {"op":"CONCAT","args":[{"fieldId":"2"},{"literalType":"STRING","value":"-1"}]}
                """, fields)).isEqualTo(new Value(ResultSchema.STRING, "WO-1"));
        assertThat(evaluate("""
                {"op":"ADD_DAYS","args":[{"fieldId":"3"},{"literalType":"INTEGER","value":2}]}
                """, fields)).isEqualTo(new Value(ResultSchema.DATE, LocalDate.parse("2026-07-23")));
        assertThat(evaluate("""
                {"literalType":"DATETIME","value":"2026-07-21T08:00:00Z"}
                """, fields).schema()).isEqualTo(ResultSchema.DATETIME);
        assertThat(evaluate("""
                {"op":"AND","args":[{"fieldId":"4"},{"literalType":"BOOLEAN","value":false}]}
                """, fields)).isEqualTo(new Value(ResultSchema.BOOLEAN, false));
    }

    @Test
    void preservesTypedNullsAndRejectsUnsafeArithmetic() throws Exception {
        var fields = Map.of(1L, Value.nullValue(ResultSchema.DECIMAL));
        assertThat(evaluate("""
                {"op":"ADD","args":[{"fieldId":"1"},{"literalType":"INTEGER","value":2}]}
                """, fields)).isEqualTo(Value.nullValue(ResultSchema.DECIMAL));
        assertThatThrownBy(() -> evaluate("""
                {"op":"DIVIDE","args":[{"literalType":"DECIMAL","value":1},
                  {"literalType":"INTEGER","value":0}]}
                """, fields)).hasMessageContaining("Division by zero");
    }

    private Value evaluate(String expression, Map<Long, Value> fields) throws Exception {
        return evaluator.evaluate(objectMapper.readTree(expression), fields::get);
    }
}
