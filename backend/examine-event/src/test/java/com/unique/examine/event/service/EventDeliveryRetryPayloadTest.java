package com.unique.examine.event.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDeliveryRetryPayloadTest {
    @Test
    void roundTripsEveryCommandFieldThroughTheValidatedPublicCodec() {
        var command = command();

        var encoded = EventDeliveryRetryPayload.encode(42, command);
        var decoded = EventDeliveryRetryPayload.decode(encoded);

        assertThat(encoded).containsOnlyKeys(
                "deliveryId", "systemId", "tenantId", "senderMemberId",
                "recipientMemberId", "templateCode", "variables",
                "targetType", "targetId", "targetPath", "dedupeKey");
        assertThat(decoded.deliveryId()).isEqualTo(42);
        assertThat(decoded.command()).isEqualTo(command);

        var numericIds = new LinkedHashMap<>(encoded);
        numericIds.put("deliveryId", 42L);
        numericIds.put("systemId", 10);
        assertThat(EventDeliveryRetryPayload.decode(numericIds))
                .isEqualTo(decoded);
    }

    @Test
    void decodeRejectsUnknownMissingAndUnvalidatedCommandFields() {
        var extra = new LinkedHashMap<>(
                EventDeliveryRetryPayload.encode(42, command()));
        extra.put("rawBody", "must-not-survive");
        assertThatThrownBy(() -> EventDeliveryRetryPayload.decode(extra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event delivery retry payload is invalid");

        var wrongPath = new LinkedHashMap<>(
                EventDeliveryRetryPayload.encode(42, command()));
        wrongPath.put("targetPath", "/systems/11/workbench");
        assertThatThrownBy(() -> EventDeliveryRetryPayload.decode(wrongPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event delivery retry payload is invalid");

        var rawVariables = new LinkedHashMap<>(
                EventDeliveryRetryPayload.encode(42, command()));
        rawVariables.put("variables", Map.of("rows", 3));
        assertThatThrownBy(() -> EventDeliveryRetryPayload.decode(rawVariables))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event delivery retry payload is invalid");
    }

    private static ResultNotificationFacade.Command command() {
        return new ResultNotificationFacade.Command(
                10, 20, 30, 40,
                "MODULE_EXPORT_SUCCEEDED",
                Map.of("moduleCode", "purchase_order", "rows", "3"),
                new AggregateRef("MODULE_EXPORT_TASK", "50"),
                "/systems/10/workbench?module=purchase_order&task=50",
                "job-result:export:50:SUCCEEDED"
        );
    }
}
