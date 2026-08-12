package com.unique.examine.openapi.repository;

import com.unique.examine.openapi.domain.OpenApiCallbackAttempt;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.domain.OpenApiCallbackSubscription;
import com.unique.examine.openapi.domain.OpenApiCallbackVersion;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OpenApiCallbackRepository {
    Optional<Bundle> find(long systemId, long tenantId, long applicationId, long subscriptionId);

    List<Bundle> list(long systemId, long tenantId, long applicationId);

    List<Bundle> listActiveForEvent(long systemId, long tenantId, long applicationId, String eventType);

    void insert(OpenApiCallbackSubscription subscription, OpenApiCallbackVersion version);

    boolean replaceVersion(OpenApiCallbackSubscription updated, OpenApiCallbackVersion previous,
                           OpenApiCallbackVersion replacement, long expectedVersion);

    boolean changeStatus(long systemId, long tenantId, long applicationId, long subscriptionId,
                         OpenApiCallbackSubscription.Status status, long actorId, Instant now,
                         long expectedVersion);

    boolean insertDelivery(OpenApiCallbackDelivery delivery);

    Optional<DeliveryBundle> findDelivery(long deliveryId);

    boolean completeAttempt(OpenApiCallbackDelivery delivery, OpenApiCallbackAttempt attempt,
                            int expectedAttemptCount, long expectedVersion);

    List<OpenApiCallbackDelivery> listDeliveries(long systemId, long tenantId, long applicationId,
                                                 long subscriptionId, int offset, int limit);

    long countDeliveries(long systemId, long tenantId, long applicationId, long subscriptionId);

    record Bundle(OpenApiCallbackSubscription subscription, OpenApiCallbackVersion version) { }

    record DeliveryBundle(OpenApiCallbackDelivery delivery, OpenApiCallbackSubscription subscription,
                          OpenApiCallbackVersion callbackVersion) { }
}
