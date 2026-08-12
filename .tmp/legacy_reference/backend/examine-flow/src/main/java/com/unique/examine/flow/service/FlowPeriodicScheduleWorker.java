package com.unique.examine.flow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.api.RuntimeApproverDirectoryFacade;
import com.unique.examine.flow.domain.ApprovalBranchRoute;
import com.unique.examine.flow.domain.ResolvedApprovalBranchRoute;
import com.unique.examine.flow.domain.FlowPeriodicScheduleState;
import com.unique.examine.flow.repository.jdbc.JdbcFlowPeriodicSql;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class FlowPeriodicScheduleWorker {
    public static final int DEFAULT_BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final FlowRequestServiceFactory services;
    private final RuntimeActiveMemberFacade activeMembers;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApprovalApproverSourceResolver approverSources;

    @Autowired
    public FlowPeriodicScheduleWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowRequestServiceFactory services,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            ObjectMapper objectMapper
    ) {
        this(
                jdbc,
                transactionManager,
                services,
                activeMembers,
                approverDirectory,
                Clock.systemUTC(),
                objectMapper
        );
    }

    FlowPeriodicScheduleWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowRequestServiceFactory services,
            RuntimeActiveMemberFacade activeMembers,
            Clock clock
    ) {
        this(
                jdbc,
                transactionManager,
                services,
                activeMembers,
                unsupportedApproverDirectory(),
                clock,
                new ObjectMapper()
        );
    }

    FlowPeriodicScheduleWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowRequestServiceFactory services,
            RuntimeActiveMemberFacade activeMembers,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this(
                jdbc,
                transactionManager,
                services,
                activeMembers,
                unsupportedApproverDirectory(),
                clock,
                objectMapper
        );
    }

    FlowPeriodicScheduleWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowRequestServiceFactory services,
            RuntimeActiveMemberFacade activeMembers,
            RuntimeApproverDirectoryFacade approverDirectory,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.services = Objects.requireNonNull(services, "services");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
        this.approverSources = new ApprovalApproverSourceResolver(
                approverDirectory,
                activeMembers);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(
            initialDelayString = "${examine.flow.periodic.initial-delay-ms:1000}",
            fixedDelayString = "${examine.flow.periodic.poll-delay-ms:1000}"
    )
    public void poll() {
        pollOnce(DEFAULT_BATCH_SIZE);
    }

    public int pollOnce(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Periodic schedule poll limit must be between 1 and 100");
        }
        var now = clock.instant();
        var due = jdbc.query(
                JdbcFlowPeriodicSql.SELECT_DUE_KEYS,
                (result, row) -> new DueKey(
                        result.getLong("system_id"),
                        result.getLong("tenant_id"),
                        result.getLong("definition_id")
                ),
                Timestamp.from(now),
                limit
        );
        var fired = 0;
        for (var key : due) {
            try {
                var result = transactions.execute(status -> fireLocked(key, now));
                if (Boolean.TRUE.equals(result)) {
                    fired++;
                }
            } catch (RuntimeException ignored) {
                // A failed schedule rolls back and remains due; other schedules keep running.
            }
        }
        return fired;
    }

    private boolean fireLocked(DueKey key, Instant now) {
        var rows = jdbc.query(
                JdbcFlowPeriodicSql.SELECT_SCHEDULE_FOR_UPDATE,
                (result, row) -> new LockedSchedule(
                        result.getLong("system_id"),
                        result.getLong("tenant_id"),
                        result.getLong("definition_id"),
                        result.getInt("definition_version"),
                        result.getLong("requester_id"),
                        result.getInt("interval_minutes"),
                        result.getTimestamp("next_fire_at").toInstant(),
                        FlowPeriodicScheduleState.Status.valueOf(result.getString("status"))
                ),
                key.systemId(), key.tenantId(), key.definitionId()
        );
        if (rows.isEmpty()) {
            return false;
        }
        var schedule = rows.getFirst();
        if (schedule.status() != FlowPeriodicScheduleState.Status.ACTIVE
                || schedule.nextFireAt().isAfter(now)) {
            return false;
        }
        if (activeMembers.lockActiveMember(
                schedule.systemId(),
                schedule.tenantId(),
                schedule.requesterId()
        ).isEmpty()) {
            requireOne(jdbc.update(
                    JdbcFlowPeriodicSql.UPDATE_PAUSED,
                    "REQUESTER_INACTIVE",
                    Timestamp.from(now),
                    schedule.systemId(), schedule.tenantId(), schedule.definitionId(),
                    schedule.definitionVersion(), Timestamp.from(schedule.nextFireAt())
            ));
            return false;
        }
        var workflow = services.forTenant(schedule.systemId(), schedule.tenantId());
        var definition = workflow.definitionVersion(
                schedule.definitionId(),
                schedule.definitionVersion()
        );
        final com.unique.examine.flow.domain.ApprovalInstance instance;
        if (definition.parallelGateway() != null) {
            instance = workflow.startBranches(
                    schedule.definitionId(),
                    schedule.definitionVersion(),
                    definition.parallelGateway().branches().stream()
                            .map(branch -> resolveBranch(schedule, definition, branch))
                            .toList(),
                    businessKey(schedule),
                    schedule.requesterId(),
                    null
            );
        } else if (definition.inclusiveGateway() != null) {
            var selected = new ApprovalInclusiveRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(definition.inclusiveGateway(), java.util.Map.of());
            instance = workflow.startBranches(
                    schedule.definitionId(),
                    schedule.definitionVersion(),
                    selected.stream()
                            .map(branch -> resolveBranch(schedule, definition, branch))
                            .toList(),
                    businessKey(schedule),
                    schedule.requesterId(),
                    null
            );
        } else {
            var route = new ApprovalRouteResolver(
                    new TriggerConditionMatcher(objectMapper)
            ).resolve(
                    definition.gateway(),
                    definition.approverIds(),
                    definition.approvalMode(),
                    java.util.Map.of()
            );
            var source = route.branchCode() == null
                    ? definition.approverSources().route()
                    : definition.approverSources().branch(route.branchCode());
            var members = approverSources.requireMembers(
                    schedule.systemId(),
                    schedule.tenantId(),
                    source,
                    route.approverIds()
            );
            instance = workflow.startResolved(
                        schedule.definitionId(),
                        schedule.definitionVersion(),
                        members,
                        route.approvalMode(),
                        com.unique.examine.flow.domain.ApprovalQuorumRules.requiredApprovals(
                                route.approvalMode(),
                                route.branchCode() == null
                                        ? definition.quorumRules().primary()
                                        : definition.quorumRules().branch(route.branchCode()),
                                members.size()
                        ),
                        route.branchCode() == null
                                ? definition.deadlinePolicies().primary()
                                : definition.deadlinePolicies().branch(route.branchCode()),
                        businessKey(schedule),
                        schedule.requesterId(),
                        null
            );
        }
        requireOne(jdbc.update(
                JdbcFlowPeriodicSql.UPDATE_FIRED,
                Timestamp.from(nextFireAt(schedule, now)),
                Timestamp.from(schedule.nextFireAt()),
                instance.id(),
                Timestamp.from(now),
                schedule.systemId(), schedule.tenantId(), schedule.definitionId(),
                schedule.definitionVersion(), Timestamp.from(schedule.nextFireAt())
        ));
        return true;
    }

    private ResolvedApprovalBranchRoute resolveBranch(
            LockedSchedule schedule,
            com.unique.examine.flow.domain.ApprovalDefinitionVersion definition,
            ApprovalBranchRoute branch
    ) {
        return ResolvedApprovalBranchRoute.from(
                branch,
                approverSources.requireMembers(
                        schedule.systemId(),
                        schedule.tenantId(),
                        definition.approverSources().branch(branch.code()),
                        branch.approverIds()
                )
        );
    }

    static Instant nextFireAt(LockedSchedule schedule, Instant now) {
        var interval = Duration.ofMinutes(schedule.intervalMinutes());
        if (schedule.nextFireAt().isAfter(now)) {
            return schedule.nextFireAt();
        }
        var elapsedIntervals = Duration.between(schedule.nextFireAt(), now).dividedBy(interval);
        return schedule.nextFireAt().plus(interval.multipliedBy(elapsedIntervals + 1));
    }

    static String businessKey(LockedSchedule schedule) {
        return "schedule:%d:v%d:%d".formatted(
                schedule.definitionId(),
                schedule.definitionVersion(),
                schedule.nextFireAt().toEpochMilli()
        );
    }

    private static void requireOne(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Periodic schedule changed concurrently");
        }
    }

    private static RuntimeApproverDirectoryFacade unsupportedApproverDirectory() {
        return new RuntimeApproverDirectoryFacade() {
            @Override
            public Resolution resolveRoleMembers(long systemId, long tenantId, long roleId) {
                return Resolution.missing();
            }

            @Override
            public Resolution resolveDepartmentMembers(
                    long systemId,
                    long tenantId,
                    long departmentId
            ) {
                return Resolution.missing();
            }
        };
    }

    record DueKey(long systemId, long tenantId, long definitionId) {
        DueKey {
            if (systemId <= 0 || tenantId <= 0 || definitionId <= 0) {
                throw new IllegalArgumentException("Periodic schedule scope is invalid");
            }
        }
    }

    record LockedSchedule(
            long systemId,
            long tenantId,
            long definitionId,
            int definitionVersion,
            long requesterId,
            int intervalMinutes,
            Instant nextFireAt,
            FlowPeriodicScheduleState.Status status
    ) {
        LockedSchedule {
            if (systemId <= 0
                    || tenantId <= 0
                    || definitionId <= 0
                    || definitionVersion <= 0
                    || requesterId <= 0
                    || intervalMinutes < 1) {
                throw new IllegalArgumentException("Locked periodic schedule is invalid");
            }
            Objects.requireNonNull(nextFireAt, "nextFireAt");
            Objects.requireNonNull(status, "status");
        }
    }
}
