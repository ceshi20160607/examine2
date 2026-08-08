package com.unique.examine.module.runtime.security;

import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveCryptoServiceTest {
    private static final SensitiveCryptoService.ValueContext CONTEXT =
            new SensitiveCryptoService.ValueContext(1, 2, 3, 4, 5, 4, 0, "SECRET");

    @Test
    void encryptsWithAadAndDecryptsByPersistedKeyVersion() {
        var service = new SensitiveCryptoService(provider("enc-v2", "hash-v2"));
        var encrypted = service.encrypt("secret-value", CONTEXT);

        assertThat(encrypted.encryptionKeyVersion()).isEqualTo("enc-v2");
        assertThat(encrypted.hashKeyVersion()).isEqualTo("hash-v2");
        assertThat(encrypted.valueHash()).hasSize(64).doesNotContain("secret-value");
        assertThat(new String(encrypted.envelope(), StandardCharsets.UTF_8)).doesNotContain("secret-value");
        assertThat(service.decrypt(encrypted.envelope(), encrypted.encryptionKeyVersion(), CONTEXT))
                .isEqualTo("secret-value");

        var wrongContext = new SensitiveCryptoService.ValueContext(1, 2, 3, 4, 6, 4, 0, "SECRET");
        assertThatThrownBy(() -> service.decrypt(
                encrypted.envelope(), encrypted.encryptionKeyVersion(), wrongContext))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SENSITIVE_VALUE_INVALID"));
    }

    @Test
    void readsOldEncryptionVersionAndComputesEachCompatibleHashVersion() {
        var oldService = new SensitiveCryptoService(provider("enc-v1", "hash-v1"));
        var old = oldService.encrypt("same-value", CONTEXT);
        var rotatedService = new SensitiveCryptoService(provider("enc-v2", "hash-v2"));

        assertThat(rotatedService.decrypt(old.envelope(), old.encryptionKeyVersion(), CONTEXT))
                .isEqualTo("same-value");
        assertThat(rotatedService.equalityHash("same-value", "hash-v1", CONTEXT))
                .isEqualTo(old.valueHash());
        assertThat(rotatedService.equalityHash("same-value", "hash-v2", CONTEXT))
                .isNotEqualTo(old.valueHash());
        var anotherRecord = new SensitiveCryptoService.ValueContext(1, 2, 3, 4, 999, 4, 0, "SECRET");
        assertThat(rotatedService.equalityHash("same-value", "hash-v1", anotherRecord))
                .isEqualTo(old.valueHash());
        assertThat(rotatedService.equalityHashes("same-value", CONTEXT.blindIndexContext()))
                .extracting(SensitiveCryptoService.EqualityHash::hashKeyVersion)
                .containsExactly("hash-v1", "hash-v2");
    }

    @Test
    void failsClosedWithoutAConfiguredKeyRing() {
        var service = new SensitiveCryptoService(Optional::empty);
        assertThat(service.available()).isFalse();
        assertThatThrownBy(() -> service.encrypt("secret", CONTEXT))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SENSITIVE_KEY_UNAVAILABLE"));
    }

    @Test
    void isCreatedBySpringWithTheKeyProviderConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SensitiveKeyProvider.class, () -> Optional::empty);
            context.register(SensitiveCryptoService.class);
            context.refresh();
            assertThat(context.getBean(SensitiveCryptoService.class)).isNotNull();
        }
    }

    private static SensitiveKeyProvider provider(String activeEncryption, String activeHash) {
        var ring = new SensitiveKeyProvider.KeyRing(
                activeEncryption,
                activeHash,
                Map.of("enc-v1", bytes(1), "enc-v2", bytes(2)),
                Map.of("hash-v1", bytes(3), "hash-v2", bytes(4)),
                List.of("hash-v1", "hash-v2")
        );
        return () -> Optional.of(ring);
    }

    private static byte[] bytes(int seed) {
        var value = new byte[32];
        java.util.Arrays.fill(value, (byte) seed);
        return value;
    }
}
