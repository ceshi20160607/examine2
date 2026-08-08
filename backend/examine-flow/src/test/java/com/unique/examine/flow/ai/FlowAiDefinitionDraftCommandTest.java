package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import com.unique.examine.core.api.SecretResolverFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowAiDefinitionDraftCommandTest {
    private static final Instant EXPIRES =
            Instant.parse("2026-08-04T08:15:00Z");

    @Test
    void canonicalRoundTripBindsFullIdentityPolicyAndNarrative() {
        var codec = new FlowAiDefinitionDraftCommandCodec();
        var command = codec.command(request(), EXPIRES);
        var decoded = codec.decode(codec.encode(command));

        assertThat(decoded).isEqualTo(command);
        assertThat(decoded.draft().name()).isEqualTo("Expense approval");
        assertThat(decoded.effectivePermissions())
                .containsExactly("flow.definition.manage");
        assertThat(decoded.policyVersionId()).isEqualTo("51");
        assertThat(decoded.providerId()).isEqualTo("61");
        assertThat(decoded.payloadHash()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void duplicateUnknownAndChangedPayloadFailClosed() {
        var codec = new FlowAiDefinitionDraftCommandCodec();
        var encoded = codec.encode(codec.command(request(), EXPIRES));
        assertInvalid(() -> codec.decode(encoded.replaceFirst(
                "\\{", "{\"unknown\":true,")));
        assertInvalid(() -> codec.decode(encoded.replaceFirst(
                "\"proposalId\":\"proposal-1\"",
                "\"proposalId\":\"proposal-1\","
                        + "\"proposalId\":\"proposal-2\"")));
        assertInvalid(() -> codec.decode(encoded.replace(
                "Expense approval", "Changed approval")));
    }

    @Test
    void envelopeOpensOnlyWithExactOwnerBindingAndRejectsTamper() {
        var sealer = sealer();
        var binding = binding("proposal-1");
        var sealed = sealer.seal("complete flow command", binding);

        assertThat(sealer.open(sealed, binding))
                .isEqualTo("complete flow command");
        assertInvalid(() -> sealer.open(sealed, binding("proposal-2")));
        var first = sealed.ciphertext().charAt(0) == 'A' ? 'B' : 'A';
        var tampered = new AiFlowDefinitionDraftFacade.SealedCommand(
                first + sealed.ciphertext().substring(1),
                sealed.encryptionKeyVersion(), sealed.commandSha256());
        assertInvalid(() -> sealer.open(tampered, binding));
    }

    static AiFlowDefinitionDraftFacade.PrepareRequest request() {
        return new AiFlowDefinitionDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                99, 1, 2, 10, 7,
                java.util.Set.of("flow.definition.manage"),
                AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT,
                new AiFlowDefinitionDraftFacade.Draft(
                        "Expense approval", List.of("20", "30")),
                "51", "61", 2, "prompt-v1",
                "prepare-request", "prepare-trace");
    }

    static FlowAiDefinitionDraftCommandSealer sealer() {
        PlatformSecretResolverFacade secrets = value -> Optional.of(
                SecretResolverFacade.ResolvedSecret.utf8(
                        "flow-ai-definition-draft-test-key"));
        return new FlowAiDefinitionDraftCommandSealer(
                secrets, "env://TEST", new SecureRandom());
    }

    private static FlowAiDefinitionDraftCommandSealer.Binding binding(
            String proposalId) {
        return new FlowAiDefinitionDraftCommandSealer.Binding(
                99, 1, 2, 10, proposalId, "session-1", "turn-1",
                AiFlowDefinitionDraftFacade.Operation.FLOW_DEFINITION_DRAFT);
    }

    static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_FLOW_DRAFT_COMMAND_INVALID"));
    }
}
