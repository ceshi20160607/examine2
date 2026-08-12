package com.unique.examine.plat.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class EnterpriseOAuthAdapterTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> requests = new ConcurrentHashMap<>();
    private HttpServer server;
    private String base;
    private EnterpriseOAuthAdapter adapter;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 8);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        context("/oauth-token", Map.of("access_token", "oauth-access", "token_type", "Bearer"));
        context("/oauth-user", Map.of("sub", "oauth-42", "email", "oauth@example.test",
                "name", "OAuth User", "employee_no", "E-42", "department_id", "D-1"));
        context("/wecom-token", Map.of("errcode", 0, "access_token", "wecom-access"));
        context("/wecom-user", Map.of("errcode", 0, "UserId", "wecom-42", "name", "WeCom User",
                "email", "wecom@example.test", "department", List.of(7L)));
        context("/ding-token", Map.of("accessToken", "ding-access", "expireIn", 7200));
        context("/ding-user", Map.of("unionId", "ding-42", "nick", "Ding User",
                "email", "ding@example.test", "jobNumber", "JOB-7", "deptIdList", List.of(8L)));
        server.start();
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8("provider-secret"));
        adapter = new EnterpriseOAuthAdapter(secrets, json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), true);
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void executesStandardAuthorizationCodeAndBearerUserInfo() {
        var provider = provider(IdentityApi.Protocol.OAUTH2, "/oauth-token", "/oauth-user");
        var identity = adapter.exchange(provider, "oauth-code", "pkce-verifier");

        assertThat(identity.subject()).isEqualTo("oauth-42");
        assertThat(identity.employeeNo()).isEqualTo("E-42");
        assertThat(requests.get("/oauth-token")).contains("code=oauth-code", "code_verifier=pkce-verifier",
                "client_secret=provider-secret");
        assertThat(requests.get("/oauth-user-auth")).isEqualTo("Bearer oauth-access");
    }

    @Test
    void executesWeComCorpTokenAndCodeUserMapping() {
        var provider = provider(IdentityApi.Protocol.WECOM, "/wecom-token", "/wecom-user");
        var preflight = adapter.preflight(provider);
        var identity = adapter.exchange(provider, "wecom-code", "unused");

        assertThat(preflight.successful()).isTrue();
        assertThat(preflight.checks()).contains("wecom-corp-token-issued");
        assertThat(identity.subject()).isEqualTo("wecom-42");
        assertThat(identity.departmentId()).isEqualTo("7");
        assertThat(requests.get("/wecom-token-query")).contains("corpid=client-id", "corpsecret=provider-secret");
        assertThat(requests.get("/wecom-user-query")).contains("access_token=wecom-access", "code=wecom-code");
    }

    @Test
    void executesDingTalkJsonTokenAndBearerProfileMapping() {
        var provider = provider(IdentityApi.Protocol.DINGTALK, "/ding-token", "/ding-user");
        var identity = adapter.exchange(provider, "ding-code", "unused");

        assertThat(identity.subject()).isEqualTo("ding-42");
        assertThat(identity.employeeNo()).isEqualTo("JOB-7");
        assertThat(requests.get("/ding-token")).contains("\"clientId\":\"client-id\"",
                "\"clientSecret\":\"provider-secret\"", "\"code\":\"ding-code\"");
        assertThat(requests.get("/ding-user-auth")).isEqualTo("Bearer ding-access");
    }

    private void context(String path, Map<String, ?> response) {
        server.createContext(path, exchange -> respond(exchange, response));
    }

    private void respond(HttpExchange exchange, Map<String, ?> response) throws java.io.IOException {
        requests.put(exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requests.put(exchange.getRequestURI().getPath() + "-query",
                String.valueOf(exchange.getRequestURI().getRawQuery()));
        var authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null) requests.put(exchange.getRequestURI().getPath() + "-auth", authorization);
        var body = json.writeValueAsBytes(response);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private IdentityApi.Provider provider(IdentityApi.Protocol protocol, String token, String user) {
        return new IdentityApi.Provider(1, protocol.name().toLowerCase(), protocol.name(), protocol,
                null, base + "/authorize", base + token, null, base + user, "client-id",
                "env://PROVIDER_TEST", "v1", base + "/callback", "profile email", List.of(), Map.of(),
                true, false, null, null, IdentityApi.MfaPolicy.OPTIONAL, IdentityApi.Status.PUBLISHED,
                "PASSED", 0L, null, null, null, 0);
    }
}
