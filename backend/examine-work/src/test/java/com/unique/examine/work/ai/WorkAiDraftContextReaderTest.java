package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.work.adapter.memory.InMemoryWorkDailyReportRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkProjectRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkProjectMember;
import com.unique.examine.work.port.WorkMemberDirectory;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkProjectService;
import com.unique.examine.work.service.WorkTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkAiDraftContextReaderTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Set<String> TASK_PERMISSIONS = Set.of(
            "ai.agent.use", WorkTaskService.ACCESS, WorkTaskService.CREATE);
    private static final Set<String> REPORT_PERMISSIONS = Set.of(
            "ai.agent.use", WorkDailyReportService.ACCESS,
            WorkDailyReportService.CREATE);

    private AtomicReference<EffectivePermissionFacade.Evaluation> live;
    private WorkProjectService projects;
    private WorkDailyReportService reports;
    private WorkAiDraftContextReader reader;

    @BeforeEach
    void setUp() {
        live = new AtomicReference<>(evaluation(9, TASK_PERMISSIONS));
        WorkMemberDirectory members = (system, tenant, member) ->
                system == 10 && tenant == 20
                        && Set.of(100L, 101L).contains(member);
        projects = new WorkProjectService(
                new InMemoryWorkProjectRepository(), members, CLOCK);
        reports = new WorkDailyReportService(
                new InMemoryWorkDailyReportRepository(members), members,
                CLOCK);
        reader = new WorkAiDraftContextReader(
                (system, tenant, member) -> live.get(), members,
                projects, reports, CLOCK);
    }

    @Test
    void taskRequiresLiveActiveVisibleProjectAndAssigneeMembership() {
        var owner = new WorkActor(10, 20, 100,
                Set.of(WorkProjectService.ACCESS));
        var project = projects.create(owner, "Release", null);
        projects.addMember(owner, project.id(), 101,
                WorkProjectMember.Role.MEMBER);

        var actor = reader.validate(
                access(TASK_PERMISSIONS,
                        AiWorkDraftFacade.Operation.WORK_TASK_DRAFT),
                new AiWorkDraftFacade.TaskDraft(
                        "Ship", null, "101", Long.toString(project.id()),
                        NOW.plusSeconds(3600)), null);

        assertThat(actor.memberId()).isEqualTo(100);
        assertThatThrownBy(() -> reader.validateFacts(
                actor, AiWorkDraftFacade.Operation.WORK_TASK_DRAFT,
                new AiWorkDraftFacade.TaskDraft(
                        "Ship", null, "999", null, null), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_WORK_ASSIGNEE_UNAVAILABLE"));

        var archived = projects.archive(owner, project.id(), project.version());
        assertThatThrownBy(() -> reader.validateFacts(
                actor, AiWorkDraftFacade.Operation.WORK_TASK_DRAFT,
                new AiWorkDraftFacade.TaskDraft(
                        "Ship", null, "101",
                        Long.toString(archived.id()), null), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_WORK_PROJECT_UNAVAILABLE"));
    }

    @Test
    void reportRejectsFutureAndExistingCurrentMemberDate() {
        live.set(evaluation(9, REPORT_PERMISSIONS));
        var access = access(REPORT_PERMISSIONS,
                AiWorkDraftFacade.Operation.WORK_DAILY_REPORT_DRAFT);
        var today = LocalDate.of(2026, 8, 4);
        reader.validate(access, null, report(today));

        assertThatThrownBy(() -> reader.validate(
                access, null, report(today.plusDays(1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_WORK_DRAFT_INVALID"));

        reports.create(new WorkActor(10, 20, 100, REPORT_PERMISSIONS),
                today, "Done", "Next", null);
        assertThatThrownBy(() -> reader.validate(access, null, report(today)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_WORK_REPORT_EXISTS"));
    }

    @Test
    void permissionRevocationIsDeniedWhileOtherAuthorizationChangesAreStale() {
        var access = access(TASK_PERMISSIONS,
                AiWorkDraftFacade.Operation.WORK_TASK_DRAFT);
        live.set(evaluation(10, Set.of(
                "ai.agent.use", WorkTaskService.ACCESS)));
        assertCode(() -> reader.authorize(access),
                "AI_WORK_PERMISSION_DENIED");

        live.set(evaluation(10, TASK_PERMISSIONS));
        assertCode(() -> reader.authorize(access),
                "AI_WORK_AUTHORIZATION_STALE");

        var changed = new java.util.HashSet<>(TASK_PERMISSIONS);
        changed.add("work.task.manage");
        live.set(evaluation(9, Set.copyOf(changed)));
        assertCode(() -> reader.authorize(access),
                "AI_WORK_AUTHORIZATION_STALE");
    }

    private static WorkAiDraftContextReader.Access access(
            Set<String> permissions, AiWorkDraftFacade.Operation operation) {
        return new WorkAiDraftContextReader.Access(
                10, 20, 100, 9, permissions, operation);
    }

    private static AiWorkDraftFacade.DailyReportDraft report(LocalDate date) {
        return new AiWorkDraftFacade.DailyReportDraft(
                date, "Done", "Next", null);
    }

    private static EffectivePermissionFacade.Evaluation evaluation(
            long epoch, Set<String> permissions) {
        return new EffectivePermissionFacade.Evaluation(
                epoch, false, permissions, List.of(), List.of());
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }
}
