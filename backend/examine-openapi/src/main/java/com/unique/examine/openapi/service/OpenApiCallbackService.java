package com.unique.examine.openapi.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.api.OpenApiAdminSession;
import com.unique.examine.openapi.api.OpenApiCallbackRequests;
import com.unique.examine.openapi.api.OpenApiCallbackViews;
import com.unique.examine.openapi.domain.OpenApiCallbackSubscription;
import com.unique.examine.openapi.domain.OpenApiCallbackVersion;
import com.unique.examine.openapi.repository.OpenApiCallbackRepository;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.secret.SecretRefResolver;
import com.unique.examine.openapi.security.OpenApiCallbackTargetPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class OpenApiCallbackService implements OpenApiCallbackUseCase {
    private final OpenApiCallbackRepository callbacks;
    private final OpenApiRepository applications;
    private final SecretRefResolver secrets;
    private final OpenApiCallbackTargetPolicy targets;
    private final OperationAuditFacade audits;
    private final IdService ids;
    private final Clock clock;

    public OpenApiCallbackService(OpenApiCallbackRepository callbacks, OpenApiRepository applications,
                                  SecretRefResolver secrets, OpenApiCallbackTargetPolicy targets,
                                  OperationAuditFacade audits, IdService ids, Clock clock) {
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
        this.applications = Objects.requireNonNull(applications, "applications");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override @Transactional public OpenApiCallbackViews.Subscription create(
            OpenApiAdminSession session, long applicationId, OpenApiCallbackRequests.Create request,
            String requestId, String traceId) {
        requireApplication(session, applicationId);
        if (request == null) throw invalid("callback request is required");
        var endpoint = requireTarget(request.endpoint());
        var events = events(request.eventTypes());
        requireSecret(request.secretRef());
        var now = clock.instant();
        var subscription = new OpenApiCallbackSubscription(ids.nextId(), session.systemId(),
                session.tenantId(), applicationId, request.name(), OpenApiCallbackSubscription.Status.ACTIVE,
                1, now, session.accountId(), now, session.accountId(), 0);
        var version = new OpenApiCallbackVersion(ids.nextId(), subscription.id(), 1, endpoint, events,
                request.secretRef(), 1, attempts(request.maxAttempts()),
                backoff(request.baseBackoffSeconds()), OpenApiCallbackVersion.Status.ACTIVE,
                now, null, now, session.accountId());
        callbacks.insert(subscription, version);
        audit(session, subscription, version, "OPENAPI_CALLBACK_CREATED", requestId, traceId);
        return OpenApiCallbackViews.Subscription.from(new OpenApiCallbackRepository.Bundle(subscription, version));
    }

    @Override @Transactional(readOnly = true) public List<OpenApiCallbackViews.Subscription> list(
            OpenApiAdminSession session, long applicationId) {
        requireApplication(session, applicationId);
        return callbacks.list(session.systemId(), session.tenantId(), applicationId).stream()
                .map(OpenApiCallbackViews.Subscription::from).toList();
    }

    @Override @Transactional(readOnly = true) public OpenApiCallbackViews.Subscription detail(
            OpenApiAdminSession session, long applicationId, long subscriptionId) {
        requireApplication(session, applicationId);
        return OpenApiCallbackViews.Subscription.from(require(session, applicationId, subscriptionId));
    }

    @Override @Transactional public OpenApiCallbackViews.Subscription replace(
            OpenApiAdminSession session, long applicationId, long subscriptionId,
            OpenApiCallbackRequests.ReplaceConfiguration request, String requestId, String traceId) {
        if (request == null) throw invalid("callback request is required");
        var current = require(session, applicationId, subscriptionId);
        requireVersion(current.subscription(), request.version());
        var endpoint = requireTarget(request.endpoint());
        var events = events(request.eventTypes());
        requireSecret(request.secretRef());
        var secretChanged = !current.version().secretRef().equals(request.secretRef().strip());
        var now = clock.instant();
        var next = Math.addExact(current.version().configVersion(), 1);
        var updated = subscription(current.subscription(), request.name(), next, now,
                session.accountId());
        var retired = retire(current.version(), now);
        var replacement = new OpenApiCallbackVersion(ids.nextId(), subscriptionId, next, endpoint, events,
                request.secretRef(), secretChanged
                        ? Math.addExact(current.version().signingSecretVersion(), 1)
                        : current.version().signingSecretVersion(),
                attempts(request.maxAttempts()), backoff(request.baseBackoffSeconds()),
                OpenApiCallbackVersion.Status.ACTIVE, now, null, now, session.accountId());
        if (!callbacks.replaceVersion(updated, retired, replacement, current.subscription().version())) {
            throw conflict();
        }
        audit(session, updated, replacement, "OPENAPI_CALLBACK_CONFIGURATION_REPLACED",
                requestId, traceId);
        return OpenApiCallbackViews.Subscription.from(new OpenApiCallbackRepository.Bundle(updated, replacement));
    }

    @Override @Transactional public OpenApiCallbackViews.Subscription rotateSecret(
            OpenApiAdminSession session, long applicationId, long subscriptionId,
            OpenApiCallbackRequests.RotateSigningSecret request, String requestId, String traceId) {
        if (request == null) throw invalid("callback request is required");
        var current = require(session, applicationId, subscriptionId);
        requireVersion(current.subscription(), request.version());
        requireSecret(request.secretRef());
        if (current.version().secretRef().equals(request.secretRef().strip())) {
            throw invalid("a new signing SecretRef is required");
        }
        var now = clock.instant();
        var next = Math.addExact(current.version().configVersion(), 1);
        var updated = subscription(current.subscription(), current.subscription().name(), next, now,
                session.accountId());
        var replacement = new OpenApiCallbackVersion(ids.nextId(), subscriptionId, next,
                current.version().endpoint(), current.version().eventTypes(), request.secretRef(),
                Math.addExact(current.version().signingSecretVersion(), 1),
                current.version().maxAttempts(), current.version().baseBackoffSeconds(),
                OpenApiCallbackVersion.Status.ACTIVE, now, null, now, session.accountId());
        if (!callbacks.replaceVersion(updated, retire(current.version(), now), replacement,
                current.subscription().version())) throw conflict();
        audit(session, updated, replacement, "OPENAPI_CALLBACK_SIGNING_SECRET_ROTATED",
                requestId, traceId);
        return OpenApiCallbackViews.Subscription.from(new OpenApiCallbackRepository.Bundle(updated, replacement));
    }

    @Override @Transactional public OpenApiCallbackViews.Subscription enable(OpenApiAdminSession session, long applicationId,
                                                              long subscriptionId,
                                                              OpenApiCallbackRequests.ChangeStatus request,
                                                              String requestId, String traceId) {
        return status(session, applicationId, subscriptionId, request,
                OpenApiCallbackSubscription.Status.ACTIVE, "OPENAPI_CALLBACK_ENABLED",
                requestId, traceId);
    }

    @Override @Transactional public OpenApiCallbackViews.Subscription disable(OpenApiAdminSession session, long applicationId,
                                                               long subscriptionId,
                                                               OpenApiCallbackRequests.ChangeStatus request,
                                                               String requestId, String traceId) {
        return status(session, applicationId, subscriptionId, request,
                OpenApiCallbackSubscription.Status.DISABLED, "OPENAPI_CALLBACK_DISABLED",
                requestId, traceId);
    }

    @Override @Transactional(readOnly = true) public OpenApiCallbackViews.DeliveryPage deliveries(
            OpenApiAdminSession session, long applicationId, long subscriptionId, int page, int size) {
        require(session, applicationId, subscriptionId);
        if (page < 1 || page > 10_000 || size < 1 || size > 100) throw invalid("delivery page is invalid");
        var offset = Math.multiplyExact(page - 1, size);
        var total = callbacks.countDeliveries(session.systemId(), session.tenantId(), applicationId,
                subscriptionId);
        var items = callbacks.listDeliveries(session.systemId(), session.tenantId(), applicationId,
                subscriptionId, offset, size).stream().map(OpenApiCallbackViews.Delivery::from).toList();
        return new OpenApiCallbackViews.DeliveryPage(items, page, size, total);
    }

    private OpenApiCallbackViews.Subscription status(
            OpenApiAdminSession session, long applicationId, long subscriptionId,
            OpenApiCallbackRequests.ChangeStatus request, OpenApiCallbackSubscription.Status status,
            String action, String requestId, String traceId) {
        if (request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) throw invalid("status reason is invalid");
        var current = require(session, applicationId, subscriptionId);
        requireVersion(current.subscription(), request.version());
        if (current.subscription().status() == status) return OpenApiCallbackViews.Subscription.from(current);
        if (status == OpenApiCallbackSubscription.Status.ACTIVE) {
            requireTarget(current.version().endpoint().toASCIIString());
            requireSecret(current.version().secretRef());
        }
        var now = clock.instant();
        if (!callbacks.changeStatus(session.systemId(), session.tenantId(), applicationId, subscriptionId,
                status, session.accountId(), now, current.subscription().version())) throw conflict();
        var updated = new OpenApiCallbackSubscription(current.subscription().id(), session.systemId(),
                session.tenantId(), applicationId, current.subscription().name(), status,
                current.subscription().currentConfigVersion(), current.subscription().createdAt(),
                current.subscription().createdBy(), now, session.accountId(),
                current.subscription().version() + 1);
        audit(session, updated, current.version(), action, requestId, traceId);
        return OpenApiCallbackViews.Subscription.from(new OpenApiCallbackRepository.Bundle(updated,
                current.version()));
    }

    private void requireApplication(OpenApiAdminSession session, long applicationId) {
        if (applicationId <= 0 || applications.find(session.systemId(), session.tenantId(), applicationId).isEmpty())
            throw notFound();
    }

    private OpenApiCallbackRepository.Bundle require(OpenApiAdminSession session, long applicationId,
                                                     long subscriptionId) {
        requireApplication(session, applicationId);
        if (subscriptionId <= 0) throw notFound();
        return callbacks.find(session.systemId(), session.tenantId(), applicationId, subscriptionId)
                .orElseThrow(OpenApiCallbackService::notFound);
    }

    private void requireSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank() || secretRef.length() > 512) throw invalid("SecretRef is invalid");
        var value = secrets.resolve(secretRef.strip()).orElseThrow(() -> new BusinessException(
                "OPENAPI_CALLBACK_SECRET_UNAVAILABLE", "Callback signing SecretRef cannot be resolved",
                HttpStatus.UNPROCESSABLE_ENTITY));
        if (value.length < 32) {
            Arrays.fill(value, (byte) 0);
            throw invalid("callback signing secret must contain at least 32 bytes");
        }
        Arrays.fill(value, (byte) 0);
    }

    private java.net.URI requireTarget(String endpoint) {
        try { return targets.requireSafe(endpoint); }
        catch (OpenApiCallbackTargetPolicy.UnsafeCallbackTargetException unsafe) {
            throw new BusinessException("OPENAPI_CALLBACK_TARGET_UNSAFE",
                    "Callback target is not permitted", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void audit(OpenApiAdminSession session, OpenApiCallbackSubscription subscription,
                       OpenApiCallbackVersion version, String action,
                       String requestId, String traceId) {
        audits.recordSuccess(OperationAudit.success(new OperationAudit.Actor(session.accountId(), "WEB"),
                new OperationAudit.Context(ContextType.SYSTEM, session.systemId(), session.tenantId()),
                new AggregateRef("OPENAPI_CALLBACK", Long.toString(subscription.id())), action, null,
                Map.of("applicationId", Long.toString(subscription.applicationId()),
                        "status", subscription.status().name(), "configVersion", version.configVersion(),
                        "signingSecretVersion", version.signingSecretVersion(),
                        "eventTypeCount", version.eventTypes().size()), requestId, traceId));
    }

    private static OpenApiCallbackSubscription subscription(OpenApiCallbackSubscription current,
                                                            String name, int configVersion,
                                                            java.time.Instant now, long actorId) {
        return new OpenApiCallbackSubscription(current.id(), current.systemId(), current.tenantId(),
                current.applicationId(), name, current.status(), configVersion, current.createdAt(),
                current.createdBy(), now, actorId, current.version() + 1);
    }

    private static OpenApiCallbackVersion retire(OpenApiCallbackVersion current, java.time.Instant now) {
        return new OpenApiCallbackVersion(current.id(), current.subscriptionId(), current.configVersion(),
                current.endpoint(), current.eventTypes(), current.secretRef(), current.signingSecretVersion(),
                current.maxAttempts(), current.baseBackoffSeconds(), OpenApiCallbackVersion.Status.RETIRED,
                current.activatedAt(), now, current.createdAt(), current.createdBy());
    }

    private static Set<String> events(Set<String> values) {
        if (values == null || values.isEmpty() || values.size() > 32) throw invalid("eventTypes are invalid");
        var result = new TreeSet<String>();
        for (var value : values) {
            if (value == null || !value.matches("^[A-Z][A-Z0-9_]{1,63}$")) throw invalid("event type is invalid");
            result.add(value);
        }
        return Set.copyOf(result);
    }

    private static int attempts(Integer value) {
        if (value == null || value < 1 || value > 10) throw invalid("maxAttempts must be between 1 and 10");
        return value;
    }
    private static int backoff(Integer value) {
        if (value == null || value < 1 || value > 3600) throw invalid("baseBackoffSeconds must be between 1 and 3600");
        return value;
    }
    private static void requireVersion(OpenApiCallbackSubscription current, Long expected) {
        if (expected == null || expected < 0 || current.version() != expected) throw conflict();
    }
    private static BusinessException invalid(String message) {
        return new BusinessException("OPENAPI_CALLBACK_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static BusinessException conflict() {
        return new BusinessException("OPENAPI_CALLBACK_VERSION_CONFLICT",
                "OpenAPI callback changed concurrently", HttpStatus.CONFLICT);
    }
    private static BusinessException notFound() {
        return new BusinessException("OPENAPI_CALLBACK_NOT_FOUND", "OpenAPI callback was not found",
                HttpStatus.NOT_FOUND);
    }
}
