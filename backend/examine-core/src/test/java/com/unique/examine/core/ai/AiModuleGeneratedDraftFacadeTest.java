package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModuleGeneratedDraftFacadeTest {

    @Test
    void prepareBindsIdentityAndExactlyOneModuleDraft() {
        var permissions = new HashSet<>(Set.of(
                "system.admin.access", "module.config.manage"));
        var report = new AiModuleGeneratedDraftFacade.ReportDraft(
                "Quarterly", " Quarterly report ", " Summary ",
                "101", List.of("amount", "createdAt"));
        var request = new AiModuleGeneratedDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, permissions,
                AiModuleGeneratedDraftFacade.Operation.CONFIG_REPORT_DRAFT,
                report, null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1");
        permissions.clear();

        assertThat(request.effectivePermissions()).containsExactlyInAnyOrder(
                "system.admin.access", "module.config.manage");
        assertThat(request.report().name()).isEqualTo("Quarterly report");
        assertThat(request.report().description()).isEqualTo("Summary");
        assertThatThrownBy(() -> new AiModuleGeneratedDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3, Set.of(),
                AiModuleGeneratedDraftFacade.Operation.CONFIG_PRINT_TEMPLATE_DRAFT,
                report, null, "51", "61", 0, "prompt-v1",
                "request-1", "trace-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matching module draft");
    }

    @Test
    void readbacksCannotClaimPublishedOrEnabledArtifacts() {
        assertThatThrownBy(() -> new AiModuleGeneratedDraftFacade.ReportReadback(
                "1", "Report", "Report", null, "101", List.of("amount"),
                1, 1, Instant.EPOCH, Instant.EPOCH, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unpublished draft");
        assertThatThrownBy(() ->
                new AiModuleGeneratedDraftFacade.PrintTemplateReadback(
                        "1", "2", "orders", "summary", "Summary", "A4",
                        "PORTRAIT", "Order summary", List.of("amount"), null,
                        "ENABLED", 0, Instant.EPOCH, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled unpublished draft");
    }
}
