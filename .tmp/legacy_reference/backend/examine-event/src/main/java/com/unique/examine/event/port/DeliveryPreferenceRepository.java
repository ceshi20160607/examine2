package com.unique.examine.event.port;

import com.unique.examine.event.domain.DeliveryPreference;
import com.unique.examine.event.domain.DeliveryChannel;

import java.util.List;
import java.util.Optional;

public interface DeliveryPreferenceRepository {
    long nextId();

    Optional<DeliveryPreference> find(
            long systemId,
            long tenantId,
            long memberId,
            String templateCode,
            DeliveryChannel channel
    );

    List<DeliveryPreference> findAll(
            long systemId,
            long tenantId,
            long memberId,
            DeliveryChannel channel
    );

    boolean insert(DeliveryPreference preference);

    boolean update(DeliveryPreference preference, long expectedVersion);
}
