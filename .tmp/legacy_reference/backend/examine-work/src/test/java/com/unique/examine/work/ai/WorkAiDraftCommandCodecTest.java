package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkAiDraftCommandCodecTest {
    private static final Instant EXPIRES =
            Instant.parse("2026-08-04T08:15:00Z");

    @Test
    void commandRoundTripBindsCompleteIdentityPolicyAndPayload() {
        var codec = new WorkAiDraftCommandCodec();
        var command = codec.command(taskRequest(), EXPIRES);

        var decoded = codec.decode(codec.encode(command));

        assertThat(decoded).isEqualTo(command);
        assertThat(decoded.effectivePermissions()).containsExactly(
                "ai.agent.use", "work.task.access", "work.task.create");
        assertThat(decoded.policyVersionId()).isEqualTo("51");
        assertThat(decoded.providerId()).isEqualTo("61");
        assertThat(decoded.providerVersion()).isEqualTo(2);
        assertThat(decoded.promptVersion()).isEqualTo("prompt-v1");
        assertThat(decoded.prepareRequestId()).isEqualTo("request-1");
        assertThat(decoded.prepareTraceId()).isEqualTo("trace-1");
        assertThat(decoded.payloadHash()).matches("^[0-9a-f]{64}$");
    }

    @Test
    void duplicateUnknownAndChangedPayloadHashesFailClosed() {
        var codec = new WorkAiDraftCommandCodec();
        var encoded = codec.encode(codec.command(taskRequest(), EXPIRES));

        assertInvalid(() -> codec.decode(encoded.replaceFirst(
                "\\{", "{\"unknown\":true,")));
        assertInvalid(() -> codec.decode(encoded.replaceFirst(
                "\"proposalId\":\"proposal-1\"",
                "\"proposalId\":\"proposal-1\",\"proposalId\":\"proposal-2\"")));
        assertInvalid(() -> codec.decode(encoded.replace(
                "Create release task", "Changed release task")));
    }

    static AiWorkDraftFacade.PrepareRequest taskRequest() {
        return new AiWorkDraftFacade.PrepareRequest(
                "proposal-1", "session-1", "turn-1",
                7, 11, 21, 17, 3,
                Set.of("work.task.create", "ai.agent.use", "work.task.access"),
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT,
                new AiWorkDraftFacade.TaskDraft(
                        "Create release task", "Prepare notes", "19", "31",
                        Instant.parse("2026-08-06T08:00:00Z")),
                null, "51", "61", 2, "prompt-v1",
                "request-1", "trace-1");
    }

    static AiWorkDraftFacade.PrepareRequest reportRequest() {
        return new AiWorkDraftFacade.PrepareRequest(
                "proposal-2", "session-1", "turn-2",
                7, 11, 21, 17, 3,
                Set.of("work.report.create", "ai.agent.use", "work.report.access"),
                AiWorkDraftFacade.Operation.WORK_DAILY_REPORT_DRAFT,
                null, new AiWorkDraftFacade.DailyReportDraft(
                        java.time.LocalDate.of(2026, 8, 4),
                        "Completed", "Planned", "Waiting"),
                "51", "61", 2, "prompt-v1",
                "request-1", "trace-1");
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(com.unique.examine.core.error.BusinessException.class)
                .extracting(value -> ((com.unique.examine.core.error.BusinessException) value).code())
                .isEqualTo("AI_WORK_DRAFT_COMMAND_INVALID");
    }
}
