package com.unique.examine.event.domain;

import java.util.Comparator;
import java.util.List;

/** Controlled notification channels in stable fan-out order. */
public enum DeliveryChannel {
    INBOX,
    EMAIL,
    WEBHOOK;

    public static final List<DeliveryChannel> STABLE_ORDER = List.of(INBOX, EMAIL, WEBHOOK);

    public static DeliveryChannel parse(String value) {
        if (value == null || value.isBlank()) {
            throw new EventDomainException("EVENT_DELIVERY_CHANNEL_INVALID", "Delivery channel is required");
        }
        try {
            var channel = valueOf(value);
            if (!channel.name().equals(value)) throw new IllegalArgumentException();
            return channel;
        } catch (IllegalArgumentException invalid) {
            throw new EventDomainException("EVENT_DELIVERY_CHANNEL_INVALID", "Delivery channel is invalid");
        }
    }

    public static Comparator<DeliveryChannel> stableComparator() {
        return Comparator.comparingInt(STABLE_ORDER::indexOf);
    }
}
