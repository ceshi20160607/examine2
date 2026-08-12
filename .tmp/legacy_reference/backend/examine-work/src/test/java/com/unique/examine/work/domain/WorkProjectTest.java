package com.unique.examine.work.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkProjectTest {
    private static final Instant NOW = Instant.parse("2026-07-31T14:00:00Z");

    @Test
    void enforcesMetadataBoundsAndReversibleArchiveState() {
        var project = project();

        var archived = project.archive(NOW.plusSeconds(1));
        var reopened = archived.reopen(NOW.plusSeconds(2));

        assertThat(archived.status()).isEqualTo(WorkProject.Status.ARCHIVED);
        assertThat(reopened.status()).isEqualTo(WorkProject.Status.ACTIVE);
        assertThat(reopened.version()).isEqualTo(3);
        assertThatThrownBy(() -> new WorkProject(
                1L, 10L, 20L, 100L, "x".repeat(201), null,
                WorkProject.Status.ACTIVE, NOW, NOW, 1))
                .isInstanceOf(WorkDomainException.class);
        assertThatThrownBy(() -> project.reopen(NOW.plusSeconds(1)))
                .isInstanceOf(WorkDomainException.class);
    }

    @Test
    void taskMetadataMovesWithoutChangingIdentityOrStateMachine() {
        var task = new WorkTask(
                11L, 10L, 20L, 100L, 101L, "Task",
                WorkTask.Status.OPEN, NOW, NOW, 1);

        var linked = task.reviseMetadata(
                "Scheduled task", "Shared details", 1L,
                NOW.plusSeconds(3_600), NOW.plusSeconds(1));
        var standalone = linked.reviseMetadata(
                linked.title(), null, null, null, NOW.plusSeconds(2));

        assertThat(linked.id()).isEqualTo(task.id());
        assertThat(linked.status()).isEqualTo(WorkTask.Status.OPEN);
        assertThat(linked.projectId()).isEqualTo(1L);
        assertThat(linked.dueAt()).isEqualTo(NOW.plusSeconds(3_600));
        assertThat(standalone.projectId()).isNull();
        assertThat(standalone.dueAt()).isNull();
        assertThat(standalone.version()).isEqualTo(3);
        assertThat(linked.complete(NOW.plusSeconds(2)).projectId())
                .isEqualTo(1L);
    }

    private static WorkProject project() {
        return new WorkProject(
                1L, 10L, 20L, 100L, "Delivery", "Release work",
                WorkProject.Status.ACTIVE, NOW, NOW, 1);
    }
}
