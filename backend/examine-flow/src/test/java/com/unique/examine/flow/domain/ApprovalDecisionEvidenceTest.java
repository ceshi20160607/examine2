package com.unique.examine.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApprovalDecisionEvidenceTest {
    private static final Instant DECIDED_AT =
            Instant.parse("2026-07-31T05:00:00Z");
    private static final ApprovalDecisionEvidenceFile IMAGE =
            new ApprovalDecisionEvidenceFile(
                    41L, "proof.png", "image/png", 128L,
                    "0123456789abcdef0123456789abcdef"
                            + "0123456789abcdef0123456789abcdef");
    private static final ApprovalDecisionEvidenceFile OTHER =
            new ApprovalDecisionEvidenceFile(
                    42L, "proof.bin", "application/octet-stream", 64L,
                    "abcdef0123456789abcdef0123456789"
                            + "abcdef0123456789abcdef0123456789");

    @Test
    void enforcesBoundedMimeAndSignaturePolicy() {
        var policy = new ApprovalDecisionEvidencePolicy(
                1, 2,
                Set.of(ApprovalDecisionEvidencePolicy.MimeFamily.IMAGE),
                ApprovalDecisionEvidencePolicy.SignatureMode.REQUIRED);

        policy.validate(
                List.of(IMAGE),
                ApprovalDecisionEvidence.Signature.typed("Jane Approver"));

        assertThatThrownBy(() -> policy.validate(
                List.of(),
                ApprovalDecisionEvidence.Signature.typed("Jane Approver")))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("count");
        assertThatThrownBy(() -> policy.validate(
                List.of(OTHER),
                ApprovalDecisionEvidence.Signature.typed("Jane Approver")))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("MIME");
        assertThatThrownBy(() -> policy.validate(List.of(IMAGE), null))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("requires signature");
    }

    @Test
    void deduplicatesAFileUsedAsAttachmentAndSignature() {
        var evidence = evidence(
                301L,
                ApprovalDecisionEvidence.Signature.file(IMAGE),
                20L,
                20L,
                null);

        assertThat(evidence.attachments()).containsExactly(IMAGE);
        assertThat(evidence.referencedFiles()).containsExactly(IMAGE);
    }

    @Test
    void bindsOnlyToMatchingLatestDecisionAudit() {
        var definition = new ApprovalDefinitionVersion(
                101L, 1, "Expense", 20L, 1, DECIDED_AT.minusSeconds(60));
        var decided = ApprovalInstance.start(
                201L, definition, "expense-201", 10L,
                DECIDED_AT.minusSeconds(30)
        ).approve(20L, "", DECIDED_AT);
        var evidence = evidence(301L, null, 20L, 20L, null);

        var attached = decided.withLatestDecisionEvidence(evidence);

        assertThat(attached.history().getLast().evidence()).isEqualTo(evidence);
        assertThatThrownBy(() -> decided.withLatestDecisionEvidence(
                evidence(302L, null, 21L, 21L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audit facts");
    }

    @Test
    void preservesDelegatedActorRepresentationFacts() {
        var delegated = evidence(303L, null, 30L, 20L, 501L);

        assertThat(delegated.actorId()).isEqualTo(30L);
        assertThat(delegated.representedMemberId()).isEqualTo(20L);
        assertThat(delegated.delegationRuleId()).isEqualTo(501L);
        assertThatThrownBy(() ->
                evidence(304L, null, 30L, 20L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authority rule");
    }

    private static ApprovalDecisionEvidence evidence(
            long id,
            ApprovalDecisionEvidence.Signature signature,
            long actorId,
            long representedMemberId,
            Long delegationId
    ) {
        return new ApprovalDecisionEvidence(
                id, 201L, 2, null, 0,
                ApprovalInstance.Decision.APPROVED,
                List.of(IMAGE), signature, null,
                actorId, representedMemberId, delegationId, DECIDED_AT);
    }
}
