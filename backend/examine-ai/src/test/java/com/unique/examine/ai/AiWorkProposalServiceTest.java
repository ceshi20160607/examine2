package com.unique.examine.ai;

import com.unique.examine.ai.domain.AiConversation;
import com.unique.examine.ai.domain.AiPolicy;
import com.unique.examine.ai.domain.AiWorkProposal;
import com.unique.examine.ai.plan.AiWorkDraftPlanParser;
import com.unique.examine.ai.service.AiWorkProposalService;
import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiWorkProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void taskPrepareIsZeroWriteAndConfirmationIsSingularReplaySafeAndRedacted() {
        var repository = repository();
        var owner = new Owner();
        var service = service(repository, owner, Clock.fixed(NOW, ZoneOffset.UTC));
        var prepared = service.prepare(
                actor(), repository.turnValues.get(71L), policy(), taskPlan());
        repository.insertWorkProposal(prepared.storedProposal(), prepared.event());

        assertThat(prepared.liveProposal().preview().task().title())
                .isEqualTo("Close quarter");
        assertThat(owner.prepareCalls).isOne();
        assertThat(owner.executeCalls).isZero();
        assertThat(repository.workProposals.values() + " " + repository.workEvents)
                .doesNotContain("Close quarter")
                .doesNotContain("Reconcile secret ledger")
                .contains(AiWorkProposal.REDACTED_TITLE)
                .contains(AiWorkProposal.REDACTED_DESCRIPTION);

        var succeeded = service.confirm(
                actor(), "70", Long.toString(prepared.liveProposal().id()),
                0, "work-task-1");
        var replay = service.confirm(
                actor(), "70", Long.toString(prepared.liveProposal().id()),
                0, "work-task-1");

        assertThat(succeeded.state()).isEqualTo(AiWorkProposal.State.SUCCEEDED);
        assertThat(succeeded.result().task().taskId()).isEqualTo("601");
        assertThat(succeeded.result().task().title()).isEqualTo("Close quarter");
        assertThat(replay.result().task().taskId()).isEqualTo("601");
        assertThat(replay.result().task().title())
                .isEqualTo(AiWorkProposal.REDACTED_TITLE);
        assertThat(owner.executeCalls).isOne();
        assertThat(repository.workProposals.values() + " "
                + repository.workAttempts.values() + " " + repository.workEvents)
                .doesNotContain("Close quarter")
                .doesNotContain("Reconcile secret ledger");
        assertThat(repository.workEvents)
                .extracting(AiWorkProposal.Event::eventType)
                .containsExactly("PROPOSED", "CONFIRMING", "SUCCEEDED");
        assertThatThrownBy(() -> service.confirm(
                actor(), "70", Long.toString(prepared.liveProposal().id()),
                1, "work-task-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(failure -> ((BusinessException) failure).code())
                .isEqualTo("AI_WORK_REPLAY_CONFLICT");
    }

    @Test
    void existingReportAndUnavailableFactsBecomeStableStaleTerminals() {
        var reportRepository = repository();
        var duplicate = new Owner();
        duplicate.failureCode = "AI_WORK_REPORT_EXISTS";
        var service = service(
                reportRepository, duplicate, Clock.fixed(NOW, ZoneOffset.UTC));
        var prepared = service.prepare(
                actor(), reportRepository.turnValues.get(71L), policy(), reportPlan());
        reportRepository.insertWorkProposal(
                prepared.storedProposal(), prepared.event());

        var stale = service.confirm(
                actor(), "70", Long.toString(prepared.liveProposal().id()),
                0, "report-duplicate");
        var replay = service.confirm(
                actor(), "70", Long.toString(prepared.liveProposal().id()),
                0, "report-duplicate");
        assertThat(stale.state()).isEqualTo(AiWorkProposal.State.STALE);
        assertThat(stale.resultCode()).isEqualTo("AI_WORK_REPORT_EXISTS");
        assertThat(replay).isEqualTo(stale);
        assertThat(duplicate.executeCalls).isOne();

        var unavailableRepository = repository();
        var unavailable = new Owner();
        unavailable.failureCode = "AI_WORK_ASSIGNEE_UNAVAILABLE";
        var unavailableService = service(
                unavailableRepository, unavailable,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var task = unavailableService.prepare(
                actor(), unavailableRepository.turnValues.get(71L),
                policy(), taskPlan());
        unavailableRepository.insertWorkProposal(task.storedProposal(), task.event());
        assertThat(unavailableService.confirm(
                actor(), "70", Long.toString(task.liveProposal().id()),
                0, "assignee-unavailable").state())
                .isEqualTo(AiWorkProposal.State.STALE);
    }

    @Test
    void livePermissionDenialHasExplicitTerminalState() {
        var repository = repository();
        var owner = new Owner();
        owner.failureCode = "WORK_TASK_FORBIDDEN";
        var service = service(repository, owner, Clock.fixed(NOW, ZoneOffset.UTC));
        var prepared = service.prepare(
                actor(), repository.turnValues.get(71L), policy(), taskPlan());
        repository.insertWorkProposal(prepared.storedProposal(), prepared.event());

        var denied = service.confirm(
                actor(), "70", Long.toString(prepared.liveProposal().id()),
                0, "permission-denied");
        assertThat(denied.state()).isEqualTo(
                AiWorkProposal.State.PERMISSION_DENIED);
        assertThat(denied.resultCode()).isEqualTo("WORK_TASK_FORBIDDEN");
    }

    @Test
    void clarificationRejectAndExpiryNeverExecuteOwner() {
        var owner = new Owner();
        var clarificationRepository = repository();
        var clarificationService = service(
                clarificationRepository, owner, Clock.fixed(NOW, ZoneOffset.UTC));
        var clarification = clarificationService.prepare(
                actor(), clarificationRepository.turnValues.get(71L), policy(),
                new AiWorkDraftPlanParser.TaskPlan(
                        null, null, null, null, null, 0.4,
                        "Which assignee?", "a".repeat(64)));
        clarificationRepository.insertWorkProposal(
                clarification.storedProposal(), clarification.event());
        assertThat(clarification.liveProposal().state()).isEqualTo(
                AiWorkProposal.State.CLARIFICATION_REQUIRED);
        assertThat(clarification.storedProposal().clarification())
                .isEqualTo(AiWorkProposal.REDACTED_CLARIFICATION);

        var rejectRepository = repository();
        var rejectService = service(
                rejectRepository, owner, Clock.fixed(NOW, ZoneOffset.UTC));
        var pending = rejectService.prepare(
                actor(), rejectRepository.turnValues.get(71L), policy(), taskPlan());
        rejectRepository.insertWorkProposal(pending.storedProposal(), pending.event());
        assertThat(rejectService.reject(
                actor(), "70", Long.toString(pending.liveProposal().id()), 0).state())
                .isEqualTo(AiWorkProposal.State.REJECTED);

        var expiryRepository = repository();
        var mutable = new MutableClock(NOW);
        var expiryService = service(expiryRepository, owner, mutable);
        var expiring = expiryService.prepare(
                actor(), expiryRepository.turnValues.get(71L), policy(), taskPlan());
        expiryRepository.insertWorkProposal(
                expiring.storedProposal(), expiring.event());
        mutable.now = NOW.plusSeconds(601);
        assertThat(expiryService.get(
                actor(), "70", Long.toString(expiring.liveProposal().id())).state())
                .isEqualTo(AiWorkProposal.State.EXPIRED);
        assertThat(owner.executeCalls).isZero();
    }

    private static AiWorkProposalService service(
            MemoryAiRepository repository, Owner owner, Clock clock) {
        return new AiWorkProposalService(
                repository, owner, new SequenceIdService(5_000), clock);
    }

    private static AiWorkDraftPlanParser.TaskPlan taskPlan() {
        return new AiWorkDraftPlanParser.TaskPlan(
                "Close quarter", "Reconcile secret ledger", "7", "701",
                NOW.plusSeconds(86_400), 0.98, null, "a".repeat(64));
    }

    private static AiWorkDraftPlanParser.DailyReportPlan reportPlan() {
        return new AiWorkDraftPlanParser.DailyReportPlan(
                LocalDate.parse("2026-08-04"), "Secret completed",
                "Secret planned", "Secret blocker", 0.97, null,
                "b".repeat(64));
    }

    private static MemoryAiRepository repository() {
        var repository = new MemoryAiRepository();
        var policy = policy();
        repository.versions.put(policy.id(), policy);
        repository.drafts.put("1:2", new AiPolicy.Draft(
                90, 1, 2, 1, AiPolicy.DraftStatus.PUBLISHED, 80, 3,
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "WORK_TASK_DRAFT",
                        "WORK_DAILY_REPORT_DRAFT"),
                Map.of(), 10, AiPolicy.ConfirmationMode.REQUIRED, 60, true,
                AiPolicy.RedactionMode.STRICT, "v84", "c".repeat(64),
                policy.id(), NOW, 7));
        repository.insertSession(new AiConversation.Session(
                70, 1, 2, 7, policy.id(), 80, 3, "gpt-work", "v84", 41,
                AiConversation.SessionStatus.ACTIVE, "[title:redacted]", NOW, NOW));
        repository.insertTurn(new AiConversation.Turn(
                71, 1, 2, 70, policy.id(), 80, 3, 41,
                AiConversation.TurnStatus.RUNNING, "[request:redacted]",
                "d".repeat(64), null, null, null, 0, "AI_TURN_RUNNING", false,
                0, "request-84", "trace-84", NOW, null));
        return repository;
    }

    private static AiPolicy.Version policy() {
        return new AiPolicy.Version(
                91, 1, 2, 90, 1, 80, 3, "gpt-work",
                Set.of("orders"), Map.of("orders", Set.of("status")),
                Set.of("RECORD_QUERY", "WORK_TASK_DRAFT",
                        "WORK_DAILY_REPORT_DRAFT"),
                10, true, AiPolicy.RedactionMode.STRICT, "v84",
                "e".repeat(64), NOW, 7);
    }

    private static AiActor actor() {
        return new AiActor(
                5, 1, 2, 7,
                Set.of("system.runtime.access", "ai.agent.use",
                        "work.task.create", "work.report.create"),
                41, "request-84", "trace-84");
    }

    private static final class Owner implements AiWorkDraftFacade {
        private int prepareCalls;
        private int executeCalls;
        private String failureCode;

        @Override
        public PreparedDraft prepare(PrepareRequest request) {
            prepareCalls++;
            return new PreparedDraft(
                    new DraftPreview(
                            request.operation(), request.task(), request.report()),
                    new SealedCommand(
                            "sealed_work_command", "key-v1", "f".repeat(64)),
                    NOW.plusSeconds(900));
        }

        @Override
        public DraftReadback execute(ExecuteRequest request) {
            executeCalls++;
            if (failureCode != null) {
                throw new BusinessException(
                        failureCode, "Owner rejected Work draft",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            if (request.operation() == Operation.WORK_TASK_DRAFT) {
                return new DraftReadback(request.operation(), new TaskReadback(
                        "601", 1, "Close quarter", "Reconcile secret ledger",
                        "OPEN", "7", "701", NOW.plusSeconds(86_400),
                        NOW, NOW), null);
            }
            return new DraftReadback(request.operation(), null,
                    new DailyReportReadback(
                            "801", 1, "7", LocalDate.parse("2026-08-04"),
                            "Secret completed", "Secret planned", "Secret blocker",
                            "DRAFT", NOW, NOW));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) { this.now = now; }

        @Override
        public ZoneId getZone() { return ZoneOffset.UTC; }

        @Override
        public Clock withZone(ZoneId zone) { return this; }

        @Override
        public Instant instant() { return now; }
    }
}
