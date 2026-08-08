package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.module.datasource.domain.DataSourceDraft;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Strict scalar decoder shared by draft preview and published HTTP rows. */
final class HttpJsonProjectedValueDecoder {
    private static final Decoded INVALID = new Decoded(false, null);

    private HttpJsonProjectedValueDecoder() {
    }

    static Decoded decode(
            JsonNode value,
            DataSourceDraft.HttpJsonSourceType sourceType
    ) {
        if (value == null || value.isNull()) {
            return new Decoded(true, null);
        }
        try {
            return switch (sourceType) {
                case STRING -> value.isTextual()
                        && validText(value.textValue())
                        ? new Decoded(true, value.textValue()) : INVALID;
                case INTEGER -> integer(value);
                case DECIMAL -> decimal(value);
                case BOOLEAN -> value.isBoolean()
                        ? new Decoded(true, value.booleanValue()) : INVALID;
            };
        } catch (RuntimeException invalid) {
            return INVALID;
        }
    }

    private static Decoded integer(JsonNode node) {
        if (!node.isIntegralNumber()) {
            return INVALID;
        }
        BigInteger value = node.bigIntegerValue();
        return value.abs().toString().length() <= 38
                ? new Decoded(true, value) : INVALID;
    }

    private static Decoded decimal(JsonNode node) {
        if (!node.isNumber()) {
            return INVALID;
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() == 0) {
            return value.scale() <= 10
                    ? new Decoded(true, value) : INVALID;
        }
        var expandedPrecision = value.precision()
                - Math.min(value.scale(), 0);
        return value.precision() <= 38
                && expandedPrecision <= 38
                && value.scale() <= 10
                ? new Decoded(true, value) : INVALID;
    }

    private static boolean validText(String value) {
        if (value == null
                || value.length() > HttpJsonDataSourceProbe
                .MAXIMUM_SCALAR_LENGTH) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            var character = value.charAt(offset);
            if (Character.isHighSurrogate(character)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(
                        value.charAt(offset + 1))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
            offset += Character.charCount(value.codePointAt(offset));
        }
        return true;
    }

    record Decoded(boolean valid, Object value) {
        @Override
        public String toString() {
            return "HttpJsonProjectedValueDecoder.Decoded[valid=" + valid
                    + ", value=redacted]";
        }
    }
}
