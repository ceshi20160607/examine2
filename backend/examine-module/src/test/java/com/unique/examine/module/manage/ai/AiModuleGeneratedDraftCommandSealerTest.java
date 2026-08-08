package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModuleGeneratedDraftCommandSealerTest {

    @Test
    void exactBindingOpensAndActorProposalOrOperationChangesFail() {
        var sealer = sealer();
        var binding = binding("proposal-1",
                AiModuleGeneratedDraftFacade.Operation.CONFIG_REPORT_DRAFT);
        var sealed = sealer.seal("complete-command", binding);

        assertThat(sealer.open(sealed, binding)).isEqualTo("complete-command");
        assertInvalid(() -> sealer.open(sealed, binding(
                "proposal-2",
                AiModuleGeneratedDraftFacade.Operation.CONFIG_REPORT_DRAFT)));
        assertInvalid(() -> sealer.open(sealed, binding(
                "proposal-1", AiModuleGeneratedDraftFacade.Operation
                        .CONFIG_PRINT_TEMPLATE_DRAFT)));
    }

    @Test
    void unavailableKeyFailsClosed() {
        PlatformSecretResolverFacade missing = request -> Optional.empty();
        var sealer = new AiModuleGeneratedDraftCommandSealer(
                missing, "env://TEST", new SecureRandom());

        assertThatThrownBy(() -> sealer.seal(
                "command", binding("proposal-1",
                        AiModuleGeneratedDraftFacade.Operation
                                .CONFIG_REPORT_DRAFT)))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_MODULE_DRAFT_COMMAND_KEY_UNAVAILABLE");
    }

    static AiModuleGeneratedDraftCommandSealer sealer() {
        PlatformSecretResolverFacade secrets = request -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8(
                        "module-ai-generated-draft-test-key-material"));
        return new AiModuleGeneratedDraftCommandSealer(
                secrets, "env://TEST", new SecureRandom());
    }

    private static AiModuleGeneratedDraftCommandSealer.Binding binding(
            String proposal,
            AiModuleGeneratedDraftFacade.Operation operation) {
        return new AiModuleGeneratedDraftCommandSealer.Binding(
                7, 11, 21, 17, proposal, "session-1", "turn-1", operation);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_MODULE_DRAFT_COMMAND_INVALID");
    }
}
