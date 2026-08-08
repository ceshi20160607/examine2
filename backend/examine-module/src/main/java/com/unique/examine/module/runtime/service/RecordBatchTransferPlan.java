package com.unique.examine.module.runtime.service;

import com.unique.examine.core.api.RuntimeRecordTeamOwnershipFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

final class RecordBatchTransferPlan {
    static final String UPDATE_RECORD_SQL = """
            UPDATE un_module_record
               SET owner_member_id=?,owner_department_id=?,
                   updated_at=?,updated_by=?,version=version+1
             WHERE system_id=? AND tenant_id=? AND logical_module_id=?
               AND schema_version_id=? AND module_snapshot_id=? AND record_id=?
               AND status='ACTIVE' AND owner_member_id=? AND version=?
            """;

    private final RecordBatchMutationPlan mutation;
    private final long targetMemberId;

    private RecordBatchTransferPlan(RecordBatchMutationPlan mutation, long targetMemberId) {
        this.mutation = mutation;
        this.targetMemberId = targetMemberId;
    }

    static RecordBatchTransferPlan from(RecordRuntimeViews.BatchTransferRequest request) {
        if (request == null) {
            RecordBatchMutationPlan.from(null, 1, "transfer");
            throw new IllegalStateException("Unreachable transfer request validation");
        }
        var mutation = RecordBatchMutationPlan.from(
                new RecordRuntimeViews.BatchCommandRequest(request.items()),
                1,
                "transfer");
        if (request.targetMemberId() == null
                || !request.targetMemberId().matches("^[1-9][0-9]*$")) {
            throw invalidTarget();
        }
        final long targetMemberId;
        try {
            targetMemberId = Long.parseLong(request.targetMemberId());
        } catch (NumberFormatException exception) {
            throw invalidTarget();
        }
        return new RecordBatchTransferPlan(mutation, targetMemberId);
    }

    long targetMemberId() {
        return targetMemberId;
    }

    List<Long> lockOrder() {
        return mutation.lockOrder();
    }

    List<RecordBatchMutationPlan.Selection> requestOrder() {
        return mutation.requestOrder();
    }

    PreparedTransfer preflight(
            List<RecordState> records,
            List<RuntimeRecordTeamOwnershipFacade.TeamOwnership> teams
    ) {
        var recordsById = new HashMap<Long, RecordState>();
        for (var record : records) {
            if (recordsById.putIfAbsent(record.recordId(), record) != null) {
                throw new IllegalArgumentException("Transfer record state ids must be unique");
            }
        }
        var teamsById = new HashMap<Long, RuntimeRecordTeamOwnershipFacade.TeamOwnership>();
        for (var team : teams) {
            if (teamsById.putIfAbsent(team.recordId(), team) != null) {
                throw new IllegalArgumentException("Transfer team state ids must be unique");
            }
        }
        var invalidStateIds = new HashSet<Long>();
        for (var record : records) {
            if (record.ownerMemberId() == targetMemberId) {
                invalidStateIds.add(record.recordId());
            }
            var team = teamsById.get(record.recordId());
            if (team != null && (team.ownerMemberId() != record.ownerMemberId()
                    || !team.targetMemberAdditionAllowed())) {
                invalidStateIds.add(record.recordId());
            }
        }
        var prepared = mutation.preflight(
                records.stream().map(record -> new RecordBatchMutationPlan.RecordState(
                        record.recordId(),
                        record.version(),
                        record.status())).toList(),
                Set.of("ACTIVE"),
                invalidStateIds);
        return new PreparedTransfer(prepared);
    }

    record RecordState(
            long recordId,
            long version,
            String status,
            long ownerMemberId,
            Long ownerDepartmentId
    ) { }

    final class PreparedTransfer {
        private final RecordBatchMutationPlan.PreparedBatch prepared;

        private PreparedTransfer(RecordBatchMutationPlan.PreparedBatch prepared) {
            this.prepared = prepared;
        }

        RecordRuntimeViews.BatchMutationResponse apply(LongFunction<Long> transfer) {
            return prepared.apply("ACTIVE", transfer);
        }
    }

    private static BusinessException invalidTarget() {
        return new BusinessException(
                "BATCH_TRANSFER_TARGET_INVALID",
                "Batch transfer target must be a positive active tenant member id",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
