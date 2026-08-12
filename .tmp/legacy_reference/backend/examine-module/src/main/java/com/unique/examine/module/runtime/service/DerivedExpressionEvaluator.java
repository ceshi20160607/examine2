package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Component
public class DerivedExpressionEvaluator {
    private static final int MAX_STRING_LENGTH = 4000;
    private static final int MAX_DECIMAL_SCALE = 10;
    private static final int MAX_DECIMAL_PRECISION = 38;

    public Value evaluate(JsonNode expression, Function<Long, Value> fieldResolver) {
        if (expression == null || !expression.isObject()) {
            throw invalid("Expression node is unavailable");
        }
        if (expression.has("fieldId")) {
            var id = positiveId(expression.path("fieldId"));
            var value = id == null ? null : fieldResolver.apply(id);
            if (value == null) {
                throw invalid("Expression field dependency is unavailable");
            }
            return value;
        }
        if (expression.has("literalType")) {
            return literal(expression.path("literalType").asText(), expression.path("value"));
        }
        var args = new ArrayList<Value>();
        expression.path("args").forEach(arg -> args.add(evaluate(arg, fieldResolver)));
        return apply(expression.path("op").asText(), List.copyOf(args));
    }

    private Value apply(String op, List<Value> args) {
        return switch (op) {
            case "ADD" -> numeric(op, args, (left, right) -> left.add(right));
            case "SUBTRACT" -> numeric(op, args, (left, right) -> left.subtract(right));
            case "MULTIPLY" -> numeric(op, args, (left, right) -> left.multiply(right));
            case "DIVIDE" -> numeric(op, args, (left, right) -> {
                if (right.signum() == 0) {
                    throw invalid("Division by zero");
                }
                return left.divide(right, MAX_DECIMAL_SCALE, RoundingMode.HALF_EVEN);
            });
            case "CONCAT" -> concat(args);
            case "EQ", "NE", "GT", "GTE", "LT", "LTE" -> compare(op, args);
            case "AND", "OR" -> booleanBinary(op, args);
            case "NOT" -> not(args);
            case "IF" -> conditional(args);
            case "ADD_DAYS" -> addDays(args);
            case "DAYS_BETWEEN" -> daysBetween(args);
            default -> throw invalid("Expression operator is unsupported");
        };
    }

    private Value numeric(String op, List<Value> args, DecimalOperator operator) {
        requireArity(op, args, 2);
        if (!args.stream().allMatch(value -> value.schema().numeric())) {
            throw invalid(op + " requires numeric operands");
        }
        if (hasNull(args)) {
            return Value.nullValue(ResultSchema.DECIMAL);
        }
        return new Value(ResultSchema.DECIMAL, bounded(operator.apply(
                (BigDecimal) args.get(0).value(), (BigDecimal) args.get(1).value())));
    }

    private Value concat(List<Value> args) {
        requireArity("CONCAT", args, 2);
        if (!args.stream().allMatch(value -> value.schema() == ResultSchema.STRING)) {
            throw invalid("CONCAT requires STRING operands");
        }
        if (hasNull(args)) {
            return Value.nullValue(ResultSchema.STRING);
        }
        var result = (String) args.get(0).value() + args.get(1).value();
        if (result.length() > MAX_STRING_LENGTH) {
            throw invalid("CONCAT result exceeds 4000 characters");
        }
        return new Value(ResultSchema.STRING, result);
    }

    private Value compare(String op, List<Value> args) {
        requireArity(op, args, 2);
        if (args.get(0).schema() != args.get(1).schema()) {
            throw invalid(op + " requires identical operand types");
        }
        if (hasNull(args)) {
            return Value.nullValue(ResultSchema.BOOLEAN);
        }
        var comparison = compare(args.get(0), args.get(1));
        var result = switch (op) {
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> false;
        };
        return new Value(ResultSchema.BOOLEAN, result);
    }

    private Value booleanBinary(String op, List<Value> args) {
        requireArity(op, args, 2);
        if (!args.stream().allMatch(value -> value.schema() == ResultSchema.BOOLEAN)) {
            throw invalid(op + " requires BOOLEAN operands");
        }
        if (hasNull(args)) {
            return Value.nullValue(ResultSchema.BOOLEAN);
        }
        var left = (Boolean) args.get(0).value();
        var right = (Boolean) args.get(1).value();
        return new Value(ResultSchema.BOOLEAN, "AND".equals(op) ? left && right : left || right);
    }

    private Value not(List<Value> args) {
        requireArity("NOT", args, 1);
        if (args.getFirst().schema() != ResultSchema.BOOLEAN) {
            throw invalid("NOT requires a BOOLEAN operand");
        }
        return args.getFirst().value() == null ? Value.nullValue(ResultSchema.BOOLEAN)
                : new Value(ResultSchema.BOOLEAN, !(Boolean) args.getFirst().value());
    }

    private Value conditional(List<Value> args) {
        requireArity("IF", args, 3);
        if (args.get(0).schema() != ResultSchema.BOOLEAN || args.get(1).schema() != args.get(2).schema()) {
            throw invalid("IF operands are type incompatible");
        }
        if (args.get(0).value() == null) {
            return Value.nullValue(args.get(1).schema());
        }
        return (Boolean) args.get(0).value() ? args.get(1) : args.get(2);
    }

