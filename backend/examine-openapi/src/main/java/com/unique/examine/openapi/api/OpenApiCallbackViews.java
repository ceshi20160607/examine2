package com.unique.examine.openapi.api;

import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;

import java.net.URI;
import java.util.List;
import java.util.Set;

public final class OpenApiCallbackViews {
    private OpenApiCallbackViews() { }

    public record Subscription(
            String id,
            String applicationId,
            String name,
            String status,
            int configVersion,
            String endpoint,
            Set<String> eventTypes,
            String secretRef,
            int signingSecretVersion,
            int maxAttempts,
            int baseBackoffSeconds,
            long version,
            String createdAt,
            String updatedAt
    ) {
        public Subscription { eventTypes = Set.copyOf(eventTypes); }

        public static Subscription from(OpenApiCallbackRepository.Bundle bundle) {
            var subscription = bundle.subscription();
            var version = bundle.version();
            return new Subscription(
                    Long.toString(subscription.id()),
                    Long.toString(subscription.applicationId()),
                    subscription.name(), subscription.status().name(),
                    version.configVersion(), maskEndpoint(version.endpoint()),
                    version.eventTypes(), maskSecretRef(version.secretRef()),
                    version.signingSecretVersion(), version.maxAttempts(),
                    version.baseBackoffSeconds(), subscription.version(),
                    subscription.createdAt().toString(), subscription.updatedAt().toString());
        }
    }

    public record Delivery(
            String id,
            String eventId,
            String eventType,
            String status,
            int attemptCount,
            Integer lastHttpStatus,
            String failureCode,
            String requestId,
            String traceId,
            String createdAt,
            String updatedAt,
            String completedAt
    ) {
        public static Delivery from(OpenApiCallbackDelivery value) {
            return new Delivery(Long.toString(value.id()), value.eventId(), value.eventType(),
                    value.status().name(), value.attemptCount(), value.lastHttpStatus(),
                    value.failureCode(), value.requestId(), value.traceId(),
                    value.createdAt().toString(), value.updatedAt().toString(),
                    value.completedAt() == null ? null : value.completedAt().toString());
        }
    }

    public record DeliveryPage(List<Delivery> items, int page, int size, long total) {
        public DeliveryPage { items = List.copyOf(items); }
    }

    static String maskSecretRef(String secretRef) {
        var separator = secretRef.indexOf("://");
        return separator > 0 ? secretRef.substring(0, separator + 3) + "********" : "********";
    }

    static String maskEndpoint(URI endpoint) {
        var port = endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort();
        return endpoint.getScheme() + "://" + endpoint.getHost() + port + "/********";
    }
}
