package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkProject;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkProjectService;
import com.unique.examine.work.service.WorkTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/** Live Work authorization and fact checks shared by prepare and execute. */
@Component
public class WorkAiDraftContextReader {
    private static final String AGENT_USE = "ai.agent.use";

    private final EffectivePermissionFacade authorization;
    private final WorkMemberDirectory members;
    private final WorkProjectService projects;
    private final WorkDailyReportService reports;
    private final Clock clock;

    public WorkAiDraftContextReader(
            EffectivePermissionFacade authorization,
            WorkMemberDirectory members,
            WorkProjectService projects,
            WorkDailyReportService reports,
            Clock clock
    ) {
        this.authorization = Objects.requireNonNull(
                authorization, "authorization");
        this.members = Objects.requireNonNull(members, "members");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public WorkActor validate(
            Access access,
            AiWorkDraftFacade.TaskDraft task,
            AiWorkDraftFacade.DailyReportDraft report
    ) {
        var actor = authorize(access);
        validateFacts(actor, access.operation(), task, report);
        return actor;
    }

    @Transactional(readOnly = true)
    public WorkActor authorize(Access access) {
        Objects.requireNonNull(access, "access");
        var live = authorization.evaluateSystem(
                access.systemId(), access.tenantId(), access.memberId());
        requirePermissions(live.permissions(), access.operation());
        if (live.epoch() != access.authorizationEpoch()
                || !live.permissions().equals(access.effectivePermissions())) {
            throw new BusinessException(
                    "AI_WORK_AUTHORIZATION_STALE",
                    "Work authorization changed before the draft operation",
                    HttpStatus.CONFLICT);
        }
        var actor = new WorkActor(
                access.systemId(), access.tenantId(), access.memberId(),
                live.permissions());
        return actor;
    }

    @Transactional(readOnly = true)
    public void validateFacts(
            WorkActor actor,
            AiWorkDraftFacade.Operation operation,
            AiWorkDraftFacade.TaskDraft task,
            AiWorkDraftFacade.DailyReportDraft report
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(operation, "operation");
        if (operation
                == AiWorkDraftFacade.Operation.WORK_TASK_DRAFT) {
            validateTask(actor, Objects.requireNonNull(task, "task"));
        } else {
            validateReport(actor, Objects.requireNonNull(report, "report"));
        }
    }

    private void validateTask(
            WorkActor actor, AiWorkDraftFacade.TaskDraft task) {
        var assigneeId = Long.parseLong(task.assigneeMemberId());
        if (!members.isActiveMember(
                actor.systemId(), actor.tenantId(), assigneeId)) {
            throw stale(
                    "AI_WORK_ASSIGNEE_UNAVAILABLE",
                    "The proposed task assignee is not an active tenant member");
        }
        if (task.projectId() == null) return;
        var projectId = Long.parseLong(task.projectId());
        final WorkProject project;
        try {
            project = projects.get(actor, projectId);
        } catch (RuntimeException failure) {
            throw stale(
                    "AI_WORK_PROJECT_UNAVAILABLE",
                    "The proposed task project is not visible", failure);
        }
        if (project.status() != WorkProject.Status.ACTIVE
                || projects.members(actor, projectId).stream()
                .filter(member -> member.memberId() == assigneeId)
                .noneMatch(member -> member.status()
                        == WorkProjectMember.Status.ACTIVE)) {
            throw stale(
                    "AI_WORK_PROJECT_UNAVAILABLE",
                    "The proposed task project or assignee membership is unavailable");
        }
    }

    private void validateReport(
            WorkActor actor, AiWorkDraftFacade.DailyReportDraft report) {
        if (report.workDate().isAfter(LocalDate.now(clock))) {
            throw new BusinessException(
                    "AI_WORK_DRAFT_INVALID",
                    "workDate cannot be in the future",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        var existing = reports.page(
                actor, WorkDailyReportQuery.Scope.SELF, null,
                report.workDate(), report.workDate(),
                WorkDailyReportQuery.StatusFilter.ALL, 1, 1);
        if (existing.total() != 0) {
            throw stale(
                    "AI_WORK_REPORT_EXISTS",
                    "A daily report already exists for the proposed work date");
        }
    }

    private static void requirePermissions(
            Set<String> permissions,
            AiWorkDraftFacade.Operation operation
    ) {
        var required = operation
                == AiWorkDraftFacade.Operation.WORK_TASK_DRAFT
                ? Set.of(AGENT_USE, WorkTaskService.ACCESS,
                WorkTaskService.CREATE)
                : Set.of(AGENT_USE, WorkDailyReportService.ACCESS,
                WorkDailyReportService.CREATE);
        if (!permissions.containsAll(required)) {
            throw new BusinessException(
                    "AI_WORK_PERMISSION_DENIED",
                    "AI Agent use and the matching Work create permissions are required",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static BusinessException stale(
            String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    private static BusinessException stale(
            String code, String message, Throwable cause) {
        var failure = stale(code, message);
        failure.initCause(cause);
        return failure;
    }

    public record Access(
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            AiWorkDraftFacade.Operation operation
    ) {
        public Access {
            if (systemId <= 0 || tenantId <= 0 || memberId <= 0
                    || authorizationEpoch <= 0) {
                throw new IllegalArgumentException(
                        "Work AI draft access identity is invalid");
            }
            effectivePermissions = Set.copyOf(Objects.requireNonNull(
                    effectivePermissions, "effectivePermissions"));
            operation = Objects.requireNonNull(operation, "operation");
        }
    }
}
