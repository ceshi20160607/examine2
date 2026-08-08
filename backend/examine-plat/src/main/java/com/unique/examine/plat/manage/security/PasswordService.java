package com.unique.examine.plat.manage.security;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordService {
    private static final int MEMORY_KB = 65_536;
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 1;
    private static final int HASH_LENGTH = 32;
    private static final int SALT_LENGTH = 16;
    private static final String PARAMETERS = "m=65536,t=3,p=1";

    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordHash hash(String password) {
        var salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        var hash = derive(password, salt);
        var encoded = "$argon2id$v=19$" + PARAMETERS + "$"
                + Base64.getEncoder().withoutPadding().encodeToString(salt) + "$"
                + Base64.getEncoder().withoutPadding().encodeToString(hash);
        return new PasswordHash(encoded, "ARGON2ID", PARAMETERS);
    }

    public boolean matches(String password, String encoded) {
        try {
            var parts = encoded.split("\\$");
            if (parts.length != 6 || !"argon2id".equals(parts[1]) || !PARAMETERS.equals(parts[3])) {
                return false;
            }
            var salt = Base64.getDecoder().decode(parts[4]);
            var expected = Base64.getDecoder().decode(parts[5]);
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) {
        var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_KB)
                .withIterations(ITERATIONS)
                .withParallelism(PARALLELISM)
                .withSalt(salt)
                .build();
        var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        var result = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), result);
        return result;
    }

    public record PasswordHash(String encoded, String algorithm, String parameters) {
    }
}
