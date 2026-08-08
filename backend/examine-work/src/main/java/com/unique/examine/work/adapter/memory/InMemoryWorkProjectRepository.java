package com.unique.examine.work.adapter.memory;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectPage;
import com.unique.examine.work.domain.WorkProjectQuery;
import com.unique.examine.work.port.WorkProjectRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryWorkProjectRepository
        implements WorkProjectRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<ProjectKey, WorkProject> projects =
            new ConcurrentHashMap<>();
    private final Map<MemberKey, WorkProjectMember> members =
            new ConcurrentHashMap<>();

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public synchronized WorkProject create(
            WorkProject project, WorkProjectMember creatorOwner
    ) {
        var key = projectKey(project.systemId(), project.tenantId(), project.id());
        if (project.version() != 1
                || project.status() != WorkProject.Status.ACTIVE
                || creatorOwner.systemId() != project.systemId()
                || creatorOwner.tenantId() != project.tenantId()
                || creatorOwner.projectId() != project.id()
                || creatorOwner.memberId() != project.creatorMemberId()
                || creatorOwner.version() != 1
                || !creatorOwner.activeOwner()
                || projects.containsKey(key)) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT",
                    "Project creation snapshot is invalid or already exists");
        }
        projects.put(key, project);
        members.put(memberKey(creatorOwner), creatorOwner);
        return project;
    }

    @Override
    public Optional<WorkProject> findById(
            long systemId, long tenantId, long projectId
    ) {
        return Optional.ofNullable(
                projects.get(projectKey(systemId, tenantId, projectId)));
    }

    @Override
    public Optional<WorkProject> findVisibleById(
            long systemId,
            long tenantId,
            long projectId,
            long memberId,
            boolean manager
    ) {
        var project = findById(systemId, tenantId, projectId);
        if (manager) {
            return project;
        }
        return project.filter(ignored -> findMember(
                        systemId, tenantId, projectId, memberId)
                .filter(value -> value.status()
                        == WorkProjectMember.Status.ACTIVE)
                .isPresent());
    }

    @Override
    public WorkProjectPage findPage(
            long systemId,
            long tenantId,
            long memberId,
            boolean manager,
            WorkProjectQuery query
    ) {
        var keyword = query.keyword().toLowerCase(Locale.ROOT);
        var filtered = projects.values().stream()
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId)
                .filter(value -> manager || findMember(
                                systemId, tenantId, value.id(), memberId)
                        .filter(member -> member.status()
                                == WorkProjectMember.Status.ACTIVE)
                        .isPresent())
                .filter(value -> query.status()
                        == WorkProjectQuery.StatusFilter.ALL
                        || value.status().name().equals(query.status().name()))
                .filter(value -> keyword.isEmpty()
                        || value.title().toLowerCase(Locale.ROOT)
                        .contains(keyword)
                        || value.description() != null
                        && value.description().toLowerCase(Locale.ROOT)
                        .contains(keyword))
                .sorted(projectOrder())
                .toList();
        var from = (int) Math.min(query.offset(), filtered.size());
        var to = Math.min(from + query.size(), filtered.size());
        return new WorkProjectPage(
                filtered.subList(from, to), query.page(), query.size(),
                filtered.size());
    }

    @Override
    public synchronized WorkProject save(WorkProject project) {
        var key = projectKey(project.systemId(), project.tenantId(), project.id());
        var current = projects.get(key);
        if (current == null || project.version() != current.version() + 1
                || project.creatorMemberId() != current.creatorMemberId()
                || !project.createdAt().equals(current.createdAt())) {
            throw conflict("WORK_PROJECT_VERSION_CONFLICT",
                    "Project version is stale");
        }
        projects.put(key, project);
        return project;
    }

    @Override
    public Optional<WorkProjectMember> findMember(
            long systemId,
            long tenantId,
            long projectId,
            long memberId
    ) {
        return Optional.ofNullable(members.get(
                new MemberKey(systemId, tenantId, projectId, memberId)));
    }

    @Override
    public synchronized Optional<WorkProjectMember> findMemberForUpdate(
            long systemId,
            long tenantId,
            long projectId,
            long memberId
    ) {
        return findMember(systemId, tenantId, projectId, memberId);
    }

    @Override
    public List<WorkProjectMember> findMembers(
            long systemId, long tenantId, long projectId
    ) {
        return members.values().stream()
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId
                        && value.projectId() == projectId
                        && value.status() == WorkProjectMember.Status.ACTIVE)
                .sorted(Comparator
                        .comparing((WorkProjectMember value) -> value.role()
                                == WorkProjectMember.Role.OWNER ? 0 : 1)
                        .thenComparing(WorkProjectMember::joinedAt)
                        .thenComparingLong(WorkProjectMember::memberId))
                .toList();
    }

    @Override
    public synchronized WorkProjectMember saveMember(
            WorkProjectMember member
    ) {
        if (findById(member.systemId(), member.tenantId(), member.projectId())
                .isEmpty()) {
            throw conflict("WORK_PROJECT_NOT_FOUND", "Project was not found");
        }
        var key = memberKey(member);
        var current = members.get(key);
        if (current == null) {
            if (member.version() != 1
                    || member.status() != WorkProjectMember.Status.ACTIVE) {
                throw conflict("WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                        "New project member must be active at version 1");
            }
        } else if (member.version() != current.version() + 1
                || !member.joinedAt().equals(current.joinedAt())) {
            throw conflict("WORK_PROJECT_MEMBER_VERSION_CONFLICT",
                    "Project member version is stale");
        }
        if (current != null && current.activeOwner()
                && !member.activeOwner()
                && activeOwnerCount(
                member.systemId(), member.tenantId(), member.projectId()) <= 1) {
            throw conflict("WORK_PROJECT_LAST_OWNER_INVALID",
                    "A project must retain at least one active owner");
        }
        members.put(key, member);
        return member;
    }

    private long activeOwnerCount(long systemId, long tenantId, long projectId) {
        return members.values().stream()
                .filter(value -> value.systemId() == systemId
                        && value.tenantId() == tenantId
                        && value.projectId() == projectId
                        && value.activeOwner())
                .count();
    }

    private static Comparator<WorkProject> projectOrder() {
        return Comparator.comparing(WorkProject::updatedAt).reversed()
                .thenComparing(Comparator.comparingLong(WorkProject::id)
                        .reversed());
    }

    private static ProjectKey projectKey(
            long systemId, long tenantId, long projectId
    ) {
        return new ProjectKey(systemId, tenantId, projectId);
    }

    private static MemberKey memberKey(WorkProjectMember value) {
        return new MemberKey(value.systemId(), value.tenantId(),
                value.projectId(), value.memberId());
    }

    private static WorkDomainException conflict(String code, String message) {
        return new WorkDomainException(code, message);
    }

    private record ProjectKey(long systemId, long tenantId, long projectId) {
    }

    private record MemberKey(
            long systemId, long tenantId, long projectId, long memberId
    ) {
    }
}
