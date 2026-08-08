package com.unique.examine.module.runtime.query;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RecordNeighborQueryBuilder {
    private static final String ANCHOR_ALIAS_PREFIX = "sort_anchor_";

    public NeighborQuery locate(
            String whereSql,
            List<Object> whereArguments,
            RecordQueryModels.CompiledQuery compiled,
            String direction,
            long recordId
    ) {
        var function = switch (direction) {
            case "PREVIOUS" -> "LAG";
            case "NEXT" -> "LEAD";
            default -> throw new IllegalArgumentException("Neighbor direction must be PREVIOUS or NEXT");
        };
        var sql = "SELECT query_bound.neighbor_id FROM ("
                + "SELECT r.record_id," + function + "(r.record_id) OVER (ORDER BY "
                + compiled.orderSql() + ") AS neighbor_id "
                + "FROM un_module_record r WHERE " + whereSql
                + ") query_bound WHERE query_bound.record_id=?";
        var arguments = new ArrayList<Object>();
        arguments.addAll(compiled.orderArguments());
        arguments.addAll(whereArguments);
        arguments.add(recordId);
        return new NeighborQuery(sql, arguments);
    }

    public AnchorProjection anchors(List<RecordQueryModels.EffectiveSort> sorts) {
        var expressions = new ArrayList<String>();
        var arguments = new ArrayList<Object>();
        for (var index = 0; index < sorts.size(); index++) {
            var sort = sorts.get(index);
            expressions.add(sort.expressionSql() + " AS " + anchorAlias(index));
            arguments.addAll(sort.arguments());
        }
        return new AnchorProjection(
                expressions.isEmpty() ? "" : "," + String.join(",", expressions),
                arguments);
    }

    public static String anchorAlias(int index) {
        return ANCHOR_ALIAS_PREFIX + index;
    }

    public record NeighborQuery(String sql, List<Object> arguments) {
        public NeighborQuery {
            arguments = List.copyOf(arguments);
        }
    }

    public record AnchorProjection(String selectSql, List<Object> arguments) {
        public AnchorProjection {
            arguments = List.copyOf(arguments);
        }
    }
}
