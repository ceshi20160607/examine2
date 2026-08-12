package com.unique.examine.flow.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.core.api.RuntimeActiveMemberFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.flow.domain.ApprovalDeadlineState;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalStage;
import com.unique.examine.flow.domain.ApprovalStageExecution;
import com.unique.examine.flow.repository.jdbc.JdbcFlowDeadlineSql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.List;

/**
 * Restart-safe database poller for approval reminders and timeout actions.
 */
@Component
public class FlowApprovalDeadlineWorker {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(FlowApprovalDeadlineWorker.class);
    public static final int DEFAULT_BATCH_SIZE = 50;
    public static final String REMINDER_TEMPLATE = "FLOW_APPROVAL_DEADLINE_REMINDER";

    private final JdbcTemplate jdbc;
    private final FlowRequestServiceFactory services;
    private final ResultNotificationFacade notifications;
    private final RuntimeActiveMemberFacade activeMembers;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public FlowApprovalDeadlineWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowRequestServiceFactory services,
            ResultNotificationFacade notifications,
            RuntimeActiveMemberFacade activeMembers
    ) {
        this(
                jdbc,
                transactionManager,
                services,
                notifications,
                activeMembers,
                Clock.systemUTC()
        );
    }

    FlowApprovalDeadlineWorker(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            FlowRequestServiceFactory services,
            ResultNotificationFacade notifications,
            RuntimeActiveMemberFacade activeMembers,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.services = Objects.requireNonNull(services, "services");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.activeMembers = Objects.requireNonNull(activeMembers, "activeMembers");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager")
        );
        this.transactions.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    @Scheduled(
            initialDelayString = "${examine.flow.deadline.initial-delay-ms:1000}",
            fixedDelayString = "${examine.flow.deadline.poll-delay-ms:1000}"
    )
    public void poll() {
        pollOnce(DEFAULT_BATCH_SIZE);
    }

    public int pollOnce(int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException(
                    "Approval deadline poll limit must be between 1 and 200"
            );
        }
        var now = clock.instant();
        var timestamp = Timestamp.from(now);
        var due = jdbc.query(
                JdbcFlowDeadlineSql.SELECT_DUE,
                (result, row) -> new DueKey(
                        result.getLong("system_id"),
                        result.getLong("tenant_id"),
                        result.getLong("instance_id"),
                        result.getString("branch_code"),
                        EventType.valueOf(result.getString("event_type"))
                ),
                timestamp, timestamp, timestamp, timestamp, timestamp,
                timestamp, timestamp, timestamp, timestamp, timestamp,
                limit
        );
        var processed = 0;
        for (var key : due) {
            try {
                var changed = transactions.execute(status -> processLocked(key, now));
                if (Boolean.TRUE.equals(changed)) {
                    processed++;
                }
            } catch (RuntimeException failure) {
                // One route remains retryable without blocking other due work.
                LOGGER.warn(
                        "Failed to process approval deadline event {} for "
                                + "system={}, tenant={}, instance={}, branch={}",
                        key.eventType(),
                        key.systemId(),
                        key.tenantId(),
                        key.instanceId(),
                        key.branchCode(),
                        failure
                );
            }
        }
        return processed;
    }

    private boolean processLocked(DueKey key, Instant now) {
        var locked = jdbc.query(
                JdbcFlowDeadlineSql.LOCK_INSTANCE,
                (result, row) -> result.getLong("instance_id"),
                key.systemId(), key.tenantId(), key.instanceId()
        );
        if (locked.isEmpty()) {
            return false;
        }
        var workflow = services.forTenant(key.systemId(), key.tenantId());
        var instance = workflow.instance(key.instanceId());
        if (instance.status() != ApprovalInstance.Status.PENDING) {
            return false;
        }
        var deadline = instance.deadline(key.branchCode());
        if (deadline == null || deadline.processedAt() != null) {
            return false;
        }
        return key.eventType() == EventType.TIMEOUT
                ? processTimeout(workflow, instance, key, deadline, now)
                : processReminder(workflow, instance, key, deadline, now);
    }

    private boolean processTimeout(
            ApprovalWorkflowService workflow,
            ApprovalInstance instance,
            DueKey key,
            ApprovalDeadlineState deadline,
            Instant now
    ) {
        if (now.isBefore(deadline.dueAt())) {
            return false;
        }
        workflow.processDeadline(
                instance.id(),
                key.branchCode(),
                instance.requesterId(),
                now,
                (nextStage, completedStage) -> stageActivationMembers(
                        key, nextStage, completedStage)
        );
        return true;
    }

    private List<Long> stageActivationMembers(
            DueKey key,
            ApprovalStage nextStage,
            ApprovalStageExecution completedStage
    ) {
        var members = switch (nextStage.approverSource().kind()) {
            case FIXED -> nextStage.approverIds();
            case PREVIOUS_HANDLER -> completedStage.actualHandlerIds();
            default -> throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_INACTIVE",
                    "Later approval stage source is not supported",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        };
        if (members.isEmpty()) {
            throw new BusinessException(
                    "FLOW_APPROVER_SOURCE_EMPTY",
                    "The next approval stage resolved no human handler",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        for (var memberId : members) {
            if (activeMembers.lockActiveMember(
                    key.systemId(), key.tenantId(), memberId).isEmpty()) {
                throw new BusinessException(
                        "FLOW_APPROVER_SOURCE_INACTIVE",
                        "Next-stage approver " + memberId
                                + " is inactive in the current system and tenant",
                        HttpStatus.UNPROCESSABLE_ENTITY
                );
            }
        }
        return List.copyOf(members);
    }

    private boolean processReminder(
            ApprovalWorkflowService workflow,
            ApprovalInstance instance,
            DueKey key,
            ApprovalDeadlineState deadline,
            Instant now
    ) {
        if (deadline.remindAt() == null
                || deadline.remindedAt() != null
                || now.isBefore(deadline.remindAt())
                || !now.isBefore(deadline.dueAt())) {
            return false;
        }
        var branchName = key.branchCode() == null
                ? "route"
                : instance.parallelBranches().stream()
                        .filter(branch -> branch.code().equals(key.branchCode()))
                        .map(branch -> branch.name())
                        .findFirst()
                        .orElse(key.branchCode());
        for (var recipientId : instance.deadlineRecipients(key.branchCode())) {
            notifications.dispatch(new ResultNotificationFacade.Command(
                    key.systemId(),
                    key.tenantId(),
                    instance.requesterId(),
                    recipientId,
                    REMINDER_TEMPLATE,
                    Map.of(
                            "definitionId", Long.toString(instance.definitionId()),
                            "instanceId", Long.toString(instance.id()),
                            "branchName", branchName,
                            "dueAt", deadline.dueAt().toString()
                    ),
                    new AggregateRef("FLOW_INSTANCE", Long.toString(instance.id())),
                    "/systems/" + key.systemId() + "/flow?instanceId=" + instance.id(),
                    "flow-deadline-reminder:" + instance.id()
                            + ":" + (key.branchCode() == null ? "route" : key.branchCode())
                            + ":" + recipientId
            ));
        }
        workflow.markDeadlineReminded(
                instance.id(),
                key.branchCode(),
                instance.requesterId(),
                now
        );
        return true;
    }

    enum EventType {
        REMINDER,
        TIMEOUT
    }

    record DueKey(
            long systemId,
            long tenantId,
            long instanceId,
            String branchCode,
            EventType eventType
    ) {
        DueKey {
            if (systemId <= 0 || tenantId <= 0 || instanceId <= 0) {
                throw new IllegalArgumentException(
                        "Approval deadline scope must contain positive ids"
                );
            }
            if (branchCode != null
                    && !branchCode.matches("^[a-z][a-z0-9_]{0,63}$")) {
                throw new IllegalArgumentException(
                        "Approval deadline branch code is invalid"
                );
            }
            Objects.requireNonNull(eventType, "eventType");
        }
    }
}
