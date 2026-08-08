package com.unique.examine.work.service;

import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkProjectRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskReminderRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkTaskServiceTest {
    private InMemoryWorkTaskRepository repository;
    private WorkTaskService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryWorkTaskRepository();
        service = new WorkTaskService(repository,
                (systemId, tenantId, memberId) -> systemId == 10 && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createAssignCompleteAndReopenFormAClosedLifecycle() {
        var creator = actor(100, WorkTaskService.CREATE);
        var task = service.create(creator, "Prepare release notes", 101);
        assertThat(task.status()).isEqualTo(WorkTask.Status.OPEN);
        assertThat(task.assigneeMemberId()).isEqualTo(101);

        task = service.assign(creator, task.id(), 102);
        assertThat(task.assigneeMemberId()).isEqualTo(102);

        task = service.complete(actor(102), task.id());
        assertThat(task.status()).isEqualTo(WorkTask.Status.COMPLETED);

        task = service.reopen(creator, task.id());
        assertThat(task.status()).isEqualTo(WorkTask.Status.OPEN);
        assertThat(task.version()).isEqualTo(4);
    }

    @Test
    void permissionsProtectCreateAssignCompleteAndReopen() {
        assertThatThrownBy(() -> service.create(actor(100), "Denied", 101))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_FORBIDDEN"));

        var task = service.create(actor(100, WorkTaskService.CREATE), "Owned", 101);
        assertThatThrownBy(() -> service.assign(actor(101), task.id(), 102))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_FORBIDDEN"));
        assertThatThrownBy(() -> service.complete(actor(102), task.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_FORBIDDEN"));

        var completed = service.complete(actor(101), task.id());
        assertThatThrownBy(() -> service.reopen(actor(101), completed.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_FORBIDDEN"));
    }

    @Test
    void managerCanOperateButCrossTenantAndInactiveAssignmentsAreHidden() {
        var task = service.create(actor(100, WorkTaskService.CREATE), "Managed", 101);
        var manager = actor(102, WorkTaskService.MANAGE);
        assertThat(service.complete(manager, task.id()).status()).isEqualTo(WorkTask.Status.COMPLETED);
        assertThat(service.reopen(manager, task.id()).status()).isEqualTo(WorkTask.Status.OPEN);

        assertThatThrownBy(() -> service.get(
                new WorkActor(10, 21, 102, Set.of(WorkTaskService.MANAGE)), task.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_NOT_FOUND"));
        assertThatThrownBy(() -> service.assign(manager, task.id(), 999))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_ASSIGNEE_INVALID"));
    }

    @Test
    void invalidStateTransitionsAreRejected() {
        var creator = actor(100, WorkTaskService.CREATE);
        var task = service.create(creator, "Stateful", 101);
        assertThatThrownBy(() -> service.reopen(creator, task.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_STATE_INVALID"));

        var completed = service.complete(actor(101), task.id());
        assertThatThrownBy(() -> service.complete(actor(101), completed.id()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_STATE_INVALID"));
        assertThatThrownBy(() -> service.assign(creator, completed.id(), 102))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_STATE_INVALID"));
    }

    @Test
    void pagesByStatusRoleAndLiteralKeywordInStableUnfinishedFirstOrder() {
        var first = service.create(
                actor(100, WorkTaskService.CREATE),
                "100% _literal_ task",
                101);
        var second = service.create(
                actor(100, WorkTaskService.CREATE),
                "Another task",
                102);
        var third = service.create(
                actor(101, WorkTaskService.CREATE),
                "Assigned elsewhere",
                102);
        service.complete(actor(101), first.id());

        var participating = service.page(
                actor(100, WorkTaskService.ACCESS),
                query("", WorkTaskQuery.StatusFilter.ALL,
                        WorkTaskQuery.RoleFilter.PARTICIPATING, 1, 20));
        assertThat(participating.items()).extracting(WorkTask::id)
                .containsExactly(second.id(), first.id());

        var completedLiteral = service.page(
                actor(100, WorkTaskService.ACCESS),
                query("% _literal_", WorkTaskQuery.StatusFilter.COMPLETED,
                        WorkTaskQuery.RoleFilter.CREATED_BY_ME, 1, 20));
        assertThat(completedLiteral.items()).extracting(WorkTask::id)
                .containsExactly(first.id());

        var assigned = service.page(
                actor(102, WorkTaskService.ACCESS),
                query("", WorkTaskQuery.StatusFilter.OPEN,
                        WorkTaskQuery.RoleFilter.ASSIGNED_TO_ME, 1, 1));
        assertThat(assigned.total()).isEqualTo(2);
        assertThat(assigned.items()).extracting(WorkTask::id)
                .containsExactly(third.id());
        assertThat(assigned.page()).isEqualTo(1);
        assertThat(assigned.size()).isEqualTo(1);
    }

    @Test
    void allRoleRequiresManageAndEveryPageRemainsTenantScoped() {
        service.create(actor(100, WorkTaskService.CREATE), "Tenant 20", 101);
        repository.save(new WorkTask(
                repository.nextId(),
                10,
                21,
                100,
                101,
                "Tenant 21",
                WorkTask.Status.OPEN,
                Instant.parse("2026-07-25T08:00:00Z"),
                Instant.parse("2026-07-25T08:00:00Z"),
                1));
        var all = query(
                "",
                WorkTaskQuery.StatusFilter.ALL,
                WorkTaskQuery.RoleFilter.ALL,
                1,
                20);

        assertThatThrownBy(() -> service.page(actor(100, WorkTaskService.ACCESS), all))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_FORBIDDEN"));

        var managed = service.page(
                actor(102, WorkTaskService.ACCESS, WorkTaskService.MANAGE),
                all);
        assertThat(managed.total()).isEqualTo(1);
        assertThat(managed.items()).allMatch(task -> task.tenantId() == 20);
    }

    @Test
    void pageRequiresAccessAndValidatesKeywordAndBounds() {
        var defaults = query(
                "",
                WorkTaskQuery.StatusFilter.ALL,
                WorkTaskQuery.RoleFilter.PARTICIPATING,
                1,
                20);
        assertThatThrownBy(() -> service.page(actor(100), defaults))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_FORBIDDEN"));
        assertThatThrownBy(() -> new WorkTaskQuery(
                "x".repeat(101),
                WorkTaskQuery.StatusFilter.ALL,
                WorkTaskQuery.RoleFilter.PARTICIPATING,
                1,
                20))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_KEYWORD_INVALID"));
        assertThatThrownBy(() -> query(
                "",
                WorkTaskQuery.StatusFilter.ALL,
                WorkTaskQuery.RoleFilter.PARTICIPATING,
                1,
                101))
                .isInstanceOfSatisfying(
                        WorkDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("WORK_TASK_SIZE_INVALID"));
    }

    @Test
    void projectMetadataDueWindowAndLegacyStandaloneShareOneTaskFact() {
        var taskRepository = new InMemoryWorkTaskRepository();
        var projectRepository = new InMemoryWorkProjectRepository();
        var memberDirectory = new com.unique.examine.work.port.WorkMemberDirectory() {
            @Override
            public boolean isActiveMember(
                    long systemId, long tenantId, long memberId
            ) {
                return systemId == 10 && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId);
            }
        };
        var clock = Clock.fixed(
                Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC);
        var projects = new WorkProjectService(
                projectRepository, memberDirectory, clock);
        var tasks = new WorkTaskService(
                taskRepository, projectRepository, memberDirectory, clock);
        var owner = actor(
                100, WorkProjectService.ACCESS, WorkTaskService.CREATE);
        var project = projects.create(owner, "Release", null);
        projects.addMember(
                owner, project.id(), 101,
                com.unique.examine.work.domain.WorkProjectMember.Role.MEMBER);
        var dueAt = Instant.parse("2026-08-01T09:30:00Z");

        var linked = tasks.create(
                owner, "Ship", 101, project.id(), "Production release",
                dueAt);
        var standalone = tasks.create(
                owner, "Legacy", 101);
        assertThat(standalone.projectId()).isNull();
        assertThat(standalone.description()).isNull();
        assertThat(standalone.dueAt()).isNull();

        var page = tasks.page(
                new WorkActor(10, 20, 101,
                        Set.of(WorkTaskService.ACCESS)),
                new WorkTaskQuery(
                        "", WorkTaskQuery.StatusFilter.ALL,
                        WorkTaskQuery.RoleFilter.PARTICIPATING, 1, 20,
                        project.id(), dueAt.minusSeconds(1),
                        dueAt.plusSeconds(1)));
        assertThat(page.items()).extracting(WorkTask::id)
                .containsExactly(linked.id());

        var moved = tasks.updateMetadata(
                owner, linked.id(), "Ship now", null, null, null,
                linked.version());
        assertThat(moved.id()).isEqualTo(linked.id());
        assertThat(moved.projectId()).isNull();
        assertThat(moved.version()).isEqualTo(linked.version() + 1);

        project = projects.archive(owner, project.id(), project.version());
        var archived = project;
        assertThatThrownBy(() -> tasks.create(
                owner, "Rejected", 101, archived.id(), null, null))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_PROJECT_STATE_INVALID"));
    }

    @Test
    void creatorAssigneeAndManagerControlReminderGenerationsWithoutLeakingLease() {
        var taskRepository = new InMemoryWorkTaskRepository();
        var reminderRepository = new InMemoryWorkTaskReminderRepository();
        var service = reminderService(
                taskRepository, reminderRepository,
                Instant.parse("2026-08-01T08:00:00Z"));
        var dueAt = Instant.parse("2026-08-01T12:00:00Z");
        var firstAt = Instant.parse("2026-08-01T09:00:00Z");
        var secondAt = Instant.parse("2026-08-01T10:00:00Z");
        var creator = actor(100, WorkTaskService.CREATE);

        var task = service.create(
                creator, "Reminder task", 101,
                null, "Description", dueAt, firstAt);
        var first = service.latestReminder(creator, task.id()).orElseThrow();
        assertThat(first.generation()).isEqualTo(1);
        assertThat(first.status())
                .isEqualTo(com.unique.examine.work.domain.WorkTaskReminder
                        .Status.PENDING);

        task = service.updateMetadata(
                actor(101), task.id(), task.title(), task.projectId(),
                task.description(), task.dueAt(), secondAt, true,
                task.version());
        var second = service.latestReminder(actor(101), task.id())
                .orElseThrow();
        assertThat(second.generation()).isEqualTo(2);
        assertThat(reminderRepository.find(
                        10, 20, task.id(), 1).orElseThrow().status())
                .isEqualTo(com.unique.examine.work.domain.WorkTaskReminder
                        .Status.CANCELLED);

        task = service.updateMetadata(
                creator, task.id(), "Renamed", task.projectId(),
                task.description(), task.dueAt(), task.version());
        assertThat(task.reminderAt()).isEqualTo(secondAt);
        assertThat(service.latestReminder(creator, task.id()).orElseThrow()
                .generation()).isEqualTo(2);

        var current = task;
        assertThatThrownBy(() -> service.updateMetadata(
                actor(102), current.id(), current.title(), current.projectId(),
                current.description(), current.dueAt(), null, true,
                current.version()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_TASK_FORBIDDEN"));

        task = service.updateMetadata(
                actor(101), task.id(), task.title(), task.projectId(),
                task.description(), task.dueAt(), null, true,
                task.version());
        assertThat(task.reminderAt()).isNull();
        var cancelled = service.latestReminder(creator, task.id())
                .orElseThrow();
        assertThat(cancelled.status())
                .isEqualTo(com.unique.examine.work.domain.WorkTaskReminder
                        .Status.CANCELLED);
        assertThat(cancelled.lease()).isNull();
    }

    @Test
    void temporalRulesCompletionCancellationReopenAndManagerRetryAreExact() {
        var tasks = new InMemoryWorkTaskRepository();
        var reminders = new InMemoryWorkTaskReminderRepository();
        var createdAt = Instant.parse("2026-08-01T08:00:00Z");
        var service = reminderService(tasks, reminders, createdAt);
        var creator = actor(100, WorkTaskService.CREATE);
        var dueAt = createdAt.plusSeconds(7_200);

        assertThatThrownBy(() -> service.create(
                creator, "Past", 101, null, null, dueAt,
                createdAt.minusSeconds(1)))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_TASK_REMINDER_INVALID"));
        assertThatThrownBy(() -> service.create(
                creator, "After due", 101, null, null, dueAt,
                dueAt.plusSeconds(1)))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_TASK_REMINDER_INVALID"));

        var task = service.create(
                creator, "Complete", 101, null, null, dueAt,
                createdAt.plusSeconds(3_600));
        var completed = service.complete(actor(101), task.id());
        assertThat(completed.reminderAt()).isNull();
        assertThat(service.latestReminder(creator, task.id()).orElseThrow()
                .status()).isEqualTo(
                com.unique.examine.work.domain.WorkTaskReminder.Status.CANCELLED);
        assertThat(service.reopen(creator, task.id()).reminderAt()).isNull();

        var retryTask = service.create(
                creator, "Retry", 101, null, null, dueAt,
                createdAt.plusSeconds(60));
        var token = "a".repeat(64);
        var claimedAt = createdAt.plusSeconds(61);
        var failed = reminders.claimDue(
                        "worker", token, claimedAt,
                        claimedAt.plusSeconds(60), 10)
                .stream()
                .filter(value -> value.taskId() == retryTask.id())
                .findFirst().orElseThrow()
                .fail(token, "DELIVERY_FAILED", "failed",
                        claimedAt.plusSeconds(1));
        failed = reminders.save(failed);
        var retryService = reminderService(
                tasks, reminders, claimedAt.plusSeconds(2));
        var terminal = failed;
        assertThatThrownBy(() -> retryService.retryReminder(
                actor(100), retryTask.id(), terminal.version()))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("WORK_TASK_FORBIDDEN"));
        assertThatThrownBy(() -> retryService.retryReminder(
                actor(102, WorkTaskService.MANAGE), retryTask.id(),
                terminal.version() + 1))
                .isInstanceOfSatisfying(WorkDomainException.class,
                        error -> assertThat(error.code())
                                .isEqualTo(
                                        "WORK_TASK_REMINDER_VERSION_CONFLICT"));

        var retried = retryService.retryReminder(
                actor(102, WorkTaskService.MANAGE), retryTask.id(),
                terminal.version());
        assertThat(retried.status()).isEqualTo(
                com.unique.examine.work.domain.WorkTaskReminder.Status.PENDING);
        assertThat(retried.attemptCount()).isEqualTo(1);
        assertThat(retryService.get(
                actor(102, WorkTaskService.MANAGE), retryTask.id()).version())
                .isEqualTo(retryTask.version());
    }

    private static WorkTaskService reminderService(
            InMemoryWorkTaskRepository tasks,
            InMemoryWorkTaskReminderRepository reminders,
            Instant now
    ) {
        return new WorkTaskService(
                tasks, null, reminders,
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20
                        && Set.of(100L, 101L, 102L).contains(memberId),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static WorkTaskQuery query(
            String keyword,
            WorkTaskQuery.StatusFilter status,
            WorkTaskQuery.RoleFilter role,
            int page,
            int size
    ) {
        return new WorkTaskQuery(keyword, status, role, page, size);
    }

    private static WorkActor actor(long memberId, String... permissions) {
        return new WorkActor(10, 20, memberId, Set.of(permissions));
    }
}
