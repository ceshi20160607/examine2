package com.unique.examine.flow.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public record TriggerCondition(
        String fieldCode,
        Operator operator,
        String valueJson
) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public TriggerCondition {
        if (fieldCode == null || !fieldCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException("Trigger condition field code is invalid");
        }
        Objects.requireNonNull(operator, "operator");
        if (operator.requiresValue()) {
            if (valueJson == null) {
                throw new IllegalArgumentException("Trigger condition operator requires a JSON value");
            }
            try {
                var value = JSON.readTree(valueJson);
                if (value == null || value.isNull()) {
                    throw new IllegalArgumentException(
                            "Trigger condition operator requires a non-null JSON value"
                    );
                }
                valueJson = JSON.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Trigger condition JSON value is invalid", exception);
            }
        } else if (valueJson != null) {
            throw new IllegalArgumentException("Empty trigger operators do not accept a value");
        }
    }

    public enum Operator {
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        EMPTY,
        NOT_EMPTY;

        public boolean requiresValue() {
            return this != EMPTY && this != NOT_EMPTY;
        }
    }
}
