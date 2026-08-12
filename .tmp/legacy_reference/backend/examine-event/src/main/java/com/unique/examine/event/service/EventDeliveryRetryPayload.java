package com.unique.examine.event.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Durable, bounded codec for one Event delivery retry command. */
public final class EventDeliveryRetryPayload {
    private static final Set<String> FIELDS = Set.of(
            "deliveryId",
            "systemId",
            "tenantId",
            "senderMemberId",
            "recipientMemberId",
            "templateCode",
            "variables",
            "targetType",
            "targetId",
            "targetPath",
            "dedupeKey"
    );

    private EventDeliveryRetryPayload() {
    }

    public static Map<String, Object> encode(
            long deliveryId,
            ResultNotificationFacade.Command command
    ) {
        if (deliveryId <= 0 || command == null
                || command.target().id() == null) {
            throw invalid();
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("deliveryId", Long.toString(deliveryId));
        result.put("systemId", Long.toString(command.systemId()));
        result.put("tenantId", Long.toString(command.tenantId()));
        result.put("senderMemberId", Long.toString(command.senderMemberId()));
        result.put("recipientMemberId", Long.toString(command.recipientMemberId()));
        result.put("templateCode", command.templateCode());
        result.put("variables", Map.copyOf(command.variables()));
        result.put("targetType", command.target().type());
        result.put("targetId", command.target().id());
        result.put("targetPath", command.targetPath());
        result.put("dedupeKey", command.dedupeKey());
        return Map.copyOf(result);
    }

    public static Decoded decode(Map<String, Object> input) {
        try {
            if (input == null || !input.keySet().equals(FIELDS)) {
                throw invalid();
            }
            var deliveryId = positiveLong(input.get("deliveryId"));
            var command = new ResultNotificationFacade.Command(
                    positiveLong(input.get("systemId")),
                    positiveLong(input.get("tenantId")),
                    positiveLong(input.get("senderMemberId")),
                    positiveLong(input.get("recipientMemberId")),
                    text(input.get("templateCode")),
                    variables(input.get("variables")),
                    new AggregateRef(
                            text(input.get("targetType")),
                            text(input.get("targetId"))
                    ),
                    text(input.get("targetPath")),
                    text(input.get("dedupeKey"))
            );
            return new Decoded(deliveryId, command);
        } catch (IllegalArgumentException invalid) {
            throw invalid();
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static Map<String, String> variables(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw invalid();
        }
        var result = new LinkedHashMap<String, String>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || !(entry.getValue() instanceof String item)) {
                throw invalid();
            }
            result.put(key, item);
        }
        return Map.copyOf(result);
    }

    private static long positiveLong(Object value) {
        try {
            var encoded = value instanceof Number number
                    ? number.toString()
                    : text(value);
            var parsed = Long.parseLong(encoded);
            if (parsed <= 0) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException malformed) {
            throw invalid();
        }
    }

    private static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid();
        }
        return text;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Event delivery retry payload is invalid");
    }

    public record Decoded(
            long deliveryId,
            ResultNotificationFacade.Command command
    ) {
        public Decoded {
            if (deliveryId <= 0 || command == null) {
                throw invalid();
            }
        }
    }
}
