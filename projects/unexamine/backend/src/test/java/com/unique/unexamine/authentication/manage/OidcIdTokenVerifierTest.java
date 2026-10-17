package com.unique.unexamine.authentication.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcIdTokenVerifierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OidcIdTokenVerifier verifier = new OidcIdTokenVerifier(mapper);

    @Test
    void acceptsSignedIdentityWithMatchingIssuerAudienceNonceAndTime() throws Exception {
        KeyPair key = keyPair();
        Map<String, Object> claims = claims();
        String token = signed(key, claims);
        assertThat(verifier.hasUsableSigningKey(jwks(key))).isTrue();
        assertThat(verifier.verify(token, jwks(key), "https://identity.example.test", "client-1", "nonce-1"))
                .isEqualTo("external-1");
    }

    @Test
    void rejectsReplayedOrSubstitutedIdentityClaimsAndWeakKeys() throws Exception {
        KeyPair key = keyPair();
        String token = signed(key, claims());
        assertThatThrownBy(() -> verifier.verify(token, jwks(key), "https://identity.example.test", "client-1", "another-nonce"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verifier.verify(token, jwks(key), "https://identity.example.test", "another-client", "nonce-1"))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verifier.verify(token, jwks(keyPair()), "https://identity.example.test", "client-1", "nonce-1"))
                .isInstanceOf(DomainException.class);

        Map<String, Object> expired = claims();
        expired.put("exp", Instant.now().minusSeconds(120).getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(signed(key, expired), jwks(key),
                "https://identity.example.test", "client-1", "nonce-1")).isInstanceOf(DomainException.class);
        assertThat(verifier.hasUsableSigningKey(Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "stub"))))).isFalse();
    }

    private Map<String, Object> claims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "https://identity.example.test");
        claims.put("aud", "client-1");
        claims.put("sub", "external-1");
        claims.put("nonce", "nonce-1");
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(300).getEpochSecond());
        return claims;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Map<String, Object> jwks(KeyPair key) {
        RSAPublicKey publicKey = (RSAPublicKey) key.getPublic();
        return Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "key-1", "alg", "RS256", "use", "sig",
                "n", encoded(publicKey.getModulus()), "e", encoded(publicKey.getPublicExponent()))));
    }

    private String signed(KeyPair key, Map<String, Object> claims) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                mapper.writeValueAsBytes(Map.of("alg", "RS256", "kid", "key-1", "typ", "JWT")));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(claims));
        String message = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.getPrivate());
        signature.update(message.getBytes(StandardCharsets.US_ASCII));
        return message + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private String encoded(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                bytes[0] == 0 ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
    }
}
