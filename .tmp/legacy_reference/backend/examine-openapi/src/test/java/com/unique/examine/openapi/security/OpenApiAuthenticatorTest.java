package com.unique.examine.openapi.security;

import com.unique.examine.core.api.OpenApiPrincipalFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.domain.OpenApiRateBucket;
import com.unique.examine.openapi.repository.OpenApiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiAuthenticatorTest {
    private static final Instant NOW = Instant.parse("2026-07-28T08:00:00Z");
    private static final byte[] SECRET =
            "external-secret-material".getBytes(StandardCharsets.UTF_8);
    private final InMemoryRepository repository = new InMemoryRepository();
    private OpenApiPrincipalFacade.Principal principal;
    private OpenApiAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("openapi.ping"),
                List.of("203.0.113.0/24"),
                10
        );
        principal = new OpenApiPrincipalFacade.Principal(
                700, 9, true, true, true, Set.of("system.runtime.access"));
        authenticator = new OpenApiAuthenticator(
                repository,
                ignored -> Optional.of(SECRET.clone()),
                (systemId, tenantId, memberId) -> principal,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void authenticatesMachineSessionAndConsumesNonceOnlyAfterSignature() {
        var attempt = new OpenApiAttempt();

        var authentication = authenticator.authenticate(
                request("nonce-valid-000001", null), attempt);

        assertThat(authentication.session().accountId()).isEqualTo(700);
        assertThat(authentication.session().systemId()).isEqualTo(20);
        assertThat(authentication.session().tenantId()).isEqualTo(30);
        assertThat(authentication.session().memberId()).isEqualTo(40);
        assertThat(attempt.applicationId()).isEqualTo(10);
        assertThat(repository.consumedNonces).containsExactly("nonce-valid-000001");
        assertThat(repository.rateCount).isEqualTo(1);
    }

    @Test
    void businessRoutesIntersectExplicitScopeWithCurrentRuntimePermission() {
        var routes = List.of(
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records", null,
                        "{\"state\":\"DRAFT\"}", "record.write"),
                new RouteCase("GET",
                        "/openapi/v1/modules/orders/records", "page=1&size=20",
                        "", "record.read"),
                new RouteCase("GET",
                        "/openapi/v1/modules/orders/records/42", null,
                        "", "record.read"),
                new RouteCase("PUT",
                        "/openapi/v1/modules/orders/records/42", null,
                        "{\"expectedVersion\":3,\"values\":{\"title\":\"Revised\"}}",
                        "record.write"),
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records/42:activate", null,
                        "{\"expectedVersion\":3}", "record.write"),
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records/42:archive", null,
                        "{\"expectedVersion\":4}", "record.write"),
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records/42:unarchive", null,
                        "{\"expectedVersion\":5}", "record.write"),
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records/42:trash", null,
                        "{\"expectedVersion\":6}", "record.write"),
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records/42:restore-from-trash",
                        null, "{\"expectedVersion\":7}", "record.write"),
                new RouteCase("POST",
                        "/openapi/v1/modules/orders/records/42/files", null,
                        "{\"originalName\":\"invoice.pdf\","
                                + "\"mediaType\":\"application/pdf\","
                                + "\"contentBase64\":\"UERG\"}",
                        "file.write"),
                new RouteCase("GET",
                        "/openapi/v1/modules/orders/records/42/files",
                        "page=1&size=20", "", "file.read"),
                new RouteCase("GET",
                        "/openapi/v1/modules/orders/records/42/files/17/content",
                        null, "", "file.read")
        );
        principal = new OpenApiPrincipalFacade.Principal(
                700, 11, true, true, true,
                Set.of("system.runtime.access", "module.orders.view"));

        for (var index = 0; index < routes.size(); index++) {
            var value = routes.get(index);
            repository.bundle = bundle(
                    OpenApiApplication.Status.ACTIVE,
                    OpenApiCredential.Status.ACTIVE,
                    Set.of(value.scope()), List.of("203.0.113.0/24"), 100);

            var authentication = authenticator.authenticate(
                    signedRequest(value, "nonce-record-allow-0" + index),
                    new OpenApiAttempt());

            assertThat(authentication.session().permissionVersion()).isEqualTo(11);
            assertThat(authentication.session().permissions())
                    .contains("system.runtime.access", "module.orders.view");
        }

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("record.read"), List.of("203.0.113.0/24"), 100);
        assertCode(() -> authenticator.authenticate(
                        signedRequest(routes.getFirst(), "nonce-record-scope-deny"),
                        new OpenApiAttempt()),
                "OPENAPI_SCOPE_DENIED");

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("record.read"), List.of("203.0.113.0/24"), 100);
        principal = new OpenApiPrincipalFacade.Principal(
                700, 12, true, true, true, Set.of("module.orders.view"));
        assertCode(() -> authenticator.authenticate(
                        signedRequest(routes.get(2),
                                "nonce-record-permission-deny"),
                        new OpenApiAttempt()),
                "OPENAPI_PERMISSION_DENIED");

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("file.read"), List.of("203.0.113.0/24"), 100);
        assertCode(() -> authenticator.authenticate(
                        signedRequest(routes.get(9), "nonce-file-scope-deny"),
                        new OpenApiAttempt()),
                "OPENAPI_SCOPE_DENIED");

        assertCode(() -> authenticator.authenticate(
                        signedRequest(routes.get(11),
                                "nonce-file-permission-deny"),
                        new OpenApiAttempt()),
                "OPENAPI_PERMISSION_DENIED");
    }

    @Test
    void flowStatusIntersectsReadScopeWithCurrentInstancePermission() {
        var route = new RouteCase(
                "GET", "/openapi/v1/flow/instances/42",
                null, "", "flow.read");
        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("flow.read"), List.of("203.0.113.0/24"), 100);
        principal = new OpenApiPrincipalFacade.Principal(
                700, 13, true, true, true,
                Set.of("flow.instance.read"));

        var authentication = authenticator.authenticate(
                signedRequest(route, "nonce-flow-status-ok1"),
                new OpenApiAttempt());

        assertThat(authentication.session().permissionVersion()).isEqualTo(13);
        assertThat(authentication.session().permissions())
                .containsExactly("flow.instance.read");

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("flow.instance.start"),
                List.of("203.0.113.0/24"), 100);
        assertCode(() -> authenticator.authenticate(
                        signedRequest(route, "nonce-flow-status-scope"),
                        new OpenApiAttempt()),
                "OPENAPI_SCOPE_DENIED");

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("flow.read"), List.of("203.0.113.0/24"), 100);
        principal = new OpenApiPrincipalFacade.Principal(
                700, 14, true, true, true,
                Set.of("system.runtime.access"));
        assertCode(() -> authenticator.authenticate(
                        signedRequest(route, "nonce-flow-status-perm1"),
                        new OpenApiAttempt()),
                "OPENAPI_PERMISSION_DENIED");
    }

    @Test
    void compositionRoutesIntersectRecordScopeWithRuntimePermission() {
        var routes = List.of(
                new RouteCase(
                        "GET",
                        "/openapi/v1/modules/orders/records/42/relations/items",
                        "page=1&size=20", "", "record.read"),
                new RouteCase(
                        "POST",
                        "/openapi/v1/modules/orders/records/42/relations/"
                                + "items:mutate",
                        null,
                        "{\"expectedVersion\":3,\"targets\":[{\"recordId\":\"91\"}]}",
                        "record.write"),
                new RouteCase(
                        "GET",
                        "/openapi/v1/modules/orders/records/42/subtables/lines",
                        "page=2&size=10", "", "record.read"),
                new RouteCase(
                        "POST",
                        "/openapi/v1/modules/orders/records/42/subtables/"
                                + "lines:mutate",
                        null,
                        "{\"expectedVersion\":4,\"rows\":[]}",
                        "record.write")
        );
        principal = new OpenApiPrincipalFacade.Principal(
                700, 15, true, true, true,
                Set.of("system.runtime.access", "module.orders.view"));

        for (var index = 0; index < routes.size(); index++) {
            var route = routes.get(index);
            repository.bundle = bundle(
                    OpenApiApplication.Status.ACTIVE,
                    OpenApiCredential.Status.ACTIVE,
                    Set.of(route.scope()), List.of("203.0.113.0/24"), 100);

            var authentication = authenticator.authenticate(
                    signedRequest(route, "nonce-composition-ok0" + index),
                    new OpenApiAttempt());

            assertThat(authentication.session().permissionVersion())
                    .isEqualTo(15);
            assertThat(authentication.session().permissions())
                    .contains("system.runtime.access", "module.orders.view");
        }

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("record.read"), List.of("203.0.113.0/24"), 100);
        assertCode(() -> authenticator.authenticate(
                        signedRequest(routes.get(1),
                                "nonce-composition-scope"),
                        new OpenApiAttempt()),
                "OPENAPI_SCOPE_DENIED");

        principal = new OpenApiPrincipalFacade.Principal(
                700, 16, true, true, true,
                Set.of("module.orders.view"));
        assertCode(() -> authenticator.authenticate(
                        signedRequest(routes.get(2),
                                "nonce-composition-perm1"),
                        new OpenApiAttempt()),
                "OPENAPI_PERMISSION_DENIED");
    }

    @Test
    void invalidSignatureDoesNotConsumeNonceOrRate() {
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-invalid-0001", "0".repeat(64)),
                        new OpenApiAttempt()),
                "OPENAPI_SIGNATURE_INVALID"
        );

        assertThat(repository.consumedNonces).isEmpty();
        assertThat(repository.rateCount).isZero();
    }

    @Test
    void reusedDurableNonceIsRejected() {
        var nonce = "nonce-replayed-0001";
        authenticator.authenticate(request(nonce, null), new OpenApiAttempt());

        assertCode(
                () -> authenticator.authenticate(request(nonce, null), new OpenApiAttempt()),
                "OPENAPI_REPLAY_DETECTED"
        );
    }

    @Test
    void rejectsTimestampScopeMemberIpAndRateFailClosed() {
        assertCode(
                () -> authenticator.authenticate(
                        requestAt("nonce-expired-0001", NOW.minusSeconds(301), null),
                        new OpenApiAttempt()),
                "OPENAPI_TIMESTAMP_INVALID"
        );

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("other.scope"),
                List.of("203.0.113.0/24"),
                10
        );
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-no-scope-001", null), new OpenApiAttempt()),
                "OPENAPI_SCOPE_DENIED"
        );

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("openapi.ping"),
                List.of("203.0.113.0/24"),
                10
        );
        principal = new OpenApiPrincipalFacade.Principal(
                700, 9, true, true, false, Set.of("system.runtime.access"));
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-no-member-01", null), new OpenApiAttempt()),
                "OPENAPI_PERMISSION_DENIED"
        );

        principal = new OpenApiPrincipalFacade.Principal(
                700, 9, true, true, true, Set.of("system.runtime.access"));
        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("openapi.ping"),
                List.of("198.51.100.1"),
                10
        );
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-ip-denied-01", null), new OpenApiAttempt()),
                "OPENAPI_IP_DENIED"
        );

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("openapi.ping"),
                List.of("203.0.113.0/24"),
                1
        );
        repository.rateCount = 1;
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-rate-limit1", null), new OpenApiAttempt()),
                "OPENAPI_RATE_LIMITED"
        );
    }

    @Test
    void disabledApplicationAndUnavailableSecretAreIndistinguishableFromCredentials() {
        repository.bundle = bundle(
                OpenApiApplication.Status.DISABLED,
                OpenApiCredential.Status.ACTIVE,
                Set.of("openapi.ping"),
                List.of("203.0.113.0/24"),
                10
        );
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-disabled-001", null), new OpenApiAttempt()),
                "OPENAPI_APP_UNAVAILABLE"
        );

        repository.bundle = bundle(
                OpenApiApplication.Status.ACTIVE,
                OpenApiCredential.Status.ACTIVE,
                Set.of("openapi.ping"),
                List.of("203.0.113.0/24"),
                10
        );
        authenticator = new OpenApiAuthenticator(
                repository,
                ignored -> Optional.empty(),
                (systemId, tenantId, memberId) -> principal,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        assertCode(
                () -> authenticator.authenticate(
                        request("nonce-secret-miss", null), new OpenApiAttempt()),
                "OPENAPI_CREDENTIAL_UNAVAILABLE"
        );
    }

    private OpenApiAuthenticator.Request request(String nonce, String forcedSignature) {
        return requestAt(nonce, NOW, forcedSignature);
    }

    private OpenApiAuthenticator.Request requestAt(
            String nonce,
            Instant timestamp,
            String forcedSignature
    ) {
        var timestampValue = Long.toString(timestamp.getEpochSecond());
        var idempotencyKey = "ping-operation-1";
        var body = new byte[0];
        var canonical = OpenApiCanonicalRequest.canonical(
                "GET",
                "/openapi/v1/ping",
                null,
                body,
                timestampValue,
                nonce,
                idempotencyKey
        );
        var headers = new OpenApiHeaders(
                repository.bundle.application().appKey(),
                timestampValue,
                nonce,
                forcedSignature == null
                        ? OpenApiCanonicalRequest.signature(SECRET, canonical)
                        : forcedSignature,
                idempotencyKey
        );
        return new OpenApiAuthenticator.Request(
                "GET",
                "/openapi/v1/ping",
                null,
                body,
                "203.0.113.42",
                headers,
                OpenApiRoutePolicy.resolve("GET", "/openapi/v1/ping").orElseThrow()
        );
    }

    private OpenApiAuthenticator.Request signedRequest(
            RouteCase value, String nonce) {
        var timestamp = Long.toString(NOW.getEpochSecond());
        var body = value.body().getBytes(StandardCharsets.UTF_8);
        var idempotencyKey = "operation-" + nonce;
        var canonical = OpenApiCanonicalRequest.canonical(
                value.method(), value.path(), value.query(), body,
                timestamp, nonce, idempotencyKey);
        return new OpenApiAuthenticator.Request(
                value.method(), value.path(), value.query(), body,
                "203.0.113.42",
                new OpenApiHeaders(
                        repository.bundle.application().appKey(), timestamp,
                        nonce, OpenApiCanonicalRequest.signature(SECRET, canonical),
                        idempotencyKey),
                OpenApiRoutePolicy.resolve(value.method(), value.path())
                        .orElseThrow());
    }

    private static OpenApiRepository.ApplicationBundle bundle(
            OpenApiApplication.Status applicationStatus,
            OpenApiCredential.Status credentialStatus,
            Set<String> scopes,
            List<String> allowlist,
            int rate
    ) {
        var application = new OpenApiApplication(
                10, 20, 30, 40,
                "application-key-1234567890",
                "Integration client",
                applicationStatus,
                scopes,
                allowlist,
                rate,
                3,
                NOW.minusSeconds(60),
                700,
                NOW.minusSeconds(60),
                700,
                0
        );
        var credential = new OpenApiCredential(
                50,
                10,
                3,
                "env://OPENAPI_TEST_SECRET",
                credentialStatus,
                NOW.minusSeconds(60),
                credentialStatus == OpenApiCredential.Status.REVOKED ? NOW : null,
                NOW.minusSeconds(60),
                700
        );
        return new OpenApiRepository.ApplicationBundle(application, credential);
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code)
                );
    }

    private record RouteCase(
            String method,
            String path,
            String query,
            String body,
            String scope
    ) {
    }

    private static final class InMemoryRepository implements OpenApiRepository {
        private ApplicationBundle bundle;
        private final Set<String> consumedNonces = new HashSet<>();
        private int rateCount;
        private long rateVersion;

        @Override
        public Optional<ApplicationBundle> findByAppKey(String appKey) {
            return bundle.application().appKey().equals(appKey)
                    ? Optional.of(bundle)
                    : Optional.empty();
        }

        @Override
        public Optional<ApplicationBundle> find(
                long systemId, long tenantId, long applicationId) {
            return Optional.empty();
        }

        @Override
        public List<ApplicationBundle> list(
                long systemId, long tenantId, int offset, int limit) {
            return List.of();
        }

        @Override
        public long count(long systemId, long tenantId) {
            return 0;
        }

        @Override
        public void insertApplication(OpenApiApplication application) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insertCredential(OpenApiCredential credential) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updatePolicy(OpenApiApplication application, long expectedVersion) {
            return false;
        }

        @Override
        public boolean updateStatus(
                long systemId,
                long tenantId,
                long applicationId,
                OpenApiApplication.Status status,
                long actorId,
                Instant updatedAt,
                long expectedVersion
        ) {
            return false;
        }

        @Override
        public boolean rotateCredential(
                OpenApiApplication application,
                OpenApiCredential previous,
                OpenApiCredential replacement,
                long expectedVersion
        ) {
            return false;
        }

        @Override
        public boolean consumeNonce(
                long applicationId,
                int credentialVersion,
                String nonce,
                Instant expiresAt,
                Instant createdAt
        ) {
            return consumedNonces.add(nonce);
        }

        @Override
        public OpenApiRateBucket lockRateBucket(long applicationId, Instant windowStart) {
            return new OpenApiRateBucket(applicationId, windowStart, rateCount, rateVersion);
        }

        @Override
        public boolean incrementRateBucket(
                long applicationId,
                Instant windowStart,
                int expectedCount,
                long expectedVersion
        ) {
            if (expectedCount != rateCount || expectedVersion != rateVersion) {
                return false;
            }
            rateCount++;
            rateVersion++;
            return true;
        }

        @Override
        public void insertCallLog(OpenApiCallLog callLog) {
            throw new UnsupportedOperationException();
        }
    }
}
