package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalDecisionCommentTemplate;
import com.unique.examine.flow.domain.ApprovalDecisionEvidence;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicies;
import com.unique.examine.flow.domain.ApprovalDecisionEvidencePolicy;
import com.unique.examine.flow.domain.ApprovalInstance;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDecisionEvidenceMappingTest {
    @Test
    void mapsTemplateHeadToItsCurrentImmutableVersion() throws Exception {
        var updatedAt = Instant.parse("2026-07-31T05:01:00Z");
        var template = JdbcApprovalRepository.COMMENT_TEMPLATE_MAPPER.mapRow(
                row(
                        column("template_id", Types.BIGINT, 101L),
                        column("name", Types.VARCHAR, "Standard approval"),
                        column("comment_body", Types.VARCHAR,
                                "Approved with evidence."),
                        column("current_version", Types.INTEGER, 2),
                        column("status", Types.VARCHAR, "ACTIVE"),
                        column("created_by", Types.BIGINT, 20L),
                        column("created_at", Types.TIMESTAMP,
                                Timestamp.from(updatedAt.minusSeconds(60))),
                        column("updated_by", Types.BIGINT, 21L),
                        column("updated_at", Types.TIMESTAMP,
                                Timestamp.from(updatedAt))
                ),
                0);

        assertThat(template.status())
                .isEqualTo(ApprovalDecisionCommentTemplate.Status.ACTIVE);
        assertThat(template.currentVersion()).isEqualTo(2);
        assertThat(template.currentVersionSnapshot().body())
                .isEqualTo("Approved with evidence.");
    }

    @Test
    void reconstructsOverlappingAttachmentAndSignatureSnapshot()
            throws Exception {
        var decidedAt = Instant.parse("2026-07-31T05:02:00Z");
        var evidenceRow = JdbcApprovalRepository.DECISION_EVIDENCE_MAPPER
                .mapRow(row(
                        column("evidence_id", Types.BIGINT, 301L),
                        column("instance_id", Types.BIGINT, 201L),
                        column("history_sequence", Types.INTEGER, 3),
                        column("branch_code", Types.VARCHAR, "finance"),
                        column("stage_index", Types.INTEGER, 1),
                        column("decision", Types.VARCHAR, "APPROVED"),
                        column("signature_kind", Types.VARCHAR, "FILE"),
                        column("signature_file_id", Types.BIGINT, 41L),
                        column("typed_signature", Types.VARCHAR, null),
                        column("template_id", Types.BIGINT, 101L),
                        column("template_version", Types.INTEGER, 2),
                        column("template_name", Types.VARCHAR,
                                "Standard approval"),
                        column("actor_id", Types.BIGINT, 30L),
                        column("represented_member_id", Types.BIGINT, 20L),
                        column("delegation_id", Types.BIGINT, 501L),
                        column("decided_at", Types.TIMESTAMP,
                                Timestamp.from(decidedAt))
                ), 0);
        var fileRow = JdbcApprovalRepository.DECISION_EVIDENCE_FILE_MAPPER
                .mapRow(row(
                        column("file_id", Types.BIGINT, 41L),
                        column("original_name", Types.VARCHAR, "proof.pdf"),
                        column("content_type", Types.VARCHAR,
                                "application/pdf"),
                        column("size_bytes", Types.BIGINT, 512L),
                        column("sha256", Types.CHAR,
                                "0123456789abcdef0123456789abcdef"
                                        + "0123456789abcdef0123456789abcdef"),
                        column("is_attachment", Types.BOOLEAN, true),
                        column("is_signature", Types.BOOLEAN, true),
                        column("attachment_order", Types.INTEGER, 0)
                ), 0);

        var evidence = evidenceRow.toDomain(List.of(fileRow));

        assertThat(evidence.decision())
                .isEqualTo(ApprovalInstance.Decision.APPROVED);
        assertThat(evidence.attachments()).hasSize(1);
        assertThat(evidence.signature().kind())
                .isEqualTo(ApprovalDecisionEvidence.Signature.Kind.FILE);
        assertThat(evidence.referencedFiles()).hasSize(1);
        assertThat(evidence.template().version()).isEqualTo(2);
        assertThat(evidence.delegationRuleId()).isEqualTo(501L);
    }

    @Test
    void policyJsonFieldsRemainExplicitAndBoundedInSqlProjection() {
        var policy = new ApprovalDecisionEvidencePolicy(
                1, 3,
                Set.of(
                        ApprovalDecisionEvidencePolicy.MimeFamily.IMAGE,
                        ApprovalDecisionEvidencePolicy.MimeFamily.PDF),
                ApprovalDecisionEvidencePolicy.SignatureMode.OPTIONAL);

        assertThat(policy.allowedMimeFamilies())
                .containsExactlyInAnyOrder(
                        ApprovalDecisionEvidencePolicy.MimeFamily.IMAGE,
                        ApprovalDecisionEvidencePolicy.MimeFamily.PDF);
        assertThat(JdbcApprovalRepository.decisionEvidencePolicy(policy))
                .isEqualTo(
                        "{\"minimumAttachments\":1,\"maximumAttachments\":3,"
                                + "\"allowedMimeFamilies\":[\"IMAGE\",\"PDF\"],"
                                + "\"signatureMode\":\"OPTIONAL\"}");
        assertThat(JdbcApprovalRepository.decisionEvidencePolicies(
                new ApprovalDecisionEvidencePolicies(
                        policy, Map.of("finance", policy))))
                .contains("\"route\":{", "\"branches\":{\"finance\":{");
        assertThat(JdbcApprovalRepository.decisionEvidencePolicy(null))
                .isNull();
        assertThat(JdbcApprovalSql.SELECT_DRAFT)
                .contains("decision_evidence_policies");
        assertThat(JdbcApprovalSql.SELECT_INSTANCE)
                .contains("decision_evidence_policy");
        assertThat(JdbcApprovalSql.SELECT_PARALLEL_BRANCHES)
                .contains("decision_evidence_policy");
    }

    private static CachedRowSet row(Column... columns) throws Exception {
        var metadata = new RowSetMetaDataImpl();
        metadata.setColumnCount(columns.length);
        for (var index = 0; index < columns.length; index++) {
            metadata.setColumnName(index + 1, columns[index].name());
            metadata.setColumnLabel(index + 1, columns[index].name());
            metadata.setColumnType(index + 1, columns[index].type());
        }
        var rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.moveToInsertRow();
        for (var index = 0; index < columns.length; index++) {
            rowSet.updateObject(index + 1, columns[index].value());
        }
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();
        assertThat(rowSet.next()).isTrue();
        return rowSet;
    }

    private static Column column(String name, int type, Object value) {
        return new Column(name, type, value);
    }

    private record Column(String name, int type, Object value) {
    }
}
