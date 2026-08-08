package com.unique.examine.work.ai;

import com.unique.examine.core.ai.AiWorkDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.work.adapter.memory.InMemoryWorkDailyReportRepository;
import com.unique.examine.work.adapter.memory.InMemoryWorkTaskRepository;
import com.unique.examine.work.domain.WorkActor;
import com.unique.examine.work.service.WorkDailyReportService;
import com.unique.examine.work.service.WorkTaskService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcWorkAiDraftExecutionStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private InMemoryWorkTaskRepository taskRepository;
    private InMemoryWorkDailyReportRepository reportRepository;
    private JdbcWorkAiDraftExecutionStore store;
    private WorkAiDraftCommandCodec codec;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:work_ai_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE un_work_ai_draft_execution(
                  id BIGINT NOT NULL,
                  system_id BIGINT NOT NULL,
                  tenant_id BIGINT NOT NULL,
                  member_id BIGINT NOT NULL,
                  proposal_id VARCHAR(128) NOT NULL,
                  session_id VARCHAR(128) NOT NULL,
                  turn_id VARCHAR(128) NOT NULL,
                  account_id BIGINT NOT NULL,
                  authorization_epoch BIGINT NOT NULL,
                  operation VARCHAR(32) NOT NULL,
                  policy_version_id BIGINT NOT NULL,
                  provider_id BIGINT NOT NULL,
                  provider_version BIGINT NOT NULL,
                  prompt_version VARCHAR(64) NOT NULL,
                  expires_at TIMESTAMP(6) NOT NULL,
                  prepare_request_id VARCHAR(64) NOT NULL,
                  prepare_trace_id VARCHAR(64) NOT NULL,
                  payload_hash CHAR(64) NOT NULL,
                  idempotency_key VARCHAR(128) NOT NULL,
                  execute_request_id VARCHAR(64) NOT NULL,
                  execute_trace_id VARCHAR(64) NOT NULL,
                  result_json MEDIUMTEXT NULL,
                  created_at TIMESTAMP(6) NOT NULL,
                  completed_at TIMESTAMP(6) NULL,
                  PRIMARY KEY(system_id,tenant_id,id),
                  UNIQUE(id),
                  UNIQUE(system_id,tenant_id,member_id,proposal_id),
                  UNIQUE(system_id,tenant_id,member_id,idempotency_key)
                )
                """);
        transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        taskRepository = new InMemoryWorkTaskRepository();
        reportRepository = new InMemoryWorkDailyReportRepository();
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var members = (com.unique.examine.work.port.WorkMemberDirectory)
                (system, tenant, member) -> system == 11 && tenant == 21
                        && Set.of(17L, 19L).contains(member);
        var tasks = new WorkTaskService(taskRepository, members, clock);
        var reports = new WorkDailyReportService(
                reportRepository, members, clock);
        codec = new WorkAiDraftCommandCodec();
        var sequence = new AtomicLong(1000);
        store = new JdbcWorkAiDraftExecutionStore(
                jdbc, new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                }, tasks, reports, codec, clock);
    }

    @Test
    void taskAndReportCreateOnceAndReplayFromDurableReadback() {
        var facts = new AtomicInteger();
        var taskCommand = codec.command(
                standaloneTaskRequest(),
                NOW.plusSeconds(900));
        var taskActor = actor(taskCommand);

        var first = execute(taskCommand, taskActor,
                facts::incrementAndGet, "idem-task");
        var replay = execute(taskCommand, taskActor,
                facts::incrementAndGet, "idem-task");

        assertThat(replay).isEqualTo(first);
        assertThat(first.task().status()).isEqualTo("OPEN");
        assertThat(taskRepository.findAll(11, 21)).hasSize(1);
        assertThat(facts).hasValue(1);

        var reportCommand = codec.command(
                WorkAiDraftCommandCodecTest.reportRequest(),
                NOW.plusSeconds(900));
        var report = execute(reportCommand, actor(reportCommand),
                facts::incrementAndGet, "idem-report");
        var reportReplay = execute(reportCommand, actor(reportCommand),
                facts::incrementAndGet, "idem-report");

        assertThat(reportReplay).isEqualTo(report);
        assertThat(report.report().status()).isEqualTo("DRAFT");
        assertThat(reportRepository.findByAuthorAndDate(
                11, 21, 17, reportCommand.report().workDate())).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_work_ai_draft_execution",
                Integer.class)).isEqualTo(2);
        assertThat(facts).hasValue(2);
    }

    @Test
    void changedProposalOrIdempotencyRouteConflictsWithoutSecondWrite() {
        var command = codec.command(
                standaloneTaskRequest(),
                NOW.plusSeconds(900));
        execute(command, actor(command), () -> { }, "idem-task");

        assertCode(() -> execute(command, actor(command), () -> { },
                "idem-changed"), "AI_WORK_DRAFT_IDEMPOTENCY_CONFLICT");

        var changedProposal = codec.command(
                new AiWorkDraftFacade.PrepareRequest(
                        "proposal-changed", command.sessionId(),
                        command.turnId(), command.accountId(),
                        command.systemId(), command.tenantId(),
                        command.memberId(), command.authorizationEpoch(),
                        Set.copyOf(command.effectivePermissions()),
                        command.operation(), command.task(), null,
                        command.policyVersionId(), command.providerId(),
                        command.providerVersion(), command.promptVersion(),
                        command.prepareRequestId(), command.prepareTraceId()),
                command.expiresAt());
        assertCode(() -> execute(
                        changedProposal, actor(changedProposal), () -> { },
                        "idem-task"),
                "AI_WORK_DRAFT_IDEMPOTENCY_CONFLICT");

        assertThat(taskRepository.findAll(11, 21)).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_work_ai_draft_execution",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void failedLiveFactCheckRollsBackClaimAndAllowsCleanRetry() {
        var command = codec.command(
                standaloneTaskRequest(),
                NOW.plusSeconds(900));
        assertThatThrownBy(() -> execute(
                command, actor(command),
                () -> { throw new IllegalStateException("stale fact"); },
                "idem-task"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stale fact");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_work_ai_draft_execution",
                Integer.class)).isZero();
        assertThat(taskRepository.findAll(11, 21)).isEmpty();

        var result = execute(command, actor(command), () -> { }, "idem-task");
        assertThat(result.task().status()).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_work_ai_draft_execution",
                Integer.class)).isEqualTo(1);
    }

    private AiWorkDraftFacade.DraftReadback execute(
            WorkAiDraftCommandCodec.Command command,
            WorkActor actor, Runnable facts, String idempotencyKey) {
        return transactions.execute(status -> store.execute(
                command, actor, facts, idempotencyKey,
                "execute-request", "execute-trace"));
    }

    private static AiWorkDraftFacade.PrepareRequest standaloneTaskRequest() {
        var source = WorkAiDraftCommandCodecTest.taskRequest();
        return new AiWorkDraftFacade.PrepareRequest(
                source.proposalId(), source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), source.authorizationEpoch(),
                source.effectivePermissions(), source.operation(),
                new AiWorkDraftFacade.TaskDraft(
                        source.task().title(), source.task().description(),
                        source.task().assigneeMemberId(), null,
                        source.task().dueAt()),
                null, source.policyVersionId(), source.providerId(),
                source.providerVersion(), source.promptVersion(),
                source.requestId(), source.traceId());
    }

    private static WorkActor actor(WorkAiDraftCommandCodec.Command command) {
        return new WorkActor(
                command.systemId(), command.tenantId(), command.memberId(),
                Set.copyOf(command.effectivePermissions()));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(code));
    }
}
