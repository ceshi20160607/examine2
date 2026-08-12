package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonPublishedDataSourceRowsReaderTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 30);
    private static final String SECRET_REF =
            "env://EXAMINE_DS_S10_T20_RUNTIME_V6";

    @Test
    void readsOneFixedRequestAndReturnsOnlyOrderedTypedProjection() {
        var marker = "published-value-4af";
        var transport = new StubTransport();
        transport.response = response("""
                {"rows":[{
                  "ignored":"not-returned",
                  "remoteAmount":12.5,
                  "remoteName":"%s",
                  "remoteEnabled":true,
                  "remoteCount":7
                }]}
                """.formatted(marker));
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "runtime-token");
        var reader = reader(
                transport, secrets, List.of("runtime.example.com"));

        var result = reader.read(ACTOR, publication(draft(SECRET_REF)));

        assertThat(result.dataSourceId()).isEqualTo(100);
        assertThat(result.dataSourceCode()).isEqualTo("published_http");
        assertThat(result.versionId()).isEqualTo(501);
        assertThat(result.versionNumber()).isEqualTo(3);
        assertThat(result.fields()).containsExactly(
                new PublishedHttpDataSourceRowsReader.Field(
                        "name", "STRING"),
                new PublishedHttpDataSourceRowsReader.Field(
                        "count", "INTEGER"),
                new PublishedHttpDataSourceRowsReader.Field(
                        "amount", "DECIMAL"),
                new PublishedHttpDataSourceRowsReader.Field(
                        "enabled", "BOOLEAN"));
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().rowIndex()).isEqualTo(1);
        assertThat(result.rows().getFirst().values().keySet())
                .containsExactly("name", "count", "amount", "enabled");
        assertThat(result.rows().getFirst().values())
                .containsEntry("name", marker)
                .containsEntry("count", new BigInteger("7"))
                .containsEntry("amount", new BigDecimal("12.5"))
                .containsEntry("enabled", true)
                .doesNotContainKey("ignored");
        assertThat(transport.calls).isEqualTo(1);
        assertThat(new String(
                transport.request.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1,\"size\":25}");
        assertThat(transport.request.uri().toString())
                .isEqualTo("https://runtime.example.com/rows");
        assertThat(transport.request.headers())
                .containsEntry("Authorization", "Bearer runtime-token");
        assertThat(result.toString()).doesNotContain(marker, SECRET_REF,
                "runtime.example.com", "remoteName");
        assertThat(result.rows().getFirst().toString())
                .doesNotContain(marker);
        assertThatThrownBy(secrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsEmptyAndTwentyFiveRowsAndPreservesNulls() {
        var empty = read("{\"rows\":[]}");
        assertThat(empty.fields()).hasSize(4);
        assertThat(empty.rows()).isEmpty();

        var full = read(validRows(25));
        assertThat(full.rows()).hasSize(25);
        assertThat(full.rows()).extracting(
                        PublishedHttpDataSourceRowsReader.Row::rowIndex)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 25)
                                .boxed().toList());

        var nullable = read("""
                {"rows":[
                  {"remoteName":null,"remoteCount":null,
                   "remoteAmount":null,"remoteEnabled":null},
                  {"remoteName":"proved","remoteCount":2,
                   "remoteAmount":2,"remoteEnabled":false}
                ]}
                """);
        assertThat(nullable.rows().getFirst().values()).containsOnly(
                org.assertj.core.data.MapEntry.entry("name", null),
                org.assertj.core.data.MapEntry.entry("count", null),
                org.assertj.core.data.MapEntry.entry("amount", null),
                org.assertj.core.data.MapEntry.entry("enabled", null));
    }

    @Test
    void wrongScopeAndNativePublicationFailBeforeSecretOrNetwork() {
        var transport = new StubTransport();
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8("token");
        var reader = reader(
                transport, secrets, List.of("runtime.example.com"));

        assertThatThrownBy(() -> reader.read(
                        new DataSourceActor(10, 21, 30),
                        publication(draft(SECRET_REF))))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_ROWS_UNAVAILABLE"));
        assertThat(transport.calls).isZero();
        assertThat(secrets.calls).isZero();

        assertThatThrownBy(() -> reader.read(
                        ACTOR, publication(DataSourceDraft.empty())))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_RUNTIME_UNAVAILABLE"));
        assertThat(transport.calls).isZero();
        assertThat(secrets.calls).isZero();
    }

    @Test
    void missingNullOnlyMixedOrOutOfRangeProjectionFailsWholePage() {
        assertSchemaStale("""
                {"rows":[
                  {"remoteName":"one","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":"two","remoteCount":2,
                   "remoteEnabled":false}
                ]}
                """);
        assertSchemaStale("""
                {"rows":[
                  {"remoteName":null,"remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":null,"remoteCount":2,
                   "remoteAmount":2,"remoteEnabled":false}
                ]}
                """);
        assertSchemaStale("""
                {"rows":[
                  {"remoteName":"one","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":9,"remoteCount":2,
                   "remoteAmount":2.5,"remoteEnabled":false}
                ]}
                """);
        assertSchemaStale("""
                {"rows":[{
                  "remoteName":"one",
                  "remoteCount":123456789012345678901234567890123456789,
                  "remoteAmount":1,"remoteEnabled":true
                }]}
                """);
        assertSchemaStale("""
                {"rows":[{
                  "remoteName":"one","remoteCount":1,
                  "remoteAmount":0.12345678901,"remoteEnabled":true
                }]}
                """);
    }

    @Test
    void decimalAcceptsIntegralAndDecimalAndExtrasDoNotLeak() {
        var result = read("""
                {"rows":[
                  {"remoteName":"one","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true,"extra":"text"},
                  {"remoteName":"two","remoteCount":2,
                   "remoteAmount":2.5,"remoteEnabled":false,"extra":9}
                ]}
                """);

        assertThat(result.rows().get(0).values().get("amount"))
                .isEqualTo(new BigDecimal("1"));
        assertThat(result.rows().get(1).values().get("amount"))
                .isEqualTo(new BigDecimal("2.5"));
        assertThat(result.rows()).allSatisfy(row ->
                assertThat(row.values()).doesNotContainKey("extra"));
    }

    @Test
    void enforcesRowUnionNameCollisionAndScalarBounds() {
        assertContractInvalid(validRows(26));
        assertContractInvalid(unionOfFiftyOneFields());
        assertContractInvalid("""
                {"rows":[{
                  "remoteName":"one","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "ID":1,"id":2
                }]}
                """);
        assertContractInvalid("""
                {"rows":[{
                  "remoteName":"one","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "Ａ":1,"a":2
                }]}
                """);
        assertContractInvalid("""
                {"rows":[{
                  "remoteName":"one","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  " bad":null
                }]}
                """);
        assertContractInvalid("""
                {"rows":[{
                  "remoteName":"one","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "nested":{}
                }]}
                """);
    }

    @Test
    void allowlistAndSecretScopeDenyWithoutResolutionOrOutbound() {
        var deniedTransport = new StubTransport();
        var deniedSecrets = new StubSecrets();
        deniedSecrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "token");
        var denied = reader(deniedTransport, deniedSecrets, List.of());

        assertThatThrownBy(() -> denied.read(
                        ACTOR, publication(draft(SECRET_REF))))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_ROWS_SAFE_TARGET"));
        assertThat(deniedTransport.calls).isZero();
        assertThat(deniedSecrets.calls).isZero();

        var crossTransport = new StubTransport();
        var crossSecrets = new StubSecrets();
        var cross = reader(crossTransport, crossSecrets,
                List.of("runtime.example.com"));
        assertThatThrownBy(() -> cross.read(ACTOR, publication(draft(
                        "env://EXAMINE_DS_S10_T21_RUNTIME_V6"))))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_ROWS_SECRET_UNAVAILABLE"));
        assertThat(crossTransport.calls).isZero();
        assertThat(crossSecrets.calls).isZero();
    }

    @Test
    void redirectTimeoutAndProviderDetailsAreRedactedWithoutRetry() {
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.UNSAFE_TARGET,
                "DATA_SOURCE_HTTP_ROWS_SAFE_TARGET");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.TIMEOUT,
                "DATA_SOURCE_HTTP_ROWS_TIMEOUT");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.TLS,
                "DATA_SOURCE_HTTP_ROWS_TLS");
        assertTransportFailure(
                OutboundHttpTransport.TransportException.Kind.IO,
                "DATA_SOURCE_HTTP_ROWS_IO");
    }

    private static void assertTransportFailure(
            OutboundHttpTransport.TransportException.Kind kind,
            String code
    ) {
        var marker = "provider-detail-never-returned";
        var transport = new StubTransport();
        transport.failure = new OutboundHttpTransport.TransportException(
                kind, "https://redirect.invalid/" + marker);
        var reader = reader(transport, new StubSecrets(),
                List.of("runtime.example.com"));

        assertThatThrownBy(() -> reader.read(
                        ACTOR, publication(draft(null))))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(code);
                            assertThat(failure.getMessage())
                                    .doesNotContain(marker,
                                            "redirect.invalid",
                                            "runtime.example.com");
                        });
        assertThat(transport.calls).isEqualTo(1);
    }

    private static PublishedHttpDataSourceRowsReader.Result read(String body) {
        var transport = new StubTransport();
        transport.response = response(body);
        var result = reader(transport, new StubSecrets(),
                List.of("runtime.example.com"))
                .read(ACTOR, publication(draft(null)));
        assertThat(transport.calls).isEqualTo(1);
        return result;
    }

    private static void assertSchemaStale(String body) {
        assertFailure(body, "DATA_SOURCE_HTTP_ROWS_SCHEMA_STALE");
    }

    private static void assertContractInvalid(String body) {
        assertFailure(body, "DATA_SOURCE_HTTP_ROWS_CONTRACT_INVALID");
    }

    private static void assertFailure(String body, String code) {
        var marker = "remoteName";
        var transport = new StubTransport();
        transport.response = response(body);
        var reader = reader(transport, new StubSecrets(),
                List.of("runtime.example.com"));

        assertThatThrownBy(() -> reader.read(
                        ACTOR, publication(draft(null))))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(code);
                            assertThat(failure.getMessage()).doesNotContain(
                                    marker, "runtime.example.com");
                        });
        assertThat(transport.calls).isEqualTo(1);
    }

    private static HttpJsonPublishedDataSourceRowsReader reader(
            StubTransport transport,
            StubSecrets secrets,
            List<String> allowedHosts
    ) {
        return new HttpJsonPublishedDataSourceRowsReader(
                new HttpJsonDataSourceProbe(
                        transport, secrets, new ObjectMapper(),
                        new HttpDataSourceCheckProperties(allowedHosts)));
    }

    private static DataSourcePublication publication(DataSourceDraft snapshot) {
        var now = Instant.parse("2026-08-05T00:00:00Z");
        var root = new ModuleDataSource(
                100, 10, 20, "published_http", 200,
                "Published HTTP", null, snapshot, 4,
                501L, 3, now, now, 4);
        var version = new DataSourceVersion(
                501, 100, 10, 20, 3, "published_http",
                200, "http_anchor", "701", "Published HTTP", null,
                snapshot, "0".repeat(64), 30, now);
        return new DataSourcePublication(root, version);
    }

    private static DataSourceDraft draft(String secretRef) {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://runtime.example.com/rows",
                        secretRef, 7),
                List.of(
                        projection("remoteName", "name",
                                DataSourceDraft.HttpJsonSourceType.STRING),
                        projection("remoteCount", "count",
                                DataSourceDraft.HttpJsonSourceType.INTEGER),
                        projection("remoteAmount", "amount",
                                DataSourceDraft.HttpJsonSourceType.DECIMAL),
                        projection("remoteEnabled", "enabled",
                                DataSourceDraft.HttpJsonSourceType.BOOLEAN)));
    }

    private static DataSourceDraft.HttpJsonFieldProjection projection(
            String sourceField,
            String fieldCode,
            DataSourceDraft.HttpJsonSourceType sourceType
    ) {
        return new DataSourceDraft.HttpJsonFieldProjection(
                sourceField, fieldCode, sourceType);
    }

    private static OutboundHttpTransport.Response response(String body) {
        return new OutboundHttpTransport.Response(
                200, body.getBytes(StandardCharsets.UTF_8),
                Duration.ofMillis(18));
    }

    private static String validRows(int count) {
        var value = new StringBuilder("{\"rows\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append("{\"remoteName\":\"row")
                    .append(index)
                    .append("\",\"remoteCount\":").append(index)
                    .append(",\"remoteAmount\":").append(index)
                    .append(index % 2 == 0 ? ".5" : "")
                    .append(",\"remoteEnabled\":true}");
        }
        return value.append("]}").toString();
    }

    private static String unionOfFiftyOneFields() {
        var value = new StringBuilder("{\"rows\":[{")
                .append("\"remoteName\":\"ok\",\"remoteCount\":1,")
                .append("\"remoteAmount\":1,\"remoteEnabled\":true");
        for (int index = 0; index < 46; index++) {
            value.append(",\"extra").append(index).append("\":null");
        }
        return value.append("},{\"remoteName\":\"ok\","
                        + "\"remoteCount\":1,\"remoteAmount\":1,"
                        + "\"remoteEnabled\":true,\"extra46\":null}]}")
                .toString();
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
        private ResolvedSecret resolved;

        @Override
        public Optional<ResolvedSecret> resolve(SecretRequest request) {
            calls++;
            return Optional.ofNullable(resolved);
        }
    }
}
