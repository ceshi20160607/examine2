package com.unique.examine.openapi.security;

import com.unique.examine.core.api.OpenApiPrincipalFacade;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.core.id.IdService;
import com.unique.examine.openapi.domain.OpenApiApplication;
import com.unique.examine.openapi.domain.OpenApiCallLog;
import com.unique.examine.openapi.domain.OpenApiCredential;
import com.unique.examine.openapi.repository.OpenApiRepository;
import com.unique.examine.openapi.service.OpenApiCallLogService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiAuthenticationFilterTest {
    private static final Instant NOW = Instant.parse("2026-08-04T02:00:00Z");

    @Test
    void recordCallLogPersistsOnlyParameterizedRouteAndSanitizedMetadata()
            throws Exception {
        var stored = new AtomicReference<OpenApiCallLog>();
        var repository = repository(stored);
        var filter = new OpenApiAuthenticationFilter(
                new AcceptingAuthenticator(repository),
                new OpenApiCallLogService(
                        repository, new IdService(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                (request, response, handler, failure) -> null);
        var rawAppKey = "record-client-key-1234567890";
        var rawSignature = "a".repeat(64);
        var rawIdempotencyKey = "create-secret-order-0001";
        var body = """
                {"state":"ACTIVE","values":{"customer_name":"Sensitive Acme","secret":"never-log-me"}}""";
        var request = new MockHttpServletRequest(
                "POST", "/openapi/v1/modules/secret_orders/records");
        request.setQueryString("debugFieldValue=never-log-query");
        request.setRemoteAddr("203.0.113.42");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-App-Key", rawAppKey);
        request.addHeader("X-Timestamp", Long.toString(NOW.getEpochSecond()));
        request.addHeader("X-Nonce", "nonce-filter-record-001");
        request.addHeader("X-Signature", rawSignature);
        request.addHeader("Idempotency-Key", rawIdempotencyKey);
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-record-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-record-1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (buffered, output) -> {
            assertThat(new String(buffered.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)).isEqualTo(body);
            ((jakarta.servlet.http.HttpServletResponse) output).setStatus(201);
        });

        var log = stored.get();
        assertThat(log).isNotNull();
        assertThat(log.routeTemplate()).isEqualTo(
                "/openapi/v1/modules/{moduleCode}/records");
        assertThat(log.requestMethod()).isEqualTo("POST");
        assertThat(log.resultCategory())
                .isEqualTo(OpenApiCallLog.ResultCategory.SUCCESS);
        assertThat(log.httpStatus()).isEqualTo(201);
        assertThat(log.applicationId()).isEqualTo(10);
        assertThat(log.credentialVersion()).isEqualTo(3);
        assertThat(log.appKeyHash())
                .isEqualTo(OpenApiCanonicalRequest.sha256(
                        rawAppKey.getBytes(StandardCharsets.UTF_8)))
                .doesNotContain(rawAppKey);
        assertThat(log.toString()).doesNotContain(
                "secret_orders", "Sensitive Acme", "customer_name",
                "never-log-me", "never-log-query", rawSignature,
                rawAppKey, rawIdempotencyKey, "nonce-filter-record-001");
    }

    @Test
    void lifecycleCallLogKeepsActionButRemovesModuleRecordAndPayload()
            throws Exception {
        var stored = new AtomicReference<OpenApiCallLog>();
        var repository = repository(stored);
        var filter = new OpenApiAuthenticationFilter(
                new AcceptingAuthenticator(repository),
                new OpenApiCallLogService(
                        repository, new IdService(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                (request, response, handler, failure) -> null);
        var rawIdempotencyKey = "restore-sensitive-order-0042";
        var body = "{\"expectedVersion\":7,\"secret\":\"never-log-action\"}";
        var request = new MockHttpServletRequest(
                "POST",
                "/openapi/v1/modules/secret_orders/records/42:restore-from-trash");
        request.setRemoteAddr("203.0.113.42");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-App-Key", "record-client-key-1234567890");
        request.addHeader("X-Timestamp", Long.toString(NOW.getEpochSecond()));
        request.addHeader("X-Nonce", "nonce-filter-action-01");
        request.addHeader("X-Signature", "b".repeat(64));
        request.addHeader("Idempotency-Key", rawIdempotencyKey);
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-action-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-action-1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response,
                (buffered, output) -> ((jakarta.servlet.http.HttpServletResponse)
                        output).setStatus(200));

        var log = stored.get();
        assertThat(log).isNotNull();
        assertThat(log.routeTemplate()).isEqualTo(
                "/openapi/v1/modules/{moduleCode}/records/"
                        + "{recordId}:restore-from-trash");
        assertThat(log.requestMethod()).isEqualTo("POST");
        assertThat(log.resultCategory())
                .isEqualTo(OpenApiCallLog.ResultCategory.SUCCESS);
        assertThat(log.toString()).doesNotContain(
                "secret_orders", "/42", "expectedVersion",
                "never-log-action", rawIdempotencyKey,
                "nonce-filter-action-01", "b".repeat(64));
    }

    @Test
    void fileUploadLogParameterizesIdsAndOmitsSignedJsonAndHeaders()
            throws Exception {
        var stored = new AtomicReference<OpenApiCallLog>();
        var repository = repository(stored);
        var filter = new OpenApiAuthenticationFilter(
                new AcceptingAuthenticator(repository),
                new OpenApiCallLogService(
                        repository, new IdService(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                (request, response, handler, failure) -> null);
        var rawIdempotencyKey = "upload-sensitive-file-0017";
        var body = """
                {"originalName":"private-invoice.pdf","mediaType":"application/pdf","contentBase64":"U0VDUkVU"}""";
        var request = new MockHttpServletRequest(
                "POST", "/openapi/v1/modules/secret_orders/records/42/files");
        request.setRemoteAddr("203.0.113.42");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-App-Key", "record-client-key-1234567890");
        request.addHeader("X-Timestamp", Long.toString(NOW.getEpochSecond()));
        request.addHeader("X-Nonce", "nonce-filter-file-001");
        request.addHeader("X-Signature", "c".repeat(64));
        request.addHeader("Idempotency-Key", rawIdempotencyKey);
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-file-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-file-1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (buffered, output) -> {
            assertThat(new String(buffered.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)).isEqualTo(body);
            ((jakarta.servlet.http.HttpServletResponse) output).setStatus(201);
        });

        var log = stored.get();
        assertThat(log).isNotNull();
        assertThat(log.routeTemplate()).isEqualTo(
                "/openapi/v1/modules/{moduleCode}/records/{recordId}/files");
        assertThat(log.requestMethod()).isEqualTo("POST");
        assertThat(log.httpStatus()).isEqualTo(201);
        assertThat(log.toString()).doesNotContain(
                "secret_orders", "/42", "private-invoice.pdf",
                "application/pdf", "contentBase64", "U0VDUkVU",
                rawIdempotencyKey, "nonce-filter-file-001", "c".repeat(64));
    }

    @Test
    void rejectsBodiesBeyondExistingOneMibAuthenticationBuffer()
            throws Exception {
        var stored = new AtomicReference<OpenApiCallLog>();
        var repository = repository(stored);
        var filter = new OpenApiAuthenticationFilter(
                new AcceptingAuthenticator(repository),
                new OpenApiCallLogService(
                        repository, new IdService(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                (request, response, handler, failure) -> null);
        var request = new MockHttpServletRequest(
                "POST", "/openapi/v1/modules/orders/records/42/files");
        request.setContent(new byte[
                BufferedOpenApiRequest.MAXIMUM_BODY_BYTES + 1]);
        request.setRemoteAddr("203.0.113.42");
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-large-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-large-1");
        var reachedApplication = new AtomicBoolean();

        filter.doFilterInternal(request, new MockHttpServletResponse(),
                (buffered, output) -> reachedApplication.set(true));

        assertThat(reachedApplication).isFalse();
        assertThat(stored.get()).isNotNull();
        assertThat(stored.get().routeTemplate()).isEqualTo(
                "/openapi/v1/modules/{moduleCode}/records/{recordId}/files");
        assertThat(stored.get().httpStatus()).isEqualTo(401);
        assertThat(stored.get().resultCategory())
                .isEqualTo(OpenApiCallLog.ResultCategory.AUTH_REJECTED);
    }

    @Test
    void flowStatusLogUsesParameterizedRouteAndOmitsHeadersAndValues()
            throws Exception {
        var stored = new AtomicReference<OpenApiCallLog>();
        var repository = repository(stored);
        var filter = new OpenApiAuthenticationFilter(
                new AcceptingAuthenticator(repository),
                new OpenApiCallLogService(
                        repository, new IdService(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                (request, response, handler, failure) -> null);
        var rawAppKey = "record-client-key-1234567890";
        var rawSignature = "d".repeat(64);
        var rawIdempotencyKey = "flow-status-sensitive-1";
        var request = new MockHttpServletRequest(
                "GET", "/openapi/v1/flow/instances/987654321");
        request.setQueryString("recordValue=Sensitive%20Acme");
        request.setRemoteAddr("203.0.113.42");
        request.addHeader("X-App-Key", rawAppKey);
        request.addHeader("X-Timestamp", Long.toString(NOW.getEpochSecond()));
        request.addHeader("X-Nonce", "nonce-filter-flow-001");
        request.addHeader("X-Signature", rawSignature);
        request.addHeader("Idempotency-Key", rawIdempotencyKey);
        request.addHeader("X-Debug-Record-Value", "Private approval value");
        request.setAttribute(WebRequestAttributes.REQUEST_ID, "request-flow-1");
        request.setAttribute(WebRequestAttributes.TRACE_ID, "trace-flow-1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response,
                (buffered, output) -> ((jakarta.servlet.http.HttpServletResponse)
                        output).setStatus(200));

        var log = stored.get();
        assertThat(log).isNotNull();
        assertThat(log.routeTemplate()).isEqualTo(
                "/openapi/v1/flow/instances/{instanceId}");
        assertThat(log.requestMethod()).isEqualTo("GET");
        assertThat(log.httpStatus()).isEqualTo(200);
        assertThat(log.resultCategory())
                .isEqualTo(OpenApiCallLog.ResultCategory.SUCCESS);
        assertThat(log.toString()).doesNotContain(
                "987654321", "Sensitive Acme", "recordValue",
                "Private approval value", rawAppKey, rawSignature,
                rawIdempotencyKey, "nonce-filter-flow-001");
    }

    @Test
    void compositionMutationLogParameterizesFieldAndOmitsPayloadCredentials()
            throws Exception {
        var stored = new AtomicReference<OpenApiCallLog>();
        var repository = repository(stored);
        var filter = new OpenApiAuthenticationFilter(
                new AcceptingAuthenticator(repository),
                new OpenApiCallLogService(
                        repository, new IdService(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                (request, response, handler, failure) -> null);
        var rawAppKey = "record-client-key-1234567890";
        var rawSignature = "e".repeat(64);
        var rawIdempotencyKey = "relation-sensitive-mutation-1";
        var body = """
                {"expectedVersion":3,"targets":[{"recordId":"91","label":"Private Acme"}]}""";
        var request = new MockHttpServletRequest(
                "POST", "/openapi/v1/modules/secret_orders/records/42/"
                        + "relations/private_customers:mutate");
        request.setRemoteAddr("203.0.113.42");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-App-Key", rawAppKey);
        request.addHeader("X-Timestamp", Long.toString(NOW.getEpochSecond()));
        request.addHeader("X-Nonce", "nonce-filter-composition-1");
        request.addHeader("X-Signature", rawSignature);
        request.addHeader("Idempotency-Key", rawIdempotencyKey);
        request.addHeader("X-Debug-Credential", "plaintext-secret-material");
        request.setAttribute(
                WebRequestAttributes.REQUEST_ID, "request-composition-1");
        request.setAttribute(
                WebRequestAttributes.TRACE_ID, "trace-composition-1");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (buffered, output) -> {
            assertThat(new String(buffered.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)).isEqualTo(body);
            ((jakarta.servlet.http.HttpServletResponse) output).setStatus(200);
        });

        var log = stored.get();
        assertThat(log).isNotNull();
        assertThat(log.routeTemplate()).isEqualTo(
                "/openapi/v1/modules/{moduleCode}/records/{recordId}/"
                        + "relations/{fieldCode}:mutate");
        assertThat(log.requestMethod()).isEqualTo("POST");
        assertThat(log.httpStatus()).isEqualTo(200);
        assertThat(log.resultCategory())
                .isEqualTo(OpenApiCallLog.ResultCategory.SUCCESS);
        assertThat(log.toString()).doesNotContain(
                "secret_orders", "/42", "private_customers",
                "expectedVersion", "targets", "Private Acme", "\"91\"",
                rawAppKey, rawSignature, rawIdempotencyKey,
                "nonce-filter-composition-1", "plaintext-secret-material");
    }

    private static OpenApiRepository repository(
            AtomicReference<OpenApiCallLog> stored) {
        return (OpenApiRepository) Proxy.newProxyInstance(
                OpenApiAuthenticationFilterTest.class.getClassLoader(),
                new Class<?>[]{OpenApiRepository.class},
                (proxy, method, arguments) -> {
                    if ("insertCallLog".equals(method.getName())) {
                        stored.set((OpenApiCallLog) arguments[0]);
                        return null;
                    }
                    var type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == Optional.class) return Optional.empty();
                    if (type == List.class) return List.of();
                    return null;
                });
    }

    private static final class AcceptingAuthenticator
            extends OpenApiAuthenticator {
        private AcceptingAuthenticator(OpenApiRepository repository) {
            super(repository, ignored -> Optional.empty(),
                    (systemId, tenantId, memberId) ->
                            new OpenApiPrincipalFacade.Principal(
                                    700, 9, true, true, true,
                                    Set.of("system.runtime.access")),
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        public OpenApiAuthentication authenticate(
                Request request, OpenApiAttempt attempt) {
            attempt.identified(10, 3);
            var application = new OpenApiApplication(
                    10, 20, 30, 40, "record-client-key-1234567890",
                    "Record client", OpenApiApplication.Status.ACTIVE,
                    Set.of("record.write"), List.of("203.0.113.0/24"),
                    100, 3, NOW.minusSeconds(60), 700,
                    NOW.minusSeconds(60), 700, 0);
            var credential = new OpenApiCredential(
                    50, 10, 3, "env://OPENAPI_RECORD_SECRET",
                    OpenApiCredential.Status.ACTIVE, NOW.minusSeconds(60),
                    null, NOW.minusSeconds(60), 700);
            return new OpenApiAuthentication(
                    application, credential,
                    new OpenApiMachineSession(
                            10, 700, 20L, 30L, 40L, 9,
                            Set.of("system.runtime.access")));
        }
    }
}
