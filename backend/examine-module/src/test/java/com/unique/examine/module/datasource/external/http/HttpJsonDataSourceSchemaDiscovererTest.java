package com.unique.examine.module.datasource.external.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OutboundHttpTransport;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceException;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceRepository;
import com.unique.examine.module.datasource.service.DataSourceSchemaDiscoveryUseCase;
import com.unique.examine.module.datasource.service.DataSourceService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpJsonDataSourceSchemaDiscovererTest {
    private static final DataSourceActor ACTOR =
            new DataSourceActor(10, 20, 30);
    private static final String SECRET_REF =
            "env://EXAMINE_DS_S10_T20_SCHEMA_V3";

    @Test
    void discoversSortedMetadataWithOneFixedScopedRequestAndClosesSecret() {
        var transport = new StubTransport();
        transport.response = response(200, """
                {"rows":[
                  {"name":"first","amount":1.5,"id":1,"enabled":true},
                  {"name":"second","amount":2,"id":2,
                   "enabled":false,"late":"present"}
                ]}
                """);
        var secrets = new StubSecrets();
        secrets.resolved = SecretResolverFacade.ResolvedSecret.utf8(
                "schema-token");
        var discoverer = discoverer(
                source("https://schema.example.com/discover", SECRET_REF, 8),
                transport, secrets, List.of("*.example.com"));

        var result = discoverer.discover(ACTOR, 100, 1);

        assertThat(result.reachable()).isTrue();
        assertThat(result.contractValid()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.durationMillis()).isEqualTo(17);
        assertThat(result.code()).isEqualTo("SUCCESS");
        assertThat(result.checkedDraftVersion()).isEqualTo(1);
        assertThat(result.fields()).extracting(
                        DataSourceSchemaDiscoveryUseCase.Field::sourceField)
                .containsExactly("amount", "enabled", "id", "late", "name");
        assertField(result, "amount", "DECIMAL", false, true, null,
                "amount");
        assertField(result, "enabled", "BOOLEAN", false, true, null,
                "enabled");
        assertField(result, "id", "INTEGER", false, true, null, "id");
        assertField(result, "late", "STRING", true, true, null, "late");
        assertField(result, "name", "STRING", false, true, null, "name");

        assertThat(transport.calls).isEqualTo(1);
        assertThat(transport.request.uri().toString())
                .isEqualTo("https://schema.example.com/discover");
        assertThat(transport.request.timeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(text(transport.request.body()))
                .isEqualTo("{\"page\":1,\"size\":25}");
        assertThat(transport.request.headers())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("Accept", "application/json")
                .containsEntry("Authorization", "Bearer schema-token")
                .doesNotContainKeys("Host", "Connection", "Content-Length");
        assertThat(secrets.calls).isEqualTo(1);
        assertThat(secrets.request.systemId()).isEqualTo(ACTOR.systemId());
        assertThat(secrets.request.tenantId()).isEqualTo(ACTOR.tenantId());
        assertThat(secrets.request.reference()).isEqualTo(SECRET_REF);
        assertThatThrownBy(secrets.resolved::copyBytes)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void promotesNumbersAndMarksMissingNullMixedAndNfkcCollisionsForReview() {
        var result = discover("""
                {"rows":[
                  {"amount":1,"ID":1,"id":2,"Ａ":"wide","a":"ascii",
                   "mixed":"text","nullOnly":null,"optional":1},
                  {"amount":2.5,"mixed":9,"optional":null}
                ]}
                """);

        assertThat(result.code()).isEqualTo("SCHEMA_REVIEW_REQUIRED");
        assertThat(result.contractValid()).isTrue();
        assertField(result, "amount", "DECIMAL", false, true, null,
                "amount");
        assertField(result, "optional", "INTEGER", true, true, null,
                "optional");
        assertField(result, "mixed", "MIXED", false, false,
                "FIELD_TYPE_MIXED", null);
        assertField(result, "nullOnly", "UNKNOWN", true, false,
                "FIELD_TYPE_UNKNOWN", null);
        assertField(result, "ID", "INTEGER", true, false,
                "FIELD_NAME_COLLISION", null);
        assertField(result, "id", "INTEGER", true, false,
                "FIELD_NAME_COLLISION", null);
        assertField(result, "Ａ", "STRING", true, false,
                "FIELD_NAME_COLLISION", null);
        assertField(result, "a", "STRING", true, false,
                "FIELD_NAME_COLLISION", null);
    }

    @Test
    void onlySuggestsAnExactSafeAsciiFieldCode() {
        var result = discover("""
                {"rows":[{"valid_code":"ok","bad-name":"ok","中文":"ok"}]}
                """);

        assertThat(result.code()).isEqualTo("SUCCESS");
        assertField(result, "valid_code", "STRING", false, true,
                null, "valid_code");
        assertField(result, "bad-name", "STRING", false, true,
                null, null);
        assertField(result, "中文", "STRING", false, true,
                null, null);
    }

    @Test
    void rejectsEmptyOversizedAndUnionWideSchemasWithoutPartialFields() {
        assertSchemaFailure("{\"rows\":[]}", "SCHEMA_EMPTY");
        assertSchemaFailure("{\"rows\":[{},{}]}", "SCHEMA_EMPTY");
        assertSchemaFailure(rows(26), "CONTRACT_INVALID");
        assertSchemaFailure(unionOfFiftyOneFields(), "SCHEMA_TOO_WIDE");
    }

    @Test
    void rejectsUnsafeSourceNamesWithoutEchoingThem() {
        for (var name : List.of(
                " leading", "trailing ", "bad\u200Bformat",
                "x".repeat(129))) {
            var marker = name;
            var result = discover("{\"rows\":[{\""
                    + name + "\":1}]}");

            assertThat(result.code()).isEqualTo("SCHEMA_FIELD_INVALID");
            assertThat(result.fields()).isEmpty();
            assertThat(result.message()).doesNotContain(marker);
        }
    }

    @Test
    void rejectsMalformedNestedDuplicateTrailingAndOverlongContracts() {
        assertSchemaFailure("{\"rows\":[{\"nested\":{}}]}",
                "CONTRACT_INVALID");
        assertSchemaFailure("{\"rows\":[{\"items\":[]}]}",
                "CONTRACT_INVALID");
        assertSchemaFailure("{\"rows\":[],\"rows\":[]}",
                "JSON_INVALID");
        assertSchemaFailure("{\"rows\":[{}]}{\"rows\":[{}]}",
                "JSON_INVALID");
        assertSchemaFailure("{\"rows\":[{\"value\":\""
                        + "x".repeat(4_097) + "\"}]}",
                "CONTRACT_INVALID");
    }

    @Test
    void denyAndCrossScopeSecretFailBeforeNetwork() {
        var deniedTransport = new StubTransport();
        var deniedSecrets = new StubSecrets();
        var denied = discoverer(
                source("https://schema.example.com/discover", SECRET_REF, 5),
                deniedTransport, deniedSecrets, List.of());

        var deniedResult = denied.discover(ACTOR, 100, 1);

        assertThat(deniedResult.code()).isEqualTo("SAFE_TARGET");
        assertThat(deniedTransport.calls).isZero();
        assertThat(deniedSecrets.calls).isZero();

        var crossTransport = new StubTransport();
        var crossSecrets = new StubSecrets();
        var cross = discoverer(source(
                        "https://schema.example.com/discover",
                        "env://EXAMINE_DS_S10_T21_SCHEMA_V3", 5),
                crossTransport, crossSecrets, List.of("schema.example.com"));

        var crossResult = cross.discover(ACTOR, 100, 1);

        assertThat(crossResult.code()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(crossTransport.calls).isZero();
        assertThat(crossSecrets.calls).isZero();
    }

    @Test
    void transportAndResponseFailuresAreFixedAndRedactedWithoutRetry() {
        var marker = "do-not-disclose-schema-91";
        var transport = new StubTransport();
        transport.failure = new OutboundHttpTransport.TransportException(
                OutboundHttpTransport.TransportException.Kind.IO,
                "https://schema.example.com/" + marker);
        var result = discoverer(
                source("https://schema.example.com/discover", null, 5),
                transport, new StubSecrets(), List.of("schema.example.com"))
                .discover(ACTOR, 100, 1);

        assertThat(result.code()).isEqualTo("IO");
        assertThat(result.message()).isEqualTo("Schema discovery failed");
        assertThat(result.toString()).doesNotContain(marker)
                .doesNotContain("schema.example.com");
        assertThat(transport.calls).isEqualTo(1);

        var status = discoverResponse(503,
                "{\"rows\":[{\"sample\":\"" + marker + "\"}]}");
        assertThat(status.code()).isEqualTo("HTTP_STATUS");
        assertThat(status.httpStatus()).isEqualTo(503);
        assertThat(status.fields()).isEmpty();
        assertThat(status.toString()).doesNotContain(marker);
    }

    @Test
    void successfulResultContainsMetadataButNeverSampleValuesOrRawPayload() {
        var marker = "sample-value-never-returned-4e8";
        var result = discover("{\"rows\":[{\"safeField\":\""
                + marker + "\"}]}");

        assertThat(result.code()).isEqualTo("SUCCESS");
        assertThat(result.toString()).doesNotContain(marker)
                .doesNotContain("rows")
                .doesNotContain("https://");
    }

    @Test
    void exactVersionAndHttpKindAreRequiredBeforeNetwork() {
        var transport = new StubTransport();
        var discoverer = discoverer(
                source("https://schema.example.com/discover", null, 5),
                transport, new StubSecrets(), List.of("schema.example.com"));

        assertThatThrownBy(() -> discoverer.discover(ACTOR, 100, 2))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo("DATA_SOURCE_VERSION_CONFLICT"));
        assertThat(transport.calls).isZero();

        var nativeDiscoverer = discoverer(nativeSource(), transport,
                new StubSecrets(), List.of("schema.example.com"));
        assertThatThrownBy(() -> nativeDiscoverer.discover(ACTOR, 100, 1))
                .isInstanceOfSatisfying(DataSourceException.class,
                        failure -> assertThat(failure.code()).isEqualTo(
                                "DATA_SOURCE_SCHEMA_DISCOVERY_INVALID"));
        assertThat(transport.calls).isZero();
    }

    private static void assertField(
            DataSourceSchemaDiscoveryUseCase.Result result,
            String name,
            String type,
            boolean nullable,
            boolean selectable,
            String issue,
            String suggestion
    ) {
        var field = result.fields().stream()
                .filter(value -> value.sourceField().equals(name))
                .findFirst().orElseThrow();
        assertThat(field.inferredType()).isEqualTo(type);
        assertThat(field.nullable()).isEqualTo(nullable);
        assertThat(field.selectable()).isEqualTo(selectable);
        assertThat(field.issueCode()).isEqualTo(issue);
        assertThat(field.suggestedFieldCode()).isEqualTo(suggestion);
    }

    private static void assertSchemaFailure(String body, String code) {
        var result = discover(body);
        assertThat(result.code()).isEqualTo(code);
        assertThat(result.contractValid()).isFalse();
        assertThat(result.fields()).isEmpty();
    }

    private static DataSourceSchemaDiscoveryUseCase.Result discover(
            String body
    ) {
        return discoverResponse(200, body);
    }

    private static DataSourceSchemaDiscoveryUseCase.Result discoverResponse(
            int status,
            String body
    ) {
        var transport = new StubTransport();
        transport.response = response(status, body);
        return discoverer(
                source("https://schema.example.com/discover", null, 5),
                transport, new StubSecrets(), List.of("schema.example.com"))
                .discover(ACTOR, 100, 1);
    }

    private static HttpJsonDataSourceSchemaDiscoverer discoverer(
            ModuleDataSource source,
            StubTransport transport,
            StubSecrets secrets,
            List<String> allowedHosts
    ) {
        var service = new DataSourceService(
                new SingleSourceRepository(source),
                (systemId, tenantId, moduleId) -> Optional.empty(),
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"),
                        ZoneOffset.UTC));
        var probe = new HttpJsonDataSourceProbe(
                transport, secrets, new ObjectMapper(),
                new HttpDataSourceCheckProperties(allowedHosts));
        return new HttpJsonDataSourceSchemaDiscoverer(service, probe);
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
                100, ACTOR.systemId(), ACTOR.tenantId(), "external_schema",
                200, "External schema", null, draft, 1,
                null, null, now, now, 1);
    }

    private static OutboundHttpTransport.Response response(
            int status,
            String body
    ) {
        return new OutboundHttpTransport.Response(
                status, body.getBytes(StandardCharsets.UTF_8),
                Duration.ofMillis(17));
    }

    private static String rows(int count) {
        var result = new StringBuilder("{\"rows\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append("{}");
        }
        return result.append("]}").toString();
    }

    private static String unionOfFiftyOneFields() {
        var result = new StringBuilder("{\"rows\":[{");
        for (int index = 0; index < 50; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append("\"f").append(index).append("\":")
                    .append(index);
        }
        return result.append("},{\"f50\":50}]}").toString();
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
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
