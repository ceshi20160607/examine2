package com.unique.examine.module.manage.ai;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationFieldCommandSealerTest {

    @Test
    void authenticatesOpaquePlaintextAndEveryDraftBinding() {
        var sealer = new AiConfigurationFieldCommandSealer(
                keys(), new java.security.SecureRandom());
        var binding = binding("proposal-1", 31, 41, 5);
        var sealed = sealer.seal("{\"field\":\"safe\"}", binding);

        assertThat(sealed.ciphertext()).doesNotContain("field", "safe");
        assertThat(sealer.open(sealed, binding))
                .isEqualTo("{\"field\":\"safe\"}");
        assertInvalid(() -> sealer.open(
                sealed, binding("proposal-2", 31, 41, 5)));
        assertInvalid(() -> sealer.open(
                sealed, binding("proposal-1", 32, 41, 5)));
        assertInvalid(() -> sealer.open(
                sealed, binding("proposal-1", 31, 42, 5)));
        assertInvalid(() -> sealer.open(
                sealed, binding("proposal-1", 31, 41, 6)));
    }

    @Test
    void tamperAndMissingKeyFailClosed() {
        var sealer = new AiConfigurationFieldCommandSealer(
                keys(), new java.security.SecureRandom());
        var binding = binding("proposal-1", 31, 41, 5);
        var sealed = sealer.seal("{}", binding);
        var first = sealed.ciphertext().charAt(0) == 'A' ? 'B' : 'A';
        var tampered = new com.unique.examine.core.ai
                .AiConfigurationFieldFacade.SealedCommand(
                first + sealed.ciphertext().substring(1),
                sealed.encryptionKeyVersion(), sealed.commandSha256());

        assertInvalid(() -> sealer.open(tampered, binding));
        var unavailable = new AiConfigurationFieldCommandSealer(
                Optional::<SensitiveKeyProvider.KeyRing>empty,
                new java.security.SecureRandom());
        assertThatThrownBy(() -> unavailable.seal("{}", binding))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_CONFIG_FIELD_COMMAND_KEY_UNAVAILABLE");
    }

    private static SensitiveKeyProvider keys() {
        return () -> Optional.of(new SensitiveKeyProvider.KeyRing(
                "v1", "h1", Map.of("v1", "k".repeat(32).getBytes()),
                Map.of(), List.of()));
    }

    private static AiConfigurationFieldCommandSealer.Binding binding(
            String proposalId, long rootId, long moduleId, long revision) {
        return new AiConfigurationFieldCommandSealer.Binding(
                7, 11, 21, 17, proposalId,
                rootId, moduleId, "customers", revision);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_CONFIG_FIELD_COMMAND_INVALID");
    }
}
