package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RecordBatchEditPlanTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesBoundedUniqueItemsAndSetClearShape() {
        assertInvalid(null);
        assertInvalid(request(List.of(ref("1", 0)), clear("note")));
        assertInvalid(request(List.of(ref("1", 1), ref("1", 2)), clear("note")));
        assertInvalid(request(List.of(ref("1", 1))));
        assertInvalid(request(List.of(ref("1", 1)), set("note", null)));
        assertInvalid(request(List.of(ref("1", 1)),
                new RecordRuntimeViews.BatchFieldChange(
                        "note", "CLEAR", objectMapper.getNodeFactory().textNode("unexpected"))));
        assertInvalid(request(List.of(ref("1", 1)),
                new RecordRuntimeViews.BatchFieldChange(
                        "note", "ADD", objectMapper.getNodeFactory().textNode("value"))));
        assertInvalid(request(List.of(ref("1", 1)), clear("note"), clear("note")));

        var tooMany = new ArrayList<RecordRuntimeViews.BatchFieldChange>();
        for (var index = 0; index < 21; index++) {
            tooMany.add(clear("field_" + index));
        }
        assertInvalid(new RecordRuntimeViews.BatchEditRequest(List.of(ref("1", 1)), tooMany));
    }

    @Test
    void preservesSetClearAndLocksNumerically() {
        var plan = RecordBatchEditPlan.from(request(
                List.of(ref("20", 2), ref("3", 4)),
                set("priority", objectMapper.getNodeFactory().textNode("HIGH")),
                clear("note")));

        assertThat(plan.lockOrder()).containsExactly(3L, 20L);
        assertThat(plan.changes()).extracting(RecordBatchEditPlan.Change::fieldCode)
                .containsExactly("priority", "note");
        assertThat(plan.changes()).extracting(RecordBatchEditPlan.Change::operation)
                .containsExactly(RecordBatchEditPlan.Operation.SET, RecordBatchEditPlan.Operation.CLEAR);
    }

    @Test
    void fullPreflightReportsMissingVersionStateAndStaleSchemaInRequestOrder() {
        var plan = RecordBatchEditPlan.from(request(
                List.of(
                        ref("20", 2),
                        ref("3", 4),
                        ref("11", 6),
                        ref("30", 9)),
                clear("note")));
        var exception = catchThrowableOfType(
                () -> plan.preflight(List.of(
                        state(20, 8, "ACTIVE", true),
                        state(11, 6, "ARCHIVED", true),
                        state(30, 9, "ACTIVE", false))),
                BusinessException.class);

        assertThat(exception.code()).isEqualTo("BATCH_PRECONDITION_FAILED");
        assertThat(exception.status().value()).isEqualTo(409);
        var response = (RecordRuntimeViews.BatchMutationResponse) exception.data();
        assertThat(response.allApplied()).isFalse();
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3", "11", "30");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::resultCode)
                .containsExactly("VERSION_STALE", "RECORD_NOT_FOUND", "STATE_INVALID", "STATE_INVALID");
    }

    @Test
    void appliesNumericallyAndReturnsActiveResultsInRequestOrder() {
        var plan = RecordBatchEditPlan.from(request(
                List.of(ref("20", 2), ref("3", 4)),
                clear("note")));
        var applied = new ArrayList<Long>();
        var response = plan.preflight(List.of(
                        state(3, 4, "ACTIVE", true),
                        state(20, 2, "ACTIVE", true)))
                .apply(recordId -> {
                    applied.add(recordId);
                    return recordId + 100;
                });

        assertThat(applied).containsExactly(3L, 20L);
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::recordId)
                .containsExactly("20", "3");
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::newVersion)
                .containsExactly(120L, 103L);
        assertThat(response.items()).extracting(RecordRuntimeViews.BatchMutationItem::status)
                .containsOnly("ACTIVE");
    }

    @Test
    void sqlContractsCasHeaderAndDeleteOnlyExplicitTouchedIds() {
        assertThat(RecordBatchEditPlan.UPDATE_RECORD_SQL)
                .contains("SET updated_at=?,updated_by=?,version=version+1")
                .contains("system_id=? AND tenant_id=? AND logical_module_id=?")
                .contains("schema_version_id=? AND module_snapshot_id=? AND record_id=?")
                .contains("status='ACTIVE' AND version=?")
                .doesNotContain(
                        "SET title=",
                        "SET status=",
                        "owner_member_id=",
                        "owner_department_id=");
        assertThat(RecordBatchEditPlan.DELETE_TOUCHED_VALUE_SQL)
                .contains("record_id=?")
                .contains("field_snapshot_id IN (%s)");
        assertThat(RecordBatchEditPlan.DELETE_TOUCHED_INDEX_SQL)
                .contains("record_id=?")
                .contains("logical_field_id IN (%s)");
        assertThat(RecordBatchEditPlan.DELETE_TOUCHED_SEARCH_SQL)
                .contains("record_id=?")
                .contains("logical_field_id IN (%s)");
        assertThat(RecordBatchEditPlan.DELETE_TOUCHED_UNIQUE_SQL)
                .contains("record_id IN (%s)")
                .contains("logical_field_id IN (%s)");
    }

    private void assertInvalid(RecordRuntimeViews.BatchEditRequest request) {
        var exception = catchThrowableOfType(
                () -> RecordBatchEditPlan.from(request),
                BusinessException.class);
        assertThat(exception.code()).isEqualTo("BATCH_EDIT_INVALID");
        assertThat(exception.status().value()).isEqualTo(422);
    }

    private static RecordRuntimeViews.BatchEditRequest request(
            List<RecordRuntimeViews.BatchRecordRef> refs,
            RecordRuntimeViews.BatchFieldChange... changes
    ) {
        return new RecordRuntimeViews.BatchEditRequest(refs, List.of(changes));
    }

    private static RecordRuntimeViews.BatchRecordRef ref(String recordId, long version) {
        return new RecordRuntimeViews.BatchRecordRef(recordId, version);
    }

    private static RecordRuntimeViews.BatchFieldChange clear(String fieldCode) {
        return new RecordRuntimeViews.BatchFieldChange(fieldCode, "CLEAR", null);
    }

    private static RecordRuntimeViews.BatchFieldChange set(String fieldCode, com.fasterxml.jackson.databind.JsonNode value) {
        return new RecordRuntimeViews.BatchFieldChange(fieldCode, "SET", value);
    }

    private static RecordBatchEditPlan.RecordState state(
            long recordId,
            long version,
            String status,
            boolean currentSchema
    ) {
        return new RecordBatchEditPlan.RecordState(recordId, version, status, currentSchema);
    }
}
