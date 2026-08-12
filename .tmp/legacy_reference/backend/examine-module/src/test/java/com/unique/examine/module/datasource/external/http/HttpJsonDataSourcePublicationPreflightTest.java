package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonDataSourcePublicationPreflightTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 30);
    private static final String SECRET_REF =
            "env://EXAMINE_DS_S10_T20_PUBLICATION_V5";

    @Test
    void sendsExactlyOneFixedDiscoveryRequestAndClosesSecret() {
        var transport = new StubTransport();
        transport.response = response(validRows(1));
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "publication-token");
        var preflight = preflight(
                transport, secrets, List.of("publish.example.com"));

        assertThatCode(() -> preflight.verify(
                ACTOR, draft(SECRET_REF))).doesNotThrowAnyException();

        assertThat(transport.calls).isEqualTo(1);
        assertThat(transport.request.uri().toString())
                .isEqualTo("https://publish.example.com/discovery");
        assertThat(new String(
                transport.request.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1,\"size\":25}");
        assertThat(transport.request.headers())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("Accept", "application/json")
                .containsEntry("Authorization", "Bearer publication-token");
        assertThatThrownBy(secrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requiresOneToTwentyFiveRows() {
        assertFailure("{\"rows\":[]}",
                "DATA_SOURCE_HTTP_PUBLICATION_CONTRACT_INVALID");
        assertSuccess(validRows(1));
        assertSuccess(validRows(25));
        assertFailure(validRows(26),
                "DATA_SOURCE_HTTP_PUBLICATION_CONTRACT_INVALID");
    }

    @Test
    void everyProjectionMustBePresentAndHaveNonNullEvidence() {
        assertSchemaStale("""
                {"rows":[
                  {"remoteName":"first","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":"second","remoteCount":2,
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

        assertSuccess("""
                {"rows":[
                  {"remoteName":null,"remoteCount":null,
                   "remoteAmount":null,"remoteEnabled":null},
                  {"remoteName":"proved","remoteCount":2,
                   "remoteAmount":2.5,"remoteEnabled":false}
                ]}
                """);
    }

    @Test
    void exactTypesRejectMixedOrChangedProjectedValues() {
        assertSchemaStale("""
                {"rows":[
                  {"remoteName":"ok","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":9,"remoteCount":2,
                   "remoteAmount":2,"remoteEnabled":false}
                ]}
                """);
        assertSchemaStale("""
                {"rows":[
                  {"remoteName":"ok","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":"ok","remoteCount":2.5,
                   "remoteAmount":2,"remoteEnabled":false}
                ]}
                """);
        assertSchemaStale("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":"2.5","remoteEnabled":true
                }]}
                """);
        assertSchemaStale("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":2.5,"remoteEnabled":"true"
                }]}
                """);
    }

    @Test
    void decimalAcceptsIntegralDecimalPromotionAndExtraMixedTypes() {
        assertSuccess("""
                {"rows":[
                  {"remoteName":"one","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true,"extra":"text"},
                  {"remoteName":"two","remoteCount":2,
                   "remoteAmount":2.5,"remoteEnabled":false,"extra":9}
                ]}
                """);
    }

    @Test
    void rejectsCrossRowUnionAndNormalizedNameCollisions() {
        assertFailure(unionOfFiftyOneFields(),
                "DATA_SOURCE_HTTP_PUBLICATION_CONTRACT_INVALID");
        assertFailure("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "ID":1,"id":2
                }]}
                """, "DATA_SOURCE_HTTP_PUBLICATION_CONTRACT_INVALID");
        assertFailure("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "Ａ":1,"a":2
                }]}
                """, "DATA_SOURCE_HTTP_PUBLICATION_CONTRACT_INVALID");
    }

    @Test
    void allowlistAndSecretScopeDenyWithoutResolutionOrOutbound() {
        var deniedTransport = new StubTransport();
        var deniedSecrets = new StubSecrets();
        deniedSecrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "token");
        var denied = preflight(deniedTransport, deniedSecrets, List.of());

        assertThatThrownBy(() -> denied.verify(ACTOR, draft(SECRET_REF)))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_PUBLICATION_SAFE_TARGET"));
        assertThat(deniedTransport.calls).isZero();
        assertThat(deniedSecrets.calls).isZero();

        var crossTransport = new StubTransport();
        var crossSecrets = new StubSecrets();
        var cross = preflight(crossTransport, crossSecrets,
                List.of("publish.example.com"));
        assertThatThrownBy(() -> cross.verify(ACTOR, draft(
                        "env://EXAMINE_DS_S10_T21_PUBLICATION_V5")))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_PUBLICATION_SECRET_UNAVAILABLE"));
        assertThat(crossTransport.calls).isZero();
        assertThat(crossSecrets.calls).isZero();
    }

    @Test
    void schemaAndTransportFailuresAreRedactedAndNeverRetried() {
        var marker = "never-disclose-publication-7bf";
        var staleTransport = new StubTransport();
        staleTransport.response = response("""
                {"rows":[{
                  "remoteName":"%s","remoteCount":"wrong",
                  "remoteAmount":1,"remoteEnabled":true
                }]}
                """.formatted(marker));
        var staleSecrets = new StubSecrets();
        staleSecrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                marker);
        var stale = preflight(staleTransport, staleSecrets,
                List.of("publish.example.com"));

        assertThatThrownBy(() -> stale.verify(ACTOR, draft(SECRET_REF)))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(
                                    "DATA_SOURCE_HTTP_PUBLICATION_SCHEMA_STALE");
                            assertThat(failure.getMessage())
                                    .doesNotContain(marker, SECRET_REF,
                                            "publish.example.com",
                                            "remoteCount");
                        });
        assertThat(staleTransport.calls).isEqualTo(1);
        assertThatThrownBy(staleSecrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);

        var failedTransport = new StubTransport();
        failedTransport.failure = new OutboundHttpTransport.TransportException(
                OutboundHttpTransport.TransportException.Kind.IO, marker);
        var failed = preflight(failedTransport, new StubSecrets(),
                List.of("publish.example.com"));
        assertThatThrownBy(() -> failed.verify(ACTOR, draft(null)))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(
                                    "DATA_SOURCE_HTTP_PUBLICATION_IO");
                            assertThat(failure.getMessage())
                                    .doesNotContain(marker);
                        });
        assertThat(failedTransport.calls).isEqualTo(1);
    }

    private static void assertSuccess(String body) {
        var transport = new StubTransport();
        transport.response = response(body);
        assertThatCode(() -> preflight(
                transport, new StubSecrets(),
                List.of("publish.example.com"))
                .verify(ACTOR, draft(null))).doesNotThrowAnyException();
        assertThat(transport.calls).isEqualTo(1);
    }

    private static void assertSchemaStale(String body) {
        assertFailure(body,
                "DATA_SOURCE_HTTP_PUBLICATION_SCHEMA_STALE");
    }

    private static void assertFailure(String body, String code) {
        var transport = new StubTransport();
        transport.response = response(body);
        assertThatThrownBy(() -> preflight(
                transport, new StubSecrets(),
                List.of("publish.example.com"))
                .verify(ACTOR, draft(null)))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(code);
                            assertThat(failure.getMessage())
                                    .doesNotContain("remoteName",
                                            "publish.example.com");
                        });
        assertThat(transport.calls).isEqualTo(1);
    }

    private static HttpJsonDataSourcePublicationPreflight preflight(
            StubTransport transport,
            StubSecrets secrets,
            List<String> allowedHosts
    ) {
        return new HttpJsonDataSourcePublicationPreflight(
                new HttpJsonDataSourceProbe(
                        transport, secrets, new ObjectMapper(),
                        new HttpDataSourceCheckProperties(allowedHosts)));
    }

    private static DataSourceDraft draft(String secretRef) {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://publish.example.com/discovery",
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
                Duration.ofMillis(16));
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
