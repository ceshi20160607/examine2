package com.unique.examine.work.service;

import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectPage;
import com.unique.examine.work.domain.WorkProjectQuery;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.port.WorkProjectRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Transactional
public class WorkProjectService {
    public static final String ACCESS = WorkTaskService.ACCESS;
    public static final String MANAGE = "work.project.manage";

    private final WorkProjectRepository repository;
    private final WorkMemberDirectory members;
    private final Clock clock;

    public WorkProjectService(
            WorkProjectRepository repository,
            WorkMemberDirectory members,
            Clock clock
    ) {
        this.repository = required(repository, "repository");
        this.members = required(members, "member directory");
        this.clock = required(clock, "clock");
    }

    public WorkProject create(
            WorkActor actor, String title, String description
    ) {
        requireAccess(actor);
        requireActiveTenantMember(actor, actor.memberId());
        var now = Instant.now(clock);
        var project = new WorkProject(
                repository.nextId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), title, description,
                WorkProject.Status.ACTIVE, now, now, 1);
        return repository.create(
                project,
                WorkProjectMember.owner(
                        actor.systemId(), actor.tenantId(), project.id(),
                        actor.memberId(), now));
    }

    @Transactional(readOnly = true)
    public WorkProjectPage page(
            WorkActor actor, WorkProjectQuery query
    ) {
        requireAccess(actor);
        if (query == null) {
            throw new IllegalArgumentException("query is required");
        }
        return repository.findPage(
                actor.systemId(), actor.tenantId(), actor.memberId(),
                actor.has(MANAGE), query);
    }

    @Transactional(readOnly = true)
    public WorkProject get(WorkActor actor, long projectId) {
        requireAccess(actor);
        return visible(actor, projectId);
    }

    public WorkProject update(
            WorkActor actor,
            long projectId,
            String title,
            String description,
            long expectedVersion
    ) {
        var project = manageable(actor, projectId);
        requireVersion(project.version(), expectedVersion,
                "WORK_PROJECT_VERSION_CONFLICT",
                "Project version is stale");
        return repository.save(project.revise(
                title, description, Instant.now(clock)));
    }

    public WorkProject archive(
            WorkActor actor, long projectId, long expectedVersion
    ) {
        var project = manageable(actor, projectId);
        requireVersion(project.version(), expectedVersion,
                "WORK_PROJECT_VERSION_CONFLICT",
                "Project version is stale");
        return repository.save(project.archive(Instant.now(clock)));
    }

    public WorkProject reopen(
            WorkActor actor, long projectId, long expectedVersion
    ) {
        var project = manageable(actor, projectId);
        requireVersion(project.version(), expectedVersion,
                "WORK_PROJECT_VERSION_CONFLICT",
                "Project version is stale");
        return repository.save(project.reopen(Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<WorkProjectMember> members(
            WorkActor actor, long projectId
    ) {
        requireAccess(actor);
        visible(actor, projectId);
        return repository.findMembers(
                        actor.systemId(), actor.tenantId(), projectId)
                .stream()
                .filter(value -> value.status()
                        == WorkProjectMember.Status.ACTIVE)
                .toList();
    }

    public WorkProjectMember addMember(
            WorkActor actor,
            long projectId,
            long memberId,
            WorkProjectMember.Role role
    ) {
        manageable(actor, projectId);
        requireActiveTenantMember(actor, memberId);
        if (role == null) {
            throw error("WORK_PROJECT_MEMBER_INVALID",
                    "Project member role is required");
        }
        var now = Instant.now(clock);
        var existing = repository.findMemberForUpdate(
                        actor.systemId(), actor.tenantId(),
                        projectId, memberId)
                .orElse(null);
        final WorkProjectMember next;
        if (existing == null) {
            next = new WorkProjectMember(
                    actor.systemId(), actor.tenantId(), projectId, memberId,
                    role, WorkProjectMember.Status.ACTIVE, now, now, 1);
        } else if (existing.status() == WorkProjectMember.Status.REMOVED) {
            next = existing.reactivate(role, now);
        } else {
            throw error("WORK_PROJECT_MEMBER_STATE_INVALID",
                    "Project member is already active");
        }
        return repository.saveMember(next);
    }

    public WorkProjectMember updateMember(
            WorkActor actor,
            long projectId,
            long memberId,
            WorkProjectMember.Role role,
            long expectedVersion
    ) {
        manageable(actor, projectId);
        if (role == null) {
            throw error("WORK_PROJECT_MEMBER_INVALID",
                    "Project member role is required");
        }
        var member = activeMemberForUpdate(actor, projectId, memberId);
        requireVersion(member.version(), expectedVersion,
                "WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                "Project member version is stale");
        if (member.role() == role) {
            return member;
        }
        return repository.saveMember(
                member.changeRole(role, Instant.now(clock)));
    }

    public WorkProjectMember removeMember(
            WorkActor actor,
            long projectId,
            long memberId,
            long expectedVersion
    ) {
        manageable(actor, projectId);
        var member = activeMemberForUpdate(actor, projectId, memberId);
        requireVersion(member.version(), expectedVersion,
                "WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                "Project member version is stale");
        return repository.saveMember(member.remove(Instant.now(clock)));
    }

    private WorkProject manageable(WorkActor actor, long projectId) {
        requireAccess(actor);
        var project = visible(actor, projectId);
        if (actor.has(MANAGE)) {
            return project;
        }
        var member = repository.findMember(
                        actor.systemId(), actor.tenantId(),
                        projectId, actor.memberId())
                .orElse(null);
        if (member == null || !member.activeOwner()) {
            throw error("WORK_PROJECT_FORBIDDEN",
                    "Only a project owner or project manager can change this project");
        }
        return project;
    }

    private WorkProject visible(WorkActor actor, long projectId) {
        return repository.findVisibleById(
                        actor.systemId(), actor.tenantId(), projectId,
                        actor.memberId(), actor.has(MANAGE))
                .orElseThrow(WorkProjectService::notFound);
    }

    private WorkProjectMember activeMemberForUpdate(
            WorkActor actor, long projectId, long memberId
    ) {
        var member = repository.findMemberForUpdate(
                        actor.systemId(), actor.tenantId(),
                        projectId, memberId)
                .orElseThrow(WorkProjectService::notFound);
        if (member.status() != WorkProjectMember.Status.ACTIVE) {
            throw notFound();
        }
        return member;
    }

    private void requireActiveTenantMember(
            WorkActor actor, long memberId
    ) {
        if (memberId <= 0 || !members.isActiveMember(
                actor.systemId(), actor.tenantId(), memberId)) {
            throw error("WORK_PROJECT_MEMBER_INVALID",
                    "Project member is not an active member of this tenant");
        }
    }

    private static void requireAccess(WorkActor actor) {
        if (actor == null || !actor.has(ACCESS)) {
            throw error("WORK_PROJECT_FORBIDDEN",
                    "Missing permission: " + ACCESS);
        }
    }

    private static void requireVersion(
            long current,
            long expected,
            String code,
            String message
    ) {
        if (expected <= 0 || current != expected) {
            throw error(code, message);
        }
    }

    private static WorkDomainException notFound() {
        return error("WORK_PROJECT_NOT_FOUND", "Project was not found");
    }

    private static WorkDomainException error(String code, String message) {
        return new WorkDomainException(code, message);
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
