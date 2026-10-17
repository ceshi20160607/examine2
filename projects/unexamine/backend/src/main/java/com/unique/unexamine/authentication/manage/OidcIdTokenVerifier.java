package com.unique.unexamine.authentication.manage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** OIDC 登录以经 JWKS 验签的 ID Token 为身份依据，拒绝仅靠 userinfo 声称的外部身份。 */
@Component
public class OidcIdTokenVerifier {
    private static final long CLOCK_SKEW_SECONDS = 60;
    private final ObjectMapper objectMapper;

    public OidcIdTokenVerifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String verify(String token, Map<?, ?> jwks, String issuer, String clientId, String nonce) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) throw invalid();
            Map<String, Object> header = readPart(parts[0]);
            Map<String, Object> claims = readPart(parts[1]);
            if (!"RS256".equals(header.get("alg")) || !(header.get("kid") instanceof String kid)) throw invalid();
            Map<?, ?> key = signingKeys(jwks).stream()
                    .filter(value -> kid.equals(value.get("kid")) && usableKey(value)).findFirst().orElseThrow(this::invalid);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey(key));
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) throw invalid();

            long now = Instant.now().getEpochSecond();
            if (!issuer.equals(claims.get("iss")) || !audienceMatches(claims.get("aud"), clientId)
                    || !nonce.equals(claims.get("nonce")) || !(claims.get("exp") instanceof Number exp)
                    || exp.longValue() <= now - CLOCK_SKEW_SECONDS
                    || !(claims.get("iat") instanceof Number issued)
                    || issued.longValue() > now + CLOCK_SKEW_SECONDS
                    || issued.longValue() < now - 86400) throw invalid();
            if (claims.get("aud") instanceof Collection<?> audience && audience.size() > 1
                    && !clientId.equals(claims.get("azp"))) throw invalid();
            Object subject = claims.get("sub");
            if (!(subject instanceof String value) || value.isBlank()) throw invalid();
            return value;
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    /** 发布预检必须确认至少一把可用于当前 RS256 契约的真实公钥。 */
    public boolean hasUsableSigningKey(Map<?, ?> jwks) {
        return signingKeys(jwks).stream().anyMatch(this::usableKey);
    }

    private Map<String, Object> readPart(String part) throws Exception {
        return objectMapper.readValue(Base64.getUrlDecoder().decode(part), new TypeReference<>() {});
    }

    private List<Map<?, ?>> signingKeys(Map<?, ?> jwks) {
        Object keys = jwks == null ? null : jwks.get("keys");
        if (!(keys instanceof Collection<?> collection)) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object value : collection) {
            if (value instanceof Map<?, ?> key) result.add(key);
        }
        return result;
    }

    private boolean usableKey(Map<?, ?> key) {
        if (!"RSA".equals(key.get("kty")) || !(key.get("kid") instanceof String kid) || kid.isBlank()
                || key.get("alg") != null && !"RS256".equals(key.get("alg"))
                || key.get("use") != null && !"sig".equals(key.get("use"))) return false;
        try {
            return publicKey(key).getModulus().bitLength() >= 2048;
        } catch (Exception exception) {
            return false;
        }
    }

    private RSAPublicKey publicKey(Map<?, ?> key) throws Exception {
        if (!(key.get("n") instanceof String modulus) || !(key.get("e") instanceof String exponent)) throw invalid();
        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    private boolean audienceMatches(Object audience, String clientId) {
        if (audience instanceof String value) return clientId.equals(value);
        return audience instanceof Collection<?> values && values.contains(clientId);
    }

    private DomainException invalid() {
        return new DomainException("SSO_ID_TOKEN_INVALID", "身份源身份令牌校验失败，请重新发起登录", HttpStatus.UNAUTHORIZED);
    }
}
