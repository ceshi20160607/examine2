package com.unique.examine.flow.service;

import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.flow.domain.ApprovalApproverSources;
import com.unique.examine.flow.domain.ApprovalApproverSource;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicies;
import com.unique.examine.flow.domain.ApprovalDeadlinePolicy;
import com.unique.examine.flow.domain.ApprovalDecisionCommentPolicies;
import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalMode;
import com.unique.examine.flow.domain.ApprovalParallelGateway;
import com.unique.examine.flow.domain.ApprovalQuorumRules;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.domain.FlowPeriodicScheduleState;
import com.unique.examine.flow.domain.PeriodicSchedule;
import com.unique.examine.flow.domain.TriggerBinding;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FlowPeriodicScheduleWorkerTest {
    private static final long SYSTEM_ID = 7L;
    private static final long TENANT_ID = 9L;
    private static final Instant NOW = Instant.parse("2026-07-30T00:10:00Z");

    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private JdbcApprovalRepositoryFactory repositories;
    private FlowRequestServiceFactory services;
    private RuntimeActiveMemberFacade activeMembers;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:flow_periodic_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        repositories = new JdbcApprovalRepositoryFactory(jdbc, transactions);
        var sequence = new AtomicLong(10_000L);
        services = new JdbcFlowRequestServiceFactory(
                repositories,
                new IdService() {
                    @Override
                    public long nextId() {
                        return sequence.incrementAndGet();
                    }
                },
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        activeMembers = (systemId, tenantId, memberId) -> memberId == 999L
                ? java.util.Optional.empty()
                : java.util.Optional.of(new RuntimeActiveMemberFacade.ActiveMember(memberId, null));
        createSchema();
    }

    @Test
    void publicationProjectsAndDisablingRemovesTheRuntimeSchedule() {
        var repository = repositories.forTenant(SYSTEM_ID, TENANT_ID);
        var schedule = new PeriodicSchedule(NOW.minusSeconds(600), 5, 88L);
        var periodic = new ApprovalDefinitionVersion(
                101L,
                1,
                "Scheduled approval",
                List.of(20L),
                1,
                NOW.minusSeconds(30),
                TriggerBinding.periodic(schedule)
        );

        repository.saveVersion(periodic);
        var state = repository.findPeriodicSchedule(101L).orElseThrow();

        assertThat(state.definitionVersion()).isEqualTo(1);
        assertThat(state.requesterId()).isEqualTo(88L);
        assertThat(state.intervalMinutes()).isEqualTo(5);
        assertThat(state.nextFireAt()).isEqualTo(schedule.startAt());
        assertThat(state.status()).isEqualTo(FlowPeriodicScheduleState.Status.ACTIVE);

        repository.saveVersion(new ApprovalDefinitionVersion(
                101L,
                2,
                "Manual approval",
                List.of(20L),
                2,
                NOW,
                null
        ));

        assertThat(repository.findPeriodicSchedule(101L)).isEmpty();
    }

    @Test
    void firesOneOverdueSlotSkipsAheadAndConcurrentPollsCannotDuplicateIt() throws Exception {
        publishPeriodic(201L, 1, 88L, NOW.minusSeconds(600), 5);
        var workerA = worker();
        var workerB = worker();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<java.util.concurrent.Callable<Integer>>of(
                    () -> workerA.pollOnce(20),
                    () -> workerB.pollOnce(20)
            ));
            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE definition_id=201",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT business_key FROM un_flow_instance WHERE definition_id=201",
                String.class
        )).isEqualTo("schedule:201:v1:" + NOW.minusSeconds(600).toEpochMilli());
        var state = repositories.forTenant(SYSTEM_ID, TENANT_ID)
                .findPeriodicSchedule(201L)
                .orElseThrow();
        assertThat(state.lastScheduledAt()).isEqualTo(NOW.minusSeconds(600));
        assertThat(state.nextFireAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(workerA.pollOnce(20)).isZero();
    }

    @Test
    void inactiveRequesterPausesOnlyItsScheduleAndAnotherDueScheduleStillFires() {
        publishPeriodic(301L, 1, 999L, NOW.minusSeconds(60), 1);
        publishPeriodic(302L, 1, 88L, NOW.minusSeconds(60), 1);

        assertThat(worker().pollOnce(20)).isEqualTo(1);

        var paused = repositories.forTenant(SYSTEM_ID, TENANT_ID)
                .findPeriodicSchedule(301L)
                .orElseThrow();
        assertThat(paused.status()).isEqualTo(FlowPeriodicScheduleState.Status.PAUSED);
        assertThat(paused.pauseReason()).isEqualTo("REQUESTER_INACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE definition_id=301",
                Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE definition_id=302",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void failedScheduleRollsBackAndDoesNotBlockTheNextDueSchedule() {
        insertScheduleOnly(401L, 1, 88L, NOW.minusSeconds(60), 1);
        publishPeriodic(402L, 1, 88L, NOW.minusSeconds(60), 1);

        assertThat(worker().pollOnce(20)).isEqualTo(1);

        var failed = repositories.forTenant(SYSTEM_ID, TENANT_ID)
                .findPeriodicSchedule(401L)
                .orElseThrow();
        assertThat(failed.status()).isEqualTo(FlowPeriodicScheduleState.Status.ACTIVE);
        assertThat(failed.nextFireAt()).isEqualTo(NOW.minusSeconds(60));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_instance WHERE definition_id=402",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void deadlineReminderIsDeliveredOnceAcrossConcurrentPollsThenTimeoutApproves()
            throws Exception {
        var workflow = services.forTenant(SYSTEM_ID, TENANT_ID);
        var policy = new ApprovalDeadlinePolicy(
                5,
                1,
                ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE
        );
        var definitionId = 501L;
        repositories.forTenant(SYSTEM_ID, TENANT_ID).saveVersion(
                new ApprovalDefinitionVersion(
                definitionId,
                1,
                "Deadline worker",
                List.of(20L),
                1,
                NOW,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                null,
                null,
                ApprovalApproverSources.fixedFor(null, null, null),
                ApprovalQuorumRules.none(),
                new ApprovalDeadlinePolicies(policy, Map.of())
        ));
        var pending = workflow.startLatest(
                definitionId, "deadline-worker", 88L);
        var notifications = new CopyOnWriteArrayList<ResultNotificationFacade.Command>();
        var receipts = new AtomicLong();
        ResultNotificationFacade notificationPort = command -> {
            notifications.add(command);
            var id = receipts.incrementAndGet();
            return new ResultNotificationFacade.DeliveryReceipt(
                    id, id, "DELIVERED", false
            );
        };
        var reminderAt = NOW.plusSeconds(4 * 60L);
        var workerA = deadlineWorker(reminderAt, notificationPort);
        var workerB = deadlineWorker(reminderAt, notificationPort);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.<java.util.concurrent.Callable<Integer>>of(
                    () -> workerA.pollOnce(20),
                    () -> workerB.pollOnce(20)
            ));
            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
        }

        assertThat(notifications).singleElement().satisfies(command -> {
            assertThat(command.recipientMemberId()).isEqualTo(20L);
            assertThat(command.templateCode())
                    .isEqualTo(FlowApprovalDeadlineWorker.REMINDER_TEMPLATE);
            assertThat(command.dedupeKey())
                    .isEqualTo("flow-deadline-reminder:" + pending.id() + ":route:20");
        });
        assertThat(workflow.instance(pending.id()).deadline().remindedAt())
                .isEqualTo(reminderAt);
        assertThat(workerA.pollOnce(20)).isZero();

        assertThat(deadlineWorker(
                NOW.plusSeconds(5 * 60L),
                notificationPort
        ).pollOnce(20)).isEqualTo(1);
        var approved = workflow.instance(pending.id());
        assertThat(approved.status()).isEqualTo(ApprovalInstance.Status.APPROVED);
        assertThat(approved.deadline().processedAt())
                .isEqualTo(NOW.plusSeconds(5 * 60L));
        assertThat(notifications).hasSize(1);
    }

    @Test
    void parallelBranchDeadlinesPersistAndTimeoutIndependently() {
        var financePolicy = new ApprovalDeadlinePolicy(
                1, null, ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        var ownerPolicy = new ApprovalDeadlinePolicy(
                2, null, ApprovalDeadlinePolicy.TimeoutAction.AUTO_REJECT);
        var gateway = new ApprovalParallelGateway(List.of(
                new ApprovalParallelGateway.Branch(
                        "finance", "Finance", List.of(20L), ApprovalMode.SEQUENTIAL),
                new ApprovalParallelGateway.Branch(
                        "owner", "Owner", List.of(21L), ApprovalMode.SEQUENTIAL)
        ));
        var workflow = services.forTenant(SYSTEM_ID, TENANT_ID);
        var definitionId = 502L;
        repositories.forTenant(SYSTEM_ID, TENANT_ID).saveVersion(
                new ApprovalDefinitionVersion(
                definitionId,
                1,
                "Parallel deadline worker",
                List.of(20L),
                1,
                NOW,
                null,
                null,
                null,
                ApprovalMode.SEQUENTIAL,
                gateway,
                null,
                ApprovalApproverSources.fixedFor(null, gateway, null),
                ApprovalQuorumRules.none(),
                new ApprovalDeadlinePolicies(
                        financePolicy,
                        Map.of("finance", financePolicy, "owner", ownerPolicy)
                )
        ));
        var pending = workflow.startLatest(
                definitionId, "parallel-deadline-worker", 88L);
        ResultNotificationFacade notificationPort = command ->
                new ResultNotificationFacade.DeliveryReceipt(
                        1L, 1L, "DELIVERED", false);

        assertThat(deadlineWorker(
                NOW.plusSeconds(60),
                notificationPort
        ).pollOnce(20)).isEqualTo(1);
        var financeApproved = workflow.instance(pending.id());
        assertThat(financeApproved.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(financeApproved.parallelBranch("finance").status())
                .isEqualTo(com.unique.examine.flow.domain.ApprovalBranchExecution.Status.APPROVED);
        assertThat(financeApproved.parallelBranch("owner").status())
                .isEqualTo(com.unique.examine.flow.domain.ApprovalBranchExecution.Status.PENDING);

        assertThat(deadlineWorker(
                NOW.plusSeconds(2 * 60L),
                notificationPort
        ).pollOnce(20)).isEqualTo(1);
        var rejected = workflow.instance(pending.id());
        assertThat(rejected.status()).isEqualTo(ApprovalInstance.Status.REJECTED);
        assertThat(rejected.parallelBranch("owner").status())
                .isEqualTo(com.unique.examine.flow.domain.ApprovalBranchExecution.Status.REJECTED);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_flow_parallel_branch_execution "
                        + "WHERE instance_id=? AND deadline_processed_at IS NOT NULL",
                Integer.class,
                pending.id()
        )).isEqualTo(2);
    }

    @Test
    void deadlineAutoApprovalRollsBackInactiveNextStageThenActivatesOnRetry() {
        var autoApprove = new ApprovalDeadlinePolicy(
                1, null, ApprovalDeadlinePolicy.TimeoutAction.AUTO_APPROVE);
        var first = new ApprovalStage(
                "timed",
                "Timed",
                List.of(20L),
                ApprovalMode.SEQUENTIAL,
                ApprovalApproverSource.fixed(),
                null,
                autoApprove,
                null
        );
        var second = new ApprovalStage(
                "manual",
                "Manual",
                List.of(30L),
                ApprovalMode.SEQUENTIAL,
                ApprovalApproverSource.fixed(),
                null,
                null,
                null
        );
        var definitionId = 503L;
        repositories.forTenant(SYSTEM_ID, TENANT_ID).saveVersion(
                new ApprovalDefinitionVersion(
                        definitionId,
                        1,
                        "Timed ordered stages",
                        first.approverIds(),
                        1,
                        NOW,
                        null,
                        null,
                        null,
                        first.approvalMode(),
                        null,
                        null,
                        new ApprovalApproverSources(
                                first.approverSource(), Map.of()),
                        ApprovalQuorumRules.none(),
                        new ApprovalDeadlinePolicies(autoApprove, Map.of()),
                        ApprovalDecisionCommentPolicies.none(),
                        List.of(first, second)
                )
        );
        var workflow = services.forTenant(SYSTEM_ID, TENANT_ID);
        var pending = workflow.startLatest(
                definitionId, "deadline-ordered-stage", 88L);
        ResultNotificationFacade notifications = command ->
                new ResultNotificationFacade.DeliveryReceipt(
                        1L, 1L, "DELIVERED", false);

        activeMembers = (systemId, tenantId, memberId) -> memberId == 30L
                ? java.util.Optional.empty()
                : java.util.Optional.of(
                        new RuntimeActiveMemberFacade.ActiveMember(memberId, null));
        assertThat(deadlineWorker(
                NOW.plusSeconds(60), notifications).pollOnce(20)).isZero();
        var unchanged = workflow.instance(pending.id());
        assertThat(unchanged.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(unchanged.currentStageIndex()).isZero();
        assertThat(unchanged.deadline().processedAt()).isNull();
        assertThat(unchanged.stages().get(0).status())
                .isEqualTo(ApprovalStageExecution.Status.ACTIVE);

        activeMembers = (systemId, tenantId, memberId) -> java.util.Optional.of(
                new RuntimeActiveMemberFacade.ActiveMember(memberId, null));
        assertThat(deadlineWorker(
                NOW.plusSeconds(60), notifications).pollOnce(20)).isEqualTo(1);
        var advanced = workflow.instance(pending.id());
        assertThat(advanced.status()).isEqualTo(ApprovalInstance.Status.PENDING);
        assertThat(advanced.currentStageIndex()).isEqualTo(1);
        assertThat(advanced.approverIds()).containsExactly(30L);
        assertThat(advanced.stages().get(0).status())
                .isEqualTo(ApprovalStageExecution.Status.APPROVED);
        assertThat(advanced.stages().get(1).status())
                .isEqualTo(ApprovalStageExecution.Status.ACTIVE);
    }

    private FlowPeriodicScheduleWorker worker() {
        return new FlowPeriodicScheduleWorker(
                jdbc,
                transactions,
                services,
                activeMembers,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private FlowApprovalDeadlineWorker deadlineWorker(
            Instant now,
            ResultNotificationFacade notifications
    ) {
        return new FlowApprovalDeadlineWorker(
                jdbc,
                transactions,
                services,
                notifications,
                activeMembers,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private void publishPeriodic(
            long definitionId,
            int version,
            long requesterId,
            Instant startAt,
            int intervalMinutes
    ) {
        repositories.forTenant(SYSTEM_ID, TENANT_ID).saveVersion(
                new ApprovalDefinitionVersion(
                        definitionId,
                        version,
                        "Scheduled " + definitionId,
                        List.of(20L),
                        version,
                        NOW.minusSeconds(30),
                        TriggerBinding.periodic(
                                new PeriodicSchedule(startAt, intervalMinutes, requesterId))
                )
        );
    }

    private void insertScheduleOnly(
            long definitionId,
            int version,
            long requesterId,
            Instant startAt,
            int intervalMinutes
    ) {
        jdbc.update(
                """
                INSERT INTO un_flow_periodic_schedule
                  (system_id,tenant_id,definition_id,definition_version,requester_id,
                   interval_minutes,start_at,next_fire_at,status,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                SYSTEM_ID, TENANT_ID, definitionId, version, requesterId,
                intervalMinutes, Timestamp.from(startAt), Timestamp.from(startAt),
                "ACTIVE", Timestamp.from(NOW.minusSeconds(30))
        );
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE un_flow_definition_version (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    definition_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    name VARCHAR(160) NOT NULL,
                    approver_id BIGINT NOT NULL,
                    approval_mode VARCHAR(16) NOT NULL,
                    trigger_module_code VARCHAR(64),
                    trigger_event VARCHAR(32),
                    trigger_priority INT,
                    trigger_exclusive BOOLEAN,
                    trigger_conditions VARCHAR(4000),
                    trigger_start_at TIMESTAMP,
                    trigger_interval_minutes INT,
                    trigger_requester_id BIGINT,
                    gateway_branches VARCHAR(8000),
                    parallel_branches VARCHAR(8000),
                    inclusive_branches VARCHAR(8000),
                    approver_sources VARCHAR(8000),
                    quorum_rules VARCHAR(8000),
                    deadline_policies VARCHAR(8000),
                    decision_comment_policies VARCHAR(8000),
                    decision_evidence_policies VARCHAR(8000),
                    completion_failure_policy VARCHAR(20) NOT NULL DEFAULT 'MANUAL_RETRY',
                    completion_steps VARCHAR(16000),
                    approval_stages VARCHAR(16000),
                    status_field_code VARCHAR(64),
                    status_approved_value VARCHAR(32),
                    status_rejected_value VARCHAR(32),
                    status_withdrawn_value VARCHAR(32),
                    status_terminated_value VARCHAR(32),
                    source_revision INT NOT NULL,
                    published_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (system_id,tenant_id,definition_id,version_no),
                    UNIQUE (system_id,tenant_id,definition_id,source_revision)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_definition_version_step (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    definition_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    step_no INT NOT NULL,
                    approver_id BIGINT NOT NULL,
                    PRIMARY KEY (system_id,tenant_id,definition_id,version_no,step_no)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_instance (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    instance_id BIGINT NOT NULL,
                    definition_id BIGINT NOT NULL,
                    definition_version INT NOT NULL,
                    business_key VARCHAR(160) NOT NULL,
                    module_code VARCHAR(64),
                    record_id BIGINT,
                    requester_id BIGINT NOT NULL,
                    approver_id BIGINT NOT NULL,
                    approver_ids_json VARCHAR(1000) NOT NULL,
                    current_step_index INT NOT NULL,
                    claim_state VARCHAR(16) NOT NULL,
                    approval_mode VARCHAR(16) NOT NULL,
                    required_approvals INT NOT NULL,
                    deadline_policy VARCHAR(1000),
                    deadline_remind_at TIMESTAMP,
                    deadline_due_at TIMESTAMP,
                    deadline_reminded_at TIMESTAMP,
                    deadline_processed_at TIMESTAMP,
                    decision_comment_policy VARCHAR(1000),
                    decision_evidence_policy VARCHAR(1000),
                    approval_stage_state VARCHAR(16000),
                    start_context VARCHAR(16000),
                    current_stage_index INT NOT NULL DEFAULT 0,
                    approved_approver_ids_json VARCHAR(1000) NOT NULL,
                    rejected_approver_ids_json VARCHAR(1000) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    completion_phase VARCHAR(20) NOT NULL DEFAULT 'HUMAN_APPROVAL',
                    completion_failure_policy VARCHAR(20) NOT NULL DEFAULT 'MANUAL_RETRY',
                    active_completion_ordinal INT,
                    state_version INT NOT NULL,
                    started_at TIMESTAMP NOT NULL,
                    completed_at TIMESTAMP,
                    PRIMARY KEY (system_id,tenant_id,instance_id),
                    UNIQUE (system_id,tenant_id,definition_id,business_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_completion_execution (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    execution_id BIGINT NOT NULL,
                    instance_id BIGINT NOT NULL,
                    definition_id BIGINT NOT NULL,
                    definition_version INT NOT NULL,
                    ordinal INT NOT NULL,
                    step_code VARCHAR(64) NOT NULL,
                    step_name VARCHAR(80) NOT NULL,
                    execution_type VARCHAR(16) NOT NULL,
                    parallel_group VARCHAR(64),
                    config_json VARCHAR(4096) NOT NULL,
                    payload_json VARCHAR(65535) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    attempt_count INT NOT NULL,
                    state_version INT NOT NULL,
                    available_at TIMESTAMP,
                    lease_owner VARCHAR(160),
                    lease_token_hash VARCHAR(64),
                    lease_expires_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    started_at TIMESTAMP,
                    terminal_at TIMESTAMP,
                    result_json VARCHAR(8192),
                    failure_code VARCHAR(64),
                    failure_message VARCHAR(500),
                    failure_retryable BOOLEAN,
                    PRIMARY KEY (system_id,tenant_id,execution_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_parallel_branch_execution (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    instance_id BIGINT NOT NULL,
                    branch_code VARCHAR(64) NOT NULL,
                    branch_name VARCHAR(80) NOT NULL,
                    branch_order INT NOT NULL,
                    approver_id BIGINT NOT NULL,
                    approver_ids_json VARCHAR(1000) NOT NULL,
                    current_step_index INT NOT NULL,
                    approval_mode VARCHAR(16) NOT NULL,
                    required_approvals INT NOT NULL,
                    deadline_policy VARCHAR(1000),
                    deadline_remind_at TIMESTAMP,
                    deadline_due_at TIMESTAMP,
                    deadline_reminded_at TIMESTAMP,
                    deadline_processed_at TIMESTAMP,
                    decision_comment_policy VARCHAR(1000),
                    decision_evidence_policy VARCHAR(1000),
                    approval_stage_state VARCHAR(16000),
                    current_stage_index INT NOT NULL DEFAULT 0,
                    approved_approver_ids_json VARCHAR(1000) NOT NULL,
                    rejected_approver_ids_json VARCHAR(1000) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    started_at TIMESTAMP NOT NULL,
                    completed_at TIMESTAMP,
                    PRIMARY KEY (system_id,tenant_id,instance_id,branch_code)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_history_event (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    instance_id BIGINT NOT NULL,
                    event_sequence INT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    actor_id BIGINT NOT NULL,
                    represented_member_id BIGINT,
                    delegation_id BIGINT,
                    from_status VARCHAR(16),
                    to_status VARCHAR(16) NOT NULL,
                    comment VARCHAR(1000) NOT NULL,
                    occurred_at TIMESTAMP NOT NULL,
                    target_member_id BIGINT,
                    assignment_position VARCHAR(16),
                    target_step_index INT,
                    PRIMARY KEY (system_id,tenant_id,instance_id,event_sequence)
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_decision_evidence (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    evidence_id BIGINT NOT NULL,
                    instance_id BIGINT NOT NULL,
                    history_sequence INT NOT NULL,
                    branch_code VARCHAR(64),
                    stage_index INT NOT NULL,
                    decision VARCHAR(16) NOT NULL,
                    signature_kind VARCHAR(16),
                    signature_file_id BIGINT,
                    typed_signature VARCHAR(120),
                    template_id BIGINT,
                    template_version INT,
                    template_name VARCHAR(80),
                    actor_id BIGINT NOT NULL,
                    represented_member_id BIGINT NOT NULL,
                    delegation_id BIGINT,
                    decided_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (system_id,tenant_id,evidence_id),
                    UNIQUE (
                        system_id,tenant_id,instance_id,history_sequence
                    )
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_decision_evidence_file (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    evidence_id BIGINT NOT NULL,
                    file_id BIGINT NOT NULL,
                    original_name VARCHAR(255) NOT NULL,
                    content_type VARCHAR(255) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    sha256 CHAR(64) NOT NULL,
                    is_attachment BOOLEAN NOT NULL,
                    is_signature BOOLEAN NOT NULL,
                    attachment_order INT,
                    PRIMARY KEY (
                        system_id,tenant_id,evidence_id,file_id
                    )
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_periodic_schedule (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    definition_id BIGINT NOT NULL,
                    definition_version INT NOT NULL,
                    requester_id BIGINT NOT NULL,
                    interval_minutes INT NOT NULL,
                    start_at TIMESTAMP NOT NULL,
                    next_fire_at TIMESTAMP NOT NULL,
                    last_scheduled_at TIMESTAMP,
                    last_instance_id BIGINT,
                    status VARCHAR(16) NOT NULL,
                    pause_reason VARCHAR(64),
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (system_id,tenant_id,definition_id)
                )
                """);
    }
}
