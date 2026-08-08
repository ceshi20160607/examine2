package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonDataSourceConnectionCheckerTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 30);
    private static final String SECRET_REF =
            "env://EXAMINE_DS_S10_T20_REPORTING_V2";

    @Test
    void sendsOneFixedScopedRequestAndClosesCredential() {
        var transport = new StubTransport();
        transport.response = response(200,
                "{\"rows\":[{\"name\":\"ok\",\"count\":1,"
                        + "\"enabled\":true,\"missing\":null}]}");
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "tenant-token");
        var checker = checker(
                source("https://query.example.com/check", SECRET_REF, 7),
                transport, secrets, List.of("*.example.com"));

        var result = checker.check(ACTOR, 100, 1);

        assertThat(result.reachable()).isTrue();
        assertThat(result.contractValid()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.durationMillis()).isEqualTo(12);
        assertThat(result.code()).isEqualTo("SUCCESS");
        assertThat(result.message()).isEqualTo("Connection check succeeded");
        assertThat(transport.calls).isEqualTo(1);
        assertThat(transport.request.uri().toString())
                .isEqualTo("https://query.example.com/check");
        assertThat(transport.request.timeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(new String(
                transport.request.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1,\"size\":1}");
        assertThat(transport.request.headers())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("Accept", "application/json")
                .containsEntry("Authorization", "Bearer tenant-token")
                .doesNotContainKeys("Host", "Connection", "Content-Length");
        assertThat(secrets.request.systemId()).isEqualTo(10);
        assertThat(secrets.request.tenantId()).isEqualTo(20);
        assertThat(secrets.request.reference()).isEqualTo(SECRET_REF);
        assertThatThrownBy(secrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyAllowlistDeniesBeforeSecretOrTransport() {
        var transport = new StubTransport();
        var secrets = new StubSecrets();
        var checker = checker(
                source("https://query.example.com/check", SECRET_REF, 5),
                transport, secrets, List.of());

        var result = checker.check(ACTOR, 100, 1);

        assertThat(result.code()).isEqualTo("SAFE_TARGET");
        assertThat(result.reachable()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(transport.calls).isZero();
        assertThat(secrets.calls).isZero();
    }

    @Test
    void wildcardRequiresARealLabelBoundaryAndDoesNotMatchApex() {
        assertDenied("https://example.com/check", "*.example.com");
        assertDenied("https://example.com.evil.test/check", "*.example.com");
        assertDenied("https://badexample.com/check", "*.example.com");

        var transport = new StubTransport();
        transport.response = response(200, "{\"rows\":[]}");
        var checker = checker(
                source("https://deep.query.example.com/check", null, 5),
                transport, new StubSecrets(), List.of("*.example.com"));

        assertThat(checker.check(ACTOR, 100, 1).code())
                .isEqualTo("SUCCESS");
        assertThat(transport.calls).isEqualTo(1);
    }

    @Test
    void exactHostIsCaseInsensitiveButDoesNotAuthorizeSuffixes() {
        var allowedTransport = new StubTransport();
        allowedTransport.response = response(200, "{\"rows\":[]}");
        var allowed = checker(
                source("https://API.Example.com/check", null, 5),
                allowedTransport, new StubSecrets(),
                List.of("api.example.com"));

        assertThat(allowed.check(ACTOR, 100, 1).code())
                .isEqualTo("SUCCESS");
        assertDenied("https://api.example.com.evil.test/check",
                "api.example.com");
    }

    @Test
    void rejectsCrossScopeOrMalformedSecretReferenceWithoutResolution() {
        for (var reference : List.of(
                "env://EXAMINE_DS_S10_T21_REPORTING_V2",
                "env://EXAMINE_DS_S11_T20_REPORTING_V2",
                "env://EXAMINE_DS_S10_T20_REPORTING_V0",
                "env://EXAMINE_DS_S10_T20_reporting_V2",
                "file:///secrets/tenant-token")) {
            var transport = new StubTransport();
            var secrets = new StubSecrets();
            var checker = checker(
                    source("https://api.example.com/check", reference, 5),
                    transport, secrets, List.of("api.example.com"));

            var result = checker.check(ACTOR, 100, 1);

            assertThat(result.code()).isEqualTo("SECRET_UNAVAILABLE");
            assertThat(result.message())
                    .isEqualTo("The configured credential is unavailable");
            assertThat(secrets.calls).isZero();
            assertThat(transport.calls).isZero();
        }
    }

    @Test
    void missingOrInvalidResolvedSecretIsClosedAndFailsClosed() {
        var missing = new StubSecrets();
        var missingTransport = new StubTransport();
        var missingResult = checker(
                source("https://api.example.com/check", SECRET_REF, 5),
                missingTransport, missing, List.of("api.example.com"))
                .check(ACTOR, 100, 1);
        assertThat(missingResult.code()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(missing.calls).isEqualTo(1);
        assertThat(missingTransport.calls).isZero();

        var invalid = new StubSecrets();
        invalid.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "contains whitespace");
        var invalidTransport = new StubTransport();
        var invalidResult = checker(
                source("https://api.example.com/check", SECRET_REF, 5),
                invalidTransport, invalid, List.of("api.example.com"))
                .check(ACTOR, 100, 1);
        assertThat(invalidResult.code()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(invalidTransport.calls).isZero();
        assertThatThrownBy(invalid.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapsEveryTransportFailureToFixedRedactedResultWithoutRetry() {
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.UNSAFE_TARGET,
                "SAFE_TARGET", "The configured endpoint is not permitted");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.TIMEOUT,
                "TIMEOUT", "Connection check timed out");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.TLS,
                "TLS", "TLS connection failed");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.IO,
                "IO", "Connection check failed");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.RESPONSE_TOO_LARGE,
                "RESPONSE_TOO_LARGE",
                "The endpoint response exceeded the safe limit");
    }

    @Test
    void rejectsOversizeStatusMalformedJsonAndInvalidContract() {
        var oversize = new byte[65_537];
        assertResponse(200, oversize, "RESPONSE_TOO_LARGE", false, false);
        assertResponse(503, bytes("secret response from /private"),
                "HTTP_STATUS", true, false);
        assertResponse(200, bytes("not-json"),
                "JSON_INVALID", true, false);
        assertResponse(200, bytes("{\"items\":[]}"),
                "CONTRACT_INVALID", true, false);
        assertResponse(200, bytes("{\"rows\":[{\"nested\":{}}]}"),
                "CONTRACT_INVALID", true, false);
        assertResponse(200, bytes("{\"rows\":[]}{\"rows\":[]}"),
                "JSON_INVALID", true, false);
        assertResponse(200, bytes("{\"rows\":[],\"rows\":[]}"),
                "JSON_INVALID", true, false);
    }

    @Test
    void enforcesRowFieldScalarAndDepthBounds() {
        assertResponse(200, bytes(rows(201)),
                "CONTRACT_INVALID", true, false);
        assertResponse(200, bytes(rowWithFields(51)),
                "CONTRACT_INVALID", true, false);
        assertResponse(200, bytes("{\"rows\":[{\"value\":\""
                        + "x".repeat(4_097) + "\"}]}"),
                "CONTRACT_INVALID", true, false);
        assertResponse(200, bytes("{\"rows\":[],\"extra\":"
                        + nestedArrays(9) + "}"),
                "CONTRACT_INVALID", true, false);
    }

    @Test
    void resultNeverContainsEndpointSecretBodyOrTransportDetails() {
        var marker = "do-not-disclose-7f9f";
        var transport = new StubTransport();
        transport.failure = new OutboundHttpTransport.TransportException(
                OutboundHttpTransport.TransportException.Kind.IO,
                "https://api.example.com/" + marker + " " + marker);
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(marker);
        var result = checker(
                source("https://api.example.com/check", SECRET_REF, 5),
                transport, secrets, List.of("api.example.com"))
                .check(ACTOR, 100, 1);

        assertThat(result.toString()).doesNotContain(marker)
                .doesNotContain("api.example.com")
                .doesNotContain(SECRET_REF);
        assertThat(transport.calls).isEqualTo(1);
    }

    @Test
    void exactDraftVersionAndHttpKindAreRequiredBeforeNetworkAccess() {
        var transport = new StubTransport();
        var checker = checker(
                source("https://api.example.com/check", null, 5),
                transport, new StubSecrets(), List.of("api.example.com"));

        assertThatThrownBy(() -> checker.check(ACTOR, 100, 2))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo("DATA_SOURCE_VERSION_CONFLICT"));
        assertThat(transport.calls).isZero();

        var nativeChecker = checker(nativeSource(), transport,
                new StubSecrets(), List.of("api.example.com"));
        assertThatThrownBy(() -> nativeChecker.check(ACTOR, 100, 1))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_CONNECTION_CHECK_INVALID"));
        assertThat(transport.calls).isZero();
    }

    private static void assertDenied(String endpoint, String allowedHost) {
        var transport = new StubTransport();
        var checker = checker(source(endpoint, null, 5), transport,
                new StubSecrets(), List.of(allowedHost));

        assertThat(checker.check(ACTOR, 100, 1).code())
                .isEqualTo("SAFE_TARGET");
        assertThat(transport.calls).isZero();
    }

    private static void assertTransportFailure(
            OutboundHttpTransport.TransportException.Kind kind,
            String code,
            String message
    ) {
        var marker = "sensitive-transport-detail";
        var transport = new StubTransport();
        transport.failure = new OutboundHttpTransport.TransportException(
                kind, marker);
        var checker = checker(
                source("https://api.example.com/check", null, 5),
                transport, new StubSecrets(), List.of("api.example.com"));

        var result = checker.check(ACTOR, 100, 1);

        assertThat(result.code()).isEqualTo(code);
        assertThat(result.message()).isEqualTo(message);
        assertThat(result.toString()).doesNotContain(marker);
        assertThat(result.reachable()).isFalse();
        assertThat(result.contractValid()).isFalse();
        assertThat(result.httpStatus()).isNull();
        assertThat(transport.calls).isEqualTo(1);
    }

    private static void assertResponse(
            int status,
            byte[] body,
            String code,
            boolean reachable,
            boolean contractValid
    ) {
        var transport = new StubTransport();
        transport.response = new OutboundHttpTransport.Response(
                status, body, Duration.ofMillis(9));
        var checker = checker(
                source("https://api.example.com/check", null, 5),
                transport, new StubSecrets(), List.of("api.example.com"));

        var result = checker.check(ACTOR, 100, 1);

        assertThat(result.code()).isEqualTo(code);
        assertThat(result.reachable()).isEqualTo(reachable);
        assertThat(result.contractValid()).isEqualTo(contractValid);
        assertThat(transport.calls).isEqualTo(1);
    }

    private static HttpJsonDataSourceConnectionChecker checker(
            ModuleDataSource source,
            StubTransport transport,
            StubSecrets secrets,
            List<String> allowedHosts
    ) {
        var repository = new SingleSourceRepository(source);
        var service = new DataSourceService(
                repository,
                (systemId, tenantId, moduleId) -> Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"),
                        ZoneOffset.UTC));
        var probe = new HttpJsonDataSourceProbe(
                transport, secrets, new ObjectMapper(),
                new HttpDataSourceCheckProperties(allowedHosts));
        return new HttpJsonDataSourceConnectionChecker(service, probe);
    }

    private static ModuleDataSource source(
            String endpoint,
            String secretRef,
            int timeout
    ) {
        return source(new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        endpoint, secretRef, timeout)));
    }

    private static ModuleDataSource nativeSource() {
        return source(DataSourceDraft.empty());
    }

    private static ModuleDataSource source(DataSourceDraft draft) {
        var now = Instant.parse("2026-08-05T00:00:00Z");
        return new ModuleDataSource(
                100, ACTOR.systemId(), ACTOR.tenantId(), "external_query",
                200, "External query", null, draft, 1,
                null, null, now, now, 1);
    }

    private static OutboundHttpTransport.Response response(
            int status,
            String body
    ) {
        return new OutboundHttpTransport.Response(
                status, bytes(body), Duration.ofMillis(12));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String rows(int count) {
        var value = new StringBuilder("{\"rows\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append("{}");
        }
        return value.append("]}").toString();
    }

    private static String rowWithFields(int count) {
        var value = new StringBuilder("{\"rows\":[{");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append("\"f").append(index).append("\":null");
        }
        return value.append("}]} ").toString();
    }

    private static String nestedArrays(int depth) {
        return "[".repeat(depth) + "null" + "]".repeat(depth);
    }

    private static final class StubTransport
            implements OutboundHttpTransport {
        private int calls;
        private Request request;
        private Response response;
        private TransportException failure;

        @Override
        public Response post(Request request) throws TransportException {
            calls++;
            this.request = request;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }

    private static final class StubSecrets
            implements SecretResolverFacade {
        private int calls;
        private SecretRequest request;
        private ResolvedSecret resolved;

        @Override
        public Optional<ResolvedSecret> resolve(SecretRequest request) {
            calls++;
            this.request = request;
            return Optional.ofNullable(resolved);
        }
    }

    private static final class SingleSourceRepository
            implements DataSourceRepository {
        private final ModuleDataSource source;

        private SingleSourceRepository(ModuleDataSource source) {
            this.source = source;
        }

        @Override
        public long nextDataSourceId() {
            throw unsupported();
        }

        @Override
        public long nextVersionId() {
            throw unsupported();
        }

        @Override
        public Optional<ModuleDataSource> findById(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            return source.systemId() == systemId
                    && source.tenantId() == tenantId
                    && source.id() == dataSourceId
                    ? Optional.of(source) : Optional.empty();
        }

        @Override
        public Optional<ModuleDataSource> findByCode(
                long systemId,
                long tenantId,
                String code
        ) {
            throw unsupported();
        }

        @Override
        public List<ModuleDataSource> findAll(long systemId, long tenantId) {
            throw unsupported();
        }

        @Override
        public ModuleDataSource insert(ModuleDataSource root) {
            throw unsupported();
        }

        @Override
        public ModuleDataSource saveDraft(
                ModuleDataSource expected,
                ModuleDataSource revised
        ) {
            throw unsupported();
        }

        @Override
        public DataSourceVersion publish(
                ModuleDataSource expected,
                ModuleDataSource activated,
                DataSourceVersion version
        ) {
            throw unsupported();
        }

        @Override
        public Optional<DataSourceVersion> findActiveVersion(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            throw unsupported();
        }

        @Override
        public Optional<DataSourceVersion> findVersion(
                long systemId,
                long tenantId,
                long dataSourceId,
                int versionNumber
        ) {
            throw unsupported();
        }

        @Override
        public Optional<DataSourceVersion> findVersionById(
                long systemId,
                long tenantId,
                long dataSourceId,
                long versionId
        ) {
            throw unsupported();
        }

        @Override
        public List<DataSourceVersion> findVersions(
                long systemId,
                long tenantId,
                long dataSourceId
        ) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by this test");
        }
    }
}
