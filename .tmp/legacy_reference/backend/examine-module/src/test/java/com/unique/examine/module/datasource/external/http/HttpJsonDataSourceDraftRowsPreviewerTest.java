package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceDraftRowsPreviewUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonDataSourceDraftRowsPreviewerTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 30);
    private static final String SECRET_REF =
            "env://EXAMINE_DS_S10_T20_PREVIEW_V4";

    @Test
    void sendsOneFixedRequestAndReturnsOrderedTypedProjection() {
        var transport = new StubTransport();
        transport.response = response("""
                {"rows":[{
                  "ignored":"never-returned",
                  "remoteAmount":12,
                  "remoteName":"Alice",
                  "remoteEnabled":true,
                  "remoteCount":7
                }]}
                """);
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "preview-token");
        var previewer = previewer(
                source(draft(SECRET_REF)), transport, secrets,
                List.of("preview.example.com"));

        var result = previewer.preview(ACTOR, 100, 1);

        assertThat(result.code()).isEqualTo("SUCCESS");
        assertThat(result.message()).isEqualTo(
                "Draft rows preview succeeded");
        assertThat(result.reachable()).isTrue();
        assertThat(result.contractValid()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.durationMillis()).isEqualTo(14);
        assertThat(result.checkedDraftVersion()).isEqualTo(1);
        assertThat(result.fields()).containsExactly(
                new DataSourceDraftRowsPreviewUseCase.Field(
                        "name", "STRING"),
                new DataSourceDraftRowsPreviewUseCase.Field(
                        "count", "INTEGER"),
                new DataSourceDraftRowsPreviewUseCase.Field(
                        "amount", "DECIMAL"),
                new DataSourceDraftRowsPreviewUseCase.Field(
                        "enabled", "BOOLEAN"));
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().rowIndex()).isEqualTo(1);
        assertThat(result.rows().getFirst().values().keySet())
                .containsExactly("name", "count", "amount", "enabled");
        assertThat(result.rows().getFirst().values())
                .containsEntry("name", "Alice")
                .containsEntry("count", new BigInteger("7"))
                .containsEntry("amount", new BigDecimal("12"))
                .containsEntry("enabled", true)
                .doesNotContainKey("ignored");
        assertThat(transport.calls).isEqualTo(1);
        assertThat(transport.request.uri().toString())
                .isEqualTo("https://preview.example.com/rows");
        assertThat(new String(
                transport.request.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1,\"size\":25}");
        assertThat(transport.request.headers())
                .containsEntry("Authorization", "Bearer preview-token");
        assertThatThrownBy(secrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsZeroAndTwentyFiveRowsButRejectsTwentySix() {
        var empty = preview("{\"rows\":[]}");
        assertThat(empty.code()).isEqualTo("SUCCESS");
        assertThat(empty.rows()).isEmpty();
        assertThat(empty.fields()).hasSize(4);

        var full = preview(rows(25));
        assertThat(full.code()).isEqualTo("SUCCESS");
        assertThat(full.rows()).hasSize(25);
        assertThat(full.rows()).extracting(
                        DataSourceDraftRowsPreviewUseCase.Row::rowIndex)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 25)
                                .boxed().toList());

        var over = preview(rows(26));
        assertThat(over.code()).isEqualTo("CONTRACT_INVALID");
        assertThat(over.contractValid()).isFalse();
        assertThat(over.rows()).isEmpty();
    }

    @Test
    void exactVersionHttpKindAndLocalCheckBlockBeforeSecretAndNetwork() {
        var transport = new StubTransport();
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8("token");
        var valid = previewer(source(draft(SECRET_REF)), transport, secrets,
                List.of("preview.example.com"));

        assertThatThrownBy(() -> valid.preview(ACTOR, 100, 2))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_VERSION_CONFLICT"));
        assertThat(transport.calls).isZero();
        assertThat(secrets.calls).isZero();

        var nativePreviewer = previewer(
                source(DataSourceDraft.empty()), transport, secrets,
                List.of("preview.example.com"));
        assertThatThrownBy(() -> nativePreviewer.preview(ACTOR, 100, 1))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_HTTP_PREVIEW_INVALID"));
        assertThat(transport.calls).isZero();
        assertThat(secrets.calls).isZero();

        var blockedDraft = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://preview.example.com/rows",
                        SECRET_REF, 6),
                List.of());
        var blocked = previewer(source(blockedDraft), transport, secrets,
                List.of("preview.example.com"));
        assertThatThrownBy(() -> blocked.preview(ACTOR, 100, 1))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> {
                            assertThat(failure.code()).isEqualTo(
                                    "DATA_SOURCE_HTTP_PREVIEW_BLOCKED");
                            assertThat(failure.getMessage()).doesNotContain(
                                    SECRET_REF, "preview.example.com");
                        });
        assertThat(transport.calls).isZero();
        assertThat(secrets.calls).isZero();
    }

    @Test
    void allowlistAndSecretScopeDenyBeforeOutbound() {
        var deniedTransport = new StubTransport();
        var deniedSecrets = new StubSecrets();
        deniedSecrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "token");
        var denied = previewer(source(draft(SECRET_REF)), deniedTransport,
                deniedSecrets, List.of());

        var deniedResult = denied.preview(ACTOR, 100, 1);

        assertThat(deniedResult.code()).isEqualTo("SAFE_TARGET");
        assertThat(deniedTransport.calls).isZero();
        assertThat(deniedSecrets.calls).isZero();

        var crossTransport = new StubTransport();
        var crossSecrets = new StubSecrets();
        var cross = previewer(source(draft(
                        "env://EXAMINE_DS_S10_T21_PREVIEW_V4")),
                crossTransport, crossSecrets,
                List.of("preview.example.com"));

        var crossResult = cross.preview(ACTOR, 100, 1);

        assertThat(crossResult.code()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(crossTransport.calls).isZero();
        assertThat(crossSecrets.calls).isZero();
    }

    @Test
    void enforcesPerRowAndCrossRowWidthAndScalarContract() {
        assertContractInvalid(rowWithFields(51));
        assertContractInvalid(unionOfFiftyOneFields());
        assertContractInvalid(
                "{\"rows\":[{\"remoteName\":{},"
                        + "\"remoteCount\":1,\"remoteAmount\":1,"
                        + "\"remoteEnabled\":true}]}");
    }

    @Test
    void rejectsEveryUnsafeNameAndNormalizationCollision() {
        for (var name : List.of(
                " leading", "trailing ", "bad\u200Bformat",
                "x".repeat(129))) {
            assertContractInvalid(rowWithExtra(name));
        }
        assertContractInvalid("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "ID":1,"id":2
                }]}
                """);
        assertContractInvalid("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":1,"remoteEnabled":true,
                  "Ａ":1,"a":2
                }]}
                """);
    }

    @Test
    void missingOrWrongProjectedValueFailsWholePageButNullIsPreserved() {
        var missing = preview("""
                {"rows":[
                  {"remoteName":"first","remoteCount":1,
                   "remoteAmount":1,"remoteEnabled":true},
                  {"remoteName":"second","remoteCount":2,
                   "remoteEnabled":false}
                ]}
                """);
        assertSchemaStale(missing);

        for (var body : List.of(
                body("1", "1", "true"),
                body("\"ok\"", "1.5", "true"),
                body("\"ok\"", "1", "\"true\""))) {
            assertSchemaStale(preview(body));
        }

        var nullable = preview("""
                {"rows":[{
                  "remoteName":null,"remoteCount":null,
                  "remoteAmount":null,"remoteEnabled":null
                }]}
                """);
        assertThat(nullable.code()).isEqualTo("SUCCESS");
        assertThat(nullable.rows().getFirst().values()).containsOnly(
                org.assertj.core.data.MapEntry.entry("name", null),
                org.assertj.core.data.MapEntry.entry("count", null),
                org.assertj.core.data.MapEntry.entry("amount", null),
                org.assertj.core.data.MapEntry.entry("enabled", null));
    }

    @Test
    void rejectsNumericPrecisionScaleAndMagnitudeWithoutPartialRows() {
        assertSchemaStale(preview(body(
                "\"ok\"", "123456789012345678901234567890123456789",
                "true")));
        assertSchemaStale(preview("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":0.12345678901,"remoteEnabled":true
                }]}
                """));
        assertSchemaStale(preview("""
                {"rows":[{
                  "remoteName":"ok","remoteCount":1,
                  "remoteAmount":1e38,"remoteEnabled":true
                }]}
                """));
    }

    @Test
    void failuresAndResultsNeverDiscloseRemoteValuesOrCredentials() {
        var marker = "never-disclose-preview-4ac";
        var transport = new StubTransport();
        transport.response = response("{\"rows\":[{\"remoteName\":\""
                + marker + "\",\"remoteCount\":1,\"remoteAmount\":1,"
                + "\"remoteEnabled\":true}]}");
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(marker);
        var result = previewer(source(draft(SECRET_REF)), transport, secrets,
                List.of("preview.example.com"))
                .preview(ACTOR, 100, 1);

        assertThat(result.toString()).doesNotContain(marker)
                .doesNotContain(SECRET_REF)
                .doesNotContain("preview.example.com");
        assertThat(result.rows().getFirst().toString())
                .doesNotContain(marker);
        assertThatThrownBy(secrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);

        var failedTransport = new StubTransport();
        failedTransport.failure = new OutboundHttpTransport.TransportException(
                OutboundHttpTransport.TransportException.Kind.IO, marker);
        var failed = previewer(source(draft(null)), failedTransport,
                new StubSecrets(), List.of("preview.example.com"))
                .preview(ACTOR, 100, 1);
        assertThat(failed.code()).isEqualTo("IO");
        assertThat(failed.message()).isEqualTo("Draft rows preview failed");
        assertThat(failed.toString()).doesNotContain(marker);
        assertThat(failedTransport.calls).isEqualTo(1);
    }

    private static void assertContractInvalid(String body) {
        var result = preview(body);
        assertThat(result.code()).isEqualTo("CONTRACT_INVALID");
        assertThat(result.contractValid()).isFalse();
        assertThat(result.fields()).isEmpty();
        assertThat(result.rows()).isEmpty();
    }

    private static void assertSchemaStale(
            DataSourceDraftRowsPreviewUseCase.Result result
    ) {
        assertThat(result.code()).isEqualTo("SCHEMA_STALE");
        assertThat(result.contractValid()).isFalse();
        assertThat(result.fields()).hasSize(4);
        assertThat(result.rows()).isEmpty();
        assertThat(result.message()).isEqualTo(
                "The endpoint response no longer matches the configured projections");
    }

    private static DataSourceDraftRowsPreviewUseCase.Result preview(
            String body
    ) {
        var transport = new StubTransport();
        transport.response = response(body);
        var result = previewer(source(draft(null)), transport,
                new StubSecrets(), List.of("preview.example.com"))
                .preview(ACTOR, 100, 1);
        assertThat(transport.calls).isEqualTo(1);
        return result;
    }

    private static HttpJsonDataSourceDraftRowsPreviewer previewer(
            ModuleDataSource source,
            StubTransport transport,
            StubSecrets secrets,
            List<String> allowedHosts
    ) {
        var repository = (DataSourceRepository) Proxy.newProxyInstance(
                DataSourceRepository.class.getClassLoader(),
                new Class<?>[]{DataSourceRepository.class},
                (proxy, method, arguments) -> {
                    if ("findById".equals(method.getName())) {
                        return Optional.of(source);
                    }
                    throw new AssertionError(
                            "Unexpected repository call: " + method.getName());
                });
        var service = new DataSourceService(
                repository,
                (systemId, tenantId, moduleId) -> Optional.of(catalog()),
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"),
                        ZoneOffset.UTC));
        var probe = new HttpJsonDataSourceProbe(
                transport, secrets, new ObjectMapper(),
                new HttpDataSourceCheckProperties(allowedHosts));
        return new HttpJsonDataSourceDraftRowsPreviewer(service, probe);
    }

    private static DataSourceDraft draft(String secretRef) {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.HTTP_JSON,
                new DataSourceDraft.HttpJsonConnection(
                        "https://preview.example.com/rows",
                        secretRef, 6),
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

    private static ModuleDataSource source(DataSourceDraft draft) {
        var now = Instant.parse("2026-08-05T00:00:00Z");
        return new ModuleDataSource(
                100, ACTOR.systemId(), ACTOR.tenantId(), "external_preview",
                200, "External preview", null, draft, 1,
                null, null, now, now, 1);
    }

    private static DataSourceModuleCatalog.PublishedModule catalog() {
        return new DataSourceModuleCatalog.PublishedModule(
                200, "preview_anchor", "Preview anchor", "701",
                List.of(
                        field("name", "TEXT"),
                        field("count", "NUMBER"),
                        field("amount", "NUMBER"),
                        field("enabled", "SWITCH")));
    }

    private static DataSourceModuleCatalog.FieldCapability field(
            String code,
            String type
    ) {
        return new DataSourceModuleCatalog.FieldCapability(
                code, code, type, Set.of(), false, false, true);
    }

    private static OutboundHttpTransport.Response response(String body) {
        return new OutboundHttpTransport.Response(
                200, body.getBytes(StandardCharsets.UTF_8),
                Duration.ofMillis(14));
    }

    private static String body(
            String name,
            String count,
            String enabled
    ) {
        return "{\"rows\":[{\"remoteName\":" + name
                + ",\"remoteCount\":" + count
                + ",\"remoteAmount\":1,\"remoteEnabled\":" + enabled
                + "}]}";
    }

    private static String rows(int count) {
        var value = new StringBuilder("{\"rows\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append("{\"remoteName\":\"row")
                    .append(index)
                    .append("\",\"remoteCount\":").append(index)
                    .append(",\"remoteAmount\":").append(index)
                    .append(",\"remoteEnabled\":true}");
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
        return value.append("}]}").toString();
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

    private static String rowWithExtra(String name) {
        return "{\"rows\":[{\"remoteName\":\"ok\","
                + "\"remoteCount\":1,\"remoteAmount\":1,"
                + "\"remoteEnabled\":true,\"" + name + "\":null}]}";
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
