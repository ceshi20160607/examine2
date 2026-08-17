package com.unique.unexamine.authentication.manage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pbkdf2PasswordHasherTest {
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    void hashesWithRandomSaltAndVerifiesWithoutStoringPlaintext() {
        String first = hasher.hash("123123aa".toCharArray());
        String second = hasher.hash("123123aa".toCharArray());

        assertThat(first).startsWith("pbkdf2-sha256$").doesNotContain("123123aa");
        assertThat(second).isNotEqualTo(first);
        assertThat(hasher.matches("123123aa".toCharArray(), first)).isTrue();
        assertThat(hasher.matches("wrong-password".toCharArray(), first)).isFalse();
        assertThat(hasher.matches("123123aa".toCharArray(), "not-a-valid-hash")).isFalse();
    }
}
