package com.unique.examine.plat.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
final class OidcEnterpriseIdentityClient implements EnterpriseIdentityClient {
    private final PlatformSecretResolverFacade secrets;
    private final ObjectMapper json;
    private final HttpClient http;
    private final boolean allowLoopback;
    private final EnterpriseOAuthAdapter oauth;
    private final EnterpriseSamlAdapter saml;
    private final EnterpriseLdapAdapter ldap;

    @Autowired
    OidcEnterpriseIdentityClient(PlatformSecretResolverFacade secrets, ObjectMapper json,
                                 @Value("${examine.identity.allow-loopback:false}") boolean allowLoopback) {
        this(secrets, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), allowLoopback);
    }

    OidcEnterpriseIdentityClient(PlatformSecretResolverFacade secrets, ObjectMapper json,
                                 HttpClient http, boolean allowLoopback) {
        this.secrets = secrets;
        this.json = json;
        this.http = http;
        this.allowLoopback = allowLoopback;
        this.oauth = new EnterpriseOAuthAdapter(secrets, json, http, allowLoopback);
        this.saml = new EnterpriseSamlAdapter(secrets, http, allowLoopback);
        this.ldap = new EnterpriseLdapAdapter(secrets, allowLoopback);
    }

    @Override
    public Preflight preflight(IdentityApi.Provider p) {
        var checks = new ArrayList<String>();
        try {
            switch (p.protocol()) {
                case OIDC -> {
                    endpoint(p.issuerUri(), "issuer"); checks.add("issuer-uri-policy");
                    endpoint(p.authorizationEndpoint(), "authorization"); checks.add("authorization-endpoint");
                    endpoint(p.tokenEndpoint(), "token"); checks.add("token-endpoint");
                    var jwks = endpoint(p.jwksUri(), "jwks");
                    var document = getJson(jwks);
                    if (!document.path("keys").isArray() || document.path("keys").isEmpty()) {
                        return new Preflight(false, "OIDC_JWKS_EMPTY", List.copyOf(checks));
                    }
                    checks.add("jwks-readable");
                    requireSecret(p); checks.add("secret-ref-resolved");
                }
                case OAUTH2, WECOM, DINGTALK -> { return oauth.preflight(p); }
                case SAML2 -> { return saml.preflight(p); }
                case LDAP, AD -> { return ldap.preflight(p); }
            }
            return new Preflight(true, null, List.copyOf(checks));
        } catch (IdentityTransportException e) {
            return new Preflight(false, e.code, List.copyOf(checks));
        } catch (Exception e) {
            return new Preflight(false, "IDENTITY_PREFLIGHT_UNREACHABLE", List.copyOf(checks));
        }
    }

    @Override
    public IdentityApi.ExternalIdentity exchange(IdentityApi.Provider p, String code,
                                                  String verifier, String nonce, Instant now) {
        if (p.protocol() == IdentityApi.Protocol.SAML2) return saml.validate(p, code, nonce, now);
        if (p.protocol() == IdentityApi.Protocol.OAUTH2 || p.protocol() == IdentityApi.Protocol.WECOM
                || p.protocol() == IdentityApi.Protocol.DINGTALK) return oauth.exchange(p, code, verifier);
        if (p.protocol() != IdentityApi.Protocol.OIDC)
            throw new IdentityTransportException("IDENTITY_PROTOCOL_CALLBACK_UNSUPPORTED");
        try {
            var tokenEndpoint = endpoint(p.tokenEndpoint(), "token");
            String secret;
            try (var value = secrets.resolve(new PlatformSecretResolverFacade.SecretRequest(p.secretRef()))
                    .orElseThrow(() -> new IdentityTransportException("IDENTITY_SECRET_UNRESOLVED"))) {
                secret = new String(value.copyBytes(), StandardCharsets.UTF_8);
            }
            var form = "grant_type=authorization_code&code=" + enc(code)
                    + "&redirect_uri=" + enc(p.callbackUri()) + "&client_id=" + enc(p.clientId())
                    + "&client_secret=" + enc(secret) + "&code_verifier=" + enc(verifier);
            secret = null;
            var response = http.send(HttpRequest.newBuilder(tokenEndpoint)
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Accept", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IdentityTransportException("OIDC_TOKEN_EXCHANGE_FAILED");
            }
            var token = json.readTree(response.body()).path("id_token").asText(null);
            if (token == null) throw new IdentityTransportException("OIDC_ID_TOKEN_MISSING");
            var claims = verifyIdToken(p, token, nonce, now);
            return identity(p, claims);
        } catch (IdentityTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new IdentityTransportException("OIDC_CALLBACK_FAILED", e);
        }
    }

    @Override
    public IdentityApi.ExternalIdentity authenticateDirectory(IdentityApi.Provider provider,
                                                               String username, char[] password,
                                                               Instant now) {
        return ldap.authenticate(provider, username, password, now);
    }

    private JsonNode verifyIdToken(IdentityApi.Provider p, String token, String nonce, Instant now)
            throws Exception {
        var parts = token.split("\\.");
        if (parts.length != 3) throw new IdentityTransportException("OIDC_ID_TOKEN_MALFORMED");
        var header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
        if (!"RS256".equals(header.path("alg").asText())) {
            throw new IdentityTransportException("OIDC_ID_TOKEN_ALGORITHM_DENIED");
        }
        var kid = header.path("kid").asText(null);
        if (kid == null) throw new IdentityTransportException("OIDC_ID_TOKEN_KID_MISSING");
        var jwks = getJson(endpoint(p.jwksUri(), "jwks"));
        JsonNode key = null;
        for (var candidate : jwks.path("keys")) {
            if (kid.equals(candidate.path("kid").asText()) && "RSA".equals(candidate.path("kty").asText())) {
                key = candidate; break;
            }
        }
        if (key == null) throw new IdentityTransportException("OIDC_SIGNING_KEY_MISSING");
        var publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                unsigned(key.path("n").asText()), unsigned(key.path("e").asText())));
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
            throw new IdentityTransportException("OIDC_ID_TOKEN_SIGNATURE_INVALID");
        }
        var claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        if (!p.issuerUri().equals(claims.path("iss").asText())
                || !audience(claims.path("aud"), p.clientId())
                || !nonce.equals(claims.path("nonce").asText())
                || claims.path("exp").asLong(0) <= now.getEpochSecond()) {
            throw new IdentityTransportException("OIDC_ID_TOKEN_CLAIMS_INVALID");
        }
        return claims;
    }

    private IdentityApi.ExternalIdentity identity(IdentityApi.Provider p, JsonNode claims) {
        var mapping = p.attributeMapping();
        var subject = claim(claims, mapping.getOrDefault("externalUserId", "sub"));
        if (subject == null || subject.isBlank()) throw new IdentityTransportException("IDENTITY_SUBJECT_MISSING");
        var values = json.convertValue(claims, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        return new IdentityApi.ExternalIdentity(subject,
                claim(claims, mapping.getOrDefault("email", "email")),
                claim(claims, mapping.getOrDefault("displayName", "name")),
                claim(claims, mapping.getOrDefault("mobile", "phone_number")),
                claim(claims, mapping.getOrDefault("employeeNo", "employee_no")),
                claim(claims, mapping.getOrDefault("departmentId", "department_id")), values);
    }

    private URI endpoint(String raw, String name) {
        if (raw == null || raw.isBlank()) throw new IdentityTransportException("IDENTITY_" + name.toUpperCase(Locale.ROOT) + "_MISSING");
        try {
            var uri = URI.create(raw.strip());
            if (uri.getUserInfo() != null || uri.getHost() == null || uri.getFragment() != null) {
                throw new IdentityTransportException("IDENTITY_ENDPOINT_DENIED");
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                if (!(allowLoopback && "http".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost()))) {
                    throw new IdentityTransportException("IDENTITY_ENDPOINT_TLS_REQUIRED");
                }
            }
            if (!allowLoopback) {
                for (var address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                        throw new IdentityTransportException("IDENTITY_ENDPOINT_PRIVATE_ADDRESS");
                    }
                }
            }
            return uri;
        } catch (IdentityTransportException e) { throw e; }
        catch (Exception e) { throw new IdentityTransportException("IDENTITY_ENDPOINT_INVALID", e); }
    }

    private void directoryEndpoint(String raw) {
        if (raw == null || raw.isBlank()) throw new IdentityTransportException("IDENTITY_DIRECTORY_MISSING");
        var uri = URI.create(raw);
        if (!("ldaps".equalsIgnoreCase(uri.getScheme())
                || allowLoopback && "ldap".equalsIgnoreCase(uri.getScheme()) && isLoopback(uri.getHost()))) {
            throw new IdentityTransportException("IDENTITY_DIRECTORY_TLS_REQUIRED");
        }
    }

    private JsonNode getJson(URI uri) throws Exception {
        var response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IdentityTransportException("IDENTITY_METADATA_UNREACHABLE");
        }
        return json.readTree(response.body());
    }

    private void requireSecret(IdentityApi.Provider p) {
        if (p.secretRef() == null || p.secretVersion() == null) {
            throw new IdentityTransportException("IDENTITY_SECRET_REF_MISSING");
        }
        try (var ignored = secrets.resolve(new PlatformSecretResolverFacade.SecretRequest(p.secretRef()))
                .orElseThrow(() -> new IdentityTransportException("IDENTITY_SECRET_UNRESOLVED"))) {
            // Resolution is the preflight; content is never retained.
        }
    }

    private static boolean audience(JsonNode value, String expected) {
        if (value.isTextual()) return expected.equals(value.asText());
        if (value.isArray()) for (var item : value) if (expected.equals(item.asText())) return true;
        return false;
    }
    private static String claim(JsonNode claims, String name) {
        var value = claims.path(name); return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
    private static BigInteger unsigned(String value) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(value));
    }
    private static boolean isLoopback(String host) {
        try { return InetAddress.getByName(host).isLoopbackAddress(); }
        catch (Exception e) { return false; }
    }
    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static final class IdentityTransportException extends RuntimeException {
        final String code;
        IdentityTransportException(String code) { super(code); this.code = code; }
        IdentityTransportException(String code, Throwable cause) { super(code, cause); this.code = code; }
    }
}
