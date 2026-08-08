package com.unique.examine.module.datasource.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiModuleJoinDraftTest {
    @Test
    void pinsExactPublicationsAndNamespacesEveryOutput() {
        var draft = draft("orders__amount");

        assertThat(draft.sourceKind())
                .isEqualTo(DataSourceDraft.SourceKind.MULTI_MODULE_JOIN);
        assertThat(draft.canonicalForm())
                .contains("MULTI_MODULE_JOIN", "101", "1001", "202", "2002",
                        "customers__id", "orders__amount", "ONE_TO_MANY");
    }

    @Test
    void rejectsOutputCollisionAndNonLeftDeepPlan() {
        assertThatThrownBy(() -> draft("customers__amount"))
                .isInstanceOf(DataSourceException.class)
                .hasMessageContaining("namespace");

        assertThatThrownBy(() -> new DataSourceDraft.MultiModuleJoin(
                List.of(
                        new DataSourceDraft.JoinInput("customers", 101, 1001),
                        new DataSourceDraft.JoinInput("orders", 202, 2002)),
                List.of(new DataSourceDraft.JoinEdge(
                        "orders", "customerId", "customers", "id",
                        DataSourceDraft.JoinType.INNER,
                        DataSourceDraft.JoinCardinality.MANY_TO_ONE)),
                List.of(new DataSourceDraft.JoinProjection(
                        "customers", "id", "customers__id")),
                DataSourceDraft.JoinFailureMode.FAIL_FAST, 3, 50))
                .isInstanceOf(DataSourceException.class)
                .hasMessageContaining("left-deep");
    }

    private static DataSourceDraft draft(String orderOutputCode) {
        return new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.MULTI_MODULE_JOIN,
                null, List.of(), null, List.of(),
                new DataSourceDraft.MultiModuleJoin(
                        List.of(
                                new DataSourceDraft.JoinInput(
                                        "customers", 101, 1001),
                                new DataSourceDraft.JoinInput(
                                        "orders", 202, 2002)),
                        List.of(new DataSourceDraft.JoinEdge(
                                "customers", "id", "orders", "customerId",
                                DataSourceDraft.JoinType.LEFT,
                                DataSourceDraft.JoinCardinality.ONE_TO_MANY)),
                        List.of(
                                new DataSourceDraft.JoinProjection(
                                        "customers", "id", "customers__id"),
                                new DataSourceDraft.JoinProjection(
                                        "orders", "amount", orderOutputCode)),
                        DataSourceDraft.JoinFailureMode.ALLOW_PARTIAL_LEFT,
                        3, 50));
    }
}
