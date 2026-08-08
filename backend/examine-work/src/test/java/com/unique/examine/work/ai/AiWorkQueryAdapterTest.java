package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkQueryFacade;
import com.unique.examine.work.adapter.memory.InMemoryWorkDailyReportRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkProjectRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkMetricsFacade;
import com.unique.examine.work.service.WorkProjectService;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkQueryAdapterTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 4);
    private static final WorkMemberDirectory MEMBERS = (system, tenant, member) ->
            system == 10 && tenant == 20 && Set.of(100L, 101L, 102L).contains(member);

    private WorkTaskService taskService;
    private WorkDailyReportService reportService;
    private WorkProjectService projectService;
    private AiWorkQueryAdapter adapter;
    private WorkActor projectOwner;
    private long releaseProjectId;

    @BeforeEach
    void setUp() {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var projects = new InMemoryWorkProjectRepository();
        projectService = new WorkProjectService(projects, MEMBERS, clock);
        projectOwner = actor(
                100, WorkTaskService.ACCESS, WorkTaskService.CREATE);
        var release = projectService.create(
                projectOwner, "Release", null);
        projectService.addMember(
                projectOwner, release.id(), 101,
                WorkProjectMember.Role.MEMBER);
        var other = projectService.create(projectOwner, "Other", null);
        projectService.addMember(
                projectOwner, other.id(), 101,
                WorkProjectMember.Role.MEMBER);
        releaseProjectId = release.id();

        var taskRepository = new InMemoryWorkTaskRepository(projects);
        taskService = new WorkTaskService(
                taskRepository, projects,
                MEMBERS, clock);
        taskService.create(
                projectOwner, "Release checklist", 101, release.id(),
                "Transient release detail", NOW.plusSeconds(86_400));
        var completed = taskService.create(
                projectOwner, "Release completed", 101, release.id(),
                null, NOW.plusSeconds(86_400));
        taskService.complete(actor(101), completed.id());
        taskService.create(
                projectOwner, "Other release task", 101, other.id(),
                null, NOW.plusSeconds(10 * 86_400));

        var reportRepository = new InMemoryWorkDailyReportRepository(MEMBERS);
        reportService = new WorkDailyReportService(
                reportRepository, MEMBERS, clock);
        var first = reportService.create(
                reportAuthor(100), TODAY.minusDays(1),
                "Submitted work", "Next", null);
        reportService.submit(reportReader(100), first.id(), first.version());
        reportService.create(
                reportAuthor(101), TODAY, "Draft work", "Next draft", "Waiting");

        adapter = new AiWorkQueryAdapter(
                taskService, reportService, projectService,
                new WorkMetricsFacade(taskRepository, clock));
    }

    @Test
    void taskQueryPreservesNativeRoleStatusProjectDueAndPermissionSemantics()
            throws Exception {
        var result = adapter.taskQuery(new AiWorkQueryFacade.TaskRequest(
                10, 20, 101, Set.of(WorkTaskService.ACCESS),
                "release", releaseProjectId,
                NOW, NOW.plusSeconds(2 * 86_400),
                AiWorkQueryFacade.TaskStatus.OPEN,
                AiWorkQueryFacade.TaskRole.ASSIGNED_TO_ME, 10));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.tasks()).singleElement().satisfies(task -> {
            assertThat(task.title()).isEqualTo("Release checklist");
            assertThat(task.description()).isEqualTo("Transient release detail");
            assertThat(task.projectId()).isEqualTo(Long.toString(releaseProjectId));
            assertThat(task.assigneeMemberId()).isEqualTo("101");
        });
        assertThat(AiWorkQueryAdapter.class.getMethod(
                        "taskQuery", AiWorkQueryFacade.TaskRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void reportQueryPreservesSelfTeamActiveMemberAndStatusVisibility()
            throws Exception {
        var manager = new AiWorkQueryFacade.DailyReportRequest(
                10, 20, 102,
                Set.of(WorkDailyReportService.ACCESS, WorkDailyReportService.MANAGE),
                AiWorkQueryFacade.ReportScope.ALL, "101",
                TODAY.minusDays(6), TODAY,
                AiWorkQueryFacade.ReportStatus.DRAFT, 7);

        var result = adapter.dailyReportQuery(manager);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.reports()).singleElement().satisfies(report -> {
            assertThat(report.authorMemberId()).isEqualTo("101");
            assertThat(report.status()).isEqualTo("DRAFT");
            assertThat(report.completedWork()).isEqualTo("Draft work");
        });
        assertThat(AiWorkQueryAdapter.class.getMethod(
                        "dailyReportQuery", AiWorkQueryFacade.DailyReportRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();

        var anotherMember = new AiWorkQueryFacade.DailyReportRequest(
                10, 20, 100, Set.of(WorkDailyReportService.ACCESS),
                AiWorkQueryFacade.ReportScope.SELF, "101",
                TODAY.minusDays(6), TODAY,
                AiWorkQueryFacade.ReportStatus.ALL, 7);
        assertThatThrownBy(() -> adapter.dailyReportQuery(anotherMember))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_NOT_FOUND"));
    }

    @Test
    void livePermissionRevocationAndTenantIsolationCannotBeBypassed() {
        var deniedTask = new AiWorkQueryFacade.TaskRequest(
                10, 20, 101, Set.of(), "", null, null, null,
                AiWorkQueryFacade.TaskStatus.ALL,
                AiWorkQueryFacade.TaskRole.PARTICIPATING, 10);
        assertThatThrownBy(() -> adapter.taskQuery(deniedTask))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_TASK_FORBIDDEN"));

        var crossTenant = new AiWorkQueryFacade.TaskRequest(
                10, 21, 101, Set.of(WorkTaskService.ACCESS),
                "", null, null, null,
                AiWorkQueryFacade.TaskStatus.ALL,
                AiWorkQueryFacade.TaskRole.PARTICIPATING, 10);
        assertThat(adapter.taskQuery(crossTenant).tasks()).isEmpty();

        var deniedReports = new AiWorkQueryFacade.DailyReportRequest(
                10, 20, 101, Set.of(), AiWorkQueryFacade.ReportScope.SELF,
                null, null, null, AiWorkQueryFacade.ReportStatus.ALL, 10);
        assertThatThrownBy(() -> adapter.dailyReportQuery(deniedReports))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_REPORT_FORBIDDEN"));
    }

    @Test
    void projectMetricsUsesVisibleProjectAndProjectScopedTaskPermissions()
            throws Exception {
        var memberResult = adapter.projectMetrics(projectMetrics(
                20, 101, Set.of(WorkTaskService.ACCESS)));

        assertThat(memberResult.projectId())
                .isEqualTo(Long.toString(releaseProjectId));
        assertThat(memberResult.title()).isEqualTo("Release");
        assertThat(memberResult.status()).isEqualTo("ACTIVE");
        assertThat(memberResult.visibility())
                .isEqualTo(AiWorkQueryFacade.ProjectVisibility.PARTICIPATING);
        assertThat(memberResult.total()).isEqualTo(2);
        assertThat(memberResult.open()).isEqualTo(1);
        assertThat(memberResult.completed()).isEqualTo(1);
        assertThat(memberResult.completedInRange().count()).isEqualTo(1);
        assertThat(memberResult.daily()).singleElement().satisfies(day -> {
            assertThat(day.createdCount()).isEqualTo(2);
            assertThat(day.completedCount()).isEqualTo(1);
        });
        assertThat(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(
                                memberResult.overdueOpen().route(),
                                memberResult.dueInRangeOpen().route(),
                                memberResult.completedInRange().route()),
                        java.util.stream.Stream.concat(
                                memberResult.daily().stream().flatMap(day ->
                                        java.util.stream.Stream.of(
                                                day.createdRoute(),
                                                day.completedRoute())),
                                memberResult.topAssignees().stream()
                                        .map(AiWorkQueryFacade.AssigneeOpen::route))))
                .allMatch(route -> route.contains(
                        "projectId=" + releaseProjectId));

        var projectManagerOnly = adapter.projectMetrics(projectMetrics(
                20, 102, Set.of(
                        WorkTaskService.ACCESS, WorkProjectService.MANAGE)));
        assertThat(projectManagerOnly.visibility())
                .isEqualTo(AiWorkQueryFacade.ProjectVisibility.PARTICIPATING);
        assertThat(projectManagerOnly.total()).isZero();

        var taskAndProjectManager = adapter.projectMetrics(projectMetrics(
                20, 102, Set.of(
                        WorkTaskService.ACCESS, WorkProjectService.MANAGE,
                        WorkTaskService.MANAGE)));
        assertThat(taskAndProjectManager.visibility())
                .isEqualTo(AiWorkQueryFacade.ProjectVisibility.ALL);
        assertThat(taskAndProjectManager.total()).isEqualTo(2);

        var membership = projectService.members(
                        projectOwner, releaseProjectId).stream()
                .filter(value -> value.memberId() == 101)
                .findFirst().orElseThrow();
        projectService.removeMember(
                projectOwner, releaseProjectId, 101, membership.version());
        assertThatThrownBy(() -> adapter.projectMetrics(projectMetrics(
                20, 101, Set.of(WorkTaskService.ACCESS))))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_NOT_FOUND"));
        assertThatThrownBy(() -> adapter.projectMetrics(projectMetrics(
                21, 101, Set.of(
                        WorkTaskService.ACCESS,
                        WorkProjectService.MANAGE))))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_NOT_FOUND"));

        assertThat(AiWorkQueryAdapter.class.getMethod(
                        "projectMetrics",
                        AiWorkQueryFacade.ProjectMetricsRequest.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    private AiWorkQueryFacade.ProjectMetricsRequest projectMetrics(
            long tenantId,
            long memberId,
            Set<String> permissions
    ) {
        return new AiWorkQueryFacade.ProjectMetricsRequest(
                10, tenantId, memberId, permissions,
                Long.toString(releaseProjectId), TODAY, TODAY.plusDays(1));
    }

    private static WorkActor actor(long memberId, String... permissions) {
        return new WorkActor(10, 20, memberId, Set.of(permissions));
    }

    private static WorkActor reportAuthor(long memberId) {
        return actor(memberId, WorkDailyReportService.CREATE,
                WorkDailyReportService.ACCESS);
    }

    private static WorkActor reportReader(long memberId) {
        return actor(memberId, WorkDailyReportService.ACCESS);
    }
}
