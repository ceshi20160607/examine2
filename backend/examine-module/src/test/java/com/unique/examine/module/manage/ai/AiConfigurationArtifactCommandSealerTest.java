package com.unique.examine.module.manage.ai;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.SensitiveKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigurationArtifactCommandSealerTest {

    @Test
    void opensOnlyWithTheExactProposalSessionTurnActorAndDraftBinding() {
        var sealer = new AiConfigurationArtifactCommandSealer(
                keys(), new java.security.SecureRandom());
        var binding = binding("proposal-1", "session-1", "turn-1", 31, 41, 5);
        var sealed = sealer.seal("canonical-command", binding);

        assertThat(sealer.open(sealed, binding)).isEqualTo("canonical-command");
        assertInvalid(() -> sealer.open(sealed,
                binding("proposal-2", "session-1", "turn-1", 31, 41, 5)));
        assertInvalid(() -> sealer.open(sealed,
                binding("proposal-1", "session-2", "turn-1", 31, 41, 5)));
        assertInvalid(() -> sealer.open(sealed,
                binding("proposal-1", "session-1", "turn-2", 31, 41, 5)));
        assertInvalid(() -> sealer.open(sealed,
                binding("proposal-1", "session-1", "turn-1", 31, 42, 5)));
        assertInvalid(() -> sealer.open(sealed,
                binding("proposal-1", "session-1", "turn-1", 31, 41, 6)));
    }

    @Test
    void rejectsTamperAndUnavailableKeys() {
        var sealer = new AiConfigurationArtifactCommandSealer(
                keys(), new java.security.SecureRandom());
        var binding = binding("proposal-1", "session-1", "turn-1", 31, 41, 5);
        var sealed = sealer.seal("canonical-command", binding);
        var first = sealed.ciphertext().charAt(0) == 'A' ? 'B' : 'A';
        var tampered = new com.unique.examine.core.ai
                .AiConfigurationArtifactFacade.SealedCommand(
                first + sealed.ciphertext().substring(1),
                sealed.encryptionKeyVersion(), sealed.commandSha256());
        assertInvalid(() -> sealer.open(tampered, binding));

        var unavailable = new AiConfigurationArtifactCommandSealer(
                Optional::<SensitiveKeyProvider.KeyRing>empty,
                new java.security.SecureRandom());
        assertThatThrownBy(() -> unavailable.seal("command", binding))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_COMMAND_KEY_UNAVAILABLE");
    }

    private static AiConfigurationArtifactCommandSealer.Binding binding(
            String proposalId, String sessionId, String turnId,
            long rootId, long moduleId, long revision) {
        return new AiConfigurationArtifactCommandSealer.Binding(
                7, 11, 21, 17, proposalId, sessionId, turnId,
                rootId, moduleId, "customers", revision);
    }

    private static SensitiveKeyProvider keys() {
        return () -> Optional.of(new SensitiveKeyProvider.KeyRing(
                "v1", "h1", Map.of("v1", "k".repeat(32).getBytes()),
                Map.of(), List.of()));
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_CONFIG_ARTIFACT_COMMAND_INVALID");
    }
}
