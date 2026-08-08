package com.unique.examine.plat.manage.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {
    private final PasswordService passwordService = new PasswordService();

    @Test
    void hashesAndVerifiesWithoutEmbeddingPlaintext() {
        var password = "A-strong-test-password-42!";
        var result = passwordService.hash(password);

        assertThat(result.algorithm()).isEqualTo("ARGON2ID");
        assertThat(result.encoded()).startsWith("$argon2id$").doesNotContain(password);
        assertThat(passwordService.matches(password, result.encoded())).isTrue();
        assertThat(passwordService.matches("wrong-password", result.encoded())).isFalse();
    }
}
