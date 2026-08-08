package com.unique.examine.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRecordMemberFieldFacadeTest {

    @Test
    void catalogAndRequestsKeepStablePublishedFieldIdentity() {
        var field = new RuntimeRecordMemberFieldFacade.EligibleField(
                501L, "reviewer", "Reviewer");
        var catalog = new RuntimeRecordMemberFieldFacade.PublishedFieldCatalog(
                11L, 101L, 201L, 201L, "work_order", List.of(field));
        var current = new RuntimeRecordMemberFieldFacade.CurrentRecordRequest(
                11L, 22L, "work_order", 301L, 501L);
        var source = new LinkedHashMap<String, String>();
        source.put("reviewer", "\"701\"");
        var snapshot = new RuntimeRecordMemberFieldFacade.SnapshotRequest(
                11L, 22L, "work_order", 501L, source);

        source.put("reviewer", "\"702\"");

        assertThat(catalog.fields()).containsExactly(field);
        assertThat(current.fieldId()).isEqualTo(501L);
        assertThat(snapshot.valuesJson()).containsEntry("reviewer", "\"701\"");
        assertThatThrownBy(() -> snapshot.valuesJson().put("reviewer", "\"703\""))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidContextAndCatalogShapes() {
        assertThatThrownBy(() -> new RuntimeRecordMemberFieldFacade.CurrentRecordRequest(
                11L, 0L, "work_order", 301L, 501L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new RuntimeRecordMemberFieldFacade.SnapshotRequest(
                11L, 22L, "invalid-code!", 501L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module code");
        assertThatThrownBy(() -> new RuntimeRecordMemberFieldFacade.EligibleField(
                -1L, "reviewer", "Reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fieldId");
        assertThatThrownBy(() -> new RuntimeRecordMemberFieldFacade.PublishedFieldCatalog(
                11L, 101L, 201L, 201L, "work_order",
                Arrays.asList((RuntimeRecordMemberFieldFacade.EligibleField) null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalog");
    }

    @Test
    void resolutionNeverExposesMemberForFailureStatus() {
        assertThat(RuntimeRecordMemberFieldFacade.Resolution.resolved(701L))
                .isEqualTo(new RuntimeRecordMemberFieldFacade.Resolution(
                        RuntimeRecordMemberFieldFacade.Status.RESOLVED, 701L));
        assertThat(RuntimeRecordMemberFieldFacade.Resolution.sourceMissing().memberId()).isNull();
        assertThat(RuntimeRecordMemberFieldFacade.Resolution.sourceEmpty().memberId()).isNull();
        assertThat(RuntimeRecordMemberFieldFacade.Resolution.sourceInvalid().memberId()).isNull();
        assertThat(RuntimeRecordMemberFieldFacade.Resolution.memberInactive().memberId()).isNull();

        assertThatThrownBy(() -> new RuntimeRecordMemberFieldFacade.Resolution(
                RuntimeRecordMemberFieldFacade.Status.SOURCE_INVALID, 701L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuntimeRecordMemberFieldFacade.Resolution.resolved(0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
