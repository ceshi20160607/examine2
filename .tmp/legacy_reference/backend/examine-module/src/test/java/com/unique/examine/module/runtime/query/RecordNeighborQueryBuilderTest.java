package com.unique.examine.module.runtime.query;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.module.runtime.query.RecordQueryModels.QueryField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordNeighborQueryBuilderTest {
    private final RecordQueryParser parser = new RecordQueryParser();
    private final RecordQueryCompiler compiler = new RecordQueryCompiler();
    private final RecordNeighborQueryBuilder builder = new RecordNeighborQueryBuilder();

    @Test
    void preservesEffectiveSortAndRecordIdTieBreakerForPreviousAndNext() {
        var moneySchema = JsonNodeFactory.instance.objectNode();
        moneySchema.putArray("currencies").add("CNY");
        var query = parser.parse("""
                {"schemaVersionId":"41","page":3,"size":20,"recordScope":"active","q":null,
                 "filter":null,
                 "sort":[{"fieldCode":"amount","direction":"DESC","nulls":"LAST","currency":"CNY"}],
                 "columns":["amount"],"viewId":null}
                """);
        var compiled = compiler.compile(
                query,
                List.of(new QueryField(71, "amount", "MONEY", false, true, true, moneySchema)));

        assertThat(compiled.effectiveSorts()).singleElement().satisfies(sort -> {
            assertThat(sort.direction()).isEqualTo("DESC");
            assertThat(sort.nulls()).isEqualTo("LAST");
            assertThat(sort.valueType()).isEqualTo("DECIMAL");
            assertThat(sort.arguments()).containsExactly("CNY");
        });
        assertThat(compiled.orderSql()).endsWith("r.record_id ASC");

        var previous = builder.locate(
                "r.system_id=? AND current_view_scope AND " + compiled.predicateSql(),
                List.of(11L),
                compiled,
                "PREVIOUS",
                900L);
        assertThat(previous.sql())
                .contains("LAG(r.record_id) OVER (ORDER BY ")
                .contains("r.record_id ASC")
                .contains("current_view_scope")
                .endsWith("query_bound.record_id=?");
        assertThat(previous.arguments()).containsExactly("CNY", "CNY", 11L, 900L);

        var next = builder.locate(
                "r.system_id=? AND current_view_scope",
                List.of(11L),
                compiled,
                "NEXT",
                900L);
        assertThat(next.sql()).contains("LEAD(r.record_id) OVER (ORDER BY ");

        var anchors = builder.anchors(compiled.effectiveSorts());
        assertThat(anchors.selectSql()).contains(" AS sort_anchor_0");
        assertThat(anchors.arguments()).containsExactly("CNY");
    }

    @Test
    void suppliesTheFrozenDefaultOrderingWhenNoClientSortExists() {
        var query = parser.parse("""
                {"schemaVersionId":"41","page":1,"size":20,"recordScope":"active","q":null,
                 "filter":null,"sort":[],"columns":[],"viewId":null}
                """);
        var compiled = compiler.compile(query, List.of());

        assertThat(compiled.effectiveSorts()).singleElement().satisfies(sort -> {
            assertThat(sort.expressionSql()).isEqualTo("r.updated_at");
            assertThat(sort.direction()).isEqualTo("DESC");
            assertThat(sort.valueType()).isEqualTo("DATETIME");
        });
        assertThat(compiled.orderSql()).isEqualTo("r.updated_at DESC,r.record_id ASC");
    }
}
