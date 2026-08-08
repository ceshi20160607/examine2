package com.unique.examine.event.port;

import com.unique.examine.event.domain.DeliveryChannel;

import java.net.URI;
import java.util.Optional;

/** Resolves channel targets/config references for adapters; never resolves secret material. */
public interface EventChannelTargetDirectory {
    Optional<Target> resolve(DeliveryChannel channel, long systemId, long tenantId, long recipientMemberId);

    record Target(String recipient, URI endpoint, String secretRef, Integer timeoutMs) {
    }
}
