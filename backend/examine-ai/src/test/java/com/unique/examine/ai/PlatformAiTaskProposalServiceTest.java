package com.unique.examine.ai;

import com.unique.examine.ai.domain.PlatformAiConversation;
import com.unique.examine.ai.domain.PlatformAiPolicy;
import com.unique.examine.ai.plan.PlatformAiPlanParser;
import com.unique.examine.ai.service.PlatformAiTaskProposalService;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformAiTaskProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T05:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final PlatformAiPlanParser PARSER = new PlatformAiPlanParser();

    @Test
    void preparesWithoutOwnerWriteThenConfirmsOnceAndReplaysExactly() {
        var fixture = fixture();
        var prepared = fixture.service.prepare(
                fixture.actor, fixture.turn, fixture.policy, actionablePlan());
        fixture.repository.taskProposalValues.put(
                prepared.proposal().id(), prepared.proposal());

        assertThat(prepared.proposal().state().name()).isEqualTo("PENDING");
        assertThat(fixture.owner.tasks).isEmpty();
        assertThat(fixture.owner.prepareCalls).isEqualTo(1);

        var confirmed = fixture.service.confirm(
                fixture.actor, "31", Long.toString(prepared.proposal().id()),
                0, "confirm-1");
        var replay = fixture.service.confirm(
                fixture.actor, "31", Long.toString(prepared.proposal().id()),
                0, "confirm-1");

        assertThat(confirmed.state().name()).isEqualTo("SUCCEEDED");
        assertThat(replay.result().taskId()).isEqualTo(confirmed.result().taskId());
        assertThat(fixture.owner.tasks).hasSize(1);
        assertThat(fixture.owner.executeCalls).isEqualTo(1);
        assertThatThrownBy(() -> fixture.service.confirm(
                fixture.actor, "31", Long.toString(prepared.proposal().id()),
                1, "confirm-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("PLATFORM_AI_TASK_REPLAY_CONFLICT");
    }

    @Test
    void clarificationAndRejectionNeverExecuteTheOwner() {
        var fixture = fixture();
        var clarification = fixture.service.prepare(
                fixture.actor, fixture.turn, fixture.policy, PARSER.parse("""
                        {"operation":"PLATFORM_TASK_DRAFT","title":null,
                         "description":null,"dueAt":null,"priority":null,
                         "confidence":0.3,
                         "clarification":"What should the task title be?"}
                        """));
        assertThat(clarification.proposal().state().name())
                .isEqualTo("CLARIFICATION_REQUIRED");
        assertThat(clarification.proposal().sealedCommand()).isNull();
        assertThat(fixture.owner.prepareCalls).isZero();

        var pending = fixture.service.prepare(
                fixture.actor, fixture.turn, fixture.policy, actionablePlan());
        fixture.repository.taskProposalValues.put(
                pending.proposal().id(), pending.proposal());
        var rejected = fixture.service.reject(
                fixture.actor, "31", Long.toString(pending.proposal().id()), 0);

        assertThat(rejected.state().name()).isEqualTo("REJECTED");
        assertThat(fixture.owner.executeCalls).isZero();
        assertThat(fixture.owner.tasks).isEmpty();
    }

    @Test
    void enforcesAccountSessionExpiryPolicyAndLiveOwnerAuthorization() {
        var fixture = fixture();
        var prepared = fixture.service.prepare(
                fixture.actor, fixture.turn, fixture.policy, actionablePlan());
        fixture.repository.taskProposalValues.put(
                prepared.proposal().id(), prepared.proposal());

        assertThatThrownBy(() -> fixture.service.get(
                fixture.actor, "32", Long.toString(prepared.proposal().id())))
                .isInstanceOf(BusinessException.class);

        fixture.owner.revoked = true;
        var denied = fixture.service.confirm(
                fixture.actor, "31", Long.toString(prepared.proposal().id()),
                0, "confirm-revoked");
        assertThat(denied.state().name()).isEqualTo("FAILED");
        assertThat(denied.resultCode()).isEqualTo("PLATFORM_TASK_PERMISSION_DENIED");
        assertThat(fixture.owner.tasks).isEmpty();

        fixture.owner.revoked = false;
        var expiring = fixture.service.prepare(
                fixture.actor, fixture.turn, fixture.policy, actionablePlan());
        fixture.repository.taskProposalValues.put(
                expiring.proposal().id(), expiring.proposal());
        var later = new PlatformAiTaskProposalService(
                fixture.repository, fixture.owner, new SequenceIdService(8_000),
                Clock.fixed(NOW.plusSeconds(601), ZoneOffset.UTC));
        assertThat(later.get(
                fixture.actor, "31", Long.toString(expiring.proposal().id())).state()
                .name()).isEqualTo("EXPIRED");
        assertThat(fixture.owner.tasks).isEmpty();
    }

    private static PlatformAiPlanParser.Plan actionablePlan() {
        return PARSER.parse("""
                {"operation":"PLATFORM_TASK_DRAFT",
                 "title":"Follow up with finance",
                 "description":"Confirm the revised budget",
                 "dueAt":"2026-08-05T09:00:00Z",
                 "priority":"HIGH","confidence":0.9,
                 "clarification":null}
                """);
    }

    private static Fixture fixture() {
        var repository = new MemoryPlatformAiRepository();
        var settings = new PlatformAiPolicy.Settings(
                PlatformAiPolicy.SUPPORTED_OPERATIONS, 50, 100, 100_000, 4,
                true, PlatformAiPolicy.DataResidency.PLATFORM_METADATA_ONLY,
                "v1", true);
        var policy = new PlatformAiPolicy.Version(
                91, 90, 1, 80, 3, "gpt-platform", settings,
                "b".repeat(64), NOW, 7);
        repository.versionValues.put(policy.id(), policy);
        repository.draftValue = new PlatformAiPolicy.Draft(
                90, 1, PlatformAiPolicy.DraftStatus.PUBLISHED, 80, 3,
                settings, "a".repeat(64), policy.id(), NOW, 7);
        var session = new PlatformAiConversation.Session(
                31, PlatformAiConversation.Scope.PLATFORM, 7, "session",
                PlatformAiConversation.SessionStatus.ACTIVE, NOW, NOW);
        repository.sessionValues.put(session.id(), session);
        var turn = new PlatformAiConversation.Turn(
                41, PlatformAiConversation.Scope.PLATFORM, 7, session.id(),
                policy.id(), policy.providerId(), policy.providerVersion(), 41,
                "UNRESOLVED", PlatformAiConversation.TurnStatus.RUNNING,
                "request", AiSupport.sha256("request"), null, null, null, 0,
                "PLATFORM_AI_RUNNING", false, 10_000, 0,
                "request-1", "trace-1", NOW, null);
        var actor = new PlatformAiActor(
                7, Set.of("platform.runtime.access", "platform.ai.agent.use",
                "platform.task.create"), 41, "request-1", "trace-1");
        var owner = new TaskOwner();
        var service = new PlatformAiTaskProposalService(
                repository, owner, new SequenceIdService(1_000), CLOCK);
        return new Fixture(repository, policy, turn, actor, owner, service);
    }

    private record Fixture(
            MemoryPlatformAiRepository repository,
            PlatformAiPolicy.Version policy,
            PlatformAiConversation.Turn turn,
            PlatformAiActor actor,
            TaskOwner owner,
            PlatformAiTaskProposalService service) { }

    private static final class TaskOwner implements PlatformTaskFacade {
        private final Map<String, TaskView> tasks = new LinkedHashMap<>();
        private int prepareCalls;
        private int executeCalls;
        private boolean revoked;

        @Override
        public PreparedTask prepare(PrepareRequest request) {
            prepareCalls++;
            var draft = request.draft();
            return new PreparedTask(
                    new TaskPreview(
                            request.accountId(), draft.title(), draft.description(),
                            draft.dueAt(), draft.priority(), Status.OPEN, Source.AGENT),
                    NOW.plusSeconds(600),
                    new SealedCommand(
                            "sealed-command", "key-v1",
                            AiSupport.sha256("sealed-command")));
        }

        @Override
        public TaskView execute(ExecuteRequest request) {
            executeCalls++;
            if (revoked) {
                throw new BusinessException(
                        "PLATFORM_TASK_PERMISSION_DENIED",
                        "Platform task permission was revoked", HttpStatus.FORBIDDEN);
            }
            return tasks.computeIfAbsent(request.idempotencyKey(), ignored ->
                    new TaskView(
                            "9001", request.accountId(),
                            "Follow up with finance", "Confirm the revised budget",
                            Instant.parse("2026-08-05T09:00:00Z"), Priority.HIGH,
                            Status.OPEN, Source.AGENT, request.authorizationEpoch(),
                            AiSupport.sha256("task"), request.requestId(),
                            request.traceId(), NOW.plusSeconds(5),
                            request.accountId()));
        }
    }
}
