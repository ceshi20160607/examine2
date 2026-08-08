package com.unique.examine.event.service;

import com.unique.examine.event.domain.DeliveryChannel;
import com.unique.examine.event.port.EventChannelConfigurationRepository;
import com.unique.examine.event.port.EventChannelTransport;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventChannelConfigurationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T08:00:00Z");

    @Test
    void storesOnlyASecretReferenceAndReturnsRedactedConfigurationInStableOrder() {
        var repository = new MemoryRepository();
        var service = service(repository, List.of(transport(DeliveryChannel.WEBHOOK)));

        var updated = service.update(10, 100, DeliveryChannel.WEBHOOK,
                new EventChannelConfigurationService.UpdateCommand(true,
                        "https://notify.example.test/hooks/event", "env://EVENT_WEBHOOK_V1",
                        2400, 0));

        assertThat(updated.configured()).isTrue();
        assertThat(updated.maskedDestination()).isEqualTo("https://notify.example.test/…");
        assertThat(updated.secretRefMasked()).isEqualTo("env://********");
        assertThat(updated.toString()).doesNotContain("EVENT_WEBHOOK_V1", "/hooks/event");
        assertThat(repository.find(10, DeliveryChannel.WEBHOOK).orElseThrow().secretRef())
                .isEqualTo("env://EVENT_WEBHOOK_V1");
        assertThat(service.list(10)).extracting(EventChannelConfigurationService.ConfigurationView::channel)
                .containsExactly(DeliveryChannel.INBOX, DeliveryChannel.EMAIL, DeliveryChannel.WEBHOOK);
    }

    @Test
    void rejectsUnsafeWebhookEndpointsAndStaleWrites() {
        var service = service(new MemoryRepository(), List.of(transport(DeliveryChannel.WEBHOOK)));

        assertThatThrownBy(() -> service.update(10, 100, DeliveryChannel.WEBHOOK,
                new EventChannelConfigurationService.UpdateCommand(true,
                        "http://127.0.0.1/hook", "env://EVENT_WEBHOOK_V1", 5000, 0)))
                .hasMessageContaining("invalid");

        service.update(10, 100, DeliveryChannel.EMAIL,
                new EventChannelConfigurationService.UpdateCommand(true, null, null, null, 0));
        assertThatThrownBy(() -> service.update(10, 100, DeliveryChannel.EMAIL,
                new EventChannelConfigurationService.UpdateCommand(false, null, null, null, 9)))
                .hasMessageContaining("reload");
    }

    @Test
    void connectivityCheckUsesCredentialFreeCommandAndPersistsOnlySafeOutcome() {
        var repository = new MemoryRepository();
        var captured = new ArrayList<EventChannelTransport.DeliveryCommand>();
        EventChannelTransport email = new EventChannelTransport() {
            @Override
            public DeliveryChannel channel() {
                return DeliveryChannel.EMAIL;
            }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                captured.add(command);
                return new DeliveryResult(Status.SENT, null, "a***@example.test", 12, "trace-safe");
            }
        };
        var service = service(repository, List.of(email));
        service.update(10, 100, DeliveryChannel.EMAIL,
                new EventChannelConfigurationService.UpdateCommand(true, null, null, null, 0));

        var result = service.check(10, 20, 100, DeliveryChannel.EMAIL);

        assertThat(result.status()).isEqualTo("SENT");
        assertThat(result.traceId()).isEqualTo("trace-safe");
        assertThat(captured).singleElement().satisfies(command -> {
            assertThat(command.systemId()).isEqualTo(10);
            assertThat(command.tenantId()).isEqualTo(20);
            assertThat(command.recipientMemberId()).isEqualTo(100);
            assertThat(command.toString()).doesNotContain("secret", "smtp", "@example.test");
        });
        assertThat(repository.lastCheckStatus).isEqualTo("SENT");
        assertThat(repository.lastTraceId).isEqualTo("trace-safe");
    }

    private static EventChannelConfigurationService service(
            MemoryRepository repository, List<EventChannelTransport> transports) {
        return new EventChannelConfigurationService(repository, transports,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EventChannelTransport transport(DeliveryChannel channel) {
        return new EventChannelTransport() {
            @Override
            public DeliveryChannel channel() {
                return channel;
            }

            @Override
            public DeliveryResult deliver(DeliveryCommand command) {
                return new DeliveryResult(Status.SENT, null, "[masked]", 1, "trace");
            }
        };
    }

    private static final class MemoryRepository implements EventChannelConfigurationRepository {
        private final EnumMap<DeliveryChannel, Configuration> values = new EnumMap<>(DeliveryChannel.class);
        private String lastCheckStatus;
        private String lastTraceId;

        @Override
        public Optional<Configuration> find(long systemId, DeliveryChannel channel) {
            return Optional.ofNullable(values.get(channel));
        }

        @Override
        public List<Configuration> list(long systemId) {
            return List.copyOf(values.values());
        }

        @Override
        public Optional<Configuration> create(long systemId, DeliveryChannel channel, boolean enabled,
                                              String endpoint, String secretRef, int timeoutMs,
                                              long actorId, Instant now) {
            if (values.containsKey(channel)) return Optional.empty();
            var value = configuration(systemId, channel, enabled, endpoint, secretRef,
                    timeoutMs, actorId, now, 0);
            values.put(channel, value);
            return Optional.of(value);
        }

        @Override
        public Optional<Configuration> update(long systemId, DeliveryChannel channel, boolean enabled,
                                              String endpoint, String secretRef, int timeoutMs,
                                              long actorId, long expectedVersion, Instant now) {
            var current = values.get(channel);
            if (current == null || current.version() != expectedVersion) return Optional.empty();
            var value = configuration(systemId, channel, enabled, endpoint, secretRef,
                    timeoutMs, actorId, now, expectedVersion + 1);
            values.put(channel, value);
            return Optional.of(value);
        }

        @Override
        public void recordCheck(long systemId, DeliveryChannel channel, String status,
                                String traceId, long durationMillis, Instant checkedAt) {
            lastCheckStatus = status;
            lastTraceId = traceId;
        }

        private static Configuration configuration(long systemId, DeliveryChannel channel,
                                                   boolean enabled, String endpoint, String secretRef,
                                                   int timeoutMs, long actorId, Instant now, long version) {
            return new Configuration(1, systemId, channel, enabled, endpoint, secretRef, timeoutMs,
                    null, null, null, null, now, actorId, now, actorId, version);
        }
    }
}
