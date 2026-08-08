package com.unique.examine.plat.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PlatformSecretResolverFacade;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EnterpriseOAuthAdapter {
    private final PlatformSecretResolverFacade secrets;
    private final ObjectMapper json;
    private final HttpClient http;
    private final boolean allowLoopback;

    EnterpriseOAuthAdapter(PlatformSecretResolverFacade secrets, ObjectMapper json,
                           HttpClient http, boolean allowLoopback) {
        this.secrets = secrets;
        this.json = json;
        this.http = http;
        this.allowLoopback = allowLoopback;
    }

    EnterpriseIdentityClient.Preflight preflight(IdentityApi.Provider p) {
        var checks = new ArrayList<String>();
        try {
            IdentityNetworkPolicy.https(p.authorizationEndpoint(), "authorization", allowLoopback);
            checks.add("authorization-endpoint");
            IdentityNetworkPolicy.https(p.tokenEndpoint(), "token", allowLoopback);
            checks.add("token-endpoint");
            IdentityNetworkPolicy.https(p.directoryEndpoint(), "userinfo", allowLoopback);
            checks.add("userinfo-endpoint");
            secret(p); checks.add("secret-ref-resolved");
            if (p.protocol() == IdentityApi.Protocol.WECOM) {
                weComAccessToken(p);
                checks.add("wecom-corp-token-issued");
            }
            return new EnterpriseIdentityClient.Preflight(true, null, List.copyOf(checks));
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException failure) {
            return new EnterpriseIdentityClient.Preflight(false, failure.code, List.copyOf(checks));
        } catch (Exception failure) {
            return new EnterpriseIdentityClient.Preflight(false,
                    p.protocol().name() + "_PREFLIGHT_FAILED", List.copyOf(checks));
        }
    }

    IdentityApi.ExternalIdentity exchange(IdentityApi.Provider p, String code, String verifier) {
        try {
            return switch (p.protocol()) {
                case OAUTH2 -> identity(p, genericUserInfo(p, genericAccessToken(p, code, verifier)), defaults());
                case WECOM -> identity(p, weComUser(p, code), Map.of(
                        "externalUserId", "UserId", "email", "email", "displayName", "name",
                        "mobile", "mobile", "employeeNo", "alias", "departmentId", "department"));
                case DINGTALK -> identity(p, dingTalkUser(p, dingTalkAccessToken(p, code)), Map.of(
                        "externalUserId", "unionId", "email", "email", "displayName", "nick",
                        "mobile", "mobile", "employeeNo", "jobNumber", "departmentId", "deptIdList"));
                default -> throw failure("IDENTITY_PROTOCOL_CALLBACK_UNSUPPORTED");
            };
        } catch (OidcEnterpriseIdentityClient.IdentityTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new OidcEnterpriseIdentityClient.IdentityTransportException(
                    p.protocol().name() + "_CALLBACK_FAILED", e);
        }
    }

    private String genericAccessToken(IdentityApi.Provider p, String code, String verifier) throws Exception {
        var form = "grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(p.callbackUri()) + "&client_id=" + enc(p.clientId())
                + "&client_secret=" + enc(secret(p)) + "&code_verifier=" + enc(verifier);
        var node = postForm(IdentityNetworkPolicy.https(p.tokenEndpoint(), "token", allowLoopback), form,
                "OAUTH2_TOKEN_EXCHANGE_FAILED");
        var token = node.path("access_token").asText(null);
        var tokenType = node.path("token_type").asText("Bearer");
        if (token == null || !"Bearer".equalsIgnoreCase(tokenType)) {
            throw failure("OAUTH2_BEARER_TOKEN_MISSING");
        }
        return token;
    }

    private JsonNode genericUserInfo(IdentityApi.Provider p, String accessToken) throws Exception {
        return bearerGet(IdentityNetworkPolicy.https(p.directoryEndpoint(), "userinfo", allowLoopback),
                accessToken, "OAUTH2_USERINFO_FAILED");
    }

    private String weComAccessToken(IdentityApi.Provider p) throws Exception {
        var endpoint = IdentityNetworkPolicy.https(p.tokenEndpoint(), "token", allowLoopback);
        var separator = endpoint.getRawQuery() == null ? "?" : "&";
        var uri = URI.create(endpoint + separator + "corpid=" + enc(p.clientId())
                + "&corpsecret=" + enc(secret(p)));
        var node = get(uri, "WECOM_TOKEN_FAILED");
        requireVendorSuccess(node, "WECOM_TOKEN_FAILED");
        var token = node.path("access_token").asText(null);
        if (token == null) throw failure("WECOM_ACCESS_TOKEN_MISSING");
        return token;
    }

    private JsonNode weComUser(IdentityApi.Provider p, String code) throws Exception {
        var endpoint = IdentityNetworkPolicy.https(p.directoryEndpoint(), "userinfo", allowLoopback);
        var separator = endpoint.getRawQuery() == null ? "?" : "&";
        var uri = URI.create(endpoint + separator + "access_token=" + enc(weComAccessToken(p))
                + "&code=" + enc(code));
        var node = get(uri, "WECOM_USERINFO_FAILED");
        requireVendorSuccess(node, "WECOM_USERINFO_FAILED");
        if (node.path("UserId").asText(null) == null && node.path("OpenId").asText(null) != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("UserId", node.path("OpenId").asText());
        }
        return node;
    }

    private String dingTalkAccessToken(IdentityApi.Provider p, String code) throws Exception {
        var body = json.writeValueAsString(Map.of("clientId", p.clientId(), "clientSecret", secret(p),
                "code", code, "grantType", "authorization_code"));
        var response = http.send(HttpRequest.newBuilder(
                        IdentityNetworkPolicy.https(p.tokenEndpoint(), "token", allowLoopback))
                        .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var node = response(response, "DINGTALK_TOKEN_FAILED");
        var token = node.path("accessToken").asText(null);
        if (token == null) throw failure("DINGTALK_ACCESS_TOKEN_MISSING");
        return token;
    }

    private JsonNode dingTalkUser(IdentityApi.Provider p, String token) throws Exception {
        return bearerGet(IdentityNetworkPolicy.https(p.directoryEndpoint(), "userinfo", allowLoopback),
                token, "DINGTALK_USERINFO_FAILED");
    }

    private IdentityApi.ExternalIdentity identity(IdentityApi.Provider p, JsonNode claims,
                                                   Map<String, String> protocolDefaults) {
        var mapping = new java.util.LinkedHashMap<>(protocolDefaults);
        mapping.putAll(p.attributeMapping());
        var subject = claim(claims, mapping.get("externalUserId"));
        if (subject == null || subject.isBlank()) throw failure("IDENTITY_SUBJECT_MISSING");
        return new IdentityApi.ExternalIdentity(subject, claim(claims, mapping.get("email")),
                claim(claims, mapping.get("displayName")), claim(claims, mapping.get("mobile")),
                claim(claims, mapping.get("employeeNo")), claim(claims, mapping.get("departmentId")),
                json.convertValue(claims, new TypeReference<Map<String, Object>>() {}));
    }

    private JsonNode postForm(URI uri, String form, String error) throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response(response, error);
    }

    private JsonNode bearerGet(URI uri, String token, String error) throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .header("Authorization", "Bearer " + token).header("Accept", "application/json")
                        .GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response(response, error);
    }

    private JsonNode get(URI uri, String error) throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response(response, error);
    }

    private JsonNode response(HttpResponse<String> response, String error) throws Exception {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw failure(error);
        return json.readTree(response.body());
    }

    private String secret(IdentityApi.Provider p) {
        if (p.secretRef() == null || p.secretVersion() == null) throw failure("IDENTITY_SECRET_REF_MISSING");
        try (var value = secrets.resolve(new PlatformSecretResolverFacade.SecretRequest(p.secretRef()))
                .orElseThrow(() -> failure("IDENTITY_SECRET_UNRESOLVED"))) {
            return new String(value.copyBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void requireVendorSuccess(JsonNode node, String code) {
        if (node.has("errcode") && node.path("errcode").asInt(-1) != 0) throw failure(code);
    }

    private static String claim(JsonNode claims, String name) {
        if (name == null) return null;
        var value = claims.path(name);
        if (value.isArray()) return value.isEmpty() ? null : value.get(0).asText(null);
        return value.isMissingNode() || value.isNull() ? null : value.asText(null);
    }

    private static Map<String, String> defaults() {
        return Map.of("externalUserId", "sub", "email", "email", "displayName", "name",
                "mobile", "phone_number", "employeeNo", "employee_no", "departmentId", "department_id");
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static OidcEnterpriseIdentityClient.IdentityTransportException failure(String code) {
        return new OidcEnterpriseIdentityClient.IdentityTransportException(code);
    }
}