    private Value addDays(List<Value> args) {
        requireArity("ADD_DAYS", args, 2);
        if (!args.get(0).schema().temporal() || args.get(1).schema() != ResultSchema.INTEGER) {
            throw invalid("ADD_DAYS requires DATE or DATETIME and INTEGER");
        }
        if (hasNull(args)) {
            return Value.nullValue(args.get(0).schema());
        }
        var days = ((BigDecimal) args.get(1).value()).longValueExact();
        return args.get(0).schema() == ResultSchema.DATE
                ? new Value(ResultSchema.DATE, ((LocalDate) args.get(0).value()).plusDays(days))
                : new Value(ResultSchema.DATETIME, ((LocalDateTime) args.get(0).value()).plusDays(days));
    }

    private Value daysBetween(List<Value> args) {
        requireArity("DAYS_BETWEEN", args, 2);
        if (args.get(0).schema() != args.get(1).schema() || !args.get(0).schema().temporal()) {
            throw invalid("DAYS_BETWEEN requires identical DATE or DATETIME operands");
        }
        if (hasNull(args)) {
            return Value.nullValue(ResultSchema.INTEGER);
        }
        long days = args.get(0).schema() == ResultSchema.DATE
                ? ChronoUnit.DAYS.between((LocalDate) args.get(0).value(), (LocalDate) args.get(1).value())
                : ChronoUnit.DAYS.between((LocalDateTime) args.get(0).value(),
                (LocalDateTime) args.get(1).value());
        return new Value(ResultSchema.INTEGER, BigDecimal.valueOf(days));
    }

    private Value literal(String type, JsonNode node) {
        final ResultSchema schema;
        try {
            schema = ResultSchema.valueOf(type);
        } catch (IllegalArgumentException exception) {
            throw invalid("Literal type is unsupported");
        }
        if (node == null || node.isNull()) {
            return Value.nullValue(schema);
        }
        try {
            return switch (schema) {
                case STRING -> {
                    if (!node.isTextual() || node.asText().length() > MAX_STRING_LENGTH) {
                        throw invalid("STRING literal is invalid");
                    }
                    yield new Value(schema, node.asText());
                }
                case DECIMAL -> {
                    if (!node.isNumber()) throw invalid("DECIMAL literal is invalid");
                    yield new Value(schema, bounded(node.decimalValue()));
                }
                case INTEGER -> {
                    if (!node.isIntegralNumber()) throw invalid("INTEGER literal is invalid");
                    yield new Value(schema, bounded(node.decimalValue()));
                }
                case DATE -> {
                    if (!node.isTextual()) throw invalid("DATE literal is invalid");
                    yield new Value(schema, LocalDate.parse(node.asText(), DateTimeFormatter.ISO_LOCAL_DATE));
                }
                case DATETIME -> {
                    if (!node.isTextual()) throw invalid("DATETIME literal is invalid");
                    yield new Value(schema, dateTime(node.asText()));
                }
                case BOOLEAN -> {
                    if (!node.isBoolean()) throw invalid("BOOLEAN literal is invalid");
                    yield new Value(schema, node.booleanValue());
                }
            };
        } catch (DateTimeParseException | ArithmeticException exception) {
            throw invalid(type + " literal is invalid");
        }
    }

    private static LocalDateTime dateTime(String value) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
    }

    private static int compare(Value left, Value right) {
        if (left.schema().numeric()) {
            return ((BigDecimal) left.value()).compareTo((BigDecimal) right.value());
        }
        return switch (left.schema()) {
            case STRING -> ((String) left.value()).compareTo((String) right.value());
            case DATE -> ((LocalDate) left.value()).compareTo((LocalDate) right.value());
            case DATETIME -> ((LocalDateTime) left.value()).compareTo((LocalDateTime) right.value());
            case BOOLEAN -> ((Boolean) left.value()).compareTo((Boolean) right.value());
            default -> throw invalid("Values are not comparable");
        };
    }

    private static BigDecimal bounded(BigDecimal value) {
        var result = value.scale() > MAX_DECIMAL_SCALE
                ? value.setScale(MAX_DECIMAL_SCALE, RoundingMode.HALF_EVEN) : value;
        if (result.precision() > MAX_DECIMAL_PRECISION) {
            throw invalid("Decimal result exceeds precision 38");
        }
        return result.signum() == 0 ? BigDecimal.ZERO : result.stripTrailingZeros();
    }

    private static boolean hasNull(List<Value> values) {
        return values.stream().anyMatch(value -> value.value() == null);
    }

    private static void requireArity(String op, List<Value> args, int expected) {
        if (args.size() != expected) {
            throw invalid(op + " arity is invalid");
        }
    }

    private static Long positiveId(JsonNode node) {
        try {
            var value = Long.parseLong(node.asText());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface DecimalOperator {
        BigDecimal apply(BigDecimal left, BigDecimal right);
    }

    public enum ResultSchema {
        STRING, DECIMAL, INTEGER, DATE, DATETIME, BOOLEAN;

        public boolean numeric() {
            return this == DECIMAL || this == INTEGER;
        }

        public boolean temporal() {
            return this == DATE || this == DATETIME;
        }
    }

    public record Value(ResultSchema schema, Object value) {
        public Value {
            Objects.requireNonNull(schema, "schema");
        }

        public static Value nullValue(ResultSchema schema) {
            return new Value(schema, null);
        }
    }
}
