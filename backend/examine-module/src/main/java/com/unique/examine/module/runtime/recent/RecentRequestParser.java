package com.unique.examine.module.runtime.recent;

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
public class RecentRequestParser {
    private static final Set<String> TOUCH_FIELDS = Set.of("moduleCode", "recordId");

    private final ObjectMapper mapper = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    public TouchRequest touch(String body) {
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
        var fields = new LinkedHashSet<String>();
        root.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(TOUCH_FIELDS)) {
            throw invalid("request contains unknown fields or is missing required fields");
        }
        var moduleCode = text(root.get("moduleCode"), "moduleCode");
        if (!moduleCode.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("moduleCode is invalid");
        }
        return new TouchRequest(moduleCode, positiveId(root.get("recordId")));
    }

    public PageRequest page(int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw invalid("page must be 1..1000000 and size must be 1..100");
        }
        return new PageRequest(page, size);
    }

    private static String text(JsonNode node, String name) {
        if (node == null || !node.isTextual()) {
            throw invalid(name + " must be a string");
        }
        return node.textValue();
    }

    private static long positiveId(JsonNode node) {
        if (node == null || !node.isTextual() || !node.textValue().matches("^[1-9][0-9]{0,18}$")) {
            throw invalid("recordId must be a positive ID string");
        }
        try {
            var value = Long.parseLong(node.textValue());
            if (value <= 0) {
                throw invalid("recordId must be a positive ID string");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw invalid("recordId must be a positive ID string");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("RECENT_RECORD_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public record TouchRequest(String moduleCode, long recordId) {
    }

    public record PageRequest(int page, int size) {
    }
}
