package com.unique.examine.event.domain;

import java.time.Instant;
import java.util.regex.Pattern;

public record DeliveryPreference(
        long id,
        long systemId,
        long tenantId,
        long memberId,
        String templateCode,
        DeliveryChannel channel,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    private static final Pattern TEMPLATE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,99}");

    public DeliveryPreference {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || memberId <= 0) {
            throw invalid("Delivery preference scope is incomplete");
        }
        if (templateCode == null || !TEMPLATE_CODE.matcher(templateCode).matches()) {
            throw invalid("Template code is invalid");
        }
        if (channel == null || createdAt == null || updatedAt == null || version <= 0) {
            throw invalid("Delivery preference state is incomplete");
        }
    }

    public DeliveryPreference change(boolean desiredEnabled, Instant changedAt) {
        if (changedAt == null) {
            throw invalid("Preference change timestamp is required");
        }
        return enabled == desiredEnabled ? this : new DeliveryPreference(
                id, systemId, tenantId, memberId, templateCode, channel,
                desiredEnabled, createdAt, changedAt, version + 1);
    }

    private static EventDomainException invalid(String message) {
        return new EventDomainException("EVENT_DELIVERY_PREFERENCE_INVALID", message);
    }
}
