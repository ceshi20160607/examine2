package com.unique.examine.work.port;

import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectPage;
import com.unique.examine.work.domain.WorkProjectQuery;

import java.util.List;
import java.util.Optional;

public interface WorkProjectRepository {
    long nextId();

    WorkProject create(WorkProject project, WorkProjectMember creatorOwner);

    Optional<WorkProject> findById(
            long systemId, long tenantId, long projectId);

    Optional<WorkProject> findVisibleById(
            long systemId,
            long tenantId,
            long projectId,
            long memberId,
            boolean manager
    );

    WorkProjectPage findPage(
            long systemId,
            long tenantId,
            long memberId,
            boolean manager,
            WorkProjectQuery query
    );

    WorkProject save(WorkProject project);

    Optional<WorkProjectMember> findMember(
            long systemId,
            long tenantId,
            long projectId,
            long memberId
    );

    Optional<WorkProjectMember> findMemberForUpdate(
            long systemId,
            long tenantId,
            long projectId,
            long memberId
    );

    List<WorkProjectMember> findMembers(
            long systemId, long tenantId, long projectId);

    WorkProjectMember saveMember(WorkProjectMember member);
}
