package com.unique.examine.flow.ai;

import com.unique.examine.core.ai.AiFlowDefinitionDraftFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.api.FlowViews;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import com.unique.examine.flow.security.FlowSession;
import com.unique.examine.flow.service.ApprovalWorkflowService;
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

class JdbcFlowAiDefinitionDraftExecutionStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private InMemoryApprovalRepository repository;
    private FlowAiDefinitionDraftCommandCodec codec;
    private JdbcFlowAiDefinitionDraftExecutionStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:flow_ai_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE un_flow_ai_definition_draft_execution(
                  id BIGINT NOT NULL, system_id BIGINT NOT NULL,
                  tenant_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
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
                  PRIMARY KEY(system_id,tenant_id,id), UNIQUE(id),
                  UNIQUE(system_id,tenant_id,member_id,proposal_id),
                  UNIQUE(system_id,tenant_id,member_id,idempotency_key)
                )
                """);
        transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        var ledgerIds = new AtomicLong(1000);
        var definitionIds = new AtomicLong(500);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new InMemoryApprovalRepository();
        var workflows = new ApprovalWorkflowService(
                repository, ids(definitionIds), clock);
        codec = new FlowAiDefinitionDraftCommandCodec();
        store = new JdbcFlowAiDefinitionDraftExecutionStore(
                jdbc, ids(ledgerIds),
                (session, request) -> FlowViews.DefinitionDraft.from(
                        workflows.createDraft(
                                request.name(), request.approverIds().stream()
                                .map(Long::parseLong).toList())),
                codec, clock);
    }

    @Test
    void createsOneUnpublishedOwnerDraftAndReplaysStoredReadback() {
        var command = command(FlowAiDefinitionDraftCommandTest.request());
        var facts = new AtomicInteger();
        var first = execute(command, facts::incrementAndGet, "idem-1");
        var replay = execute(command, facts::incrementAndGet, "idem-1");

        assertThat(replay).isEqualTo(first);
        assertThat(first.published()).isFalse();
        assertThat(repository.countDrafts()).isEqualTo(1);
        assertThat(facts).hasValue(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_ai_definition_draft_execution",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void changedProposalOrIdempotencyKeyConflictsWithoutSecondDraft() {
        var command = command(FlowAiDefinitionDraftCommandTest.request());
        execute(command, () -> { }, "idem-1");
        assertCode(() -> execute(command, () -> { }, "idem-2"));

        var source = FlowAiDefinitionDraftCommandTest.request();
        var changed = command(new AiFlowDefinitionDraftFacade.PrepareRequest(
                "proposal-2", source.sessionId(), source.turnId(),
                source.accountId(), source.systemId(), source.tenantId(),
                source.memberId(), source.authorizationEpoch(),
                source.effectivePermissions(), source.operation(),
                source.draft(), source.policyVersionId(), source.providerId(),
                source.providerVersion(), source.promptVersion(),
                source.requestId(), source.traceId()));
        assertCode(() -> execute(changed, () -> { }, "idem-1"));
        assertThat(repository.countDrafts()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_ai_definition_draft_execution",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void failedLiveFactCheckRollsBackClaimAndCleanRetrySucceeds() {
        var command = command(FlowAiDefinitionDraftCommandTest.request());
        assertThatThrownBy(() -> execute(command,
                () -> { throw new IllegalStateException("stale member"); },
                "idem-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stale member");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_ai_definition_draft_execution",
                Integer.class)).isZero();
        assertThat(repository.countDrafts()).isZero();

        assertThat(execute(command, () -> { }, "idem-1").published())
                .isFalse();
        assertThat(repository.countDrafts()).isEqualTo(1);
    }

    private FlowAiDefinitionDraftCommandCodec.Command command(
            AiFlowDefinitionDraftFacade.PrepareRequest request) {
        return codec.command(request, NOW.plusSeconds(900));
    }

    private AiFlowDefinitionDraftFacade.DefinitionReadback execute(
            FlowAiDefinitionDraftCommandCodec.Command command,
            Runnable facts, String key) {
        return transactions.execute(status -> store.execute(
                command, new FlowSession(
                        99, 1, 2, 10,
                        Set.of("flow.definition.manage")),
                facts, key, "execute-request", "execute-trace"));
    }

    private static IdService ids(AtomicLong sequence) {
        return new IdService() {
            @Override
            public long nextId() {
                return sequence.incrementAndGet();
            }
        };
    }

    private static void assertCode(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                "AI_FLOW_DRAFT_IDEMPOTENCY_CONFLICT"));
    }
}
