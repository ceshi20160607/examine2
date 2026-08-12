package com.unique.examine.event.port;

import com.unique.examine.event.domain.DeliveryChannel;

import java.util.Map;

/**
 * High-level, credential-free external notification transport contract.
 * Implementations resolve their target/configuration without exposing secrets to orchestration.
 */
public interface EventChannelTransport {
    DeliveryChannel channel();

    DeliveryResult deliver(DeliveryCommand command);

    enum Status {
        SENT,
        TEMPORARY_FAILURE,
        PERMANENT_FAILURE
    }

    record DeliveryCommand(
            long deliveryId,
            long systemId,
            long tenantId,
            long recipientMemberId,
            String templateCode,
            String dedupeKey,
            String subject,
            String body,
            String targetType,
            String targetId,
            String targetPath,
            Map<String, String> variables
    ) {
        public DeliveryCommand {
            variables = variables == null ? Map.of() : Map.copyOf(variables);
        }
    }

    record DeliveryResult(
            Status status,
            String failureCode,
            String maskedDestination,
            long durationMillis,
            String traceId
    ) {
    }
}
