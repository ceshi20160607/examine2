package com.unique.examine.core.ai;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformTaskFacadeTest {

    @Test
    void exposesOnlyPlatformAccountTaskFields() {
        var types = List.of(
                PlatformTaskFacade.TaskDraft.class,
                PlatformTaskFacade.PrepareRequest.class,
                PlatformTaskFacade.TaskPreview.class,
                PlatformTaskFacade.SealedCommand.class,
                PlatformTaskFacade.ExecuteRequest.class,
                PlatformTaskFacade.TaskView.class);

        var names = types.stream()
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .toList();

        assertThat(names).noneMatch(name -> List.of(
                "system", "tenant", "member", "module", "field", "record")
                .stream().anyMatch(name::contains));
        assertThat(Arrays.stream(
                PlatformTaskFacade.PrepareRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("proposalId", "accountId", "authorizationEpoch",
                        "draft", "requestId", "traceId");
    }

    @Test
    void validatesSelfAssignmentHashesAndBoundedDraft() {
        var now = Instant.parse("2026-08-04T00:00:00Z");
        var draft = new PlatformTaskFacade.TaskDraft(
                " Follow up ", null, now.plusSeconds(60),
                PlatformTaskFacade.Priority.HIGH);
        var request = new PlatformTaskFacade.PrepareRequest(
                "proposal-1", 7, 3, draft, "request-1", "trace-1");

        assertThat(request.draft().title()).isEqualTo("Follow up");
        assertThatThrownBy(() -> new PlatformTaskFacade.TaskView(
                "9", 7, "Follow up", null, null,
                PlatformTaskFacade.Priority.NORMAL,
                PlatformTaskFacade.Status.OPEN,
                PlatformTaskFacade.Source.AGENT, 3, "a".repeat(64),
                "request-1", "trace-1", now, 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self-assigned");
        assertThatThrownBy(() -> new PlatformTaskFacade.SealedCommand(
                "opaque", "v1", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
