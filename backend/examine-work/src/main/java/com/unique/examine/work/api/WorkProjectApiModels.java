package com.unique.examine.work.api;

import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.domain.WorkProjectPage;

import java.util.List;

public final class WorkProjectApiModels {
    private WorkProjectApiModels() {
    }

    public record CreateProject(String title, String description) {
    }

    public record UpdateProject(
            String title, String description, long version
    ) {
    }

    public record ProjectVersion(long version) {
    }

    public record AddMember(
            long memberId, WorkProjectMember.Role role
    ) {
    }

    public record UpdateMember(
            WorkProjectMember.Role role, long version
    ) {
    }

    public record MemberVersion(long version) {
    }

    public record ProjectPage(
            List<ProjectView> items,
            int page,
            int size,
            long total
    ) {
        public ProjectPage {
            items = List.copyOf(items);
        }

        static ProjectPage from(WorkProjectPage value) {
            return new ProjectPage(
                    value.items().stream().map(ProjectView::from).toList(),
                    value.page(), value.size(), value.total());
        }
    }

    public record ProjectView(
            String id,
            String systemId,
            String tenantId,
            String creatorMemberId,
            String title,
            String description,
            String status,
            String createdAt,
            String updatedAt,
            long version
    ) {
        static ProjectView from(WorkProject value) {
            return new ProjectView(
                    Long.toString(value.id()),
                    Long.toString(value.systemId()),
                    Long.toString(value.tenantId()),
                    Long.toString(value.creatorMemberId()),
                    value.title(), value.description(), value.status().name(),
                    value.createdAt().toString(), value.updatedAt().toString(),
                    value.version());
        }
    }

    public record MemberView(
            String projectId,
            String memberId,
            String role,
            String status,
            String joinedAt,
            String updatedAt,
            long version
    ) {
        static MemberView from(WorkProjectMember value) {
            return new MemberView(
                    Long.toString(value.projectId()),
                    Long.toString(value.memberId()),
                    value.role().name(), value.status().name(),
                    value.joinedAt().toString(), value.updatedAt().toString(),
                    value.version());
        }
    }
}
