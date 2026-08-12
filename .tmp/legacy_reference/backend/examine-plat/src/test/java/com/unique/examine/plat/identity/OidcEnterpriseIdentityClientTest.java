package com.unique.examine.plat.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcEnterpriseIdentityClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private KeyPair keys;
    private String issuer;
    private final AtomicReference<String> tokenForm = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8);
        issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/jwks", exchange -> {
            var key = (RSAPublicKey) keys.getPublic();
            var body = json.writeValueAsBytes(Map.of("keys", List.of(Map.of(
                    "kty", "RSA", "kid", "test-key", "alg", "RS256",
                    "n", unsigned(key.getModulus().toByteArray()),
                    "e", unsigned(key.getPublicExponent().toByteArray())))));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/token", exchange -> {
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = json.writeValueAsBytes(Map.of("id_token", token("expected-nonce")));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void preflightsSecretAndJwksThenExecutesPkceCodeExchangeAndVerifiesIdToken() {
        var client = client(true);
        var provider = provider();

        var preflight = client.preflight(provider);
        assertThat(preflight.successful()).isTrue();
        assertThat(preflight.checks()).contains("jwks-readable", "secret-ref-resolved");

        var identity = client.exchange(provider, "authorization-code", "pkce-verifier",
                "expected-nonce", Instant.parse("2030-01-01T00:00:00Z"));
        assertThat(identity.subject()).isEqualTo("external-42");
        assertThat(identity.email()).isEqualTo("person@example.test");
        assertThat(identity.departmentId()).isEqualTo("department-7");
        assertThat(tokenForm.get()).contains("code=authorization-code", "code_verifier=pkce-verifier",
                "client_secret=test-client-secret");
    }

    @Test
    void rejectsNonceMismatchAndNonTlsLoopbackOutsideExplicitTestMode() {
        assertThatThrownBy(() -> client(true).exchange(provider(), "code", "verifier",
                "wrong-nonce", Instant.parse("2030-01-01T00:00:00Z")))
                .isInstanceOf(OidcEnterpriseIdentityClient.IdentityTransportException.class)
                .hasMessage("OIDC_ID_TOKEN_CLAIMS_INVALID");
        assertThat(client(false).preflight(provider()).successful()).isFalse();
        assertThat(client(false).preflight(provider()).failureCode()).isEqualTo("IDENTITY_ENDPOINT_TLS_REQUIRED");
    }

    private OidcEnterpriseIdentityClient client(boolean loopback) {
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8("test-client-secret"));
        return new OidcEnterpriseIdentityClient(secrets, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), loopback);
    }

    private IdentityApi.Provider provider() {
        return new IdentityApi.Provider(1, "corp-oidc", "Corporate OIDC", IdentityApi.Protocol.OIDC,
                issuer, issuer + "/authorize", issuer + "/token", issuer + "/jwks", null,
                "client-42", "env://OIDC_TEST_SECRET", "v1", issuer + "/callback",
                "openid profile email", List.of("example.test"), Map.of("departmentId", "department"),
                true, false, null, null, IdentityApi.MfaPolicy.REQUIRED, IdentityApi.Status.PUBLISHED,
                "PASSED", 0L, null, Instant.parse("2029-12-01T00:00:00Z"),
                Instant.parse("2029-12-01T00:00:00Z"), 0);
    }

    private String token(String nonce) {
        try {
            var header = b64(json.writeValueAsBytes(Map.of("alg", "RS256", "kid", "test-key", "typ", "JWT")));
            var claims = b64(json.writeValueAsBytes(Map.of(
                    "iss", issuer, "aud", "client-42", "sub", "external-42", "nonce", nonce,
                    "exp", Instant.parse("2030-01-01T00:10:00Z").getEpochSecond(),
                    "email", "person@example.test", "name", "Example Person", "department", "department-7")));
            var signingInput = header + "." + claims;
            var signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keys.getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + b64(signature.sign());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String unsigned(byte[] value) {
        int start = value.length > 1 && value[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOfRange(value, start, value.length));
    }
    private static String b64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
}
