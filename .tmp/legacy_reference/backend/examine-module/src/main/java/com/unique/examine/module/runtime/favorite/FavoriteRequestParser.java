package com.unique.examine.module.runtime.favorite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FavoriteRequestParser {
    private static final Set<String> MODULE_FIELDS = Set.of("type", "moduleCode");
    private static final Set<String> RECORD_FIELDS = Set.of("type", "moduleCode", "recordId");
    private static final Set<String> DELETE_FIELDS = Set.of("expectedVersion");

    private final ObjectMapper mapper = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    public CreateRequest create(String body) {
        var root = root(body);
        var type = text(root.get("type"), "type");
        var expected = switch (type) {
            case "MODULE" -> MODULE_FIELDS;
            case "RECORD" -> RECORD_FIELDS;
            default -> throw invalid("type must be MODULE or RECORD");
        };
        requireFields(root, expected);
        var moduleCode = text(root.get("moduleCode"), "moduleCode");
        if (!moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("moduleCode is invalid");
        }
        var recordId = "RECORD".equals(type) ? positiveId(root.get("recordId"), "recordId") : null;
        return new CreateRequest(type, moduleCode, recordId);
    }

    public DeleteRequest delete(String body) {
        var root = root(body);
        requireFields(root, DELETE_FIELDS);
        var node = root.get("expectedVersion");
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw invalid("expectedVersion must be a non-negative integer");
        }
        return new DeleteRequest(node.longValue());
    }

    public PageRequest page(int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw invalid("page must be 1..1000000 and size must be 1..100");
        }
        return new PageRequest(page, size);
    }

    private JsonNode root(String body) {
        if (body == null || body.isBlank()) {
            throw invalid("request body is required");
        }
        final JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw invalid("request JSON is malformed or contains duplicate keys");
        }
        if (root == null || !root.isObject()) {
            throw invalid("request body must be an object");
        }
        return root;
    }

    private static void requireFields(JsonNode root, Set<String> expected) {
        var actual = new LinkedHashSet<String>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("request contains unknown fields or is missing required fields");
        }
    }

    private static String text(JsonNode node, String name) {
        if (node == null || !node.isTextual()) {
            throw invalid(name + " must be a string");
        }
        return node.textValue();
    }

    private static Long positiveId(JsonNode node, String name) {
        if (node == null || !node.isTextual() || !node.textValue().matches("^[1-9][0-9]{0,18}$")) {
            throw invalid(name + " must be a positive ID string");
        }
        try {
            var value = Long.parseLong(node.textValue());
            if (value <= 0) {
                throw invalid(name + " must be a positive ID string");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid(name + " must be a positive ID string");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("FAVORITE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record CreateRequest(String type, String moduleCode, Long recordId) {
    }

    public record DeleteRequest(long expectedVersion) {
    }

    public record PageRequest(int page, int size) {
    }
}
