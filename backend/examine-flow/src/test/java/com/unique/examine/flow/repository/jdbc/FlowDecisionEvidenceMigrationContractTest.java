package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDecisionEvidenceMigrationContractTest {
    @Test
    void addsNullablePolicySnapshotsAndImmutableEvidenceStorage()
            throws Exception {
        var sql = normalize(Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_41_0__flow_decision_evidence_templates.sql")));

        assertThat(sql)
                .contains("alter table un_flow_definition_draft add column decision_evidence_policies json null")
                .contains("alter table un_flow_definition_version add column decision_evidence_policies json null")
                .contains("alter table un_flow_instance add column decision_evidence_policy json null")
                .contains("alter table un_flow_parallel_branch_execution add column decision_evidence_policy json null")
                .contains("create table un_flow_decision_comment_template")
                .contains("unique key uk_flow_comment_template_name ( system_id, tenant_id, name )")
                .contains("create table un_flow_decision_comment_template_version")
                .contains("primary key (system_id, tenant_id, template_id, version_no)")
                .contains("create table un_flow_decision_evidence")
                .contains("unique key uk_flow_decision_evidence_history ( system_id, tenant_id, instance_id, history_sequence )")
                .contains("create table un_flow_decision_evidence_file")
                .contains("attachment_order between 0 and 4");
    }

    @Test
    void constrainsTemplateVersionSignatureAndDelegationFactsExactly()
            throws Exception {
        var sql = normalize(Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_41_0__flow_decision_evidence_templates.sql")));

        assertThat(sql)
                .contains("foreign key ( system_id, tenant_id, template_id, template_version ) references un_flow_decision_comment_template_version")
                .contains("template_id is not null and template_id > 0")
                .contains("template_version is not null and template_version > 0")
                .contains("template_name is not null")
                .contains("signature_file_id is not null and signature_file_id > 0")
                .contains("typed_signature is not null")
                .contains("actor_id = represented_member_id and delegation_id is null")
                .contains("actor_id <> represented_member_id and delegation_id is not null and delegation_id > 0")
                .doesNotContain("on delete cascade");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
