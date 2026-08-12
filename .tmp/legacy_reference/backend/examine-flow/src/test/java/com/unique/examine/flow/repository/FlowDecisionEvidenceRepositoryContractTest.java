package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalDecisionEvidenceFile;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalSql;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowDecisionEvidenceRepositoryContractTest {
    private static final Instant CREATED_AT =
            Instant.parse("2026-07-31T05:00:00Z");

    @Test
    void keepsTemplateVersionsImmutableAcrossRevisionAndStatusChanges() {
        var repository = new InMemoryApprovalRepository();
        var created = new ApprovalDecisionCommentTemplate(
                101L, "Standard approval", "Approved after review.", 1,
                ApprovalDecisionCommentTemplate.Status.ACTIVE,
                20L, CREATED_AT, 20L, CREATED_AT);

        repository.saveDecisionCommentTemplate(created);
        var revised = created.revise(
                "Standard approval", "Approved with evidence.",
                21L, CREATED_AT.plusSeconds(30));
        repository.saveDecisionCommentTemplate(revised);
        repository.saveDecisionCommentTemplate(revised.withStatus(
                ApprovalDecisionCommentTemplate.Status.INACTIVE,
                22L, CREATED_AT.plusSeconds(60)));

        assertThat(repository.findDecisionCommentTemplateVersion(101L, 1))
                .get()
                .extracting(version -> version.body())
                .isEqualTo("Approved after review.");
        assertThat(repository.findDecisionCommentTemplateVersion(101L, 2))
                .get()
                .extracting(version -> version.body())
                .isEqualTo("Approved with evidence.");
        assertThat(repository.findDecisionCommentTemplates(true, 0, 20))
                .isEmpty();
        assertThat(repository.countDecisionCommentTemplates(false)).isEqualTo(1);
    }

    @Test
    void savesOneImmutableEvidenceSnapshotIdempotentlyAndRestoresHistory() {
        var repository = new InMemoryApprovalRepository();
        var definition = new ApprovalDefinitionVersion(
                1L, 1, "Approval", 20L, 1, CREATED_AT);
        var pending = repository.saveInstance(ApprovalInstance.start(
                2L, definition, "expense-2", 10L,
                CREATED_AT.plusSeconds(1)));
        var decidedAt = CREATED_AT.plusSeconds(2);
        repository.saveInstance(pending.approve(20L, "", decidedAt));
        var file = new ApprovalDecisionEvidenceFile(
                41L, "proof.pdf", "application/pdf", 512L,
                "0123456789abcdef0123456789abcdef"
                        + "0123456789abcdef0123456789abcdef");
        var evidence = new ApprovalDecisionEvidence(
                301L, 2L, 2, "finance", 1,
                ApprovalInstance.Decision.APPROVED, List.of(file),
                ApprovalDecisionEvidence.Signature.file(file), null,
                20L, 20L, null, decidedAt);

        assertThat(repository.saveDecisionEvidence(evidence))
                .isEqualTo(evidence);
        assertThat(repository.saveDecisionEvidence(evidence))
                .isEqualTo(evidence);
        assertThat(repository.findDecisionEvidenceByInstance(2L))
                .containsExactly(evidence);
        assertThat(repository.findInstance(2L).orElseThrow()
                .history().getLast().evidence()).isEqualTo(evidence);

        var conflicting = new ApprovalDecisionEvidence(
                302L, 2L, 2, "finance", 1,
                ApprovalInstance.Decision.APPROVED, List.of(file),
                null, null, 20L, 20L, null, decidedAt);
        assertThatThrownBy(() ->
                repository.saveDecisionEvidence(conflicting))
                .isInstanceOf(ApprovalDomainException.class)
                .hasMessageContaining("another snapshot");
    }

    @Test
    void sqlKeepsPolicySnapshotsAndEvidenceIdentityDurable() {
        assertThat(JdbcApprovalSql.INSERT_DRAFT)
                .contains("decision_evidence_policies");
        assertThat(JdbcApprovalSql.INSERT_INSTANCE)
                .contains("decision_evidence_policy");
        assertThat(JdbcApprovalSql.INSERT_PARALLEL_BRANCH)
                .contains("decision_evidence_policy");
        assertThat(JdbcApprovalSql.INSERT_DECISION_EVIDENCE)
                .contains("history_sequence", "represented_member_id",
                        "delegation_id");
        assertThat(JdbcApprovalSql.SELECT_DECISION_EVIDENCE_FILES)
                .contains("original_name", "content_type", "size_bytes",
                        "sha256", "attachment_order");
    }
}
