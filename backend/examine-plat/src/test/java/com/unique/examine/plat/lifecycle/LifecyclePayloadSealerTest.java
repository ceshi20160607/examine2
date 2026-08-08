package com.unique.examine.plat.lifecycle;

import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LifecyclePayloadSealerTest {
    private static final String KEY_REF = "env://TENANT_BACKUP_TEST_KEY";

    @Test
    void roundTripsAuthenticatedPayloadWithoutPersistingPlaintext() {
        var sealer = sealer();
        var plaintext = "sensitive tenant row value".getBytes(StandardCharsets.UTF_8);
        var binding = new LifecyclePayloadSealer.Binding(10, 20, 30, "a".repeat(64));

        var sealed = sealer.seal(plaintext, binding);
        var opened = sealer.open(sealed, binding);

        assertThat(opened).isEqualTo(plaintext);
        assertThat(new String(sealed.ciphertext(), StandardCharsets.ISO_8859_1))
                .doesNotContain("sensitive", "tenant row");
        assertThat(sealed.keyReference()).isEqualTo(KEY_REF);
        assertThat(sealed.keyVersion()).matches("[0-9a-f]{16}");
        assertThat(sealed.plaintextSha256()).matches("[0-9a-f]{64}");
        assertThat(sealed.ciphertextSha256()).matches("[0-9a-f]{64}");
        Arrays.fill(opened, (byte) 0);
        Arrays.fill(plaintext, (byte) 0);
    }

    @Test
    void rejectsCiphertextBindingChecksumAndKeyVersionTampering() {
        var sealer = sealer();
        var binding = new LifecyclePayloadSealer.Binding(10, 20, 30, "b".repeat(64));
        var sealed = sealer.seal("payload".getBytes(StandardCharsets.UTF_8), binding);
        var changed = sealed.ciphertext().clone();
        changed[changed.length - 1] ^= 1;

        assertCode(() -> sealer.open(new LifecyclePayloadSealer.SealedPayload(
                changed, sealed.keyReference(), sealed.keyVersion(), sealed.plaintextSha256(),
                LifecyclePayloadSealer.sha256(changed)), binding));
        assertCode(() -> sealer.open(sealed,
                new LifecyclePayloadSealer.Binding(10, 21, 30, "b".repeat(64))));
        assertCode(() -> sealer.open(new LifecyclePayloadSealer.SealedPayload(
                sealed.ciphertext(), sealed.keyReference(), "f".repeat(16), sealed.plaintextSha256(),
                sealed.ciphertextSha256()), binding));
        assertCode(() -> sealer.open(new LifecyclePayloadSealer.SealedPayload(
                sealed.ciphertext(), sealed.keyReference(), sealed.keyVersion(), "f".repeat(64),
                sealed.ciphertextSha256()), binding));
    }

    private static LifecyclePayloadSealer sealer() {
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8("cycle115-lifecycle-test-key-material"));
        return new LifecyclePayloadSealer(secrets, KEY_REF, new SecureRandom());
    }

    private static void assertCode(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.code()).isEqualTo("TENANT_BACKUP_ENVELOPE_INVALID"));
    }
}
