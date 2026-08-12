package com.unique.examine.openapi.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

public interface OpenApiCallbackPublisher {
    Publication publish(Event event);

    record Event(long systemId, long tenantId, long applicationId, long serviceMemberId,
                 String eventId, String eventType, String resourceType, String resourceId,
                 Map<String, String> attributes, String requestId, String traceId) {
        public Event {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            if (systemId <= 0 || tenantId <= 0 || applicationId <= 0 || serviceMemberId <= 0
                    || eventId == null || eventId.isBlank() || eventId.length() > 160
                    || eventType == null || !eventType.matches("^[A-Z][A-Z0-9_]{1,63}$")
                    || resourceType == null || !resourceType.matches("^[A-Z][A-Z0-9_]{1,63}$")
                    || resourceId == null || resourceId.isBlank() || resourceId.length() > 160
                    || attributes.size() > 32 || requestId == null || requestId.isBlank()
                    || traceId == null || traceId.isBlank()) {
                throw new IllegalArgumentException("OpenAPI callback event is invalid");
            }
        }
    }

    record Publication(int selected, int enqueued, int duplicatesSuppressed) { }

    static String deterministicEventId(String eventType, String... components) {
        if (eventType == null || !eventType.matches("^[A-Z][A-Z0-9_]{1,63}$")
                || components == null || components.length == 0) {
            throw new IllegalArgumentException("OpenAPI callback event identity is invalid");
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var totalBytes = 0;
            for (var component : components) {
                if (component == null) {
                    throw new IllegalArgumentException("OpenAPI callback event identity is invalid");
                }
                var encoded = component.getBytes(StandardCharsets.UTF_8);
                totalBytes = Math.addExact(totalBytes, encoded.length);
                if (totalBytes > 4096) {
                    throw new IllegalArgumentException("OpenAPI callback event identity is too large");
                }
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(encoded.length).array());
                digest.update(encoded);
            }
            return eventType + ":" + HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    static OpenApiCallbackPublisher noop() { return event -> new Publication(0, 0, 0); }
}
