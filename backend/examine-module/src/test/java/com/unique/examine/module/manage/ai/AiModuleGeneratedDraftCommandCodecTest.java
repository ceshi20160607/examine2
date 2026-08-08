package com.unique.examine.module.manage.ai;

import com.unique.examine.core.ai.AiModuleGeneratedDraftFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModuleGeneratedDraftCommandCodecTest {
    static final Instant EXPIRES = Instant.parse("2026-08-04T08:15:00Z");

    @Test
    void bothOperationsRoundTripWithCompleteOwnerIdentity() {
        var codec = new AiModuleGeneratedDraftCommandCodec();
        var report = codec.command(reportRequest(), EXPIRES);
        var print = codec.command(printRequest(), EXPIRES);

        assertThat(codec.decode(codec.encode(report))).isEqualTo(report);
        assertThat(codec.decode(codec.encode(print))).isEqualTo(print);
        assertThat(report.effectivePermissions()).containsExactly(
                "module.config.manage", "system.admin.access");
        assertThat(report.payloadHash()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void unknownDuplicateAndChangedPayloadFailClosed() {
        var codec = new AiModuleGeneratedDraftCommandCodec();
        var encoded = codec.encode(codec.command(reportRequest(), EXPIRES));

        assertInvalid(() -> codec.decode(encoded.replaceFirst(
                "\\{", "{\"unknown\":true,")));
        assertInvalid(() -> codec.decode(encoded.replaceFirst(
                "\"proposalId\":\"proposal-report\"",
                "\"proposalId\":\"proposal-report\","
                        + "\"proposalId\":\"other\"")));
        assertInvalid(() -> codec.decode(encoded.replace(
                "Quarterly report", "Changed report")));
    }

    static AiModuleGeneratedDraftFacade.PrepareRequest reportRequest() {
        return new AiModuleGeneratedDraftFacade.PrepareRequest(
                "proposal-report", "session-1", "turn-report",
                7, 11, 21, 17, 3,
                Set.of("system.admin.access", "module.config.manage"),
                AiModuleGeneratedDraftFacade.Operation.CONFIG_REPORT_DRAFT,
                new AiModuleGeneratedDraftFacade.ReportDraft(
                        "Quarterly", "Quarterly report", "Summary", "101",
                        List.of("amount", "createdAt")),
                null, "51", "61", 2, "prompt-v1",
                "request-1", "trace-1");
    }

    static AiModuleGeneratedDraftFacade.PrepareRequest printRequest() {
        return new AiModuleGeneratedDraftFacade.PrepareRequest(
                "proposal-print", "session-1", "turn-print",
                7, 11, 21, 17, 3,
                Set.of("system.admin.access", "module.config.manage"),
                AiModuleGeneratedDraftFacade.Operation
                        .CONFIG_PRINT_TEMPLATE_DRAFT,
                null, new AiModuleGeneratedDraftFacade.PrintTemplateDraft(
                        "orders", "order_summary", "Order summary", "A4",
                        "PORTRAIT", "Order summary", List.of("amount"),
                        "Generated"),
                "51", "61", 2, "prompt-v1",
                "request-1", "trace-1");
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).code())
                .isEqualTo("AI_MODULE_DRAFT_COMMAND_INVALID");
    }
}
