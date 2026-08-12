package com.unique.examine.openapi.security;

import com.unique.examine.core.api.OpenApiPrincipalFacade;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.secret.SecretRefResolver;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

public class OpenApiAuthenticator {
    public static final Duration SIGNATURE_WINDOW = Duration.ofSeconds(300);

    private final OpenApiRepository repository;
    private final SecretRefResolver secrets;
    private final OpenApiPrincipalFacade principals;
    private final Clock clock;

    public OpenApiAuthenticator(
            OpenApiRepository repository,
            SecretRefResolver secrets,
            OpenApiPrincipalFacade principals,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.principals = Objects.requireNonNull(principals, "principals");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public OpenApiAuthentication authenticate(Request request, OpenApiAttempt attempt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(attempt, "attempt");
        var now = clock.instant();
        var signedAt = timestamp(request.headers().timestamp());
        if (Duration.between(signedAt, now).abs().compareTo(SIGNATURE_WINDOW) > 0) {
            throw OpenApiSecurityErrors.timestampInvalid();
        }

        var bundle = repository.findByAppKey(request.headers().appKey())
                .orElseThrow(OpenApiSecurityErrors::appUnavailable);
        var application = bundle.application();
        var credential = bundle.credential();
        attempt.identified(application.id(), credential.credentialVersion());
        requireActive(application, credential);

        var secret = secrets.resolve(credential.secretRef())
                .orElseThrow(OpenApiSecurityErrors::credentialUnavailable);
        try {
            try {
                var canonical = OpenApiCanonicalRequest.canonical(
                        request.method(),
                        request.rawPath(),
                        request.rawQuery(),
                        request.body(),
                        request.headers().timestamp(),
                        request.headers().nonce(),
                        request.headers().idempotencyKey()
                );
                if (!OpenApiCanonicalRequest.verify(
                        secret, canonical, request.headers().signature())) {
                    throw OpenApiSecurityErrors.signatureInvalid();
                }
            } catch (IllegalArgumentException exception) {
                throw OpenApiSecurityErrors.signatureInvalid();
            }
        } finally {
            Arrays.fill(secret, (byte) 0);
        }

        if (!IpAllowlist.allows(application.ipAllowlist(), request.observedIp())) {
            throw OpenApiSecurityErrors.ipDenied();
        }
        if (!application.scopes().contains(request.route().requiredScope())) {
            throw OpenApiSecurityErrors.scopeDenied();
        }
        var principal = principals.resolve(
                application.systemId(),
                application.tenantId(),
                application.serviceMemberId()
        );
        if (principal == null
                || !principal.systemActive()
                || !principal.tenantActive()
                || !principal.memberActive()
                || principal.accountId() <= 0) {
            throw OpenApiSecurityErrors.permissionDenied();
        }
        if (!principal.permissions().contains(request.route().requiredMemberPermission())) {
            throw OpenApiSecurityErrors.permissionDenied();
        }

        acquireRateSlot(application, now);
        var consumed = repository.consumeNonce(
                application.id(),
                credential.credentialVersion(),
                request.headers().nonce(),
                now.plus(SIGNATURE_WINDOW),
                now
        );
        if (!consumed) {
            throw OpenApiSecurityErrors.replayDetected();
        }

        return new OpenApiAuthentication(
                application,
                credential,
                new OpenApiMachineSession(
                        application.id(),
                        principal.accountId(),
                        application.systemId(),
                        application.tenantId(),
                        application.serviceMemberId(),
                        principal.permissionVersion(),
                        principal.permissions()
                )
        );
    }

    private void acquireRateSlot(OpenApiApplication application, Instant now) {
        var window = now.truncatedTo(ChronoUnit.MINUTES);
        var bucket = repository.lockRateBucket(application.id(), window);
        if (bucket.requestCount() >= application.rateLimitPerMinute()) {
            var retryAfter = Math.max(1, 60 - now.getEpochSecond() % 60);
            throw OpenApiSecurityErrors.rateLimited(
                    retryAfter, application.rateLimitPerMinute());
        }
        if (!repository.incrementRateBucket(
                application.id(),
                window,
                bucket.requestCount(),
                bucket.version()
        )) {
            var retryAfter = Math.max(1, 60 - now.getEpochSecond() % 60);
            throw OpenApiSecurityErrors.rateLimited(
                    retryAfter, application.rateLimitPerMinute());
        }
    }

    private static void requireActive(
            OpenApiApplication application,
            OpenApiCredential credential
    ) {
        if (application.status() != OpenApiApplication.Status.ACTIVE
                || credential.status() != OpenApiCredential.Status.ACTIVE
                || credential.credentialVersion() != application.currentCredentialVersion()) {
            throw OpenApiSecurityErrors.appUnavailable();
        }
    }

    private static Instant timestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException exception) {
            throw OpenApiSecurityErrors.timestampInvalid();
        }
    }

    public record Request(
            String method,
            String rawPath,
            String rawQuery,
            byte[] body,
            String observedIp,
            OpenApiHeaders headers,
            OpenApiRoutePolicy route
    ) {
        public Request {
            body = body == null ? new byte[0] : body.clone();
            Objects.requireNonNull(headers, "headers");
            Objects.requireNonNull(route, "route");
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
