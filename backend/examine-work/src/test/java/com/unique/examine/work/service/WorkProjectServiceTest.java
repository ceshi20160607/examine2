package com.unique.examine.work.service;

import com.unique.examine.work.adapter.memory.InMemoryWorkProjectRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkProjectServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");
    private InMemoryWorkProjectRepository repository;
    private WorkProjectService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryWorkProjectRepository();
        service = new WorkProjectService(
                repository,
                (systemId, tenantId, memberId) -> systemId == 10L
                        && tenantId == 20L
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creatorBecomesOwnerAndMemberVisibilityIsTenantScoped() {
        var project = service.create(
                actor(100), "Release", "Release work");

        assertThat(project.status()).isEqualTo(WorkProject.Status.ACTIVE);
        assertThat(service.members(actor(100), project.id()))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.memberId()).isEqualTo(100L);
                    assertThat(member.role())
                            .isEqualTo(WorkProjectMember.Role.OWNER);
                });
        assertThatThrownBy(() -> service.get(actor(101), project.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_NOT_FOUND"));

        var added = service.addMember(
                actor(100), project.id(), 101L,
                WorkProjectMember.Role.MEMBER);
        assertThat(service.get(actor(101), project.id()).id())
                .isEqualTo(project.id());
        assertThat(service.page(
                        actor(101), query(WorkProjectQuery.StatusFilter.ALL))
                .items()).extracting(WorkProject::id)
                .containsExactly(project.id());
        assertThat(added.version()).isEqualTo(1L);

        assertThatThrownBy(() -> service.get(
                new WorkActor(10, 21, 101, Set.of(WorkProjectService.ACCESS)),
                project.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_NOT_FOUND"));
    }

    @Test
    void ownerAndManagerCanReviseArchiveReopenAndMaintainMembers() {
        var project = service.create(actor(100), "Release", null);
        project = service.update(
                actor(100), project.id(), "Release 2", "desc",
                project.version());
        assertThat(project.title()).isEqualTo("Release 2");

        var member = service.addMember(
                actor(100), project.id(), 101,
                WorkProjectMember.Role.MEMBER);
        member = service.updateMember(
                actor(100), project.id(), 101,
                WorkProjectMember.Role.OWNER, member.version());
        assertThat(member.role()).isEqualTo(WorkProjectMember.Role.OWNER);

        project = service.archive(
                manager(102), project.id(), project.version());
        assertThat(project.status()).isEqualTo(WorkProject.Status.ARCHIVED);
        project = service.reopen(
                actor(101), project.id(), project.version());
        assertThat(project.status()).isEqualTo(WorkProject.Status.ACTIVE);

        var removed = service.removeMember(
                actor(101), project.id(), 100, 1L);
        assertThat(removed.status())
                .isEqualTo(WorkProjectMember.Status.REMOVED);
    }

    @Test
    void nonOwnerStaleWritesAndRemovingLastOwnerFailClosed() {
        var project = service.create(actor(100), "Release", null);
        var member = service.addMember(
                actor(100), project.id(), 101,
                WorkProjectMember.Role.MEMBER);

        assertThatThrownBy(() -> service.update(
                actor(101), project.id(), "Denied", null,
                project.version()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_FORBIDDEN"));
        assertThatThrownBy(() -> service.archive(
                actor(100), project.id(), project.version() + 1))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_VERSION_CONFLICT"));
        assertThatThrownBy(() -> service.updateMember(
                actor(100), project.id(), 101,
                WorkProjectMember.Role.OWNER, member.version() + 1))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(
                                        "WORK_PROJECT_MEMBER_VERSION_CONFLICT"));
        assertThatThrownBy(() -> service.removeMember(
                actor(100), project.id(), 100, 1L))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_LAST_OWNER_INVALID"));
    }

    private static WorkProjectQuery query(
            WorkProjectQuery.StatusFilter status
    ) {
        return new WorkProjectQuery("", status, 1, 20);
    }

    private static WorkActor actor(long memberId) {
        return new WorkActor(
                10, 20, memberId, Set.of(WorkProjectService.ACCESS));
    }

    private static WorkActor manager(long memberId) {
        return new WorkActor(
                10, 20, memberId,
                Set.of(WorkProjectService.ACCESS, WorkProjectService.MANAGE));
    }
}
