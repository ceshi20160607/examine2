package com.unique.examine.event.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.service.DeliveryPreferenceService;

import java.util.Set;

public final class DeliveryPreferenceApiModels {
    private static final Set<String> UPDATE_FIELDS = Set.of("enabled", "expectedVersion");

    private DeliveryPreferenceApiModels() {
    }

    public record PreferenceView(
            String templateCode,
            String eventType,
            String name,
            String channel,
            boolean enabled,
            long version,
            @JsonInclude(JsonInclude.Include.ALWAYS) String updatedAt
    ) {
        static PreferenceView from(DeliveryPreferenceService.PreferenceView value) {
            return new PreferenceView(value.templateCode(), value.eventType(), value.name(), value.channel(),
                    value.enabled(), value.version(),
                    value.updatedAt() == null ? null : value.updatedAt().toString());
        }
    }

    public record UpdateRequest(Boolean enabled, Long expectedVersion) {
        static UpdateRequest parse(JsonNode body) {
            if (body == null || !body.isObject() || body.size() != UPDATE_FIELDS.size()) {
                throw invalid();
            }
            var fields = body.properties().stream().map(java.util.Map.Entry::getKey).collect(
                    java.util.stream.Collectors.toUnmodifiableSet());
            if (!fields.equals(UPDATE_FIELDS)) throw invalid();
            var enabled = body.get("enabled");
            var version = body.get("expectedVersion");
            if (enabled == null || !enabled.isBoolean() || version == null
                    || !version.isIntegralNumber() || !version.canConvertToLong()
                    || version.longValue() < 0) {
                throw invalid();
            }
            return new UpdateRequest(enabled.booleanValue(), version.longValue());
        }

        private static EventDomainException invalid() {
            return new EventDomainException("EVENT_DELIVERY_PREFERENCE_INVALID",
                    "Delivery preference request must contain only enabled and expectedVersion");
        }
    }
}
