package com.unique.examine.event.port;

import com.unique.examine.event.domain.DeliveryChannel;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventChannelConfigurationRepository {
    Optional<Configuration> find(long systemId, DeliveryChannel channel);

    List<Configuration> list(long systemId);

    Optional<Configuration> create(long systemId, DeliveryChannel channel, boolean enabled,
                                   String endpoint, String secretRef, int timeoutMs,
                                   long actorId, Instant now);

    Optional<Configuration> update(long systemId, DeliveryChannel channel, boolean enabled,
                                   String endpoint, String secretRef, int timeoutMs,
                                   long actorId, long expectedVersion, Instant now);

    void recordCheck(long systemId, DeliveryChannel channel, String status,
                     String traceId, long durationMillis, Instant checkedAt);

    record Configuration(
            long id,
            long systemId,
            DeliveryChannel channel,
            boolean enabled,
            String endpoint,
            String secretRef,
            int timeoutMs,
            Instant lastCheckAt,
            String lastCheckStatus,
            String lastCheckTraceId,
            Long lastCheckDurationMillis,
            Instant createdAt,
            long createdBy,
            Instant updatedAt,
            long updatedBy,
            long version
    ) {
    }
}
