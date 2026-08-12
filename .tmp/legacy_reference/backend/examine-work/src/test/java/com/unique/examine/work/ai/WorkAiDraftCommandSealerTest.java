package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkAiDraftCommandSealerTest {

    @Test
    void opensOnlyWithExactActorProposalSessionTurnAndOperationBinding() {
        var sealer = sealer();
        var binding = binding("proposal-1", "session-1", "turn-1",
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT);
        var sealed = sealer.seal("complete-command", binding);

        assertThat(sealer.open(sealed, binding)).isEqualTo("complete-command");
        assertInvalid(() -> sealer.open(sealed, binding(
                "proposal-2", "session-1", "turn-1",
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT)));
        assertInvalid(() -> sealer.open(sealed, binding(
                "proposal-1", "session-2", "turn-1",
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT)));
        assertInvalid(() -> sealer.open(sealed, binding(
                "proposal-1", "session-1", "turn-2",
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT)));
        assertInvalid(() -> sealer.open(sealed, binding(
                "proposal-1", "session-1", "turn-1",
                AiWorkDraftFacade.Operation.WORK_DAILY_REPORT_DRAFT)));
    }

    @Test
    void tamperAndUnavailableKeyFailClosed() {
        var sealer = sealer();
        var binding = binding("proposal-1", "session-1", "turn-1",
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT);
        var sealed = sealer.seal("complete-command", binding);
        var first = sealed.ciphertext().charAt(0) == 'A' ? 'B' : 'A';
        var tampered = new AiWorkDraftFacade.SealedCommand(
                first + sealed.ciphertext().substring(1),
                sealed.encryptionKeyVersion(), sealed.commandSha256());
        assertInvalid(() -> sealer.open(tampered, binding));

        PlatformSecretResolverFacade missing = request -> Optional.empty();
        var unavailable = new WorkAiDraftCommandSealer(
                missing, "env://TEST", new SecureRandom());
        assertThatThrownBy(() -> unavailable.seal("command", binding))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_WORK_DRAFT_COMMAND_KEY_UNAVAILABLE");
    }

    static WorkAiDraftCommandSealer sealer() {
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8(
                        "work-ai-draft-owner-test-key-material"));
        return new WorkAiDraftCommandSealer(
                secrets, "env://TEST", new SecureRandom());
    }

    static WorkAiDraftCommandSealer.Binding binding(
            String proposal, String session, String turn,
            AiWorkDraftFacade.Operation operation
    ) {
        return new WorkAiDraftCommandSealer.Binding(
                7, 11, 21, 17, proposal, session, turn, operation);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_WORK_DRAFT_COMMAND_INVALID");
    }
}
