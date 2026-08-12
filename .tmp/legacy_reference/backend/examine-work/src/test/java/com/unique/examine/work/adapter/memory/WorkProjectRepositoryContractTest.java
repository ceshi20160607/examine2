package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectQuery;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkProjectRepositoryContractTest {
    private static final Instant NOW = Instant.parse("2026-07-31T14:30:00Z");

    @Test
    void hidesOutsidersProtectsLastOwnerAndAppliesCas() {
        var repository = new InMemoryWorkProjectRepository();
        var project = project(1L, 100L, "Delivery", NOW);
        var creator = WorkProjectMember.owner(
                10L, 20L, project.id(), 100L, NOW);
        repository.create(project, creator);

        assertThat(repository.findVisibleById(
                10L, 20L, 1L, 100L, false)).contains(project);
        assertThat(repository.findVisibleById(
                10L, 20L, 1L, 999L, false)).isEmpty();
        assertThat(repository.findVisibleById(
                10L, 20L, 1L, 999L, true)).contains(project);

        var member = WorkProjectMember.member(
                10L, 20L, 1L, 101L, NOW.plusSeconds(1));
        repository.saveMember(member);
        var promoted = member.promote(NOW.plusSeconds(2));
        repository.saveMember(promoted);
        repository.saveMember(creator.demote(NOW.plusSeconds(3)));
        assertThatThrownBy(() -> repository.saveMember(
                promoted.remove(NOW.plusSeconds(4))))
                .isInstanceOf(WorkDomainException.class)
                .hasMessageContaining("at least one active owner");

        var archived = project.archive(NOW.plusSeconds(5));
        repository.save(archived);
        assertThatThrownBy(() -> repository.save(archived))
                .isInstanceOf(WorkDomainException.class)
                .hasMessageContaining("stale");
        assertThat(repository.save(archived.reopen(NOW.plusSeconds(6))).status())
                .isEqualTo(WorkProject.Status.ACTIVE);
    }

    @Test
    void returnsStableVisibleFilteredPages() {
        var repository = new InMemoryWorkProjectRepository();
        var first = project(1L, 100L, "Alpha", NOW);
        var second = project(2L, 200L, "Beta", NOW.plusSeconds(1));
        repository.create(first, WorkProjectMember.owner(
                10L, 20L, 1L, 100L, NOW));
        repository.create(second, WorkProjectMember.owner(
                10L, 20L, 2L, 200L, NOW.plusSeconds(1)));
        repository.save(second.archive(NOW.plusSeconds(2)));

        var memberPage = repository.findPage(
                10L, 20L, 100L, false,
                new WorkProjectQuery("", WorkProjectQuery.StatusFilter.ALL,
                        1, 20));
        var managerPage = repository.findPage(
                10L, 20L, 999L, true,
                new WorkProjectQuery("", WorkProjectQuery.StatusFilter.ALL,
                        1, 20));
        var archived = repository.findPage(
                10L, 20L, 999L, true,
                new WorkProjectQuery("beta",
                        WorkProjectQuery.StatusFilter.ARCHIVED, 1, 20));

        assertThat(memberPage.items()).extracting(WorkProject::id)
                .containsExactly(1L);
        assertThat(managerPage.items()).extracting(WorkProject::id)
                .containsExactly(2L, 1L);
        assertThat(archived.items()).extracting(WorkProject::id)
                .containsExactly(2L);
    }

    @Test
    void taskRepositoryFiltersOneSharedFactByProjectAndDueWindow() {
        var projects = new InMemoryWorkProjectRepository();
        var project = project(1L, 100L, "Shared", NOW);
        projects.create(project, WorkProjectMember.owner(
                10L, 20L, 1L, 100L, NOW));
        projects.saveMember(WorkProjectMember.member(
                10L, 20L, 1L, 102L, NOW));
        var repository = new InMemoryWorkTaskRepository(projects);
        var due = NOW.plusSeconds(3_600);
        var task = new WorkTask(
                11L, 10L, 20L, 100L, 101L, "Shared",
                WorkTask.Status.OPEN, NOW, NOW, 1, 1L, "Details", due);
        repository.save(task);

        var page = repository.findPage(
                10L, 20L, 102L,
                new WorkTaskQuery("", WorkTaskQuery.StatusFilter.ALL,
                        WorkTaskQuery.RoleFilter.PARTICIPATING, 1, 20,
                        1L, due.minusSeconds(1), due.plusSeconds(1)));

        assertThat(page.items()).containsExactly(task);
        assertThat(repository.findPage(
                10L, 20L, 100L,
                new WorkTaskQuery("", WorkTaskQuery.StatusFilter.ALL,
                        WorkTaskQuery.RoleFilter.PARTICIPATING, 1, 20,
                        2L, null, null)).items()).isEmpty();
    }

    private static WorkProject project(
            long id, long creatorId, String title, Instant createdAt
    ) {
        return new WorkProject(
                id, 10L, 20L, creatorId, title, title + " work",
                WorkProject.Status.ACTIVE, createdAt, createdAt, 1);
    }
}
