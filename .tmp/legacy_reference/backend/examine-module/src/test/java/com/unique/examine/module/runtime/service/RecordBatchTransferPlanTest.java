package com.unique.examine.module.runtime.service;

import com.unique.examine.core.api.RuntimeRecordTeamOwnershipFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecordBatchTransferPlanTest {
    @Test
    void locksAndAppliesInNumericOrderButReturnsInRequestOrder() {
        var plan = RecordBatchTransferPlan.from(request(
                "50",
                ref("20", 2),
                ref("3", 4)));
        assertThat(plan.lockOrder()).containsExactly(3L, 20L);

        var prepared = plan.preflight(
                List.of(
                        state(3, 4, "ACTIVE", 10),
                        state(20, 2, "ACTIVE", 12)),
                List.of(team(20, 12, false, true)));
        var appliedOrder = new ArrayList<Long>();
        var response = prepared.apply(recordId -> {
            appliedOrder.add(recordId);
            return recordId + 100;
        });

        assertThat(appliedOrder).containsExactly(3L, 20L);
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::newVersion)
                .containsExactly(120L, 103L);
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::status)
                .containsOnly("ACTIVE");
    }

    @Test
    void fullPreflightCombinesRecordAndTeamFailuresBeforeAnyApply() {
        var plan = RecordBatchTransferPlan.from(request(
                "50",
                ref("20", 2),
                ref("3", 4),
                ref("11", 6),
                ref("30", 9),
                ref("40", 10)));
        var exception = catchThrowableOfType(
                () -> plan.preflight(
                        List.of(
                                state(20, 8, "ACTIVE", 10),
                                state(11, 6, "DRAFT", 10),
                                state(30, 9, "ACTIVE", 50),
                                state(40, 10, "ACTIVE", 10)),
                        List.of(
                                team(40, 99, false, true))),
                BusinessException.class);

        assertThat(exception.code()).isEqualTo("BATCH_PRECONDITION_FAILED");
        assertThat(exception.status().value()).isEqualTo(409);
        var response = (RecordRuntimeViews.BatchMutationResponse) exception.data();
        assertThat(response.allApplied()).isFalse();
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3", "11", "30", "40");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::resultCode)
                .containsExactly(
                        "VERSION_STALE",
                        "RECORD_NOT_FOUND",
                        "STATE_INVALID",
                        "STATE_INVALID",
                        "STATE_INVALID");
    }

    @Test
    void validatesPositiveVersionsUniqueItemsAndDedicatedTargetCode() {
        assertInvalid(request("2", ref("1", 0)), "BATCH_INVALID");
        assertInvalid(request("2", ref("1", 1), ref("1", 2)), "BATCH_INVALID");
        assertInvalid(request("0", ref("1", 1)), "BATCH_TRANSFER_TARGET_INVALID");
        assertInvalid(request("not-an-id", ref("1", 1)), "BATCH_TRANSFER_TARGET_INVALID");
        assertInvalid(null, "BATCH_INVALID");
    }

    @Test
    void updateSqlChangesOnlyOwnershipActorTimeAndVersionWithFullCasBoundary() {
        assertThat(RecordBatchTransferPlan.UPDATE_RECORD_SQL)
                .contains("SET owner_member_id=?,owner_department_id=?")
                .contains("updated_at=?,updated_by=?,version=version+1")
                .contains("system_id=? AND tenant_id=? AND logical_module_id=?")
                .contains("schema_version_id=? AND module_snapshot_id=? AND record_id=?")
                .contains("status='ACTIVE' AND owner_member_id=? AND version=?")
                .doesNotContain(
                        "SET status=",
                        "un_module_record_value",
                        "un_module_record_index",
                        "un_module_record_search",
                        "un_module_record_unique",
                        "un_module_record_reference");
    }

    private static void assertInvalid(
            RecordRuntimeViews.BatchTransferRequest request,
            String code
    ) {
        var exception = catchThrowableOfType(
                () -> RecordBatchTransferPlan.from(request),
                BusinessException.class);
        assertThat(exception.code()).isEqualTo(code);
        assertThat(exception.status().value()).isEqualTo(422);
    }

    private static RecordRuntimeViews.BatchTransferRequest request(
            String targetMemberId,
            RecordRuntimeViews.BatchRecordRef... items
    ) {
        return new RecordRuntimeViews.BatchTransferRequest(List.of(items), targetMemberId);
    }

    private static RecordRuntimeViews.BatchRecordRef ref(String id, long version) {
        return new RecordRuntimeViews.BatchRecordRef(id, version);
    }

    private static RecordBatchTransferPlan.RecordState state(
            long recordId,
            long version,
            String status,
            long ownerMemberId
    ) {
        return new RecordBatchTransferPlan.RecordState(
                recordId, version, status, ownerMemberId, null);
    }

    private static RuntimeRecordTeamOwnershipFacade.TeamOwnership team(
            long recordId,
            long ownerMemberId,
            boolean targetPresent,
            boolean additionAllowed
    ) {
        return new RuntimeRecordTeamOwnershipFacade.TeamOwnership(
                recordId, ownerMemberId, targetPresent, additionAllowed);
    }
}
