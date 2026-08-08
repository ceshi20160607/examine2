package com.unique.examine.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCallbackAttempt;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.domain.OpenApiCallbackSubscription;
import com.unique.examine.openapi.domain.OpenApiCallbackVersion;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.OpenApiRateBucket;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.security.OpenApiCallbackTargetPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiCallbackDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-06T14:00:00Z");
    private CallbackStore callbacks;
    private Jobs jobs;
    private Transport transport;
    private OpenApiCallbackDeliveryService service;

    @BeforeEach
    void setUp() {
        callbacks = new CallbackStore(bundle(100, 10, 20, 30, "RECORD_CREATED"));
        jobs = new Jobs();
        transport = new Transport();
        service = new OpenApiCallbackDeliveryService(callbacks,
                new ApplicationStore(application(10, 20, 30, 40)), jobs,
                reference -> Optional.of(
                        "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                transport, new OpenApiCallbackTargetPolicy(), new SequentialIds(),
                new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishesRealSignedDeliveryAndPersistsSanitizedAttemptAudit() {
        transport.statuses.add(204);
        var publication = service.publish(event("evt-1", 10, 20, 30, 40));
        var delivery = callbacks.deliveries.values().iterator().next();
        var execution = service.execute(delivery.id(), 1, false);

        assertThat(publication).isEqualTo(new OpenApiCallbackPublisher.Publication(1, 1, 0));
        assertThat(execution.status()).isEqualTo(OpenApiCallbackDelivery.Status.SUCCEEDED);
        assertThat(transport.last.headers()).containsKeys("X-OpenAPI-Callback-Signature",
                "X-OpenAPI-Callback-Timestamp", "X-OpenAPI-Callback-Secret-Version");
        assertThat(transport.last.headers().get("X-OpenAPI-Callback-Signature"))
                .matches("v1=[0-9a-f]{64}");
        assertThat(new String(transport.last.body(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"type\":\"RECORD_CREATED\"")
                .doesNotContain("secret", "SecretRef");
        assertThat(callbacks.attempts).singleElement().satisfies(attempt -> {
            assertThat(attempt.outcome()).isEqualTo(OpenApiCallbackAttempt.Outcome.SUCCEEDED);
            assertThat(attempt.httpStatus()).isEqualTo(204);
            assertThat(attempt.failureCode()).isNull();
        });
    }

    @Test
    void retriesRetryableFailureThenSucceedsWithinBound() {
        transport.statuses.add(503);
        transport.statuses.add(200);
        service.publish(event("evt-2", 10, 20, 30, 40));
        var deliveryId = callbacks.deliveries.keySet().iterator().next();

        var first = service.execute(deliveryId, 1, false);
        var second = service.execute(deliveryId, 2, false);

        assertThat(first.retryable()).isTrue();
        assertThat(first.failureCode()).isEqualTo("CALLBACK_HTTP_RETRYABLE");
        assertThat(second.status()).isEqualTo(OpenApiCallbackDelivery.Status.SUCCEEDED);
        assertThat(callbacks.attempts).extracting(OpenApiCallbackAttempt::outcome)
                .containsExactly(OpenApiCallbackAttempt.Outcome.RETRYABLE_FAILURE,
                        OpenApiCallbackAttempt.Outcome.SUCCEEDED);
        assertThat(OpenApiCallbackRetryWorker.backoff(5, 10)).isEqualTo(Duration.ofSeconds(2560));
    }

    @Test
    void finalRetryableFailureIsTerminalAndResponseBodyIsNeverPersisted() {
        transport.statuses.add(500);
        service.publish(event("evt-3", 10, 20, 30, 40));
        var deliveryId = callbacks.deliveries.keySet().iterator().next();

        var result = service.execute(deliveryId, 1, true);

        assertThat(result.status()).isEqualTo(OpenApiCallbackDelivery.Status.FAILED);
        assertThat(result.failureCode()).isEqualTo("CALLBACK_ATTEMPTS_EXHAUSTED");
        assertThat(callbacks.deliveries.get(deliveryId).payloadJson())
                .doesNotContain("upstream-response");
    }

    @Test
    void suppressesDuplicateEventForTheSameSubscription() {
        var first = service.publish(event("dedupe-1", 10, 20, 30, 40));
        var replay = service.publish(event("dedupe-1", 10, 20, 30, 40));

        assertThat(first.enqueued()).isEqualTo(1);
        assertThat(replay).isEqualTo(new OpenApiCallbackPublisher.Publication(1, 0, 1));
        assertThat(callbacks.deliveries).hasSize(1);
        assertThat(jobs.commands).hasSize(1);
    }

    @Test
    void rejectsCrossTenantApplicationEventAndDoesNotLeakSubscription() {
        assertThatThrownBy(() -> service.publish(event("foreign", 10, 21, 30, 40)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("OpenAPI callback event application scope is invalid");
        assertThat(callbacks.deliveries).isEmpty();
        assertThat(jobs.commands).isEmpty();
    }

    private static OpenApiCallbackPublisher.Event event(String id, long systemId, long tenantId,
                                                         long applicationId, long memberId) {
        return new OpenApiCallbackPublisher.Event(systemId, tenantId, applicationId, memberId,
                id, "RECORD_CREATED", "RECORD", "501", Map.of("moduleCode", "orders"),
                "request-1", "trace-1");
    }

    private static OpenApiCallbackRepository.Bundle bundle(long subscriptionId, long systemId,
                                                            long tenantId, long applicationId,
                                                            String eventType) {
        var subscription = new OpenApiCallbackSubscription(subscriptionId, systemId, tenantId,
                applicationId, "ERP callback", OpenApiCallbackSubscription.Status.ACTIVE, 1,
                NOW, 1, NOW, 1, 0);
        var version = new OpenApiCallbackVersion(subscriptionId + 1, subscriptionId, 1,
                URI.create("https://8.8.8.8/callback"), Set.of(eventType), "env://CALLBACK_SECRET",
                1, 3, 5, OpenApiCallbackVersion.Status.ACTIVE, NOW, null, NOW, 1);
        return new OpenApiCallbackRepository.Bundle(subscription, version);
    }

    private static OpenApiRepository.ApplicationBundle application(long systemId, long tenantId,
                                                                    long applicationId, long memberId) {
        var app = new OpenApiApplication(applicationId, systemId, tenantId, memberId,
                "abcdefghijklmnop", "ERP", OpenApiApplication.Status.ACTIVE, Set.of("record.read"),
                List.of(), 60, 1, NOW, 1, NOW, 1, 0);
        var credential = new OpenApiCredential(applicationId + 1, applicationId, 1,
                "env://API_SECRET", OpenApiCredential.Status.ACTIVE, NOW, null, NOW, 1);
        return new OpenApiRepository.ApplicationBundle(app, credential);
    }

    private static final class SequentialIds extends IdService {
        private final AtomicLong sequence = new AtomicLong(1000);
        @Override public long nextId() { return sequence.incrementAndGet(); }
    }

    private static final class CallbackStore implements OpenApiCallbackRepository {
        private final Bundle callback;
        private final Map<Long, OpenApiCallbackDelivery> deliveries = new HashMap<>();
        private final Set<String> dedupe = new HashSet<>();
        private final List<OpenApiCallbackAttempt> attempts = new ArrayList<>();
        private CallbackStore(Bundle callback) { this.callback = callback; }
        @Override public Optional<Bundle> find(long s, long t, long a, long id) {
            return callback.subscription().systemId() == s && callback.subscription().tenantId() == t
                    && callback.subscription().applicationId() == a && callback.subscription().id() == id
                    ? Optional.of(callback) : Optional.empty();
        }
        @Override public List<Bundle> list(long s, long t, long a) { return find(s, t, a,
                callback.subscription().id()).stream().toList(); }
        @Override public List<Bundle> listActiveForEvent(long s, long t, long a, String event) {
            return find(s, t, a, callback.subscription().id()).filter(value ->
                    value.subscription().status() == OpenApiCallbackSubscription.Status.ACTIVE
                            && value.version().eventTypes().contains(event)).stream().toList();
        }
        @Override public void insert(OpenApiCallbackSubscription s, OpenApiCallbackVersion v) {
            throw new UnsupportedOperationException();
        }
        @Override public boolean replaceVersion(OpenApiCallbackSubscription u, OpenApiCallbackVersion p,
                                                OpenApiCallbackVersion r, long e) { return false; }
        @Override public boolean changeStatus(long s, long t, long a, long id,
                                              OpenApiCallbackSubscription.Status status, long actor,
                                              Instant now, long version) { return false; }
        @Override public boolean insertDelivery(OpenApiCallbackDelivery value) {
            var key = value.subscriptionId() + ":" + value.eventId() + ":" + value.eventType();
            if (!dedupe.add(key)) return false;
            deliveries.put(value.id(), value);
            return true;
        }
        @Override public Optional<DeliveryBundle> findDelivery(long id) {
            return Optional.ofNullable(deliveries.get(id)).map(value ->
                    new DeliveryBundle(value, callback.subscription(), callback.version()));
        }
        @Override public boolean completeAttempt(OpenApiCallbackDelivery value,
                                                 OpenApiCallbackAttempt attempt,
                                                 int expectedCount, long expectedVersion) {
            var current = deliveries.get(value.id());
            if (current == null || current.attemptCount() != expectedCount
                    || current.version() != expectedVersion) return false;
            deliveries.put(value.id(), value);
            attempts.add(attempt);
            return true;
        }
        @Override public List<OpenApiCallbackDelivery> listDeliveries(long s, long t, long a,
                                                                      long id, int offset, int limit) {
            return deliveries.values().stream().filter(value -> value.systemId() == s
                    && value.tenantId() == t && value.applicationId() == a
                    && value.subscriptionId() == id).toList();
        }
        @Override public long countDeliveries(long s, long t, long a, long id) {
            return listDeliveries(s, t, a, id, 0, 100).size();
        }
    }

    private static final class Jobs implements DurableJobFacade {
        private final List<EnqueueCommand> commands = new ArrayList<>();
        @Override public JobRecord enqueue(EnqueueCommand command) {
            commands.add(command);
            var now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
            return new JobRecord(1, command.jobType(), command.ownerType(), command.ownerId(),
                    command.systemId(), command.tenantId(), command.requestedBy(), "QUEUED", 0,
                    command.input(), Map.of(), 0, command.maxAttempts(), now, null, null, null,
                    null, now, now, 0);
        }
        @Override public Optional<JobRecord> claim(String type, Duration lease) { return Optional.empty(); }
        @Override public JobRecord succeed(long id, long version, Map<String, Object> result) {
            throw new UnsupportedOperationException();
        }
        @Override public JobRecord fail(long id, long version, String error, Duration delay) {
            throw new UnsupportedOperationException();
        }
        @Override public JobRecord require(long id) { throw new UnsupportedOperationException(); }
    }

    private static final class Transport implements OutboundHttpTransport {
        private final ArrayDeque<Integer> statuses = new ArrayDeque<>();
        private Request last;
        @Override public Response post(Request request) {
            last = request;
            return new Response(statuses.removeFirst(), "upstream-response".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8), Duration.ofMillis(12));
        }
    }

    private static final class ApplicationStore implements OpenApiRepository {
        private final ApplicationBundle bundle;
        private ApplicationStore(ApplicationBundle bundle) { this.bundle = bundle; }
        @Override public Optional<ApplicationBundle> findByAppKey(String key) { return Optional.empty(); }
        @Override public Optional<ApplicationBundle> find(long s, long t, long a) {
            return bundle.application().systemId() == s && bundle.application().tenantId() == t
                    && bundle.application().id() == a ? Optional.of(bundle) : Optional.empty();
        }
        @Override public List<ApplicationBundle> list(long s, long t, int o, int l) { return List.of(); }
        @Override public long count(long s, long t) { return 0; }
        @Override public void insertApplication(OpenApiApplication app) { throw unsupported(); }
        @Override public void insertCredential(OpenApiCredential credential) { throw unsupported(); }
        @Override public boolean updatePolicy(OpenApiApplication app, long version) { return false; }
        @Override public boolean updateStatus(long s, long t, long a, OpenApiApplication.Status status,
                                              long actor, Instant now, long version) { return false; }
        @Override public boolean rotateCredential(OpenApiApplication app, OpenApiCredential previous,
                                                  OpenApiCredential replacement, long version) { return false; }
        @Override public boolean consumeNonce(long app, int version, String nonce, Instant expires,
                                              Instant created) { return false; }
        @Override public OpenApiRateBucket lockRateBucket(long app, Instant window) { throw unsupported(); }
        @Override public boolean incrementRateBucket(long app, Instant window, int count, long version) {
            return false;
        }
        @Override public void insertCallLog(OpenApiCallLog log) { throw unsupported(); }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }
}
