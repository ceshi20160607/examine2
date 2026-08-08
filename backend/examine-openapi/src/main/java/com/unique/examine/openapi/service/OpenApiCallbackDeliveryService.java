package com.unique.examine.openapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.job.DurableJobFacade;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallbackAttempt;
import com.unique.examine.openapi.domain.OpenApiCallbackDelivery;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.secret.SecretRefResolver;
import com.unique.examine.openapi.security.OpenApiCallbackSigner;
import com.unique.examine.openapi.security.OpenApiCallbackTargetPolicy;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class OpenApiCallbackDeliveryService implements OpenApiCallbackPublisher {
    public static final String JOB_TYPE = "OPENAPI_CALLBACK_DELIVERY";
    public static final String OWNER_TYPE = "OPENAPI_CALLBACK";
    private final OpenApiCallbackRepository callbacks;
    private final OpenApiRepository applications;
    private final DurableJobFacade jobs;
    private final SecretRefResolver secrets;
    private final OutboundHttpTransport transport;
    private final OpenApiCallbackTargetPolicy targets;
    private final IdService ids;
    private final ObjectMapper json;
    private final Clock clock;

    public OpenApiCallbackDeliveryService(OpenApiCallbackRepository callbacks,
                                          OpenApiRepository applications,
                                          DurableJobFacade jobs,
                                          SecretRefResolver secrets,
                                          OutboundHttpTransport transport,
                                          OpenApiCallbackTargetPolicy targets,
                                          IdService ids, ObjectMapper json, Clock clock) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        this.applications = Objects.requireNonNull(applications, "applications");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.json = Objects.requireNonNull(json, "json");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override @Transactional public Publication publish(Event event) {
        Objects.requireNonNull(event, "event");
        var application = applications.find(event.systemId(), event.tenantId(), event.applicationId())
                .map(OpenApiRepository.ApplicationBundle::application)
                .filter(value -> value.status() == OpenApiApplication.Status.ACTIVE)
                .filter(value -> value.serviceMemberId() == event.serviceMemberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "OpenAPI callback event application scope is invalid"));
        var selected = callbacks.listActiveForEvent(event.systemId(), event.tenantId(),
                application.id(), event.eventType());
        var enqueued = 0;
        var duplicate = 0;
        var payload = payload(event);
        var bytes = payload.getBytes(StandardCharsets.UTF_8);
        var hash = OpenApiCallbackSigner.payloadHash(bytes);
        var now = clock.instant();
        for (var callback : selected) {
            var delivery = new OpenApiCallbackDelivery(ids.nextId(), event.systemId(), event.tenantId(),
                    event.applicationId(), callback.subscription().id(), callback.version().id(),
                    event.eventId(), event.eventType(), payload, hash,
                    OpenApiCallbackDelivery.Status.PENDING, 0, null, null,
                    event.requestId(), event.traceId(), now, now, null, 0);
            if (!callbacks.insertDelivery(delivery)) {
                duplicate++;
                continue;
            }
            jobs.enqueue(new DurableJobFacade.EnqueueCommand(JOB_TYPE, OWNER_TYPE,
                    Long.toString(delivery.id()), event.systemId(), event.tenantId(),
                    event.serviceMemberId(), Map.of("deliveryId", Long.toString(delivery.id())),
                    callback.version().maxAttempts()));
            enqueued++;
        }
        return new Publication(selected.size(), enqueued, duplicate);
    }

    public Execution execute(long deliveryId, int jobAttemptNo, boolean finalAttempt) {
        var bundle = callbacks.findDelivery(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("OpenAPI callback delivery was not found"));
        var current = bundle.delivery();
        if (current.status().terminal() || jobAttemptNo != current.attemptCount() + 1
                || jobAttemptNo < 1 || jobAttemptNo > bundle.callbackVersion().maxAttempts()
                || current.systemId() != bundle.subscription().systemId()
                || current.tenantId() != bundle.subscription().tenantId()
                || current.applicationId() != bundle.subscription().applicationId()
                || current.subscriptionId() != bundle.subscription().id()
                || current.callbackVersionId() != bundle.callbackVersion().id()) {
            throw new IllegalArgumentException("OpenAPI callback delivery state is invalid");
        }
        var started = clock.instant();
        var outcome = deliver(bundle, finalAttempt);
        var completed = clock.instant();
        var terminal = outcome.status().terminal() ? completed : null;
        var updated = new OpenApiCallbackDelivery(current.id(), current.systemId(), current.tenantId(),
                current.applicationId(), current.subscriptionId(), current.callbackVersionId(),
                current.eventId(), current.eventType(), current.payloadJson(), current.payloadHash(),
                outcome.status(), jobAttemptNo, outcome.httpStatus(), outcome.failureCode(),
                current.requestId(), current.traceId(), current.createdAt(), completed, terminal,
                current.version() + 1);
        var attempt = new OpenApiCallbackAttempt(ids.nextId(), deliveryId, jobAttemptNo,
                outcome.attemptOutcome(), outcome.httpStatus(),
                Math.max(0, Duration.between(started, completed).toMillis()), outcome.failureCode(),
                started, completed);
        if (!callbacks.completeAttempt(updated, attempt, current.attemptCount(), current.version())) {
            throw new IllegalStateException("OpenAPI callback delivery changed concurrently");
        }
        return new Execution(deliveryId, updated.status(), jobAttemptNo,
                bundle.callbackVersion().baseBackoffSeconds(), outcome.httpStatus(), outcome.failureCode());
    }

    private RawOutcome deliver(OpenApiCallbackRepository.DeliveryBundle bundle, boolean finalAttempt) {
        var version = bundle.callbackVersion();
        try { targets.requireSafe(version.endpoint().toASCIIString()); }
        catch (OpenApiCallbackTargetPolicy.UnsafeCallbackTargetException unsafe) {
            return RawOutcome.terminal("CALLBACK_TARGET_UNSAFE", null);
        }
        var secret = secrets.resolve(version.secretRef()).orElse(null);
        if (secret == null || secret.length < 32) {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            return RawOutcome.terminal("CALLBACK_SECRET_UNAVAILABLE", null);
        }
        var delivery = bundle.delivery();
        var timestamp = Long.toString(clock.instant().getEpochSecond());
        final OutboundHttpTransport.Response response;
        try {
            var headers = new LinkedHashMap<String, String>();
            headers.put("Content-Type", "application/json");
            headers.put("X-OpenAPI-Callback-Id", Long.toString(delivery.id()));
            headers.put("X-OpenAPI-Callback-Event", delivery.eventType());
            headers.put("X-OpenAPI-Callback-Timestamp", timestamp);
            headers.put("X-OpenAPI-Callback-Secret-Version",
                    Integer.toString(version.signingSecretVersion()));
            headers.put("X-OpenAPI-Callback-Signature", OpenApiCallbackSigner.sign(secret, timestamp,
                    delivery.id(), delivery.eventType(), delivery.payloadHash()));
            response = transport.post(new OutboundHttpTransport.Request(version.endpoint(), headers,
                    delivery.payloadJson().getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(10)));
        } catch (OutboundHttpTransport.TransportException failure) {
            var retryable = failure.kind() == OutboundHttpTransport.TransportException.Kind.TIMEOUT
                    || failure.kind() == OutboundHttpTransport.TransportException.Kind.IO;
            return retryable && !finalAttempt
                    ? RawOutcome.retryable("CALLBACK_TRANSPORT_RETRYABLE", null)
                    : RawOutcome.terminal(retryable ? "CALLBACK_ATTEMPTS_EXHAUSTED"
                    : "CALLBACK_TRANSPORT_REJECTED", null);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        var responseBody = response.body();
        Arrays.fill(responseBody, (byte) 0);
        var status = response.statusCode();
        if (status >= 200 && status < 300) return RawOutcome.succeeded(status);
        var retryable = status == 408 || status == 425 || status == 429 || status >= 500;
        if (retryable && !finalAttempt) return RawOutcome.retryable("CALLBACK_HTTP_RETRYABLE", status);
        return RawOutcome.terminal(retryable ? "CALLBACK_ATTEMPTS_EXHAUSTED"
                : "CALLBACK_HTTP_REJECTED", status);
    }

    private String payload(Event event) {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", event.eventId());
        body.put("type", event.eventType());
        body.put("occurredAt", clock.instant().toString());
        body.put("systemId", Long.toString(event.systemId()));
        body.put("tenantId", Long.toString(event.tenantId()));
        body.put("applicationId", Long.toString(event.applicationId()));
        body.put("resource", Map.of("type", event.resourceType(), "id", event.resourceId()));
        body.put("attributes", new TreeMap<>(event.attributes()));
        body.put("requestId", event.requestId());
        body.put("traceId", event.traceId());
        try {
            var value = json.writeValueAsString(body);
            if (value.length() > 65_536) throw new IllegalArgumentException("Callback payload is too large");
            return value;
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Callback payload is not serializable", failure);
        }
    }

    public record Execution(long deliveryId, OpenApiCallbackDelivery.Status status, int attemptCount,
                            int baseBackoffSeconds, Integer httpStatus, String failureCode) {
        public boolean retryable() { return status == OpenApiCallbackDelivery.Status.RETRYING; }
    }

    private record RawOutcome(OpenApiCallbackDelivery.Status status,
                              OpenApiCallbackAttempt.Outcome attemptOutcome,
                              Integer httpStatus, String failureCode) {
        static RawOutcome succeeded(int status) { return new RawOutcome(
                OpenApiCallbackDelivery.Status.SUCCEEDED, OpenApiCallbackAttempt.Outcome.SUCCEEDED,
                status, null); }
        static RawOutcome retryable(String code, Integer status) { return new RawOutcome(
                OpenApiCallbackDelivery.Status.RETRYING,
                OpenApiCallbackAttempt.Outcome.RETRYABLE_FAILURE, status, code); }
        static RawOutcome terminal(String code, Integer status) { return new RawOutcome(
                OpenApiCallbackDelivery.Status.FAILED,
                OpenApiCallbackAttempt.Outcome.TERMINAL_FAILURE, status, code); }
    }
}
